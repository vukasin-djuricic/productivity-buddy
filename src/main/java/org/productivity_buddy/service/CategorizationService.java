package org.productivity_buddy.service;

import org.productivity_buddy.model.ProcessCategory;
import org.productivity_buddy.model.ProcessInfo;
import org.productivity_buddy.model.ProcessRegistry;
import org.productivity_buddy.model.TabInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class CategorizationService {

    private final String rulesFilePath;
    private volatile List<CompiledRule> rules;
    private volatile ProcessRegistry registry;
    private volatile Runnable onRulesReloaded;

    public CategorizationService(String rulesFilePath) {
        this.rulesFilePath = rulesFilePath;
        this.rules = Collections.emptyList();
    }

    // postavlja referencu na registry da bi reloadRules mogao da re-kategorise postojece podatke
    public void setRegistry(ProcessRegistry registry) {
        this.registry = registry;
    }

    // callback koji se okida posle reload-a (npr. za UI refresh)
    public void setOnRulesReloaded(Runnable callback) {
        this.onRulesReloaded = callback;
    }

    // ucitaj pravila iz JSON fajla
    public void loadRules() {
        try {
            String content = Files.readString(Path.of(rulesFilePath));
            List<CompiledRule> newRules = parseRules(content);
            this.rules = newRules;
            System.out.println("Ucitano " + newRules.size() + " pravila za kategorizaciju.");
        } catch (IOException e) {
            System.err.println("Greska pri citanju pravila: " + e.getMessage());
        }
    }

    // matchuj ime procesa protiv svih pravila — prvi match odlucuje
    public ProcessCategory categorize(String processName) {
        List<CompiledRule> currentRules = this.rules;
        for (CompiledRule rule : currentRules) {
            if (rule.pattern.matcher(processName).find()) {
                return rule.category;
            }
        }
        return ProcessCategory.UNCATEGORIZED;
    }

    // ponovo ucitaj pravila i re-kategorise sve procese i tabove u registry-ju
    public void reloadRules() {
        loadRules();
        recategorizeAll();
        if (onRulesReloaded != null) {
            onRulesReloaded.run();
        }
    }

    // prodji kroz sve procese i tabove pa primeni nova pravila
    private void recategorizeAll() {
        if (registry == null) return;
        for (ProcessInfo info : registry.getAll()) {
            // re-kategorise proces
            ProcessCategory procCat = categorize(info.getOriginalName());
            info.setCategory(procCat);

            // re-kategorise sve trajno pracene tabove (tabTimeMap)
            for (TabInfo tab : info.getTrackedTabs()) {
                ProcessCategory tabCat = categorize(tab.getTitle());
                if (tabCat == ProcessCategory.UNCATEGORIZED && !tab.getDomain().isEmpty()) {
                    tabCat = categorize(tab.getDomain());
                }
                tab.setCategory(tabCat);
            }

            // re-kategorise i transient tab listu (info.tabs)
            for (TabInfo tab : info.getTabs()) {
                ProcessCategory tabCat = categorize(tab.getTitle());
                if (tabCat == ProcessCategory.UNCATEGORIZED && !tab.getDomain().isEmpty()) {
                    tabCat = categorize(tab.getDomain());
                }
                tab.setCategory(tabCat);
            }
        }
    }

    public String getRulesFilePath() {
        return rulesFilePath;
    }

    // --- PARSIRANJE JSON-a (isti pristup kao FileService) ---

    private List<CompiledRule> parseRules(String json) {
        List<CompiledRule> result = new ArrayList<>();

        int rulesStart = json.indexOf("\"rules\"");
        if (rulesStart < 0) return result;

        int arrayStart = json.indexOf("[", rulesStart);
        if (arrayStart < 0) return result;

        int idx = arrayStart;
        while (true) {
            int blockStart = json.indexOf("{", idx);
            if (blockStart < 0) break;
            int blockEnd = json.indexOf("}", blockStart);
            if (blockEnd < 0) break;

            String block = json.substring(blockStart, blockEnd + 1);
            String pattern = extractStringValue(block, "pattern");
            String category = extractStringValue(block, "category");

            if (pattern != null && category != null) {
                try {
                    Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                    ProcessCategory cat = ProcessCategory.fromDisplayName(category);
                    result.add(new CompiledRule(compiled, cat));
                } catch (Exception e) {
                    System.err.println("Neispravno pravilo: " + pattern + " -> " + e.getMessage());
                }
            }

            idx = blockEnd + 1;
        }

        return result;
    }

    private String extractStringValue(String block, String key) {
        String search = "\"" + key + "\": \"";
        int idx = block.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\":\"";
            idx = block.indexOf(search);
        }
        if (idx < 0) return null;

        int start = idx + search.length();
        int end = block.indexOf("\"", start);
        if (end <= start) return null;

        return block.substring(start, end);
    }

    private static class CompiledRule {
        final Pattern pattern;
        final ProcessCategory category;

        CompiledRule(Pattern pattern, ProcessCategory category) {
            this.pattern = pattern;
            this.category = category;
        }
    }
}
