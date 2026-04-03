package org.productivity_buddy.workers;

import org.productivity_buddy.CategorizationService;
import org.productivity_buddy.FileService;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class FileWatcherWorker implements Runnable {

    private final Path filePath;
    private final FileService fileService;
    private Path rulesFilePath;
    private CategorizationService categorizationService;
    private volatile boolean running;

    public FileWatcherWorker(String filePath, FileService fileService) {
        this.filePath = Path.of(filePath);
        this.fileService = fileService;
        this.running = true;
    }

    // dodaj nadgledanje fajla sa pravilima za auto-kategorizaciju
    public void setCategorizationWatcher(String rulesFile, CategorizationService service) {
        this.rulesFilePath = Path.of(rulesFile);
        this.categorizationService = service;
    }

    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();

            // registruj DIREKTORIJUM za pracenje process_info.json
            Path dir = filePath.getParent();
            if (dir == null) {
                dir = Path.of(".");
            }
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            String fileName = filePath.getFileName().toString();

            // registruj direktorijum za rules fajl (ako je postavljen)
            String rulesFileName = null;
            if (rulesFilePath != null) {
                Path rulesDir = rulesFilePath.getParent();
                if (rulesDir == null) {
                    rulesDir = Path.of(".");
                }
                // ako su u razlicitim direktorijumima, registruj i drugi
                if (!rulesDir.toAbsolutePath().equals(dir.toAbsolutePath())) {
                    rulesDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                }
                rulesFileName = rulesFilePath.getFileName().toString();
            }

            while (running) {
                // blokiraj nit dok se ne desi promena (ne trosi CPU)
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = (Path) event.context();
                    String changedName = changed.toString();

                    if (changedName.equals(fileName)) {
                        // promena u process_info.json
                        System.out.println("Detektovana promena u " + fileName);
                        Thread.sleep(200);

                        try {
                            String content = Files.readString(filePath);
                            fileService.parseAndApplyJson(content);
                        } catch (IOException e) {
                            System.err.println("Greska pri citanju: " + e.getMessage());
                        }
                    } else if (rulesFileName != null && changedName.equals(rulesFileName)) {
                        // promena u categorization_rules.json — hot-reload
                        System.out.println("Detektovana promena u " + rulesFileName);
                        Thread.sleep(200);
                        categorizationService.reloadRules();
                    }
                }

                // reset kljuc — bez ovoga nece detektovati sledecu promenu
                if (!key.reset()) {
                    break;
                }
            }

        } catch (InterruptedException e) {
            // nit je prekinuta — normalno pri gasenju
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("FileWatcher greska: " + e.getMessage());
        }
    }

    public void stop() {
        this.running = false;
    }
}
