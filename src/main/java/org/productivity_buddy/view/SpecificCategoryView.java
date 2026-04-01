package org.productivity_buddy.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Callback;
import javafx.event.ActionEvent;

import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;
import org.productivity_buddy.ProductivityBuddy;
import org.productivity_buddy.workers.AnalyticsWorker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SpecificCategoryView implements RefreshableView {

    private final ProcessRegistry registry;
    private final AnalyticsWorker analyticsWorker;
    private final ProductivityBuddy app;
    private final String categoryName;

    // live-update polja
    private TableView<ProcessInfo> table;
    private PieChart top10Chart;
    private Label lblTotalTime;

    public SpecificCategoryView(ProcessRegistry registry, AnalyticsWorker analyticsWorker,
                                ProductivityBuddy app, String categoryName) {
        this.registry = registry;
        this.analyticsWorker = analyticsWorker;
        this.app = app;
        this.categoryName = categoryName;
    }

    public Node createView() {
        VBox view = new VBox(20);
        view.setPadding(new Insets(24));

        Button btnBack = new Button("\u2190  Back to Main");
        btnBack.getStyleClass().addAll("btn-secondary", "back-button");
        btnBack.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                app.navigateToMain();
            }
        });

        Label lblTitle = new Label(categoryName + " Category");
        lblTitle.getStyleClass().add("header-title");
        lblTitle.setPadding(new Insets(4, 0, 0, 0));

        HBox content = new HBox(24);

        // Tabela filtrirana po kategoriji
        VBox leftBox = new VBox(12);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        Label tableLabel = new Label("Processes in " + categoryName);
        tableLabel.getStyleClass().add("section-title");

        table = new TableView<>();

        TableColumn<ProcessInfo, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProcessInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<ProcessInfo, String> data) {
                return new SimpleStringProperty(data.getValue().getAliasName());
            }
        });

        TableColumn<ProcessInfo, String> colRamCpu = new TableColumn<>("RAM & CPU");
        colRamCpu.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProcessInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<ProcessInfo, String> data) {
                ProcessInfo info = data.getValue();
                String ram = ProductivityBuddy.formatRam(info.getRamUsageBytes());
                String cpu = String.format("%.1f%%", info.getCpuUsage());
                return new SimpleStringProperty(ram + " | " + cpu);
            }
        });

        TableColumn<ProcessInfo, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProcessInfo, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<ProcessInfo, String> data) {
                return new SimpleStringProperty(ProductivityBuddy.formatTime(data.getValue().getEffectiveTotalTime()));
            }
        });

        table.getColumns().addAll(colName, colRamCpu, colTime);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 1 && table.getSelectionModel().getSelectedItem() != null) {
                    String selectedProcess = table.getSelectionModel().getSelectedItem().getOriginalName();
                    app.navigateToProcessDetail(selectedProcess);
                }
            }
        });

        Label hint = new Label("Click a process for details");
        hint.getStyleClass().add("stat-rank");
        hint.setPadding(new Insets(4, 0, 0, 4));

        leftBox.getChildren().addAll(tableLabel, table, hint);

        // Top 10 Pie Chart za kategoriju
        VBox rightBox = new VBox(12);
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPrefWidth(370);

        Label top10Label = new Label("Top 10 processes by time spent");
        top10Label.getStyleClass().add("section-title");

        top10Chart = new PieChart();
        top10Chart.setLabelsVisible(true);
        top10Chart.setMaxHeight(300);
        top10Chart.setLegendVisible(true);

        lblTotalTime = new Label();
        lblTotalTime.getStyleClass().add("time-label");
        lblTotalTime.setPadding(new Insets(8, 0, 0, 0));

        rightBox.getChildren().addAll(top10Label, top10Chart, lblTotalTime);

        content.getChildren().addAll(leftBox, rightBox);
        VBox.setVgrow(content, Priority.ALWAYS);

        view.getChildren().addAll(btnBack, lblTitle, content);

        // prvi refresh
        refreshUI();

        return view;
    }

    @Override
    public void refreshUI() {
        // filtriraj procese po kategoriji
        ObservableList<ProcessInfo> filtered = FXCollections.observableArrayList();
        long totalTime = 0;
        for (ProcessInfo info : registry.getAll()) {
            if (info.getCategory().equals(categoryName)) {
                filtered.add(info);
                totalTime += info.getEffectiveTotalTime();
            }
        }

        // azuriraj tabelu sa persistentnim sort orderom
        if (table != null) {
            retainSortOrder(table, new Runnable() {
                @Override
                public void run() {
                    table.setItems(filtered);
                    table.refresh();
                }
            });
        }

        // azuriraj total time
        if (lblTotalTime != null) {
            lblTotalTime.setText(categoryName.toLowerCase() + " total time - "
                    + ProductivityBuddy.formatTime(totalTime));
        }

        // azuriraj top 10 pie chart
        if (top10Chart != null) {
            // sortiraj po vremenu, uzmi top 10
            List<ProcessInfo> sorted = new ArrayList<>(filtered);
            sorted.sort(new Comparator<ProcessInfo>() {
                @Override
                public int compare(ProcessInfo a, ProcessInfo b) {
                    return Long.compare(b.getEffectiveTotalTime(), a.getEffectiveTotalTime());
                }
            });
            if (sorted.size() > 10) {
                sorted = sorted.subList(0, 10);
            }

            // proveri da li treba rebuild (size ili imena se razlikuju)
            boolean needsRebuild = (top10Chart.getData().size() != sorted.size());
            if (!needsRebuild) {
                for (int i = 0; i < sorted.size(); i++) {
                    if (!top10Chart.getData().get(i).getName().equals(sorted.get(i).getAliasName())) {
                        needsRebuild = true;
                        break;
                    }
                }
            }

            if (needsRebuild) {
                top10Chart.getData().clear();
                for (int i = 0; i < sorted.size(); i++) {
                    ProcessInfo p = sorted.get(i);
                    top10Chart.getData().add(new PieChart.Data(
                            p.getAliasName(), p.getEffectiveTotalTime()));
                }
            } else {
                for (int i = 0; i < sorted.size(); i++) {
                    top10Chart.getData().get(i).setPieValue(sorted.get(i).getEffectiveTotalTime());
                }
            }
        }
    }
}
