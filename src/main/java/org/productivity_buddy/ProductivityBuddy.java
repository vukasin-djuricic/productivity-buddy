package org.productivity_buddy;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.productivity_buddy.view.MainChartView;
import org.productivity_buddy.view.ProcessDetailView;
import org.productivity_buddy.view.SpecificCategoryView;
import org.productivity_buddy.workers.AnalyticsWorker;
import org.productivity_buddy.workers.FileWatcherWorker;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public class ProductivityBuddy extends Application {

    private BorderPane rootPane;
    private Stage primaryStage;

    // konkurentne komponente
    private AppConfig config;
    private ProcessRegistry registry;
    private ProcessScanner scanner;
    private FileService fileService;
    private AnalyticsWorker analyticsWorker;
    private Thread analyticsThread;
    private FileWatcherWorker fileWatcherWorker;
    private Thread watcherThread;
    private ScheduledExecutorService snapshotScheduler;

    // view-ovi
    private MainChartView mainChartView;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. Ucitaj konfiguraciju
        config = new AppConfig("config/config.properties");

        // 2. Kreiraj centralni registar
        registry = new ProcessRegistry();

        // 3. Kreiraj file service i ucitaj prethodno stanje
        fileService = new FileService(registry);
        fileService.loadProcessInfo(config.getMappingFile());

        // 4. Pokreni skener procesa (ForkJoinPool)
        scanner = new ProcessScanner(registry, 10, config.getMonitorInterval());

        // 5. Pokreni analitickog workera
        analyticsWorker = new AnalyticsWorker(registry, config.getFixedSnapshotTimes());
        analyticsWorker.setOnUpdate(new Runnable() {
            @Override
            public void run() {
                refreshUI();
            }
        });
        analyticsWorker.setOnSnapshotTriggered(new Runnable() {
            @Override
            public void run() {
                fileService.saveSnapshot();
            }
        });
        analyticsThread = new Thread(analyticsWorker, "Analytics-Thread");
        analyticsThread.setDaemon(true);
        analyticsThread.start();

        // 6. Pokreni periodicni snapshot
        snapshotScheduler = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Snapshot-Scheduler");
                t.setDaemon(true);
                return t;
            }
        });
        snapshotScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                fileService.saveSnapshot();
            }
        }, config.getSnapshotInterval(), config.getSnapshotInterval(), TimeUnit.SECONDS);

        // 7. Pokreni FileWatcher nit
        fileWatcherWorker = new FileWatcherWorker(config.getMappingFile(), fileService);
        watcherThread = new Thread(fileWatcherWorker, "FileWatcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();

        // 8. Kreiraj view-ove
        mainChartView = new MainChartView(registry, analyticsWorker, this);

        // 9. UI setup
        rootPane = new BorderPane();
        rootPane.getStyleClass().add("root-pane");
        rootPane.setTop(createGlobalTopBar());
        rootPane.setCenter(mainChartView.createView());

        Scene scene = new Scene(rootPane, 1100, 750);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("CSS fajl nije pronadjen. Koristim default temu.");
        }

        primaryStage.setTitle("Productivity Buddy");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Shutdown handler
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                event.consume();
                performShutdown();
            }
        });

        primaryStage.show();
    }

    // ==========================================
    // NAVIGACIJA — pozivaju view klase
    // ==========================================
    public void navigateToMain() {
        mainChartView = new MainChartView(registry, analyticsWorker, this);
        rootPane.setCenter(mainChartView.createView());
    }

    public void navigateToProcessDetail(String processName) {
        ProcessDetailView detailView = new ProcessDetailView(registry, this);
        rootPane.setCenter(detailView.createView(processName));
    }

    public void navigateToCategory(String categoryName) {
        SpecificCategoryView categoryView = new SpecificCategoryView(registry, analyticsWorker, this);
        rootPane.setCenter(categoryView.createView(categoryName));
    }

    private void performShutdown() {
        scanner.shutdown();
        analyticsWorker.stop();
        analyticsThread.interrupt();
        fileWatcherWorker.stop();
        watcherThread.interrupt();
        snapshotScheduler.shutdown();
        fileService.shutdownAndSave(config.getMappingFile());
        primaryStage.close();
    }

    // ==========================================
    // REFRESH UI — poziva se sa UI niti
    // ==========================================
    private void refreshUI() {
        if (mainChartView != null) {
            mainChartView.refreshUI();
        }
    }

    // ==========================================
    // GLOBALNI TOP BAR
    // ==========================================
    private HBox createGlobalTopBar() {
        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(14, 24, 14, 24));
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label appTitle = new Label("\u26a1 Productivity Buddy");
        appTitle.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnSave = new Button("Save");
        btnSave.getStyleClass().add("btn-secondary");
        btnSave.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Save Process Info");
                chooser.setInitialFileName("process_info.json");
                File file = chooser.showSaveDialog(primaryStage);
                if (file != null) {
                    fileService.saveProcessInfo(file.getAbsolutePath());
                }
            }
        });

        Button btnLoad = new Button("Load");
        btnLoad.getStyleClass().add("btn-secondary");
        btnLoad.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Load Process Info");
                File file = chooser.showOpenDialog(primaryStage);
                if (file != null) {
                    fileService.loadProcessInfo(file.getAbsolutePath());
                }
            }
        });

        Button btnShutdown = new Button("Shutdown");
        btnShutdown.getStyleClass().add("btn-danger");
        btnShutdown.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                performShutdown();
            }
        });

        topBar.getChildren().addAll(appTitle, spacer, btnSave, btnLoad, btnShutdown);
        return topBar;
    }

    // ==========================================
    // POMOCNE METODE
    // ==========================================
    public static String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%dh %02dm %02ds", hours, minutes, seconds);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
