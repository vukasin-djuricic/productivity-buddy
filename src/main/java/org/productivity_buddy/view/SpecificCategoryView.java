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

import java.util.List;
import java.util.Map;

public class SpecificCategoryView {

    private final ProcessRegistry registry;
    private final AnalyticsWorker analyticsWorker;
    private final ProductivityBuddy app;

    public SpecificCategoryView(ProcessRegistry registry, AnalyticsWorker analyticsWorker, ProductivityBuddy app) {
        this.registry = registry;
        this.analyticsWorker = analyticsWorker;
        this.app = app;
    }

    public Node createView(String categoryName) {
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

        TableView<ProcessInfo> table = new TableView<>();

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
                String ram = formatRam(info.getRamUsageBytes());
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

        // filtriraj procese po kategoriji
        ObservableList<ProcessInfo> filtered = FXCollections.observableArrayList();
        for (ProcessInfo info : registry.getAll()) {
            if (info.getCategory().equals(categoryName)) {
                filtered.add(info);
            }
        }
        table.setItems(filtered);

        table.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 1 && table.getSelectionModel().getSelectedItem() != null) {
                    String selectedProcess = table.getSelectionModel().getSelectedItem().getOriginalName();
                    app.navigateToProcessDetail(selectedProcess);
                }
            }
        });

        Label hint = new Label("Double-click a process for details");
        hint.getStyleClass().add("stat-rank");
        hint.setPadding(new Insets(4, 0, 0, 4));

        leftBox.getChildren().addAll(tableLabel, table, hint);

        // Top 10 Pie Chart za kategoriju
        VBox rightBox = new VBox(12);
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPrefWidth(370);

        Label top10Label = new Label("Top 10 by time spent");
        top10Label.getStyleClass().add("section-title");

        PieChart top10Chart = new PieChart();
        List<Map.Entry<String, Long>> top10 = analyticsWorker.getTop10Processes();
        for (int i = 0; i < top10.size(); i++) {
            Map.Entry<String, Long> entry = top10.get(i);
            top10Chart.getData().add(new PieChart.Data(entry.getKey(), entry.getValue()));
        }
        top10Chart.setLabelsVisible(true);
        top10Chart.setMaxHeight(300);
        top10Chart.setLegendVisible(true);

        // Ukupno vreme za kategoriju
        long categoryTotalTime = 0;
        for (ProcessInfo info : filtered) {
            categoryTotalTime += info.getEffectiveTotalTime();
        }
        Label lblTotalTime = new Label(categoryName.toLowerCase() + " total time - "
                + ProductivityBuddy.formatTime(categoryTotalTime));
        lblTotalTime.getStyleClass().add("time-label");
        lblTotalTime.setPadding(new Insets(8, 0, 0, 0));

        rightBox.getChildren().addAll(top10Label, top10Chart, lblTotalTime);

        content.getChildren().addAll(leftBox, rightBox);
        VBox.setVgrow(content, Priority.ALWAYS);

        view.getChildren().addAll(btnBack, lblTitle, content);
        return view;
    }

    private String formatRam(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
