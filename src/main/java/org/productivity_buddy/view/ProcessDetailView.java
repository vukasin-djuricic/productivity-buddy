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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import org.productivity_buddy.ProcessCategory;
import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;
import org.productivity_buddy.ProductivityBuddy;
import org.productivity_buddy.TabInfo;

public class ProcessDetailView implements RefreshablePanel {

    private final ProcessRegistry registry;
    private final ProductivityBuddy app;
    private final String processName;

    private Label lblTime;
    private Label lblRamLabel;
    private Label lblRamRank;
    private Label lblCpuLabel;
    private Label lblCpuRank;

    // tabela browser tabova (vidljiva samo za browser procese)
    private TableView<TabInfo> tabTable;
    private Label lblTabsTitle;

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

        // tabela browser tabova — vidljiva samo ako proces ima tabove
        lblTabsTitle = new Label("Browser Tabs");
        lblTabsTitle.getStyleClass().add("section-title");
        lblTabsTitle.setVisible(false);
        lblTabsTitle.setManaged(false);

        tabTable = new TableView<>();
        tabTable.setVisible(false);
        tabTable.setManaged(false);
        tabTable.setPrefHeight(200);
        tabTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<TabInfo, String> colTabTitle = new TableColumn<>("Tab");
        colTabTitle.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TabInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<TabInfo, String> data) {
                return new SimpleStringProperty(data.getValue().getTitle());
            }
        });

        TableColumn<TabInfo, String> colTabDomain = new TableColumn<>("Domain");
        colTabDomain.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TabInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<TabInfo, String> data) {
                return new SimpleStringProperty(data.getValue().getDomain());
            }
        });

        TableColumn<TabInfo, String> colTabCategory = new TableColumn<>("Category");
        colTabCategory.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TabInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<TabInfo, String> data) {
                return new SimpleStringProperty(data.getValue().getCategory());
            }
        });

        TableColumn<TabInfo, String> colTabTime = new TableColumn<>("Time");
        colTabTime.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<TabInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<TabInfo, String> data) {
                long time = data.getValue().getEffectiveTotalTime();
                return new SimpleStringProperty(time > 0 ? ProductivityBuddy.formatTime(time) : "-");
            }
        });

        tabTable.getColumns().addAll(colTabTitle, colTabDomain, colTabCategory, colTabTime);
        VBox.setVgrow(tabTable, Priority.ALWAYS);

        rightPane.getChildren().addAll(lblTabsTitle, tabTable);

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

            // azuriraj browser tabove — spoji transient tabove sa tracked (koji imaju vreme)
            if (tabTable != null) {
                // spoji tracked tabove (sa vremenom) i transient tabove (snapshot)
                java.util.Map<String, TabInfo> merged = new java.util.LinkedHashMap<>();

                // prvo dodaj tracked tabove (imaju akumulirano vreme)
                for (TabInfo tracked : procInfo.getTrackedTabs()) {
                    merged.put(tracked.getDomain(), tracked);
                }

                // dodaj transient tabove koji nisu vec u tracked mapi
                for (TabInfo tab : procInfo.getTabs()) {
                    if (!tab.getDomain().isEmpty() && !merged.containsKey(tab.getDomain())) {
                        merged.put(tab.getDomain(), tab);
                    }
                }

                if (!merged.isEmpty()) {
                    java.util.List<TabInfo> tabList = new java.util.ArrayList<>(merged.values());
                    lblTabsTitle.setText("Browser Tabs (" + tabList.size() + ")");
                    lblTabsTitle.setVisible(true);
                    lblTabsTitle.setManaged(true);
                    tabTable.setVisible(true);
                    tabTable.setManaged(true);
                    tabTable.setItems(FXCollections.observableArrayList(tabList));
                } else {
                    lblTabsTitle.setVisible(false);
                    lblTabsTitle.setManaged(false);
                    tabTable.setVisible(false);
                    tabTable.setManaged(false);
                }
            }
        } else {
            lblTime.setText("Total time - N/A");
            lblRamLabel.setText("RAM usage N/A");
            lblCpuLabel.setText("CPU usage N/A");
            lblRamRank.setText("");
            lblCpuRank.setText("");
        }
    }
}
