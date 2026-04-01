package org.productivity_buddy.workers;

import javafx.application.Platform;
import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsWorker implements Runnable {

    private final ProcessRegistry registry;
    private final List<String> fixedSnapshotTimes;
    private volatile boolean running;

    // poslednji izracunati rezultati — UI nit ih cita
    private volatile Map<String, Long> timeByCategory;
    private volatile List<Map.Entry<String, Long>> top10Processes;

    // callback za UI update — poziva se na UI niti
    private Runnable onUpdate;
    // callback za snapshot — poziva se kada dodje fiksni termin
    private Runnable onSnapshotTriggered;

    public AnalyticsWorker(ProcessRegistry registry, List<String> fixedSnapshotTimes) {
        this.registry = registry;
        this.fixedSnapshotTimes = fixedSnapshotTimes;
        this.running = true;
        this.timeByCategory = new HashMap<>();
        this.top10Processes = new ArrayList<>();
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void setOnSnapshotTriggered(Runnable onSnapshotTriggered) {
        this.onSnapshotTriggered = onSnapshotTriggered;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(2000); // analiziraj svake 2 sekunde
                analyze();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void analyze() {
        // 1. AGREGACIJA VREMENA PO KATEGORIJAMA
        Map<String, Long> catTime = new HashMap<>();
        Map<String, Long> processTime = new HashMap<>();

        for (ProcessInfo info : registry.getAll()) {
            if(info.getCategory() == "Uncategorized") continue; // preskoci nekategorizovane procese

            long time = info.getEffectiveTotalTime();
            String cat = info.getCategory();

            // saberi vreme za kategoriju
            Long existing = catTime.get(cat);
            if (existing == null) {
                catTime.put(cat, time);
            } else {
                catTime.put(cat, existing + time);
            }

            // sacuvaj vreme za proces (za Top 10)
            processTime.put(info.getOriginalName(), time);
        }

        // 2. TOP 10 PROCESA PO UTROSENOM VREMENU
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(processTime.entrySet());
        sorted.sort(new java.util.Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        if (sorted.size() > 10) {
            sorted = sorted.subList(0, 10);
        }

        // 3. SACUVAJ REZULTATE (volatile polja)
        this.timeByCategory = catTime;
        this.top10Processes = sorted;

        // 4. OBAVESTI UI — Platform.runLater garantuje izvrsavanje na UI niti
        if (onUpdate != null) {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    onUpdate.run();
                }
            });
        }

        // 5. PROVERI FIKSNE TERMINE ZA SNAPSHOT
        checkFixedSnapshotTimes();
    }

    private void checkFixedSnapshotTimes() {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        for (int i = 0; i < fixedSnapshotTimes.size(); i++) {
            if (now.equals(fixedSnapshotTimes.get(i))) {
                if (onSnapshotTriggered != null) {
                    onSnapshotTriggered.run();
                }
            }
        }
    }

    public Map<String, Long> getTimeByCategory() {
        return timeByCategory;
    }

    public List<Map.Entry<String, Long>> getTop10Processes() {
        return top10Processes;
    }

    public void stop() {
        this.running = false;
    }
}
