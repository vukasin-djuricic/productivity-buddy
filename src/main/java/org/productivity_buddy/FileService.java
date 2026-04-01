package org.productivity_buddy;

import java.io.IOException;
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
                        json.append("      \"originalName\": \"").append(info.getOriginalName()).append("\",\n");
                        json.append("      \"aliasName\": \"").append(info.getAliasName()).append("\",\n");
                        json.append("      \"category\": \"").append(info.getCategory()).append("\",\n");
                        json.append("      \"isTrackingFreezed\": ").append(info.isFrozen()).append(",\n");
                        json.append("      \"totalTimeSeconds\": ").append(totalTime).append("\n");
                        json.append("    }");
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
        String[] blocks = content.split("\\{");

        for (String block : blocks) {
            if (!block.contains("originalName")) {
                continue;
            }

            String origName = extractStringValue(block, "originalName");
            String alias = extractStringValue(block, "aliasName");
            String category = extractStringValue(block, "category");
            boolean frozen = block.contains("\"isTrackingFreezed\": true")
                    || block.contains("\"isTrackingFreezed\":true");
            long totalTime = extractLongValue(block, "totalTimeSeconds");

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
                    String fileName = "data/snapshot_" + timestamp + ".csv";

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
