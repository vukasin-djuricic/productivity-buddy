package org.productivity_buddy.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PathTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.productivity_buddy.ProductivityBuddy;

import java.util.ArrayList;
import java.util.List;

public class NewHelpView implements RefreshableView {

    private final ProductivityBuddy app;

    // stanje scene
    private int currentScene = 0;
    private Pane canvas;
    private Button btnNext;
    private Label lblCounter;
    private Label lblTypewriter;

    // box-ovi koji se referenciraju izmedju scena
    private StackPane registryBox;
    private StackPane scannerBox;
    private StackPane scanTaskBox;
    private StackPane categorizationBox;
    private StackPane analyticsBox;
    private StackPane uiViewsBox;
    private StackPane fileServiceBox;
    private StackPane fileWatcherBox;

    // strelica iz scene 2 koja nestaje u sceni 3
    private Group scannerToRegistryLine;

    // registar svih animacija za cleanup
    private final List<Animation> allAnimations = new ArrayList<>();

    // aktivni typewriter — stopira se pre novog
    private Timeline currentTypewriter;

    // anotacije i labeli koji pripadaju scenama (za replay reset)
    private final List<Node> sceneElements = new ArrayList<>();

    public NewHelpView(ProductivityBuddy app) {
        this.app = app;
    }

    public Node createView() {
        VBox root = new VBox();
        root.setPadding(new Insets(0));
        root.getStyleClass().add("root-pane");

        // zaglavlje
        HBox header = new HBox(12);
        header.setPadding(new Insets(16, 24, 8, 24));
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("\u2190 Back to Main");
        btnBack.getStyleClass().addAll("btn-secondary", "back-button");
        btnBack.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                stopAllAnimations();
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

        // canvas — centralni prostor za animacije
        canvas = new Pane();
        canvas.setPrefSize(1200, 750);
        canvas.setMinSize(1200, 750);
        canvas.setStyle("-fx-background-color: #1a1830; -fx-background-radius: 10;");

        // ScrollPane omogucava skrolovanje kada prozor nije dovoljno veliki
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setStyle("-fx-background-color: #1a1830; -fx-background: #1a1830;");
        scrollPane.setPadding(new Insets(4, 24, 4, 24));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // footer — uvek vidljiv ispod canvasa
        lblTypewriter = new Label("");
        lblTypewriter.setPrefWidth(800);
        lblTypewriter.setMaxWidth(800);
        lblTypewriter.setMinHeight(54);   // dovoljno za 3 linije teksta
        lblTypewriter.setPrefHeight(54);
        lblTypewriter.setWrapText(true);
        lblTypewriter.setStyle("-fx-font-size: 13px; -fx-text-fill: #c4b5fd; "
                + "-fx-font-style: italic; -fx-text-alignment: center;");
        lblTypewriter.setAlignment(Pos.CENTER);

        lblCounter = new Label("1 / 5");
        lblCounter.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");

        btnNext = new Button("Next Step \u2192");
        btnNext.getStyleClass().add("btn-secondary");
        btnNext.setStyle("-fx-background-color: #8b5cf6; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 6; "
                + "-fx-padding: 6 16; -fx-cursor: hand;");
        btnNext.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                advanceScene();
            }
        });

        Region footerSpacerL = new Region();
        HBox.setHgrow(footerSpacerL, Priority.ALWAYS);
        Region footerSpacerR = new Region();
        HBox.setHgrow(footerSpacerR, Priority.ALWAYS);

        HBox footer = new HBox(12);
        footer.setPadding(new Insets(8, 24, 12, 24));
        footer.setAlignment(Pos.CENTER);
        footer.getChildren().addAll(lblCounter, footerSpacerL, lblTypewriter, footerSpacerR, btnNext);

        root.getChildren().addAll(header, scrollPane, footer);

        // pokreni prvu scenu
        advanceScene();

        return root;
    }

    @Override
    public void refreshUI() {
        // animirani sadrzaj — nema periodicnog refresha
    }

    // ==========================================
    // STATE MACHINE — napredovanje kroz scene
    // ==========================================
    private void advanceScene() {
        currentScene++;
        if (currentScene > 5) return;

        lblCounter.setText(currentScene + " / 5");

        switch (currentScene) {
            case 1: buildScene1(); break;
            case 2: buildScene2(); break;
            case 3: buildScene3(); break;
            case 4: buildScene4(); break;
            case 5: buildScene5(); break;
        }
    }

    // ==========================================
    // SCENA 1 — "The Core" — ProcessRegistry
    // ==========================================
    private void buildScene1() {
        // ProcessRegistry — centralni hub, fade in + scale
        registryBox = createBox("ProcessRegistry", "ConcurrentHashMap hub", "#a78bfa",
                530, 240, 220, 100,
                "Central thread-safe ConcurrentHashMap<String, ProcessInfo>. All threads "
                        + "read/write through this registry. getOrCreate() uses computeIfAbsent for "
                        + "lock-free atomic registration. ProcessInfo fields use AtomicLong, "
                        + "AtomicReference, AtomicBoolean, and volatile for lock-free thread safety.");
        registryBox.setStyle(registryBox.getStyle()
                + " -fx-border-color: #8b5cf6; -fx-border-width: 3; -fx-border-radius: 6;");
        registryBox.setOpacity(0);
        registryBox.setScaleX(0);
        registryBox.setScaleY(0);
        canvas.getChildren().add(registryBox);
        sceneElements.add(registryBox);

        // fade in
        FadeTransition fadeIn = new FadeTransition(Duration.millis(800), registryBox);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        // scale
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(800), registryBox);
        scaleIn.setFromX(0);
        scaleIn.setFromY(0);
        scaleIn.setToX(1);
        scaleIn.setToY(1);

        ParallelTransition intro = new ParallelTransition(fadeIn, scaleIn);
        allAnimations.add(intro);
        intro.play();

        // pulsing glow na registryBox
        DropShadow glow = new DropShadow(15, Color.web("#a78bfa", 0.5));
        registryBox.setEffect(glow);

        Timeline glowPulse = createGlowPulse(glow, 15, 30, "#a78bfa");
        allAnimations.add(glowPulse);
        glowPulse.play();

        // hover override za registry (cuva bazni glow)
        registryBox.setOnMouseEntered(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                registryBox.setEffect(new DropShadow(25, Color.web("#a78bfa", 0.7)));
                registryBox.setScaleX(1.05);
                registryBox.setScaleY(1.05);
            }
        });
        registryBox.setOnMouseExited(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                registryBox.setEffect(glow);
                registryBox.setScaleX(1.0);
                registryBox.setScaleY(1.0);
            }
        });

        // anotacije ispod registry-ja
        Label annot1 = createAnnotation("lock-free ConcurrentHashMap", 545, 345);
        annot1.setOpacity(0);
        canvas.getChildren().add(annot1);
        sceneElements.add(annot1);
        FadeTransition a1Fade = fadeIn(annot1, 600, 400);
        allAnimations.add(a1Fade);
        a1Fade.play();

        Label annot2 = createAnnotation("AtomicLong, AtomicReference, volatile", 530, 362);
        annot2.setOpacity(0);
        canvas.getChildren().add(annot2);
        sceneElements.add(annot2);
        FadeTransition a2Fade = fadeIn(annot2, 600, 600);
        allAnimations.add(a2Fade);
        a2Fade.play();

        // typewriter
        playTypewriter("ProcessRegistry is the heart of Productivity Buddy \u2014 a lock-free "
                + "ConcurrentHashMap that every thread reads and writes without blocking. "
                + "All process data lives here.");
    }

    // ==========================================
    // SCENA 2 — "The Scanners" — ProcessScanner + ScanTask
    // ==========================================
    private void buildScene2() {
        // ProcessScanner — slide sa leve strane
        scannerBox = createBox("ProcessScanner", "ScheduledExecutor + ForkJoinPool", "#06b6d4",
                60, 260, 200, 80,
                "ScheduledExecutorService triggers scan() every 3s. Phase 1: OSHI CPU/RAM metrics. "
                        + "Phase 2: ForkJoinPool with recursive ScanTask. Phase 3: time update per ProcessInfo.");
        scannerBox.setOpacity(0);
        scannerBox.setTranslateX(-300);
        canvas.getChildren().add(scannerBox);
        sceneElements.add(scannerBox);

        // slide in animacija
        TranslateTransition scannerSlide = new TranslateTransition(Duration.millis(700), scannerBox);
        scannerSlide.setFromX(-300);
        scannerSlide.setToX(0);
        FadeTransition scannerFade = new FadeTransition(Duration.millis(700), scannerBox);
        scannerFade.setFromValue(0);
        scannerFade.setToValue(1);
        ParallelTransition scannerAnim = new ParallelTransition(scannerSlide, scannerFade);
        allAnimations.add(scannerAnim);
        scannerAnim.play();

        // ScanTask — pada odozgo, dashed border
        scanTaskBox = createBox("ScanTask", "RecursiveAction", "#0891b2",
                85, 350, 150, 35,
                "Splits ProcessHandle array at midpoint (threshold=10). Left half fork()'d, "
                        + "right half compute()'d in place. Registers via registry.getOrCreate().");
        scanTaskBox.setStyle(scanTaskBox.getStyle()
                + " -fx-border-color: #06b6d4; -fx-border-width: 1;"
                + " -fx-border-style: dashed; -fx-border-radius: 6;");
        scanTaskBox.setOpacity(0);
        scanTaskBox.setTranslateY(-150);
        canvas.getChildren().add(scanTaskBox);
        sceneElements.add(scanTaskBox);

        // ScanTask drop animacija — sa 300ms zadrskom
        PauseTransition stDelay = new PauseTransition(Duration.millis(300));
        TranslateTransition stSlide = new TranslateTransition(Duration.millis(500), scanTaskBox);
        stSlide.setFromY(-150);
        stSlide.setToY(0);
        FadeTransition stFade = new FadeTransition(Duration.millis(500), scanTaskBox);
        stFade.setFromValue(0);
        stFade.setToValue(1);
        ParallelTransition stAnim = new ParallelTransition(stSlide, stFade);
        SequentialTransition stSequence = new SequentialTransition(stDelay, stAnim);
        allAnimations.add(stSequence);
        stSequence.play();

        // anotacija za ScanTask
        Label stAnnot = createAnnotation("fork()/compute() work-stealing", 68, 400);
        stAnnot.setOpacity(0);
        canvas.getChildren().add(stAnnot);
        sceneElements.add(stAnnot);
        FadeTransition stAnnotFade = fadeIn(stAnnot, 500, 800);
        allAnimations.add(stAnnotFade);
        stAnnotFade.play();

        // animirana strelica: Scanner centar desno → Registry centar levo
        // Scanner desni ivica: 60+200=260, centar Y: 260+40=300
        // Registry levi ivica: 530, centar Y: 240+50=290
        scannerToRegistryLine = animateLineDrawing(260, 300, 530, 290, "#06b6d4", 600, 500);
        sceneElements.add(scannerToRegistryLine);

        // label na strelici
        Label arrowLabel = createArrowLabel("getOrCreate()", 360, 278, "#06b6d4");
        arrowLabel.setOpacity(0);
        canvas.getChildren().add(arrowLabel);
        sceneElements.add(arrowLabel);
        FadeTransition alFade = fadeIn(arrowLabel, 400, 1100);
        allAnimations.add(alFade);
        alFade.play();

        // typewriter
        playTypewriter("Every 3 seconds, ProcessScanner triggers a scan. It uses a ForkJoinPool "
                + "with recursive ScanTask that splits the process list using work-stealing, "
                + "then registers each process in the Registry via getOrCreate().");
    }

    // ==========================================
    // SCENA 3 — "The Categorizer" — CategorizationService
    // ==========================================
    private void buildScene3() {
        // fade out stare direktne strelice Scanner → Registry
        if (scannerToRegistryLine != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(500), scannerToRegistryLine);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            allAnimations.add(fadeOut);
            fadeOut.play();
        }

        // CategorizationService — fade in + scale na (330, 275)
        categorizationBox = createBox("CategorizationService", "Rule-based regex", "#c084fc",
                330, 275, 180, 60,
                "Regex pravila iz categorization_rules.json. Poziva se atomicno unutar "
                        + "ProcessRegistry.computeIfAbsent() pri getOrCreate(). "
                        + "Hot-reload putem FileWatcherWorker.");
        categorizationBox.setOpacity(0);
        categorizationBox.setScaleX(0.5);
        categorizationBox.setScaleY(0.5);
        canvas.getChildren().add(categorizationBox);
        sceneElements.add(categorizationBox);

        FadeTransition catFade = new FadeTransition(Duration.millis(600), categorizationBox);
        catFade.setFromValue(0);
        catFade.setToValue(1);
        ScaleTransition catScale = new ScaleTransition(Duration.millis(600), categorizationBox);
        catScale.setFromX(0.5);
        catScale.setFromY(0.5);
        catScale.setToX(1);
        catScale.setToY(1);
        ParallelTransition catAnim = new ParallelTransition(catFade, catScale);
        allAnimations.add(catAnim);
        catAnim.play();

        // nova strelica 1: Scanner → Categorizer
        // Scanner desno: 260, Y: 300 → Categorizer levo: 330, centar Y: 275+30=305
        Group arrow1 = animateLineDrawing(260, 300, 330, 305, "#06b6d4", 500, 400);
        sceneElements.add(arrow1);

        Label lbl1 = createArrowLabel("scan result", 272, 288, "#06b6d4");
        lbl1.setOpacity(0);
        canvas.getChildren().add(lbl1);
        sceneElements.add(lbl1);
        FadeTransition lbl1Fade = fadeIn(lbl1, 400, 900);
        allAnimations.add(lbl1Fade);
        lbl1Fade.play();

        // nova strelica 2: Categorizer → Registry
        // Categorizer desno: 330+180=510, centar Y: 305 → Registry levo: 530, centar Y: 290
        Group arrow2 = animateLineDrawing(510, 305, 530, 290, "#c084fc", 500, 700);
        sceneElements.add(arrow2);

        /*Label lbl2 = createArrowLabel("categorize()", 490, 312, "#c084fc");
        lbl2.setOpacity(0);
        canvas.getChildren().add(lbl2);
        sceneElements.add(lbl2);
        FadeTransition lbl2Fade = fadeIn(lbl2, 400, 1200);
        allAnimations.add(lbl2Fade);
        lbl2Fade.play();*/

        // anotacija
        Label catAnnot = createAnnotation("called inside computeIfAbsent()", 330, 340);
        catAnnot.setOpacity(0);
        canvas.getChildren().add(catAnnot);
        sceneElements.add(catAnnot);
        FadeTransition catAnnotFade = fadeIn(catAnnot, 500, 1000);
        allAnimations.add(catAnnotFade);
        catAnnotFade.play();

        // typewriter
        playTypewriter("CategorizationService sits inside the Registry's computeIfAbsent() \u2014 "
                + "when a new process appears, it's automatically categorized using regex rules "
                + "from categorization_rules.json. Rules hot-reload via FileWatcher.");
    }

    // ==========================================
    // SCENA 4 — "The Consumers" — Analytics + UI Views
    // ==========================================
    private void buildScene4() {
        // AnalyticsWorker — slide sa desne strane
        analyticsBox = createBox("AnalyticsWorker", "Daemon thread, 2s loop", "#10b981",
                800, 260, 170, 80,
                "Daemon thread aggregating time-by-category and top-10 processes every 2s. "
                        + "Triggers UI refresh via Platform.runLater(). Also checks fixed snapshot times.");
        analyticsBox.setOpacity(0);
        analyticsBox.setTranslateX(400);
        canvas.getChildren().add(analyticsBox);
        sceneElements.add(analyticsBox);

        TranslateTransition analyticsSlide = new TranslateTransition(Duration.millis(700), analyticsBox);
        analyticsSlide.setFromX(400);
        analyticsSlide.setToX(0);
        FadeTransition analyticsFade = new FadeTransition(Duration.millis(700), analyticsBox);
        analyticsFade.setFromValue(0);
        analyticsFade.setToValue(1);
        ParallelTransition analyticsAnim = new ParallelTransition(analyticsSlide, analyticsFade);
        allAnimations.add(analyticsAnim);
        analyticsAnim.play();

        // UI Views — slide sa desne strane, 200ms delay
        uiViewsBox = createBox("UI Views", "4 refreshable views", "#9ca3af",
                1060, 260, 150, 80,
                "MainChartView, ProcessDetailView, SpecificCategoryView, HelpView. "
                        + "All implement RefreshableView and refresh via Platform.runLater().");
        uiViewsBox.setOpacity(0);
        uiViewsBox.setTranslateX(400);
        canvas.getChildren().add(uiViewsBox);
        sceneElements.add(uiViewsBox);

        PauseTransition uiDelay = new PauseTransition(Duration.millis(200));
        TranslateTransition uiSlide = new TranslateTransition(Duration.millis(700), uiViewsBox);
        uiSlide.setFromX(400);
        uiSlide.setToX(0);
        FadeTransition uiFade = new FadeTransition(Duration.millis(700), uiViewsBox);
        uiFade.setFromValue(0);
        uiFade.setToValue(1);
        ParallelTransition uiAnim = new ParallelTransition(uiSlide, uiFade);
        SequentialTransition uiSequence = new SequentialTransition(uiDelay, uiAnim);
        allAnimations.add(uiSequence);
        uiSequence.play();

        // strelica: Registry desno → Analytics levo
        // Registry desno: 530+220=750, centar Y: 290 → Analytics levo: 800, centar Y: 300
        Group arrow1 = animateLineDrawing(750, 290, 800, 300, "#10b981", 500, 600);
        sceneElements.add(arrow1);
        Label lbl1 = createArrowLabel("getAll()", 755, 278, "#10b981");
        lbl1.setOpacity(0);
        canvas.getChildren().add(lbl1);
        sceneElements.add(lbl1);
        FadeTransition lbl1Fade = fadeIn(lbl1, 400, 1100);
        allAnimations.add(lbl1Fade);
        lbl1Fade.play();

        // strelica: Analytics desno → UI Views levo
        // Analytics desno: 800+170=970, centar Y: 300 → UI Views levo: 1010, centar Y: 300
        Group arrow2 = animateLineDrawing(970, 300, 1060, 300, "#10b981", 500, 800);
        sceneElements.add(arrow2);
        Label lbl2 = createArrowLabel("Platform.runLater()", 968, 282, "#10b981");
        lbl2.setOpacity(0);
        canvas.getChildren().add(lbl2);
        sceneElements.add(lbl2);
        FadeTransition lbl2Fade = fadeIn(lbl2, 400, 1300);
        allAnimations.add(lbl2Fade);
        lbl2Fade.play();

        // dashed feedback strelica: Analytics → Registry (reads every 2s)
        // Analytics levo dole: 800, centar Y+20=320 → Registry desno: 750, centar Y: 310
        // Pravimo kao dashed liniju ispod glavne
        Group feedbackArrow = animateLineDrawing(800, 320, 750, 310, "#10b981", 500, 1000, true);
        sceneElements.add(feedbackArrow);
        Label lblFeedback = createAnnotation("reads every 2s", 755, 320);
        lblFeedback.setOpacity(0);
        canvas.getChildren().add(lblFeedback);
        sceneElements.add(lblFeedback);
        FadeTransition fbFade = fadeIn(lblFeedback, 400, 1500);
        allAnimations.add(fbFade);
        fbFade.play();

        // typewriter
        playTypewriter("AnalyticsWorker reads the entire Registry every 2 seconds, aggregates "
                + "time-by-category and top-10 processes, then pushes UI updates via "
                + "Platform.runLater() to the 4 refreshable views.");
    }

    // ==========================================
    // SCENA 5 — "The Live Orchestra" — FileService + FileWatcher + particles
    // ==========================================
    private void buildScene5() {
        // FileService — slide gore sa dna
        fileServiceBox = createBox("FileService", "Single-thread executor", "#6366f1",
                565, 470, 180, 65,
                "Single-thread executor serializing all file I/O. JSON save/load + CSV snapshot export.");
        fileServiceBox.setOpacity(0);
        fileServiceBox.setTranslateY(400);
        canvas.getChildren().add(fileServiceBox);
        sceneElements.add(fileServiceBox);

        TranslateTransition fsSlide = new TranslateTransition(Duration.millis(700), fileServiceBox);
        fsSlide.setFromY(400);
        fsSlide.setToY(0);
        FadeTransition fsFade = new FadeTransition(Duration.millis(700), fileServiceBox);
        fsFade.setFromValue(0);
        fsFade.setToValue(1);
        ParallelTransition fsAnim = new ParallelTransition(fsSlide, fsFade);
        allAnimations.add(fsAnim);
        fsAnim.play();

        // FileWatcherWorker — slide gore sa dna, 200ms delay
        fileWatcherBox = createBox("FileWatcherWorker", "WatchService daemon", "#f43f5e",
                555, 590, 200, 60,
                "WatchService daemon monitoring process_info.json and categorization_rules.json. "
                        + "200ms debounce, then applies changes.");
        fileWatcherBox.setOpacity(0);
        fileWatcherBox.setTranslateY(400);
        canvas.getChildren().add(fileWatcherBox);
        sceneElements.add(fileWatcherBox);

        PauseTransition fwDelay = new PauseTransition(Duration.millis(200));
        TranslateTransition fwSlide = new TranslateTransition(Duration.millis(700), fileWatcherBox);
        fwSlide.setFromY(400);
        fwSlide.setToY(0);
        FadeTransition fwFade = new FadeTransition(Duration.millis(700), fileWatcherBox);
        fwFade.setFromValue(0);
        fwFade.setToValue(1);
        ParallelTransition fwAnim = new ParallelTransition(fwSlide, fwFade);
        SequentialTransition fwSequence = new SequentialTransition(fwDelay, fwAnim);
        allAnimations.add(fwSequence);
        fwSequence.play();

        // strelice: Registry ↔ FileService
        // Registry dno: centar X=640, Y=340 → FileService vrh: centar X=655, Y=470
        Group arrowDown = animateLineDrawing(650, 340, 650, 470, "#6366f1", 500, 600);
        sceneElements.add(arrowDown);
        Label lblSave = createArrowLabel("save", 655, 395, "#6366f1");
        lblSave.setOpacity(0);
        canvas.getChildren().add(lblSave);
        sceneElements.add(lblSave);
        FadeTransition saveFade = fadeIn(lblSave, 400, 1100);
        allAnimations.add(saveFade);
        saveFade.play();

        Group arrowUp = animateLineDrawing(625, 470, 625, 340, "#6366f1", 500, 800, true);
        sceneElements.add(arrowUp);
        Label lblLoad = createArrowLabel("load", 600, 395, "#6366f1");
        lblLoad.setOpacity(0);
        canvas.getChildren().add(lblLoad);
        sceneElements.add(lblLoad);
        FadeTransition loadFade = fadeIn(lblLoad, 400, 1300);
        allAnimations.add(loadFade);
        loadFade.play();

        // FileService → FileWatcher
        Group fsFwArrow = animateLineDrawing(655, 535, 655, 590, "#6366f1", 400, 1000);
        sceneElements.add(fsFwArrow);

        // FileWatcher → CategorizationService
        // FileWatcher levo: 555, centar Y: 620 → Categorizer dno: 420, Y=335
        Group fwCatArrow = animateLineDrawing(555, 620, 420, 340, "#f43f5e", 500, 1200, true);
        sceneElements.add(fwCatArrow);
        Label lblReload = createArrowLabel("reloadRules()", 460, 380, "#f43f5e");
        lblReload.setOpacity(0);
        canvas.getChildren().add(lblReload);
        sceneElements.add(lblReload);
        FadeTransition reloadFade = fadeIn(lblReload, 400, 1700);
        allAnimations.add(reloadFade);
        reloadFade.play();

        // anotacija za FileWatcher
        Label fwAnnot = createAnnotation("WatchService + 200ms debounce", 555, 655);
        fwAnnot.setOpacity(0);
        canvas.getChildren().add(fwAnnot);
        sceneElements.add(fwAnnot);
        FadeTransition fwAnnotFade = fadeIn(fwAnnot, 500, 1400);
        allAnimations.add(fwAnnotFade);
        fwAnnotFade.play();

        // zameni Next dugme sa Replay
        btnNext.setText("Replay \u21ba");
        btnNext.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                resetAndReplay();
            }
        });

        // pokreni "Live Orchestra" particle animacije sa zadrskom
        PauseTransition orchestraDelay = new PauseTransition(Duration.millis(1800));
        orchestraDelay.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                playLiveOrchestra();
            }
        });
        allAnimations.add(orchestraDelay);
        orchestraDelay.play();

        // typewriter
        playTypewriter("The full orchestra plays \u2014 FileService persists state to disk, "
                + "FileWatcherWorker monitors for external changes and hot-reloads categorization rules. "
                + "Watch the data flow as particles travel through the system!");
    }

    // ==========================================
    // LIVE ORCHESTRA — kontinualne particle animacije
    // ==========================================
    private void playLiveOrchestra() {
        // Scanner loop: svake 3s — cijan cestica putuje Scanner → Categorizer → Registry
        Timeline scannerLoop = new Timeline(new KeyFrame(Duration.seconds(3), new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                // cestica: Scanner centar (160, 300) → Categorizer centar (420, 305) → Registry centar (640, 290)
                Circle particle = createParticle("#06b6d4");
                canvas.getChildren().add(particle);
                sceneElements.add(particle);

                PathTransition pt = createParticleTravel(particle, new double[][]{
                        {160, 300}, {420, 305}, {640, 290}
                }, 2500);
                pt.setOnFinished(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        canvas.getChildren().remove(particle);
                        sceneElements.remove(particle);
                    }
                });
                allAnimations.add(pt);
                pt.play();

                // glow pulse na scanner boxu
                if (scannerBox != null) {
                    pulseNode(scannerBox, "#06b6d4");
                }
            }
        }));
        scannerLoop.setCycleCount(Animation.INDEFINITE);
        allAnimations.add(scannerLoop);
        scannerLoop.play();

        // Analytics loop: svake 2s (1s offset) — zelena cestica Registry → Analytics → UI Views
        PauseTransition analyticsOffset = new PauseTransition(Duration.seconds(1));
        analyticsOffset.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Timeline analyticsLoop = new Timeline(new KeyFrame(Duration.seconds(2), new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event2) {
                        Circle particle = createParticle("#10b981");
                        canvas.getChildren().add(particle);
                        sceneElements.add(particle);

                        PathTransition pt = createParticleTravel(particle, new double[][]{
                                {640, 290}, {885, 300}, {1085, 300}
                        }, 1800);
                        pt.setOnFinished(new EventHandler<ActionEvent>() {
                            @Override
                            public void handle(ActionEvent e) {
                                canvas.getChildren().remove(particle);
                                sceneElements.remove(particle);
                            }
                        });
                        allAnimations.add(pt);
                        pt.play();

                        // glow pulse na analytics boxu
                        if (analyticsBox != null) {
                            pulseNode(analyticsBox, "#10b981");
                        }
                    }
                }));
                analyticsLoop.setCycleCount(Animation.INDEFINITE);
                allAnimations.add(analyticsLoop);
                analyticsLoop.play();
            }
        });
        allAnimations.add(analyticsOffset);
        analyticsOffset.play();

        // FileService loop: svake 5s — ljubicasta cestica Registry → FileService
        PauseTransition fileOffset = new PauseTransition(Duration.seconds(2));
        fileOffset.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Timeline fileLoop = new Timeline(new KeyFrame(Duration.seconds(5), new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event2) {
                        Circle particle = createParticle("#6366f1");
                        canvas.getChildren().add(particle);
                        sceneElements.add(particle);

                        PathTransition pt = createParticleTravel(particle, new double[][]{
                                {640, 340}, {655, 500}
                        }, 1500);
                        pt.setOnFinished(new EventHandler<ActionEvent>() {
                            @Override
                            public void handle(ActionEvent e) {
                                canvas.getChildren().remove(particle);
                                sceneElements.remove(particle);
                            }
                        });
                        allAnimations.add(pt);
                        pt.play();

                        // glow pulse na fileService boxu
                        if (fileServiceBox != null) {
                            pulseNode(fileServiceBox, "#6366f1");
                        }
                    }
                }));
                fileLoop.setCycleCount(Animation.INDEFINITE);
                allAnimations.add(fileLoop);
                fileLoop.play();
            }
        });
        allAnimations.add(fileOffset);
        fileOffset.play();
    }

    // ==========================================
    // REPLAY — resetuj sve i pokreni ponovo
    // ==========================================
    private void resetAndReplay() {
        stopAllAnimations();

        // ukloni sve scenske elemente sa canvasa
        for (Node node : sceneElements) {
            canvas.getChildren().remove(node);
        }
        sceneElements.clear();

        // resetuj stanje
        currentScene = 0;
        registryBox = null;
        scannerBox = null;
        scanTaskBox = null;
        categorizationBox = null;
        analyticsBox = null;
        uiViewsBox = null;
        fileServiceBox = null;
        fileWatcherBox = null;
        scannerToRegistryLine = null;

        // resetuj dugme i typewriter
        btnNext.setText("Next Step \u2192");
        btnNext.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                advanceScene();
            }
        });
        lblTypewriter.setText("");

        // pokreni prvu scenu
        advanceScene();
    }

    // ==========================================
    // STOP SVIH ANIMACIJA
    // ==========================================
    private void stopAllAnimations() {
        for (Animation anim : allAnimations) {
            if (anim != null) {
                anim.stop();
            }
        }
        allAnimations.clear();
        currentTypewriter = null;
    }

    // ==========================================
    // TYPEWRITER EFEKAT
    // ==========================================
    private void playTypewriter(final String text) {
        // stopira prethodni typewriter da ne bi doslo do preklapanja
        if (currentTypewriter != null) {
            currentTypewriter.stop();
            allAnimations.remove(currentTypewriter);
        }
        lblTypewriter.setText("");
        final StringBuilder sb = new StringBuilder();
        Timeline typewriter = new Timeline();
        currentTypewriter = typewriter;

        for (int i = 0; i < text.length(); i++) {
            final int index = i;
            typewriter.getKeyFrames().add(new KeyFrame(Duration.millis(25 * (i + 1)),
                    new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent event) {
                            sb.append(text.charAt(index));
                            lblTypewriter.setText(sb.toString());
                        }
                    }));
        }

        allAnimations.add(typewriter);
        typewriter.play();
    }

    // ==========================================
    // ANIMIRANA LINIJA — crta se od pocetka do kraja
    // ==========================================
    private Group animateLineDrawing(double x1, double y1, double x2, double y2,
                                     String color, int durationMs, int delayMs) {
        return animateLineDrawing(x1, y1, x2, y2, color, durationMs, delayMs, false);
    }

    private Group animateLineDrawing(double x1, double y1, double x2, double y2,
                                     String color, int durationMs, int delayMs, boolean dashed) {
        Group group = new Group();

        Line line = new Line(x1, y1, x1, y1);
        line.setStroke(Color.web(color));
        line.setStrokeWidth(2);
        if (dashed) {
            line.getStrokeDashArray().addAll(8.0, 5.0);
        }

        // arrowhead — na pocetku nevidljiv
        Polygon arrowhead = createArrowhead(x1, y1, x2, y2, color);
        arrowhead.setOpacity(0);

        group.getChildren().addAll(line, arrowhead);
        canvas.getChildren().add(group);

        // animacija crtanja linije
        Timeline drawLine = new Timeline();
        drawLine.getKeyFrames().addAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(line.endXProperty(), x1),
                        new KeyValue(line.endYProperty(), y1)),
                new KeyFrame(Duration.millis(durationMs),
                        new KeyValue(line.endXProperty(), x2),
                        new KeyValue(line.endYProperty(), y2))
        );

        // arrowhead pojavi se na kraju
        drawLine.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                // azuriraj poziciju arrowhead-a
                updateArrowhead(arrowhead, x1, y1, x2, y2);
                FadeTransition arrowFade = new FadeTransition(Duration.millis(200), arrowhead);
                arrowFade.setFromValue(0);
                arrowFade.setToValue(1);
                allAnimations.add(arrowFade);
                arrowFade.play();
            }
        });

        // sa zadrskom
        PauseTransition delay = new PauseTransition(Duration.millis(delayMs));
        SequentialTransition seq = new SequentialTransition(delay, drawLine);
        allAnimations.add(seq);
        seq.play();

        return group;
    }

    // ==========================================
    // PARTICLE (cestica) — sjajni kruzic
    // ==========================================
    private Circle createParticle(String color) {
        Circle particle = new Circle(5);
        particle.setFill(Color.web(color));
        particle.setStroke(Color.WHITE);
        particle.setStrokeWidth(1.2);
        DropShadow particleGlow = new DropShadow(10, Color.web(color, 0.8));
        particle.setEffect(particleGlow);
        return particle;
    }

    // ==========================================
    // PARTICLE PUTOVANJE — PathTransition duz tacaka
    // ==========================================
    private PathTransition createParticleTravel(Circle particle, double[][] points, int durationMs) {
        Path path = new Path();
        path.getElements().add(new MoveTo(points[0][0], points[0][1]));
        for (int i = 1; i < points.length; i++) {
            path.getElements().add(new LineTo(points[i][0], points[i][1]));
        }

        PathTransition pt = new PathTransition();
        pt.setDuration(Duration.millis(durationMs));
        pt.setPath(path);
        pt.setNode(particle);
        pt.setCycleCount(1);

        return pt;
    }

    // ==========================================
    // GLOW PULSE — animira DropShadow radius
    // ==========================================
    private Timeline createGlowPulse(final DropShadow shadow, double minR, double maxR, String color) {
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(shadow.radiusProperty(), minR)),
                new KeyFrame(Duration.millis(1200), new KeyValue(shadow.radiusProperty(), maxR))
        );
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        return pulse;
    }

    // ==========================================
    // KRATKI GLOW PULSE NA NODU — za orchestra efekat
    // ==========================================
    private void pulseNode(final Node node, String color) {
        DropShadow pulse = new DropShadow(20, Color.web(color, 0.8));
        node.setEffect(pulse);

        Timeline restore = new Timeline(new KeyFrame(Duration.millis(500), new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                node.setEffect(null);
            }
        }));
        allAnimations.add(restore);
        restore.play();
    }

    // ==========================================
    // FADE IN SA ZADRSKOM
    // ==========================================
    private FadeTransition fadeIn(Node node, int durationMs, int delayMs) {
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMs));
        return fade;
    }

    // ==========================================
    // ARROWHEAD KREIRANJE I AZURIRANJE
    // ==========================================
    private Polygon createArrowhead(double x1, double y1, double x2, double y2, String color) {
        Polygon arrow = new Polygon();
        arrow.setFill(Color.web(color));
        updateArrowhead(arrow, x1, y1, x2, y2);
        return arrow;
    }

    private void updateArrowhead(Polygon arrow, double x1, double y1, double x2, double y2) {
        arrow.getPoints().clear();

        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;

        // normalizuj
        double ux = dx / len;
        double uy = dy / len;

        // perpendikular
        double px = -uy;
        double py = ux;

        double size = 8;
        arrow.getPoints().addAll(
                x2, y2,
                x2 - ux * size + px * (size * 0.5), y2 - uy * size + py * (size * 0.5),
                x2 - ux * size - px * (size * 0.5), y2 - uy * size - py * (size * 0.5)
        );
    }

    // ==========================================
    // POMOCNE METODE — createBox, createAnnotation, createArrowLabel
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
            subLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + textColor + ";");
            content.getChildren().add(subLabel);
        }

        box.getChildren().add(content);

        // tooltip
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

        return box;
    }

    private Label createAnnotation(String text, double x, double y) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8; "
                + "-fx-font-family: monospace; "
                + "-fx-background-color: #252340; -fx-background-radius: 3; "
                + "-fx-padding: 1 5;");
        return label;
    }

    private Label createArrowLabel(String text, double x, double y, String color) {
        Label label = new Label(text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; "
                + "-fx-background-color: #1a1830; -fx-padding: 1 3;");
        return label;
    }

    private boolean isLightBackground(String hexColor) {
        return "#f59e0b".equals(hexColor) || "#9ca3af".equals(hexColor)
                || "#6b7280".equals(hexColor) || "#94a3b8".equals(hexColor);
    }
}
