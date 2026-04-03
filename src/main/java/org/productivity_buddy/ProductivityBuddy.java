package org.productivity_buddy;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;

import org.productivity_buddy.view.MainChartView;
import org.productivity_buddy.view.ProcessDetailView;
import org.productivity_buddy.view.RefreshablePanel;
import org.productivity_buddy.view.RefreshableView;
import org.productivity_buddy.view.HelpView;
import org.productivity_buddy.view.SpecificCategoryView;
import org.productivity_buddy.workers.AnalyticsWorker;
import org.productivity_buddy.workers.FileWatcherWorker;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private CategorizationService categorizationService;
    private ScheduledExecutorService snapshotScheduler;

    // shared layout — tabela zivi kroz celu sesiju
    private TableView<ProcessInfo> sharedTable;
    private VBox sharedLeftPane;
    private VBox dynamicRightPane;
    private HBox sharedLayout;

    // aktivan desni panel — polimorfizam za refresh
    private RefreshablePanel activeRightView;

    // da li je trenutno prikazan full-screen view (SpecificCategoryView)
    private boolean showingFullScreenView = false;
    private RefreshableView currentFullScreenView;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. Ucitaj konfiguraciju
        config = new AppConfig("config/config.properties");

        // 2. Kreiraj centralni registar
        registry = new ProcessRegistry();

        // 2.5 Kreiraj servis za auto-kategorizaciju (rule-based regex matching)
        categorizationService = new CategorizationService("config/categorization_rules.json");
        categorizationService.loadRules();
        registry.setCategorizationService(categorizationService);

        // 3. Kreiraj file service i ucitaj prethodno stanje
        // (JSON kategorije imaju prioritet nad auto-pravilima)
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

        // 7. Pokreni FileWatcher nit (nadgleda i process_info.json i pravila kategorizacije)
        fileWatcherWorker = new FileWatcherWorker(config.getMappingFile(), fileService);
        fileWatcherWorker.setCategorizationWatcher(
                categorizationService.getRulesFilePath(), categorizationService);
        watcherThread = new Thread(fileWatcherWorker, "FileWatcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();

        // 8. UI setup
        rootPane = new BorderPane();
        rootPane.getStyleClass().add("root-pane");
        rootPane.setTop(createGlobalTopBar());
        createSharedLayout();
        navigateToMain();

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
    // SHARED LAYOUT — tabela + dinamicni desni panel
    // ==========================================
    private void createSharedLayout() {
        // LEVA STRANA — shared tabela
        sharedLeftPane = new VBox(12);
        HBox.setHgrow(sharedLeftPane, Priority.ALWAYS);

        Label tableTitle = new Label("Active Processes");
        tableTitle.getStyleClass().add("section-title");

        sharedTable = new TableView<>();

        TableColumn<ProcessInfo, String> colName = new TableColumn<>("Process");
        colName.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProcessInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<ProcessInfo, String> data) {
                return new SimpleStringProperty(data.getValue().getAliasName());
            }
        });

        TableColumn<ProcessInfo, String> colCategory = new TableColumn<>("Category");
        colCategory.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProcessInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<ProcessInfo, String> data) {
                return new SimpleStringProperty(data.getValue().getCategory());
            }
        });

        sharedTable.getColumns().addAll(colName, colCategory);
        sharedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(sharedTable, Priority.ALWAYS);
        applyUncategorizedLastSortPolicy(sharedTable);

        sharedTable.setItems(FXCollections.observableArrayList(registry.getAll()));

        sharedTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 1 && sharedTable.getSelectionModel().getSelectedItem() != null) {
                    String selectedProcess = sharedTable.getSelectionModel().getSelectedItem().getOriginalName();
                    navigateToProcessDetail(selectedProcess);
                }
            }
        });

        Label hint = new Label("Click on a process for details");
        hint.getStyleClass().add("stat-rank");
        hint.setPadding(new Insets(4, 0, 0, 4));

        sharedLeftPane.getChildren().addAll(tableTitle, sharedTable, hint);

        // DESNA STRANA — dinamicni panel
        dynamicRightPane = new VBox(20);
        dynamicRightPane.setAlignment(Pos.TOP_CENTER);
        dynamicRightPane.setPrefWidth(380);

        // SPOJI
        sharedLayout = new HBox(24);
        sharedLayout.setPadding(new Insets(24));
        sharedLayout.getChildren().addAll(sharedLeftPane, dynamicRightPane);
    }

    // ==========================================
    // REFRESH SHARED TABLE — cuva sort i selection
    // ==========================================
    private void refreshSharedTable() {
        if (sharedTable == null) return;

        String selectedName = null;
        ProcessInfo selectedItem = sharedTable.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            selectedName = selectedItem.getOriginalName();
        }

        List<TableColumn<ProcessInfo, ?>> sortOrder = new ArrayList<>(sharedTable.getSortOrder());

        ObservableList<ProcessInfo> currentItems = sharedTable.getItems();
        java.util.Collection<ProcessInfo> registryItems = registry.getAll();
        boolean itemsReplaced = false;
        if (currentItems.size() != registryItems.size()) {
            sharedTable.setItems(FXCollections.observableArrayList(registryItems));
            itemsReplaced = true;
        } else {
            sharedTable.refresh();
        }

        if (!sortOrder.isEmpty()) {
            sharedTable.getSortOrder().setAll(sortOrder);
            sharedTable.sort();
        }

        if (selectedName != null) {
            for (ProcessInfo info : sharedTable.getItems()) {
                if (info.getOriginalName().equals(selectedName)) {
                    sharedTable.getSelectionModel().select(info);
                    break;
                }
            }
        }
    }

    // ==========================================
    // NAVIGACIJA — pozivaju view klase
    // ==========================================
    public void navigateToMain() {
        showingFullScreenView = false;
        currentFullScreenView = null;
        rootPane.setCenter(sharedLayout);

        MainChartView view = new MainChartView(registry, analyticsWorker, this);
        dynamicRightPane.getChildren().setAll(view.createRightPanel());
        activeRightView = view;
    }

    public void navigateToProcessDetail(String processName) {
        showingFullScreenView = false;
        rootPane.setCenter(sharedLayout);

        ProcessDetailView view = new ProcessDetailView(registry, this, processName);
        dynamicRightPane.getChildren().setAll(view.createRightPanel());
        activeRightView = view;
    }

    public void navigateToCategory(String categoryName) {
        showingFullScreenView = true;
        SpecificCategoryView view = new SpecificCategoryView(registry, analyticsWorker, this, categoryName);
        rootPane.setCenter(view.createView());
        currentFullScreenView = view;
        activeRightView = null;
    }

    public void navigateToHelp() {
        showingFullScreenView = true;
        HelpView view = new HelpView(this);
        rootPane.setCenter(view.createView());
        currentFullScreenView = view;
        activeRightView = null;
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
        if (showingFullScreenView) {
            if (currentFullScreenView != null) {
                currentFullScreenView.refreshUI();
            }
            return;
        }
        refreshSharedTable();
        if (activeRightView != null) {
            activeRightView.refreshUI();
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

        Button btnHelp = new Button("?");
        btnHelp.getStyleClass().add("btn-help");
        btnHelp.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                navigateToHelp();
            }
        });

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

        topBar.getChildren().addAll(appTitle, spacer, btnHelp, btnSave, btnLoad, btnShutdown);
        return topBar;
    }

    // ==========================================
    // POMOCNE METODE — sort policy + retain sort
    // ==========================================
    public static String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%dh %02dm %02ds", hours, minutes, seconds);
    }

    public static String formatRam(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private <T> void retainSortOrder(TableView<T> table, Runnable updateAction) {
        List<TableColumn<T, ?>> sortOrder = new ArrayList<>(table.getSortOrder());
        updateAction.run();
        if (!sortOrder.isEmpty()) {
            table.getSortOrder().setAll(sortOrder);
            table.sort();
        }
    }

    private void applyUncategorizedLastSortPolicy(TableView<ProcessInfo> table) {
        table.setSortPolicy(new Callback<TableView<ProcessInfo>, Boolean>() {
            @Override
            public Boolean call(TableView<ProcessInfo> param) {
                Comparator<ProcessInfo> tableComparator = param.getComparator();
                if (tableComparator == null) {
                    return true;
                }

                Comparator<ProcessInfo> wrapped = new Comparator<ProcessInfo>() {
                    @Override
                    public int compare(ProcessInfo a, ProcessInfo b) {
                        boolean aUncat = "Uncategorized".equals(a.getCategory());
                        boolean bUncat = "Uncategorized".equals(b.getCategory());
                        if (aUncat && !bUncat) return 1;
                        if (!aUncat && bUncat) return -1;
                        return tableComparator.compare(a, b);
                    }
                };

                FXCollections.sort(param.getItems(), wrapped);
                return true;
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
