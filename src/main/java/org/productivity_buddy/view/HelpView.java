package org.productivity_buddy.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.productivity_buddy.ProductivityBuddy;

public class HelpView implements RefreshableView {

    private final ProductivityBuddy app;

    public HelpView(ProductivityBuddy app) {
        this.app = app;
    }

    public Node createView() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("root-pane");

        // zaglavlje
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("\u2190 Back to Main");
        btnBack.getStyleClass().addAll("btn-secondary", "back-button");
        btnBack.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                app.navigateToMain();
            }
        });

        Region spacerL = new Region();
        HBox.setHgrow(spacerL, Priority.ALWAYS);
        Label title = new Label("Architecture Overview");
        title.getStyleClass().add("header-title");
        Region spacerR = new Region();
        HBox.setHgrow(spacerR, Priority.ALWAYS);

        header.getChildren().addAll(btnBack, spacerL, title, spacerR);

        // dijagram u scroll panu
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setContent(buildDiagram());

        root.getChildren().addAll(header, scrollPane);
        return root;
    }

    @Override
    public void refreshUI() {
        // staticki sadrzaj — nema potrebe za refresh
    }

    // ==========================================
    // GLAVNA METODA ZA CRTANJE DIJAGRAMA
    // ==========================================
    private Pane buildDiagram() {
        Pane pane = new Pane();
        pane.setPrefSize(1050, 800);
        pane.setMinSize(1050, 800);
        pane.setStyle("-fx-background-color: #1a1830; -fx-background-radius: 10;");

        buildThreadModel(pane);
        buildDataFlow(pane);
        buildDivider(pane);
        buildLegend(pane);

        return pane;
    }

    // ==========================================
    // LEVA STRANA — Thread Model
    // ==========================================
    private void buildThreadModel(Pane pane) {
        pane.getChildren().add(createSectionLabel("THREAD MODEL", 30, 10));

        // UI Thread kontejner (prozirni pravougaonik) — prosiren za AppConfig
        Rectangle threadContainer = new Rectangle(20, 32, 500, 100);
        threadContainer.setFill(Color.web("#8b5cf6", 0.08));
        threadContainer.setStroke(Color.web("#8b5cf6", 0.4));
        threadContainer.setStrokeWidth(1);
        threadContainer.getStrokeDashArray().addAll(8.0, 5.0);
        threadContainer.setArcWidth(10);
        threadContainer.setArcHeight(10);
        pane.getChildren().add(threadContainer);
        pane.getChildren().add(createSmallLabel("JavaFX Application Thread", 30, 35, "#8b5cf6"));

        // ProductivityBuddy
        pane.getChildren().add(createBox("ProductivityBuddy", "Main orchestrator", "#8b5cf6",
                100, 48, 230, 68,
                "Entry point (extends Application). start() initializes AppConfig, ProcessRegistry, "
                + "CategorizationService, FileService, ProcessScanner, AnalyticsWorker, "
                + "SnapshotScheduler, and FileWatcherWorker. Builds the JavaFX UI and handles "
                + "graceful shutdown. Shutdown order: Scanner \u2192 Analytics \u2192 FileWatcher "
                + "\u2192 SnapshotScheduler \u2192 FileService(save+close)."));

        // AppConfig — Faza 2d
        pane.getChildren().add(createBox("AppConfig", "config.properties", "#94a3b8",
                345, 55, 130, 50,
                "Loads monitor.interval (default 3s), mapping.file path, snapshot.interval, "
                + "and fixed_time settings from config/config.properties. Read once at startup."));

        // PB → AppConfig strelica
        pane.getChildren().add(createArrow(
                new double[][]{{330, 82}, {345, 82}},
                "#94a3b8", false));

        // ProcessScanner
        pane.getChildren().add(createBox("ProcessScanner", "ScheduledExecutor + ForkJoinPool", "#06b6d4",
                25, 190, 220, 70,
                "ScheduledExecutorService triggers scan() every Nms (configurable, default 3s). "
                + "Phase 1: collects OSHI CPU/RAM metrics into ConcurrentHashMap. "
                + "Phase 2: ForkJoinPool with recursive ScanTask processes ProcessHandle array. "
                + "Phase 3: updates time once per ProcessInfo, removes dead processes."));

        // ScanTask (ugnjezdeni)
        StackPane scanTaskBox = createBox("ScanTask", "RecursiveAction", "#0891b2",
                55, 268, 155, 38,
                "Splits ProcessHandle array at midpoint (chunk=10). Left half fork()'d, "
                + "right half compute()'d in place. Registers via registry.getOrCreate().");
        scanTaskBox.setStyle(scanTaskBox.getStyle()
                + " -fx-border-color: #06b6d4; -fx-border-width: 1;"
                + " -fx-border-style: dashed; -fx-border-radius: 6;");
        pane.getChildren().add(scanTaskBox);
        // anotacija — Faza 3f
        pane.getChildren().add(createAnnotation("fork()/compute() work-stealing", 38, 308));

        // AnalyticsWorker
        pane.getChildren().add(createBox("AnalyticsWorker", "Daemon thread, 2s loop", "#10b981",
                270, 190, 220, 70,
                "Daemon thread aggregating time-by-category and top-10 processes every 2s "
                + "(configurable). Triggers UI refresh via Platform.runLater(). "
                + "Also checks fixed snapshot times and triggers FileService.saveSnapshot()."));

        // SnapshotScheduler — Faza 4c
        pane.getChildren().add(createBox("snapshotScheduler", "ScheduledExecutor (field in PB)", "#f59e0b",
                25, 350, 220, 55,
                "ScheduledExecutorService field in ProductivityBuddy. Exports CSV snapshots at "
                + "configurable intervals (default 60s). Routes through FileService for thread-safe I/O."));

        // FileWatcherWorker
        pane.getChildren().add(createBox("FileWatcherWorker", "WatchService daemon", "#f43f5e",
                270, 350, 220, 55,
                "WatchService daemon monitoring both process_info.json and "
                + "categorization_rules.json. On file change, waits 200ms debounce then applies "
                + "changes. parseAndApplyJson() runs on the watcher thread (not FileService executor). "
                + "categorization_rules.json changes trigger reloadRules() directly."));
        // anotacija — Faza 3f
        pane.getChildren().add(createAnnotation("WatchService + 200ms debounce", 275, 407));

        // CategorizationService — Faza 1b
        pane.getChildren().add(createBox("CategorizationService", "Rule-based regex", "#c084fc",
                25, 440, 220, 55,
                "Regex pravila iz categorization_rules.json. Poziva se atomicno unutar "
                + "ProcessRegistry.computeIfAbsent() pri getOrCreate(). "
                + "Hot-reload putem FileWatcherWorker.reloadRules(). "
                + "Koristi volatile List<CompiledRule> za thread-safe zamenu pravila."));

        // FileService
        pane.getChildren().add(createBox("FileService", "Single-thread executor", "#6366f1",
                150, 530, 250, 60,
                "Single-thread executor (newSingleThreadExecutor) serializing all file I/O. "
                + "Handles JSON save/load and CSV snapshot export. "
                + "Prevents concurrent file writes via executor queue."));
        // anotacija — Faza 3f
        pane.getChildren().add(createAnnotation("single-thread executor (serialized I/O)", 155, 592));

        // --- strelice ---

        // PB → ProcessScanner — Faza 1e: ispravljeni label
        pane.getChildren().add(createArrow(
                new double[][]{{175, 116}, {175, 158}, {135, 158}, {135, 190}},
                "#06b6d4", false));
        pane.getChildren().add(createArrowLabel("new (config interval)", 85, 146, "#06b6d4"));

        // PB → AnalyticsWorker
        pane.getChildren().add(createArrow(
                new double[][]{{290, 116}, {290, 158}, {380, 158}, {380, 190}},
                "#10b981", false));
        pane.getChildren().add(createArrowLabel("Thread.start()", 332, 146, "#10b981"));

        // PB → SnapshotScheduler — Faza 3b: prava strelica umesto teksta
        pane.getChildren().add(createArrow(
                new double[][]{{145, 116}, {145, 158}, {80, 158}, {80, 328}, {135, 328}, {135, 350}},
                "#f59e0b", true));
        pane.getChildren().add(createArrowLabel("constructor", 55, 320, "#f59e0b"));

        // PB → FileWatcherWorker — Faza 3b: prava strelica umesto teksta
        pane.getChildren().add(createArrow(
                new double[][]{{310, 116}, {310, 158}, {430, 158}, {430, 328}, {380, 328}, {380, 350}},
                "#f43f5e", true));
        pane.getChildren().add(createArrowLabel("Thread.start()", 385, 320, "#f43f5e"));

        // SnapshotScheduler → FileService
        pane.getChildren().add(createArrow(
                new double[][]{{135, 405}, {135, 430}, {180, 430}, {180, 460}, {230, 460}, {230, 530}},
                "#f59e0b", false));
        pane.getChildren().add(createArrowLabel("saveSnapshot()", 175, 500, "#f59e0b"));

        // FileWatcherWorker → FileService — Faza 1d: ispravljeni label + anotacija
        pane.getChildren().add(createArrow(
                new double[][]{{380, 405}, {380, 430}, {340, 430}, {340, 460}, {300, 460}, {300, 530}},
                "#f43f5e", false));
        pane.getChildren().add(createArrowLabel("parseAndApplyJson()", 305, 500, "#f43f5e"));
        pane.getChildren().add(createSmallLabel("(na watcher niti)", 310, 513, "#f43f5e"));

        // FileWatcherWorker → CategorizationService — Faza 1c
        pane.getChildren().add(createArrow(
                new double[][]{{270, 395}, {248, 395}, {248, 467}, {245, 467}},
                "#c084fc", false));
        pane.getChildren().add(createArrowLabel("reloadRules()", 195, 420, "#c084fc"));

        // AnalyticsWorker → UI Thread (Platform.runLater povratna petlja)
        pane.getChildren().add(createArrow(
                new double[][]{{490, 225}, {515, 225}, {515, 82}, {385, 82}},
                "#10b981", true));
        pane.getChildren().add(createArrowLabel("Platform.runLater()", 442, 200, "#10b981"));

        // anotacija daemon niti — Faza 4b
        pane.getChildren().add(createAnnotation("All background threads are daemon", 25, 178));
    }

    // ==========================================
    // DESNA STRANA — Data Flow
    // ==========================================
    private void buildDataFlow(Pane pane) {
        pane.getChildren().add(createSectionLabel("DATA FLOW", 570, 10));

        // OS Processes — Faza 2a: entry point
        StackPane osBox = createBox("OS Processes", "ProcessHandle + OSHI", "#6b7280",
                680, 30, 190, 35,
                "System processes enumerated via ProcessHandle.allProcesses() and metrics "
                + "collected via OSHI library. External to the application.");
        osBox.setStyle(osBox.getStyle()
                + " -fx-border-color: #9ca3af; -fx-border-width: 1;"
                + " -fx-border-style: dashed; -fx-border-radius: 6;");
        pane.getChildren().add(osBox);

        // OS → Scanner
        pane.getChildren().add(createArrow(
                new double[][]{{775, 65}, {775, 85}},
                "#6b7280", false));

        // ProcessScanner referenca
        pane.getChildren().add(createBox("ProcessScanner", "Writes every 3s", "#06b6d4",
                575, 85, 170, 55,
                "ScheduledExecutorService triggers scan() every Nms (configurable, default 3s). "
                + "Phase 1: OSHI metrics. Phase 2: ForkJoinPool. Phase 3: time update."));

        // AnalyticsWorker referenca
        pane.getChildren().add(createBox("AnalyticsWorker", "Reads every 2s", "#10b981",
                870, 85, 155, 55,
                "Daemon thread aggregating time-by-category and top-10 processes every 2s "
                + "(configurable). Triggers UI refresh via Platform.runLater()."));

        // ProcessRegistry (centralni hub — istaknut)
        StackPane registryBox = createBox("ProcessRegistry", "ConcurrentHashMap hub", "#a78bfa",
                670, 265, 210, 85,
                "Central thread-safe ConcurrentHashMap<String, ProcessInfo>. All threads "
                + "read/write through this registry. getOrCreate() uses computeIfAbsent for "
                + "lock-free atomic registration. ProcessInfo fields use AtomicLong, "
                + "AtomicReference, AtomicBoolean, and volatile for lock-free thread safety.");
        registryBox.setStyle(registryBox.getStyle()
                + " -fx-border-color: #8b5cf6; -fx-border-width: 3; -fx-border-radius: 6;");
        DropShadow registryGlow = new DropShadow(15, Color.web("#a78bfa", 0.35));
        registryBox.setEffect(registryGlow);
        // registryBox hover cuva bazni glow
        registryBox.setOnMouseEntered(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                registryBox.setEffect(new DropShadow(20, Color.web("#a78bfa", 0.6)));
                registryBox.setScaleX(1.03);
                registryBox.setScaleY(1.03);
            }
        });
        registryBox.setOnMouseExited(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                registryBox.setEffect(registryGlow);
                registryBox.setScaleX(1.0);
                registryBox.setScaleY(1.0);
            }
        });
        pane.getChildren().add(registryBox);
        // anotacije — Faza 3f i 4a
        pane.getChildren().add(createAnnotation("lock-free ConcurrentHashMap", 680, 352));
        pane.getChildren().add(createAnnotation("ProcessInfo: AtomicLong, AtomicReference, volatile", 655, 366));

        // CategorizationService referenca na Data Flow strani
        pane.getChildren().add(createBox("CategorizationService", "categorize() inside computeIfAbsent", "#c084fc",
                560, 195, 200, 40,
                "Called atomically inside ProcessRegistry.computeIfAbsent() during "
                + "getOrCreate(). Regex matching against rules from categorization_rules.json."));

        // UI Views — Faza 1f: 4 refreshable views
        pane.getChildren().add(createBox("UI Views", "4 refreshable views", "#9ca3af",
                840, 430, 185, 85,
                "MainChartView (dashboard), ProcessDetailView (single process "
                + "stats), SpecificCategoryView (category drill-down), HelpView (this diagram). "
                + "All implement RefreshableView/RefreshablePanel and are refreshed via "
                + "Platform.runLater()."));

        // FileService referenca
        pane.getChildren().add(createBox("FileService", "Async JSON/CSV I/O", "#6366f1",
                575, 430, 170, 60,
                "Single-thread executor serializing all file I/O. Handles JSON save/load "
                + "and CSV snapshot export."));

        // External Files
        StackPane filesBox = createBox("Disk I/O", "JSON config / CSV snapshots", "#374151",
                580, 545, 165, 45,
                "process_info.json stores persistent state (name, alias, category, frozen, time). "
                + "categorization_rules.json stores regex rules. "
                + "CSV snapshots (data/snapshot_*.csv) contain timestamped metrics.");
        filesBox.setStyle(filesBox.getStyle()
                + " -fx-border-color: #6b7280; -fx-border-width: 1; -fx-border-radius: 6;");
        pane.getChildren().add(filesBox);

        // --- strelice ---

        // Scanner → Registry (getOrCreate)
        pane.getChildren().add(createArrow(
                new double[][]{{660, 140}, {660, 195}},
                "#06b6d4", false));
        pane.getChildren().add(createArrowLabel("getOrCreate()", 665, 158, "#06b6d4"));

        // CategorizationService → Registry (poziv unutar computeIfAbsent)
        pane.getChildren().add(createArrow(
                new double[][]{{660, 235}, {660, 265}},
                "#c084fc", true));
        pane.getChildren().add(createArrowLabel("categorize()", 665, 242, "#c084fc"));

        // Registry → Analytics (getAll)
        pane.getChildren().add(createArrow(
                new double[][]{{880, 300}, {920, 300}, {920, 140}},
                "#10b981", false));
        pane.getChildren().add(createArrowLabel("getAll()", 925, 220, "#10b981"));

        // Analytics → UI Views (Platform.runLater → refreshUI)
        pane.getChildren().add(createArrow(
                new double[][]{{970, 140}, {970, 430}},
                "#10b981", false));
        pane.getChildren().add(createArrowLabel("refreshUI()", 975, 290, "#10b981"));

        // Analytics → FileService (snapshot na fiksne termine) — Faza 2c
        pane.getChildren().add(createArrow(
                new double[][]{{870, 125}, {810, 125}, {810, 415}, {720, 415}, {720, 430}},
                "#10b981", true));
        pane.getChildren().add(createArrowLabel("saveSnapshot() (fixed times)", 720, 400, "#10b981"));

        // FileService → Registry (load, nagore)
        pane.getChildren().add(createArrow(
                new double[][]{{660, 430}, {660, 350}},
                "#6366f1", false));
        pane.getChildren().add(createArrowLabel("load", 625, 385, "#6366f1"));

        // Registry → FileService (save, nanize, dashed)
        pane.getChildren().add(createArrow(
                new double[][]{{720, 350}, {720, 430}},
                "#6366f1", true));
        pane.getChildren().add(createArrowLabel("save", 725, 385, "#6366f1"));

        // UI Views → FileService (Save/Load user-triggered) — Faza 2e
        pane.getChildren().add(createArrow(
                new double[][]{{840, 475}, {745, 475}, {745, 460}},
                "#9ca3af", true));
        pane.getChildren().add(createArrowLabel("Save/Load (user)", 760, 478, "#9ca3af"));

        // FileService → Disk
        pane.getChildren().add(createArrow(
                new double[][]{{660, 490}, {660, 545}},
                "#6b7280", false));
        pane.getChildren().add(createArrowLabel("write", 620, 512, "#6b7280"));
    }

    // ==========================================
    // RAZDVOJNIK I LEGENDA
    // ==========================================
    private void buildDivider(Pane pane) {
        Line divider = new Line(540, 25, 540, 640);
        divider.setStroke(Color.web("#374151"));
        divider.setStrokeWidth(1);
        divider.getStrokeDashArray().addAll(8.0, 6.0);
        pane.getChildren().add(divider);
    }

    private void buildLegend(Pane pane) {
        // hint za tooltip-e — Faza 3a
        pane.getChildren().add(createSmallLabel(
                "Hover over components for details", 410, 648, "#94a3b8"));

        Line topLine = new Line(20, 660, 1030, 660);
        topLine.setStroke(Color.web("#374151"));
        topLine.setStrokeWidth(1);
        pane.getChildren().add(topLine);

        // red 1 — sve komponente (Faza 3g: reorganizovano)
        addLegendItem(pane, 30, 670, "#8b5cf6", "UI Thread");
        addLegendItem(pane, 140, 670, "#06b6d4", "Scanner");
        addLegendItem(pane, 245, 670, "#10b981", "Analytics");
        addLegendItem(pane, 345, 670, "#f59e0b", "Snapshot");
        addLegendItem(pane, 450, 670, "#f43f5e", "FileWatcher");
        addLegendItem(pane, 570, 670, "#6366f1", "FileService");
        addLegendItem(pane, 685, 670, "#a78bfa", "Registry");
        addLegendItem(pane, 790, 670, "#c084fc", "Categorization");
        addLegendItem(pane, 920, 670, "#9ca3af", "UI Views");

        // red 2 — stilovi linija + OS Processes
        addLegendItem(pane, 30, 695, "#6b7280", "OS / External");

        Line solidSample = new Line(175, 703, 220, 703);
        solidSample.setStroke(Color.web("#d1d5db"));
        solidSample.setStrokeWidth(2);
        pane.getChildren().add(solidSample);
        pane.getChildren().add(createLegendLabel("Data flow", 228, 695));

        Line dashedSample = new Line(330, 703, 375, 703);
        dashedSample.setStroke(Color.web("#d1d5db"));
        dashedSample.setStrokeWidth(2);
        dashedSample.getStrokeDashArray().addAll(8.0, 5.0);
        pane.getChildren().add(dashedSample);
        pane.getChildren().add(createLegendLabel("Callback / triggered", 383, 695));

        // Faza 4e: shutdown ordering nota
        pane.getChildren().add(createSmallLabel(
                "Shutdown: Scanner \u2192 Analytics \u2192 FileWatcher \u2192 Snapshot \u2192 FileService(save+close)",
                560, 695, "#94a3b8"));
    }

    // ==========================================
    // POMOCNE METODE
    // ==========================================
    private StackPane createBox(String title, String subtitle, String fillColor,
                                double x, double y, double w, double h, String tooltipText) {
        StackPane box = new StackPane();
        box.setLayoutX(x);
        box.setLayoutY(y);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);
        box.setStyle("-fx-background-color: " + fillColor + "; -fx-background-radius: 6;");

        VBox content = new VBox(2);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(6));

        String textColor = isLightBackground(fillColor) ? "#1e1e2e" : "white";

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + textColor + ";");
        content.getChildren().add(titleLabel);

        if (subtitle != null) {
            Label subLabel = new Label(subtitle);
            // Faza 3d: uklonjen -fx-opacity: 0.8
            subLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + textColor + ";");
            content.getChildren().add(subLabel);
        }

        box.getChildren().add(content);

        // tooltip sa kratkim zadrskom
        Tooltip tip = new Tooltip(tooltipText);
        tip.setMaxWidth(350);
        tip.setWrapText(true);
        tip.setShowDelay(Duration.millis(300));
        Tooltip.install(box, tip);

        // hover efekat
        box.setOnMouseEntered(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                box.setEffect(new DropShadow(12, Color.web(fillColor)));
                box.setScaleX(1.03);
                box.setScaleY(1.03);
            }
        });
        box.setOnMouseExited(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                box.setEffect(null);
                box.setScaleX(1.0);
                box.setScaleY(1.0);
            }
        });
        // Faza 1a: uklonjen Cursor.HAND — nema click handlera

        return box;
    }

    private Group createArrow(double[][] points, String color, boolean dashed) {
        Group group = new Group();

        for (int i = 0; i < points.length - 1; i++) {
            Line line = new Line(points[i][0], points[i][1],
                    points[i + 1][0], points[i + 1][1]);
            line.setStroke(Color.web(color));
            line.setStrokeWidth(2);
            if (dashed) {
                line.getStrokeDashArray().addAll(8.0, 5.0);
            }
            group.getChildren().add(line);
        }

        // strelica na poslednjem segmentu
        double endX = points[points.length - 1][0];
        double endY = points[points.length - 1][1];
        double prevX = points[points.length - 2][0];
        double prevY = points[points.length - 2][1];

        Polygon arrowhead = new Polygon();
        arrowhead.setFill(Color.web(color));

        double dx = endX - prevX;
        double dy = endY - prevY;

        if (Math.abs(dy) > Math.abs(dx)) {
            if (dy > 0) {
                // nadole
                arrowhead.getPoints().addAll(
                        endX - 5.0, endY - 8.0,
                        endX + 5.0, endY - 8.0,
                        endX, endY);
            } else {
                // nagore
                arrowhead.getPoints().addAll(
                        endX - 5.0, endY + 8.0,
                        endX + 5.0, endY + 8.0,
                        endX, endY);
            }
        } else {
            if (dx > 0) {
                // udesno
                arrowhead.getPoints().addAll(
                        endX - 8.0, endY - 5.0,
                        endX - 8.0, endY + 5.0,
                        endX, endY);
            } else {
                // ulevo
                arrowhead.getPoints().addAll(
                        endX + 8.0, endY - 5.0,
                        endX + 8.0, endY + 5.0,
                        endX, endY);
            }
        }

        group.getChildren().add(arrowhead);
        return group;
    }

    // Faza 3d: font 10px umesto 9px
    private Label createArrowLabel(String text, double x, double y, String color) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; "
                + "-fx-background-color: #1a1830; -fx-padding: 1 3;");
        return label;
    }

    // Faza 3c: font 14px umesto 11px
    private Label createSectionLabel(String text, double x, double y) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;"
                + " -fx-letter-spacing: 2;");
        return label;
    }

    private Label createSmallLabel(String text, double x, double y, String color) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 9px; -fx-text-fill: " + color + "; -fx-font-style: italic;");
        return label;
    }

    // Faza 3f: anotacija — uvek vidljiva tehnicka informacija pored komponente
    private Label createAnnotation(String text, double x, double y) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 8px; -fx-text-fill: #94a3b8; "
                + "-fx-font-family: monospace; "
                + "-fx-background-color: #252340; -fx-background-radius: 3; "
                + "-fx-padding: 1 5;");
        return label;
    }

    private void addLegendItem(Pane pane, double x, double y, String color, String text) {
        Rectangle swatch = new Rectangle(x, y + 3, 12, 12);
        swatch.setFill(Color.web(color));
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);

        Label label = new Label(text);
        label.setLayoutX(x + 16);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #d1d5db;");

        pane.getChildren().addAll(swatch, label);
    }

    private Label createLegendLabel(String text, double x, double y) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #d1d5db;");
        return label;
    }

    private boolean isLightBackground(String hexColor) {
        return "#f59e0b".equals(hexColor) || "#9ca3af".equals(hexColor)
                || "#6b7280".equals(hexColor) || "#94a3b8".equals(hexColor);
    }
}
