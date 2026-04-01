package org.productivity_buddy.workers;

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
    private volatile boolean running;

    public FileWatcherWorker(String filePath, FileService fileService) {
        this.filePath = Path.of(filePath);
        this.fileService = fileService;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();

            // registruj DIREKTORIJUM za pracenje (ne fajl)
            Path dir = filePath.getParent();
            if (dir == null) {
                dir = Path.of(".");
            }
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            String fileName = filePath.getFileName().toString();

            while (running) {
                // blokiraj nit dok se ne desi promena (ne trosi CPU)
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = (Path) event.context();

                    // proveri da li je promenjen NAS fajl
                    if (changed.toString().equals(fileName)) {
                        System.out.println("Detektovana promena u " + fileName);

                        // sacekaj da se fajl kompletno zapise
                        Thread.sleep(200);

                        try {
                            String content = Files.readString(filePath);
                            fileService.parseAndApplyJson(content);
                        } catch (IOException e) {
                            System.err.println("Greska pri citanju: " + e.getMessage());
                        }
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
