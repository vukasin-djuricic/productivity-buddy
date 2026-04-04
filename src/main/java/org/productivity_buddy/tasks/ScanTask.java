package org.productivity_buddy.tasks;

import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

public class ScanTask extends RecursiveAction {

    private final ProcessHandle[] processes;
    private final int start;
    private final int end;
    private final int chunkSize;
    private final ProcessRegistry registry;
    private final ConcurrentHashMap<Long, double[]> oshiMetrics;

    public ScanTask(ProcessHandle[] processes, int start, int end, int chunkSize,
                    ProcessRegistry registry, ConcurrentHashMap<Long, double[]> oshiMetrics) {
        this.processes = processes;
        this.start = start;
        this.end = end;
        this.chunkSize = chunkSize;
        this.registry = registry;
        this.oshiMetrics = oshiMetrics;
    }

    @Override
    protected void compute() {
        // BAZNI SLUCAJ: ako je broj procesa <= chunkSize, obradi direktno
        if (end - start <= chunkSize) {
            for (int i = start; i < end; i++) {
                handleProcess(processes[i]);
            }
            return;
        }

        // REKURZIVNI SLUCAJ: podeli na dve polovine
        int mid = start + (end - start) / 2;
        ScanTask left = new ScanTask(processes, start, mid, chunkSize, registry, oshiMetrics);
        ScanTask right = new ScanTask(processes, mid, end, chunkSize, registry, oshiMetrics);

        // fork() — asinhrono pokrece left u drugoj niti
        left.fork();
        // compute() — izvrsava right na OVOJ niti (ne pravi prazan hod)
        right.compute();
        // join() — ceka da left zavrsi
        left.join();
    }

    private void handleProcess(ProcessHandle process) {
        try {
            ProcessHandle.Info info = process.info();
            String name = info.command().orElse(null);

            if (name == null || name.isEmpty()) {
                return;
            }

            // izvuci samo ime fajla iz pune putanje
            // "/usr/bin/java" -> "java"
            int lastSeparator = name.lastIndexOf(System.getProperty("file.separator"));
            if (lastSeparator >= 0) {
                name = name.substring(lastSeparator + 1);
            }

            if (name.isEmpty()) {
                return;
            }

            long pid = process.pid();
            long startTime = info.startInstant()
                    .map(new java.util.function.Function<java.time.Instant, Long>() {
                        @Override
                        public Long apply(java.time.Instant instant) {
                            return instant.toEpochMilli();
                        }
                    })
                    .orElse(0L);

            ProcessInfo procInfo = registry.getOrCreate(name);

            // DETEKCIJA PID RECIKLIRANJA
            if (procInfo.getPid() != pid) {
                if (procInfo.getPid() != -1 && procInfo.getStartTime() != startTime) {
                    // novi proces sa istim imenom, stari PID je recikliran
                    // sacuvaj akumulirano vreme u totalTime, resetuj sesiju
                    procInfo.setTotalTime(procInfo.getEffectiveTotalTime());
                    procInfo.resetSessionTime();
                }
                procInfo.setPid(pid);
                procInfo.setStartTime(startTime);
                procInfo.setLastUpdateTime(System.currentTimeMillis());
            }

            procInfo.setActive(true);

            // CPU i RAM iz OSHI metrika
            double[] metrics = oshiMetrics.get(pid);
            if (metrics != null) {
                //procInfo.setCpuUsage(metrics[0]);
                //procInfo.setRamUsageBytes((long) metrics[1]);
                //java (200mb) + java (500mb) umesto samo poslednji da upise
                procInfo.addMetrics(metrics[0], (long) metrics[1]);
            } else {
                procInfo.setCpuUsage(0.0);
                procInfo.setRamUsageBytes(0);
            }

            // VREME se NE racuna ovde — jer vise ProcessHandle-ova moze imati isto ime
            // i svi dele isti ProcessInfo objekat. Racunanje se radi JEDNOM po ProcessInfo
            // u ProcessScanner.scan() POSLE zavrsetka ForkJoinPool-a.
        }
        catch (Exception e) {
            // Proces se mogao ugasiti dok smo ga obradjivali
            // ili nemamo dozvolu — graciozno preskocimo
        }
    }
}
