package org.productivity_buddy;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AppConfig {

    private long monitorInterval;
    private String mappingFile;
    private long snapshotInterval;
    private List<String> fixedSnapshotTimes;

    public AppConfig(String configPath) {
        // default vrednosti
        this.monitorInterval = 3000;
        this.mappingFile = "data/process_info.json";
        this.snapshotInterval = 60;
        this.fixedSnapshotTimes = new ArrayList<>();

        try {
            InputStream input = new FileInputStream(configPath);
            Properties props = new Properties();
            props.load(input);
            input.close();

            String intervalStr = props.getProperty("monitor.interval", "3000");
            this.monitorInterval = Long.parseLong(intervalStr);

            this.mappingFile = props.getProperty("mapping.file", "process_info.json");

            String snapshotStr = props.getProperty("snapshot.interval", "60");
            this.snapshotInterval = Long.parseLong(snapshotStr);

            // ucitaj sve fiksne termine (snapshot.fixed_time_1, _2, itd.)
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("snapshot.fixed_time")) {
                    fixedSnapshotTimes.add(props.getProperty(key));
                }
            }

        } catch (IOException e) {
            System.out.println("config.properties nije pronadjen, koristim default vrednosti.");
        }
    }

    public long getMonitorInterval() { return monitorInterval; }
    public String getMappingFile() { return mappingFile; }
    public long getSnapshotInterval() { return snapshotInterval; }
    public List<String> getFixedSnapshotTimes() { return fixedSnapshotTimes; }
}
