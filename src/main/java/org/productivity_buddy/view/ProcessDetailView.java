package org.productivity_buddy.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.productivity_buddy.ProcessCategory;
import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;
import org.productivity_buddy.ProductivityBuddy;

public class ProcessDetailView {

    private final ProcessRegistry registry;
    private final ProductivityBuddy app;

    public ProcessDetailView(ProcessRegistry registry, ProductivityBuddy app) {
        this.registry = registry;
        this.app = app;
    }

    public Node createView(String processName) {
        HBox mainLayout = new HBox(24);
        mainLayout.setPadding(new Insets(24));

        // ===== LEVA STRANA: Tabela procesa (proces | kategorija) =====
        VBox leftPane = new VBox(12);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        Label tableTitle = new Label("Active Processes");
        tableTitle.getStyleClass().add("section-title");

        TableView<ProcessInfo> table = new TableView<>();

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

        table.getColumns().addAll(colName, colCategory);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setItems(FXCollections.observableArrayList(registry.getAll()));

        // dupli klik na drugi proces — otvori njegove detalje
        table.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 1 && table.getSelectionModel().getSelectedItem() != null) {
                    String selected = table.getSelectionModel().getSelectedItem().getOriginalName();
                    app.navigateToProcessDetail(selected);
                }
            }
        });

        leftPane.getChildren().addAll(tableTitle, table);

        // ===== DESNA STRANA: Detalji procesa =====
        VBox rightPane = new VBox(20);
        rightPane.setPrefWidth(420);
        rightPane.setAlignment(Pos.TOP_LEFT);

        ProcessInfo procInfo = registry.get(processName);

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

        long totalTime = (procInfo != null) ? procInfo.getEffectiveTotalTime() : 0;
        Label lblTime = new Label("Total time - " + ProductivityBuddy.formatTime(totalTime));
        lblTime.getStyleClass().add("time-label");

        // RAM i CPU sa rangiranjem
        String ramValue = "N/A";
        String cpuValue = "N/A";
        String ramRank = "";
        String cpuRank = "";

        if (procInfo != null) {
            ramValue = String.format("%.1f%%", (procInfo.getRamUsageBytes() * 100.0)
                    / Runtime.getRuntime().totalMemory());
            cpuValue = String.format("%.1f%%", procInfo.getCpuUsage());

            int cpuPosition = 1;
            int ramPosition = 1;
            int totalProcesses = 0;
            for (ProcessInfo other : registry.getAll()) {
                if (!other.isAlive()) continue;
                totalProcesses++;
                if (other.getCpuUsage() > procInfo.getCpuUsage()) cpuPosition++;
                if (other.getRamUsageBytes() > procInfo.getRamUsageBytes()) ramPosition++;
            }
            ramRank = ramPosition + "th on RAM usage";
            cpuRank = cpuPosition + "th on CPU usage";
        }

        // Stats grid 2x2
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(12);
        statsGrid.setVgap(12);
        statsGrid.getStyleClass().add("stats-box");

        Label lblRamLabel = new Label("RAM usage " + ramValue);
        lblRamLabel.getStyleClass().add("stat-label");
        lblRamLabel.setStyle("-fx-font-size: 14px;");

        Label lblRamRank = new Label(ramRank);
        lblRamRank.getStyleClass().add("stat-rank");
        lblRamRank.setStyle("-fx-font-size: 13px;");

        Label lblCpuLabel = new Label("CPU usage " + cpuValue);
        lblCpuLabel.getStyleClass().add("stat-label");
        lblCpuLabel.setStyle("-fx-font-size: 14px;");

        Label lblCpuRank = new Label(cpuRank);
        lblCpuRank.getStyleClass().add("stat-rank");
        lblCpuRank.setStyle("-fx-font-size: 13px;");

        statsGrid.add(lblRamLabel, 0, 0);
        statsGrid.add(lblRamRank, 1, 0);
        statsGrid.add(lblCpuLabel, 0, 1);
        statsGrid.add(lblCpuRank, 1, 1);

        // Kontrole 2x2: Kill | Change Name / Freeze | Change Category
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

        mainLayout.getChildren().addAll(leftPane, rightPane);
        return mainLayout;
    }
}
