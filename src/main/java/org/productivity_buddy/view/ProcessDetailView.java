package org.productivity_buddy.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import org.productivity_buddy.ProcessCategory;
import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;
import org.productivity_buddy.ProductivityBuddy;

public class ProcessDetailView implements RefreshablePanel {

    private final ProcessRegistry registry;
    private final ProductivityBuddy app;
    private final String processName;

    private Label lblTime;
    private Label lblRamLabel;
    private Label lblRamRank;
    private Label lblCpuLabel;
    private Label lblCpuRank;

    public ProcessDetailView(ProcessRegistry registry, ProductivityBuddy app, String processName) {
        this.registry = registry;
        this.app = app;
        this.processName = processName;
    }

    public Node createRightPanel() {
        ProcessInfo procInfo = registry.get(processName);

        VBox rightPane = new VBox(20);
        rightPane.setPrefWidth(420);
        rightPane.setAlignment(Pos.TOP_LEFT);

        Button btnBack = new Button("\u2190  Back to Main");
        btnBack.getStyleClass().addAll("btn-secondary", "back-button");
        btnBack.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                app.navigateToMain();
            }
        });

        String displayName = (procInfo != null) ? procInfo.getAliasName() : processName;
        Label lblTitle = new Label("Process: " + displayName);
        lblTitle.getStyleClass().add("header-title");

        lblTime = new Label();
        lblTime.getStyleClass().add("time-label");

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(12);
        statsGrid.setVgap(12);
        statsGrid.getStyleClass().add("stats-box");

        lblRamLabel = new Label();
        lblRamLabel.getStyleClass().add("stat-label");
        lblRamLabel.setStyle("-fx-font-size: 14px;");

        lblRamRank = new Label();
        lblRamRank.getStyleClass().add("stat-rank");
        lblRamRank.setStyle("-fx-font-size: 13px;");

        lblCpuLabel = new Label();
        lblCpuLabel.getStyleClass().add("stat-label");
        lblCpuLabel.setStyle("-fx-font-size: 14px;");

        lblCpuRank = new Label();
        lblCpuRank.getStyleClass().add("stat-rank");
        lblCpuRank.setStyle("-fx-font-size: 13px;");

        statsGrid.add(lblRamLabel, 0, 0);
        statsGrid.add(lblRamRank, 1, 0);
        statsGrid.add(lblCpuLabel, 0, 1);
        statsGrid.add(lblCpuRank, 1, 1);

        GridPane controlsGrid = new GridPane();
        controlsGrid.setHgap(14);
        controlsGrid.setVgap(14);

        Button btnKill = new Button("Kill\nProcess");
        btnKill.getStyleClass().add("btn-danger");
        btnKill.setMinWidth(150);
        btnKill.setMinHeight(60);
        btnKill.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (procInfo != null && procInfo.getPid() > 0) {
                    ProcessHandle.of(procInfo.getPid()).ifPresent(new java.util.function.Consumer<ProcessHandle>() {
                        @Override
                        public void accept(ProcessHandle ph) {
                            ph.destroy();
                        }
                    });
                }
            }
        });

        Button btnName = new Button("Change\nName");
        btnName.getStyleClass().add("btn-action");
        btnName.setMinWidth(150);
        btnName.setMinHeight(60);
        btnName.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (procInfo != null) {
                    TextInputDialog dialog = new TextInputDialog(procInfo.getAliasName());
                    dialog.setTitle("Change Name");
                    dialog.setHeaderText("Enter new alias name:");
                    dialog.showAndWait().ifPresent(new java.util.function.Consumer<String>() {
                        @Override
                        public void accept(String newName) {
                            procInfo.setAliasName(newName);
                            lblTitle.setText("Process: " + newName);
                        }
                    });
                }
            }
        });

        Button btnFreeze = new Button("Freeze\nTracking");
        btnFreeze.getStyleClass().add("btn-freeze");
        btnFreeze.setMinWidth(150);
        btnFreeze.setMinHeight(60);
        if (procInfo != null && procInfo.isFrozen()) {
            btnFreeze.setText("Unfreeze\nTracking");
        }
        btnFreeze.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (procInfo != null) {
                    boolean newState = !procInfo.isFrozen();
                    procInfo.setFrozen(newState);
                    btnFreeze.setText(newState ? "Unfreeze\nTracking" : "Freeze\nTracking");
                }
            }
        });

        Button btnCategory = new Button("Change\nCategory");
        btnCategory.getStyleClass().add("btn-action");
        btnCategory.setMinWidth(150);
        btnCategory.setMinHeight(60);
        btnCategory.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (procInfo != null) {
                    ChoiceDialog<String> dialog = new ChoiceDialog<>(
                            procInfo.getCategory(), ProcessCategory.allDisplayNames());
                    dialog.setTitle("Change Category");
                    dialog.setHeaderText("Select category:");
                    dialog.showAndWait().ifPresent(new java.util.function.Consumer<String>() {
                        @Override
                        public void accept(String newCat) {
                            procInfo.setCategory(newCat);
                        }
                    });
                }
            }
        });

        controlsGrid.add(btnKill, 0, 0);
        controlsGrid.add(btnName, 1, 0);
        controlsGrid.add(btnFreeze, 0, 1);
        controlsGrid.add(btnCategory, 1, 1);

        rightPane.getChildren().addAll(btnBack, lblTitle, lblTime, statsGrid, controlsGrid);

        refreshUI();

        return rightPane;
    }

    @Override
    public void refreshUI() {
        ProcessInfo procInfo = registry.get(processName);

        if (procInfo != null) {
            lblTime.setText("Total time - " + ProductivityBuddy.formatTime(procInfo.getEffectiveTotalTime()));
            lblRamLabel.setText("RAM usage " + ProductivityBuddy.formatRam(procInfo.getRamUsageBytes()));
            lblCpuLabel.setText("CPU usage " + String.format("%.1f%%", procInfo.getCpuUsage()));

            int cpuPosition = 1;
            int ramPosition = 1;
            int totalProcesses = 0;
            for (ProcessInfo other : registry.getAll()) {
                if (!other.isAlive()) continue;
                totalProcesses++;
                if (other.getCpuUsage() > procInfo.getCpuUsage()) cpuPosition++;
                if (other.getRamUsageBytes() > procInfo.getRamUsageBytes()) ramPosition++;
            }
            lblRamRank.setText(ramPosition + "th on RAM usage");
            lblCpuRank.setText(cpuPosition + "th on CPU usage");
        } else {
            lblTime.setText("Total time - N/A");
            lblRamLabel.setText("RAM usage N/A");
            lblCpuLabel.setText("CPU usage N/A");
            lblRamRank.setText("");
            lblCpuRank.setText("");
        }
    }
}
