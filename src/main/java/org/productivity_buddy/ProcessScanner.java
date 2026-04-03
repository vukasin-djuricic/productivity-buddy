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

    public ProcessScanner(ProcessRegistry registry, int chunkSize, long intervalMs) {
        this.registry = registry;
        this.forkJoinPool = new ForkJoinPool();
        this.chunkSize = chunkSize;
        this.os = new SystemInfo().getOperatingSystem();
        this.oshiMetrics = new ConcurrentHashMap<>();
        this.previousProcesses = new ConcurrentHashMap<>();

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

            // 6. Ukloni mrtve procese (memory cleanup)
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
