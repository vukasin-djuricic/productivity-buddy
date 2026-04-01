package org.productivity_buddy.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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
        root.setStyle("-fx-background-color: #13111c;");

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
        pane.setPrefSize(1050, 700);
        pane.setMinSize(1050, 700);
        pane.setStyle("-fx-background-color: #1a1832; -fx-background-radius: 10;");

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

        // UI Thread kontejner (prozirni pravougaonik)
        Rectangle threadContainer = new Rectangle(20, 32, 490, 100);
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
                145, 48, 240, 68,
                "Entry point (extends Application). start() initializes ProcessRegistry, "
                + "FileService, ProcessScanner, AnalyticsWorker, SnapshotScheduler, and "
                + "FileWatcherWorker. Builds the JavaFX UI and handles graceful shutdown."));

        // ProcessScanner
        pane.getChildren().add(createBox("ProcessScanner", "ForkJoinPool + OSHI", "#06b6d4",
                25, 190, 220, 70,
                "Scans system processes every 3s using ForkJoinPool with recursive ScanTask. "
                + "Collects CPU/RAM metrics via OSHI library and updates ProcessRegistry."));

        // ScanTask (ugnjezdeni)
        StackPane scanTaskBox = createBox("ScanTask", "RecursiveAction", "#0891b2",
                55, 268, 155, 38,
                "Splits ProcessHandle array at midpoint (chunk=10). Left half fork()'d, "
                + "right half compute()'d in place. Registers via registry.getOrCreate().");
        scanTaskBox.setStyle(scanTaskBox.getStyle()
                + " -fx-border-color: #06b6d4; -fx-border-width: 1;"
                + " -fx-border-style: dashed; -fx-border-radius: 6;");
        pane.getChildren().add(scanTaskBox);

        // AnalyticsWorker
        pane.getChildren().add(createBox("AnalyticsWorker", "Daemon thread, 2s loop", "#10b981",
                270, 190, 220, 70,
                "Daemon thread aggregating time-by-category and top-10 processes every 2s. "
                + "Triggers UI refresh via Platform.runLater(). Also checks fixed snapshot times."));

        // SnapshotScheduler
        pane.getChildren().add(createSmallLabel("started by PB \u2193", 95, 332, "#f59e0b"));
        pane.getChildren().add(createBox("SnapshotScheduler", "ScheduledExecutor", "#f59e0b",
                25, 345, 220, 60,
                "ScheduledExecutorService exporting CSV snapshots at configurable intervals. "
                + "Routes through FileService for thread-safe I/O."));

        // FileWatcherWorker
        pane.getChildren().add(createSmallLabel("started by PB \u2193", 340, 332, "#f43f5e"));
        pane.getChildren().add(createBox("FileWatcherWorker", "WatchService daemon", "#f43f5e",
                270, 345, 220, 60,
                "WatchService daemon monitoring process_info.json. On file change, waits 200ms "
                + "then calls FileService.parseAndApplyJson() to apply changes to the registry."));

        // FileService
        pane.getChildren().add(createBox("FileService", "Single-thread executor", "#6366f1",
                130, 445, 250, 60,
                "Single-thread executor serializing all file I/O. Handles JSON save/load "
                + "and CSV snapshot export. Prevents concurrent file writes via queue."));

        // --- strelice ---

        // PB → ProcessScanner
        pane.getChildren().add(createArrow(
                new double[][]{{200, 116}, {200, 158}, {135, 158}, {135, 190}},
                "#06b6d4", false));
        pane.getChildren().add(createArrowLabel("schedule()", 140, 146, "#06b6d4"));

        // PB → AnalyticsWorker
        pane.getChildren().add(createArrow(
                new double[][]{{330, 116}, {330, 158}, {380, 158}, {380, 190}},
                "#10b981", false));
        pane.getChildren().add(createArrowLabel("Thread.start()", 332, 146, "#10b981"));

        // SnapshotScheduler → FileService
        pane.getChildren().add(createArrow(
                new double[][]{{135, 405}, {135, 445}},
                "#f59e0b", false));
        pane.getChildren().add(createArrowLabel("saveSnapshot()", 140, 418, "#f59e0b"));

        // FileWatcherWorker → FileService
        pane.getChildren().add(createArrow(
                new double[][]{{380, 405}, {380, 445}},
                "#f43f5e", false));
        pane.getChildren().add(createArrowLabel("parseAndApply()", 300, 418, "#f43f5e"));

        // AnalyticsWorker → UI Thread (Platform.runLater povratna petlja)
        pane.getChildren().add(createArrow(
                new double[][]{{490, 225}, {508, 225}, {508, 82}, {385, 82}},
                "#10b981", true));
        pane.getChildren().add(createArrowLabel("Platform.runLater()", 435, 145, "#10b981"));
    }

    // ==========================================
    // DESNA STRANA — Data Flow
    // ==========================================
    private void buildDataFlow(Pane pane) {
        pane.getChildren().add(createSectionLabel("DATA FLOW", 570, 10));

        // ProcessScanner referenca
        pane.getChildren().add(createBox("ProcessScanner", "Writes every 3s", "#06b6d4",
                575, 65, 170, 55,
                "Scans system processes every 3s using ForkJoinPool with recursive ScanTask. "
                + "Collects CPU/RAM metrics via OSHI library and updates ProcessRegistry."));

        // AnalyticsWorker referenca
        pane.getChildren().add(createBox("AnalyticsWorker", "Reads every 2s", "#10b981",
                870, 65, 155, 55,
                "Daemon thread aggregating time-by-category and top-10 processes every 2s. "
                + "Triggers UI refresh via Platform.runLater()."));

        // ProcessRegistry (centralni hub — istaknut)
        StackPane registryBox = createBox("ProcessRegistry", "ConcurrentHashMap hub", "#a78bfa",
                670, 265, 210, 85,
                "Central thread-safe ConcurrentHashMap<String, ProcessInfo>. All threads "
                + "read/write through this registry. getOrCreate() uses computeIfAbsent for "
                + "lock-free atomic registration.");
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

        // UI Views
        pane.getChildren().add(createBox("UI Views", "3 refreshable views", "#9ca3af",
                840, 430, 185, 85,
                "MainChartView (dashboard with pie chart), ProcessDetailView (single process "
                + "stats), SpecificCategoryView (category drill-down). All implement "
                + "RefreshableView/RefreshablePanel and are refreshed via Platform.runLater()."));

        // FileService referenca
        pane.getChildren().add(createBox("FileService", "Async JSON/CSV I/O", "#6366f1",
                575, 430, 170, 60,
                "Single-thread executor serializing all file I/O. Handles JSON save/load "
                + "and CSV snapshot export."));

        // External Files
        StackPane filesBox = createBox("Disk I/O", "JSON config / CSV snapshots", "#374151",
                580, 545, 165, 45,
                "process_info.json stores persistent state (name, alias, category, frozen, time). "
                + "CSV snapshots (data/snapshot_*.csv) contain timestamped metrics.");
        filesBox.setStyle(filesBox.getStyle()
                + " -fx-border-color: #6b7280; -fx-border-width: 1; -fx-border-radius: 6;");
        pane.getChildren().add(filesBox);

        // --- strelice ---

        // Scanner → Registry
        pane.getChildren().add(createArrow(
                new double[][]{{660, 120}, {660, 265}},
                "#06b6d4", false));
        pane.getChildren().add(createArrowLabel("getOrCreate()", 665, 185, "#06b6d4"));

        // Registry → Analytics (getAll)
        pane.getChildren().add(createArrow(
                new double[][]{{880, 300}, {920, 300}, {920, 120}},
                "#10b981", false));
        pane.getChildren().add(createArrowLabel("getAll()", 925, 210, "#10b981"));

        // Analytics → UI Views (Platform.runLater → refreshUI)
        pane.getChildren().add(createArrow(
                new double[][]{{970, 120}, {970, 430}},
                "#10b981", false));
        pane.getChildren().add(createArrowLabel("refreshUI()", 975, 280, "#10b981"));

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
        Line divider = new Line(540, 25, 540, 610);
        divider.setStroke(Color.web("#374151"));
        divider.setStrokeWidth(1);
        divider.getStrokeDashArray().addAll(8.0, 6.0);
        pane.getChildren().add(divider);
    }

    private void buildLegend(Pane pane) {
        Line topLine = new Line(20, 625, 1030, 625);
        topLine.setStroke(Color.web("#374151"));
        topLine.setStrokeWidth(1);
        pane.getChildren().add(topLine);

        // red 1 — komponente
        addLegendItem(pane, 30, 635, "#8b5cf6", "UI Thread");
        addLegendItem(pane, 155, 635, "#06b6d4", "ProcessScanner");
        addLegendItem(pane, 310, 635, "#10b981", "AnalyticsWorker");
        addLegendItem(pane, 470, 635, "#f59e0b", "Snapshot");
        addLegendItem(pane, 590, 635, "#f43f5e", "FileWatcher");
        addLegendItem(pane, 730, 635, "#6366f1", "FileService");
        addLegendItem(pane, 870, 635, "#a78bfa", "Registry");

        // red 2 — stilovi linija
        addLegendItem(pane, 30, 660, "#9ca3af", "UI Views");

        Line solidSample = new Line(195, 668, 240, 668);
        solidSample.setStroke(Color.web("#d1d5db"));
        solidSample.setStrokeWidth(2);
        pane.getChildren().add(solidSample);
        pane.getChildren().add(createLegendLabel("Data flow", 248, 660));

        Line dashedSample = new Line(350, 668, 395, 668);
        dashedSample.setStroke(Color.web("#d1d5db"));
        dashedSample.setStrokeWidth(2);
        dashedSample.getStrokeDashArray().addAll(8.0, 5.0);
        pane.getChildren().add(dashedSample);
        pane.getChildren().add(createLegendLabel("Callback / triggered", 403, 660));
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
            subLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.8;");
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
        box.setCursor(Cursor.HAND);

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

    private Label createArrowLabel(String text, double x, double y, String color) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 9px; -fx-text-fill: " + color + "; "
                + "-fx-background-color: #1a1832; -fx-padding: 1 3;");
        return label;
    }

    private Label createSectionLabel(String text, double x, double y) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;"
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
        return "#f59e0b".equals(hexColor) || "#9ca3af".equals(hexColor);
    }
}
