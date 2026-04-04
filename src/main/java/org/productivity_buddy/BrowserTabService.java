package org.productivity_buddy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servis koji koristi AppleScript (macOS) da dohvati naslove i URL-ove
 * otvorenih tabova iz poznatih browsera (Chrome, Safari, Firefox, Arc, Brave, Edge).
 */
public class BrowserTabService {

    private final CategorizationService categorizationService;

    // mapa: ime procesa u registru -> AppleScript application ime
    private static final Map<String, String> BROWSER_SCRIPTS;
    static {
        Map<String, String> map = new HashMap<>();
        // Chrome i Chromium varijante koriste isti AppleScript model
        map.put("Google Chrome", "Google Chrome");
        map.put("Google Chrome Helper", "Google Chrome");
        map.put("Brave Browser", "Brave Browser");
        map.put("Microsoft Edge", "Microsoft Edge");
        map.put("Arc", "Arc");
        map.put("Vivaldi", "Vivaldi");
        map.put("Opera", "Opera");
        // Safari ima drugaciji AppleScript model
        map.put("Safari", "Safari");
        BROWSER_SCRIPTS = Collections.unmodifiableMap(map);
    }

    public BrowserTabService(CategorizationService categorizationService) {
        this.categorizationService = categorizationService;
    }

    /**
     * Proveri da li je proces browser koji podrzavamo.
     */
    public boolean isSupportedBrowser(String processName) {
        return BROWSER_SCRIPTS.containsKey(processName);
    }

    /**
     * Dohvati tabove za dati browser proces.
     * Vraca praznu listu ako browser nije pokrenut ili AppleScript ne uspe.
     */
    public List<TabInfo> getTabsForBrowser(String processName) {
        String appName = BROWSER_SCRIPTS.get(processName);
        if (appName == null) return Collections.emptyList();

        try {
            String script;
            if ("Safari".equals(appName)) {
                script = buildSafariScript();
            } else {
                // Chromium-based browseri svi koriste isti model
                script = buildChromiumScript(appName);
            }

            return executeAppleScript(script);
        } catch (Exception e) {
            // browser mozda nije pokrenut ili nema dozvolu
            return Collections.emptyList();
        }
    }

    /**
     * Dohvati tabove za sve pokrenute browsere iz registra.
     * Vraca mapu: processName -> List<TabInfo>
     */
    public Map<String, List<TabInfo>> getAllBrowserTabs(ProcessRegistry registry) {
        Map<String, List<TabInfo>> result = new HashMap<>();

        for (ProcessInfo pi : registry.getAll()) {
            if (!pi.isAlive()) continue;
            String name = pi.getOriginalName();
            if (isSupportedBrowser(name)) {
                List<TabInfo> tabs = getTabsForBrowser(name);
                if (!tabs.isEmpty()) {
                    // kategorizuj svaki tab
                    for (TabInfo tab : tabs) {
                        // pokusaj naslov, pa domen
                        ProcessCategory cat = categorizationService.categorize(tab.getTitle());
                        if (cat == ProcessCategory.UNCATEGORIZED && !tab.getDomain().isEmpty()) {
                            cat = categorizationService.categorize(tab.getDomain());
                        }
                        tab.setCategory(cat);
                    }
                    result.put(name, tabs);
                }
            }
        }

        return result;
    }

    // --- AppleScript generatori ---

    private String buildChromiumScript(String appName) {
        // format izlaza: title|url za svaki tab, razdvojeni novim redom
        return "tell application \"" + appName + "\"\n"
                + "  set output to \"\"\n"
                + "  repeat with w in windows\n"
                + "    repeat with t in tabs of w\n"
                + "      set output to output & title of t & \"|\" & URL of t & \"\n\"\n"
                + "    end repeat\n"
                + "  end repeat\n"
                + "  return output\n"
                + "end tell";
    }

    private String buildSafariScript() {
        return "tell application \"Safari\"\n"
                + "  set output to \"\"\n"
                + "  repeat with w in windows\n"
                + "    repeat with t in tabs of w\n"
                + "      set output to output & name of t & \"|\" & URL of t & \"\n\"\n"
                + "    end repeat\n"
                + "  end repeat\n"
                + "  return output\n"
                + "end tell";
    }

    // --- Izvrsavanje AppleScript-a ---

    private List<TabInfo> executeAppleScript(String script) {
        List<TabInfo> tabs = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) return tabs;

            // parsiraj izlaz: svaki red je "title|url"
            String[] lines = output.toString().split("\n");
            for (String entry : lines) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                int separator = entry.indexOf('|');
                if (separator > 0) {
                    String title = entry.substring(0, separator).trim();
                    String url = entry.substring(separator + 1).trim();
                    if (!title.isEmpty()) {
                        tabs.add(new TabInfo(title, url));
                    }
                } else {
                    // samo naslov, bez URL-a
                    if (!entry.isEmpty()) {
                        tabs.add(new TabInfo(entry, ""));
                    }
                }
            }
        } catch (Exception e) {
            // osascript nije dostupan ili browser nema AppleScript podrsku
        }
        return tabs;
    }
}
