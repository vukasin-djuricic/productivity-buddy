package org.productivity_buddy.service;

import org.productivity_buddy.config.AppDirs;
import org.productivity_buddy.model.ProcessCategory;
import org.productivity_buddy.model.ProcessInfo;
import org.productivity_buddy.model.ProcessRegistry;
import org.productivity_buddy.model.TabInfo;

import java.io.IOException;
import java.util.Collection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileService {

    private final ProcessRegistry registry;
    private final ExecutorService executor;

    public FileService(ProcessRegistry registry) {
        this.registry = registry;

        this.executor = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FileService-Thread");
                t.setDaemon(true);
                return t;
            }
        });
    }

    // SAVE — snima process_info.json asinhrono
    public void saveProcessInfo(String filePath) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder json = new StringBuilder();
                    json.append("{\n  \"processes\": [\n");

                    boolean first = true;
                    for (ProcessInfo info : registry.getAll()) {
                        if (!first) {
                            json.append(",\n");
                        }
                        first = false;

                        long totalTime = info.getTotalTime() + info.getSessionTime();

                        json.append("    {\n");
                        json.append("      \"originalName\": \"").append(escapeJson(info.getOriginalName())).append("\",\n");
                        json.append("      \"aliasName\": \"").append(escapeJson(info.getAliasName())).append("\",\n");
                        json.append("      \"category\": \"").append(info.getCategory()).append("\",\n");
                        json.append("      \"isTrackingFreezed\": ").append(info.isFrozen()).append(",\n");
                        json.append("      \"totalTimeSeconds\": ").append(totalTime);

                        // sacuvaj tab vremena ako postoje
                        java.util.Collection<TabInfo> trackedTabs = info.getTrackedTabs();
                        if (!trackedTabs.isEmpty()) {
                            json.append(",\n      \"tabTimes\": [\n");
                            boolean firstTab = true;
                            for (TabInfo tab : trackedTabs) {
                                long tabTotal = tab.getTotalTime() + tab.getSessionTime();
                                if (tabTotal <= 0) continue;
                                if (!firstTab) {
                                    json.append(",\n");
                                }
                                firstTab = false;
                                json.append("        {\n");
                                json.append("          \"domain\": \"").append(escapeJson(tab.getDomain())).append("\",\n");
                                json.append("          \"title\": \"").append(escapeJson(tab.getTitle())).append("\",\n");
                                json.append("          \"url\": \"").append(escapeJson(tab.getUrl())).append("\",\n");
                                json.append("          \"category\": \"").append(tab.getCategory()).append("\",\n");
                                json.append("          \"totalTimeSeconds\": ").append(tabTotal).append("\n");
                                json.append("        }");
                            }
                            json.append("\n      ]");
                        }

                        json.append("\n    }");
                    }

                    json.append("\n  ]\n}");
                    Files.writeString(Path.of(filePath), json.toString());
                    System.out.println("Saved: " + filePath);

                } catch (IOException e) {
                    System.err.println("Greska pri snimanju: " + e.getMessage());
                }
            }
        });
    }

    // LOAD — ucitava process_info.json asinhrono
    public void loadProcessInfo(String filePath) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Path path = Path.of(filePath);
                    if (!Files.exists(path)) {
                        System.out.println("Fajl ne postoji: " + filePath);
                        return;
                    }
                    String content = Files.readString(path);
                    parseAndApplyJson(content);
                    System.out.println("Loaded: " + filePath);
                } catch (IOException e) {
                    System.err.println("Greska pri ucitavanju: " + e.getMessage());
                }
            }
        });
    }

    // Parsira JSON i primenjuje na registry
    public void parseAndApplyJson(String content) {
        // izvuci proces blokove pomocu dubine zagrada
        java.util.List<String> processBlocks = extractProcessBlocks(content);

        for (String block : processBlocks) {
            if (!block.contains("originalName")) {
                continue;
            }

            // razdvoji proces polja od tabTimes sekcije
            String processFields = block;
            int tabTimesIdx = block.indexOf("\"tabTimes\"");
            if (tabTimesIdx >= 0) {
                processFields = block.substring(0, tabTimesIdx);
            }

            String origName = extractStringValue(processFields, "originalName");
            String alias = extractStringValue(processFields, "aliasName");
            String category = extractStringValue(processFields, "category");
            boolean frozen = processFields.contains("\"isTrackingFreezed\": true")
                    || processFields.contains("\"isTrackingFreezed\":true");
            long totalTime = extractLongValue(processFields, "totalTimeSeconds");

            if (origName != null) {
                ProcessInfo info = registry.getOrCreate(origName);
                if (alias != null) {
                    info.setAliasName(alias);
                }
                if (category != null) {
                    info.setCategory(category);
                }
                info.setFrozen(frozen);
                info.setTotalTime(totalTime);

                // parsiraj tab vremena ako postoje
                if (tabTimesIdx >= 0) {
                    parseTabTimes(block.substring(tabTimesIdx), info);
                }
            }
        }
    }

    // izvuci blokove procesa iz "processes" niza pomocu dubine zagrada
    private java.util.List<String> extractProcessBlocks(String content) {
        java.util.List<String> blocks = new java.util.ArrayList<>();

        // nadji pocetak "processes" niza
        int arrStart = content.indexOf("[");
        if (arrStart < 0) return blocks;

        int depth = 0;
        int blockStart = -1;
        for (int i = arrStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    blockStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    blocks.add(content.substring(blockStart, i + 1));
                    blockStart = -1;
                }
            }
        }

        return blocks;
    }

    // parsiraj tabTimes niz unutar proces bloka
    private void parseTabTimes(String tabSection, ProcessInfo info) {
        // izvuci svaki tab objekat iz tabTimes niza
        java.util.List<String> tabBlocks = new java.util.ArrayList<>();
        int arrStart = tabSection.indexOf("[");
        if (arrStart < 0) return;

        int depth = 0;
        int blockStart = -1;
        for (int i = arrStart; i < tabSection.length(); i++) {
            char c = tabSection.charAt(i);
            if (c == '{') {
                if (depth == 0) blockStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    tabBlocks.add(tabSection.substring(blockStart, i + 1));
                    blockStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break; // kraj tabTimes niza
            }
        }

        for (String tabBlock : tabBlocks) {
            String domain = extractStringValue(tabBlock, "domain");
            String title = extractStringValue(tabBlock, "title");
            String url = extractStringValue(tabBlock, "url");
            String tabCategory = extractStringValue(tabBlock, "category");
            long tabTime = extractLongValue(tabBlock, "totalTimeSeconds");

            if (domain != null && !domain.isEmpty()) {
                TabInfo tracked = info.getOrCreateTabTime(
                        domain,
                        title != null ? title : domain,
                        url != null ? url : "");
                tracked.setTotalTime(tabTime);
                if (tabCategory != null) {
                    tracked.setCategory(ProcessCategory.fromDisplayName(tabCategory));
                }
            }
        }
    }

    private String extractStringValue(String block, String key) {
        String search = "\"" + key + "\": \"";
        int idx = block.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\":\"";
            idx = block.indexOf(search);
        }
        if (idx < 0) {
            return null;
        }
        int start = idx + search.length();
        int end = block.indexOf("\"", start);
        if (end <= start) {
            return null;
        }
        return block.substring(start, end);
    }

    private long extractLongValue(String block, String key) {
        String search = "\"" + key + "\": ";
        int idx = block.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\":";
            idx = block.indexOf(search);
        }
        if (idx < 0) {
            return 0;
        }
        int start = idx + search.length();
        StringBuilder num = new StringBuilder();
        for (int i = start; i < block.length(); i++) {
            char c = block.charAt(i);
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
        }
        if (num.length() == 0) {
            return 0;
        }
        return Long.parseLong(num.toString());
    }

    // CSV SNAPSHOT
    public void saveSnapshot() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
                    String fileName = AppDirs.resolve("data/snapshot_" + timestamp + ".csv");

                    StringBuilder csv = new StringBuilder();
                    csv.append("timestamp,pid,process_name,cpu_usage,ram_usage,category,alias_name\n");

                    String now = Instant.now().toString();
                    for (ProcessInfo info : registry.getAll()) {
                        csv.append(now).append(",");
                        csv.append(info.getPid()).append(",");
                        csv.append(info.getOriginalName()).append(",");
                        csv.append(info.getCpuUsage()).append(",");
                        csv.append(info.getRamUsageBytes()).append(",");
                        csv.append(info.getCategory()).append(",");
                        csv.append(info.getAliasName()).append("\n");
                    }

                    Files.writeString(Path.of(fileName), csv.toString());
                    System.out.println("Snapshot saved: " + fileName);

                } catch (IOException e) {
                    System.err.println("Greska pri snapshot-u: " + e.getMessage());
                }
            }
        });
    }

    // escapuj specijalne JSON karaktere u stringovima
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // SHUTDOWN — snimi stanje pa uredno ugasi
    public void shutdownAndSave(String filePath) {
        saveProcessInfo(filePath);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("FileService nije zavrsio na vreme!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
