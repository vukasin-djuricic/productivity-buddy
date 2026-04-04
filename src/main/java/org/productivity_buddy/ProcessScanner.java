package org.productivity_buddy;

import org.productivity_buddy.tasks.ScanTask;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProcessScanner {

    private final ProcessRegistry registry;
    private final ForkJoinPool forkJoinPool;
    private final ScheduledExecutorService scheduler;
    private final int chunkSize;
    private final OperatingSystem os;

    // OSHI metrike: PID -> [cpuPercent, ramBytes]
    // Popunjava se jednom pre skeniranja, ScanTask samo cita
    private final ConcurrentHashMap<Long, double[]> oshiMetrics;

    // Prethodni snapshot OSHI procesa — potreban za getProcessCpuLoadBetweenTicks
    private final ConcurrentHashMap<Integer, OSProcess> previousProcesses;

    // brojac ciklusa — full tab enumeration se radi svaki 5. ciklus
    private int scanCycleCount = 0;

    // servis za dohvatanje browser tabova (macOS AppleScript)
    private final BrowserTabService browserTabService;

    public ProcessScanner(ProcessRegistry registry, int chunkSize, long intervalMs,
                          BrowserTabService browserTabService) {
        this.registry = registry;
        this.forkJoinPool = new ForkJoinPool();
        this.chunkSize = chunkSize;
        this.os = new SystemInfo().getOperatingSystem();
        this.oshiMetrics = new ConcurrentHashMap<>();
        this.previousProcesses = new ConcurrentHashMap<>();
        this.browserTabService = browserTabService;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Scanner-Scheduler");
                t.setDaemon(true);
                return t;
            }
        });

        this.scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                scan();
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void scan() {
        try {
            // 1. Prikupi OSHI metrike za sve procese (jednom)
            oshiMetrics.clear();
            List<OSProcess> oshiProcesses = os.getProcesses();
            int logicalProcessorCount = new SystemInfo().getHardware()
                    .getProcessor().getLogicalProcessorCount();

            for (int i = 0; i < oshiProcesses.size(); i++) {
                OSProcess proc = oshiProcesses.get(i);
                int pid = proc.getProcessID();

                // CPU se racuna kao razlika izmedju PRETHODNOG i TRENUTNOG snapshota
                OSProcess prev = previousProcesses.get(pid);
                double cpu = 0.0;
                if (prev != null) {
                    cpu = proc.getProcessCpuLoadBetweenTicks(prev) * 100.0 / logicalProcessorCount;
                }

                long ram = proc.getResidentSetSize();
                oshiMetrics.put((long) pid, new double[]{cpu, ram});

                // sacuvaj trenutni snapshot za sledeci scan
                previousProcesses.put(pid, proc);
            }

            // 2. Dohvati ProcessHandle listu (za ForkJoinPool — zahtev spec-e)
            ProcessHandle[] allProcesses = ProcessHandle.allProcesses()
                    .filter(new java.util.function.Predicate<ProcessHandle>() {
                        @Override
                        public boolean test(ProcessHandle ph) {
                            return ph.isAlive();
                        }
                    })
                    .toArray(ProcessHandle[]::new);

            // 3. Oznaci sve kao neaktivne pre skeniranja
            for (ProcessInfo pi : registry.getAll()) {
                pi.setActive(false);
                pi.setCpuUsage(0.0);
                pi.setRamUsageBytes(0);
            }

            // 4. Pokreni ForkJoinPool sa rekurzivnim zadatkom
            forkJoinPool.invoke(new ScanTask(allProcesses, 0, allProcesses.length, chunkSize, registry, oshiMetrics));

            // 5. Azuriraj vreme JEDNOM po ProcessInfo (ne po ProcessHandle)
            // Ovo sprecava bug gde 200 chrome instanci dodaje 200x3s umesto 3s
            long now = System.currentTimeMillis();
            for (ProcessInfo pi : registry.getAll()) {
                if (pi.isAlive()) {
                    if (!pi.isFrozen()) {
                        long lastUpdate = pi.getLastUpdateTime();
                        long elapsedSeconds = (now - lastUpdate) / 1000;
                        if (elapsedSeconds > 0) {
                            pi.addSessionTime(elapsedSeconds);
                        }
                    }
                    // lastUpdateTime se uvek azurira kako bi se sprecilo
                    // nagomilavanje zamrznutog vremena po odmrzavanju
                    pi.setLastUpdateTime(now);
                }
            }

            // 6. Akumuliraj vreme za aktivni browser tab
            if (browserTabService != null) {
                try {
                    String frontApp = browserTabService.getFrontmostAppName();
                    String browserKey = browserTabService.matchFrontmostToBrowser(frontApp);
                    if (browserKey != null) {
                        ProcessInfo browserProcess = registry.get(browserKey);
                        if (browserProcess != null && browserProcess.isAlive() && !browserProcess.isFrozen()) {
                            TabInfo activeTab = browserTabService.getActiveTabForBrowser(browserKey);
                            if (activeTab != null && !activeTab.getDomain().isEmpty()) {
                                String domain = activeTab.getDomain();
                                TabInfo tracked = browserProcess.getOrCreateTabTime(
                                        domain, activeTab.getTitle(), activeTab.getUrl());
                                tracked.setCategory(activeTab.getCategoryEnum());
                                long lastUpdate = browserProcess.getLastUpdateTime();
                                long elapsedSeconds = (now - lastUpdate) / 1000;
                                if (elapsedSeconds > 0) {
                                    tracked.addSessionTime(elapsedSeconds);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // greska pri detekciji aktivnog taba — ne blokira skeniranje
                }

                // 6b. Full tab enumeration — svaki 5. ciklus (za prikaz svih tabova)
                scanCycleCount++;
                if (scanCycleCount % 5 == 0) {
                    try {
                        java.util.Map<String, java.util.List<TabInfo>> allTabs =
                                browserTabService.getAllBrowserTabs(registry);
                        for (java.util.Map.Entry<String, java.util.List<TabInfo>> entry : allTabs.entrySet()) {
                            ProcessInfo pi = registry.get(entry.getKey());
                            if (pi != null) {
                                pi.setTabs(new java.util.ArrayList<>(entry.getValue()));
                            }
                        }
                    } catch (Exception e) {
                        // greska pri dohvatanju tabova
                    }
                }
            }

            // 7. Ukloni mrtve procese (memory cleanup)
            registry.removeDeadProcesses();

        } catch (Exception e) {
            System.err.println("Greska pri skeniranju: " + e.getMessage());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        forkJoinPool.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            forkJoinPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
