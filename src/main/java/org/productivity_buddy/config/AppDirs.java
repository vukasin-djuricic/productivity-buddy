package org.productivity_buddy.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Odredjuje bazni direktorijum aplikacije zavisno od nacina pokretanja.
 *
 * Razvoj:    koristi radni direktorijum (user.dir) — config/ i data/ su pored projekta
 * .app bundle: koristi ~/Library/Application Support/ProductivityBuddy/
 *              i kopira default config fajlove pri prvom pokretanju
 */
public class AppDirs {

    private static final Path BASE;

    static {
        // jpackage postavlja ovu system property kada se pokrene iz .app bundle-a
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null) {
            Path appSupport = Path.of(System.getProperty("user.home"),
                    "Library", "Application Support", "ProductivityBuddy");
            BASE = appSupport;
            initAppSupportDir();
        } else {
            BASE = Path.of(System.getProperty("user.dir"));
            initDevDirs();
        }
    }

    /**
     * Vraca apsolutnu putanju za dati relativni path unutar app direktorijuma.
     */
    public static String resolve(String relativePath) {
        return BASE.resolve(relativePath).toString();
    }

    /**
     * Vraca bazni direktorijum aplikacije.
     */
    public static Path base() {
        return BASE;
    }

    // Kreira config/ i data/ u radnom direktorijumu ako ne postoje (dev mod)
    private static void initDevDirs() {
        try {
            Files.createDirectories(BASE.resolve("config"));
            Files.createDirectories(BASE.resolve("data"));
            copyDefaultIfAbsent("defaults/config.properties", "config/config.properties");
            copyDefaultIfAbsent("defaults/categorization_rules.json", "config/categorization_rules.json");
        } catch (IOException e) {
            System.err.println("AppDirs: greška pri kreiranju direktorijuma: " + e.getMessage());
        }
    }

    // Kreira direktorijume i kopira default config fajlove ako ne postoje
    private static void initAppSupportDir() {
        try {
            Files.createDirectories(BASE.resolve("config"));
            Files.createDirectories(BASE.resolve("data"));

            copyDefaultIfAbsent("defaults/config.properties", "config/config.properties");
            copyDefaultIfAbsent("defaults/categorization_rules.json", "config/categorization_rules.json");
        } catch (IOException e) {
            System.err.println("AppDirs: greška pri inicijalizaciji direktorijuma: " + e.getMessage());
        }
    }

    // Kopira resurs iz JAR-a u BASE/relativeDest ako fajl ne postoji
    private static void copyDefaultIfAbsent(String resourcePath, String relativeDest) throws IOException {
        Path dest = BASE.resolve(relativeDest);
        if (Files.exists(dest)) return;

        try (InputStream in = AppDirs.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("AppDirs: default resurs nije pronadjen: " + resourcePath);
                return;
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("AppDirs: kopiran default: " + dest);
        }
    }
}
