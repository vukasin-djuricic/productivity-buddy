package org.productivity_buddy.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.productivity_buddy.ProcessCategory;
import org.productivity_buddy.ProcessInfo;
import org.productivity_buddy.ProcessRegistry;
import org.productivity_buddy.ProductivityBuddy;
import org.productivity_buddy.workers.AnalyticsWorker;

import java.util.Map;

public class MainChartView implements RefreshableView {

    private final ProcessRegistry registry;
    private final AnalyticsWorker analyticsWorker;
    private final ProductivityBuddy app;

    private TableView<ProcessInfo> mainTable;
    private PieChart overallChart;
    private VBox categoryStats;

    public MainChartView(ProcessRegistry registry, AnalyticsWorker analyticsWorker, ProductivityBuddy app) {
        this.registry = registry;
        this.analyticsWorker = analyticsWorker;
        this.app = app;
    }

    public Node createView() {
        HBox mainView = new HBox(24);
        mainView.setPadding(new Insets(24));

        // LEVA STRANA: Tabela procesa
        VBox leftPane = new VBox(12);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        Label tableTitle = new Label("Active Processes");
        tableTitle.getStyleClass().add("section-title");

        mainTable = new TableView<>();

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
        mainTable.getColumns().addAll(colName, colCategory);
        mainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(mainTable, Priority.ALWAYS);
        applyUncategorizedLastSortPolicy(mainTable);

        mainTable.setItems(FXCollections.observableArrayList(registry.getAll()));

        // dupli klik vodi na detalje
        mainTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 1 && mainTable.getSelectionModel().getSelectedItem() != null) {
                    String selectedProcess = mainTable.getSelectionModel().getSelectedItem().getOriginalName();
                    app.navigateToProcessDetail(selectedProcess);
                }
            }
        });

        Label hint = new Label("Click on a process for details");
        hint.getStyleClass().add("stat-rank");
        hint.setPadding(new Insets(4, 0, 0, 4));

        leftPane.getChildren().addAll(tableTitle, mainTable, hint);

        // DESNA STRANA: Grafici i kategorije
        VBox rightPane = new VBox(20);
        rightPane.setAlignment(Pos.TOP_CENTER);
        rightPane.setPrefWidth(380);

        overallChart = new PieChart();
        overallChart.setTitle("Time Distribution");
        overallChart.setLabelsVisible(true);
        overallChart.setMaxHeight(300);
        overallChart.setLegendVisible(true);

        Label catTitle = new Label("Categories");
        catTitle.getStyleClass().add("section-title");
        catTitle.setPadding(new Insets(4, 0, 0, 0));

        categoryStats = new VBox(10);

        rightPane.getChildren().addAll(overallChart, catTitle, categoryStats);
        mainView.getChildren().addAll(leftPane, rightPane);

        return mainView;
    }

    public void refreshUI() {
        // TABELA
        if (mainTable != null) {
            retainSortOrder(mainTable, new Runnable() {
                @Override
                public void run() {
                    ObservableList<ProcessInfo> currentItems = mainTable.getItems();
                    java.util.Collection<ProcessInfo> registryItems = registry.getAll();

                    if (currentItems.size() != registryItems.size()) {
                        mainTable.setItems(FXCollections.observableArrayList(registryItems));
                    } else {
                        mainTable.refresh();
                    }
                }
            });
        }

        // PIE CHART
        if (overallChart != null) {
            Map<String, Long> catTime = analyticsWorker.getTimeByCategory();

            if (overallChart.getData().size() != catTime.size()) {
                overallChart.getData().clear();
                for (Map.Entry<String, Long> entry : catTime.entrySet()) {

                    overallChart.getData().add(new PieChart.Data(entry.getKey(), entry.getValue()));
                }
            } else {
                for (PieChart.Data data : overallChart.getData()) {
                    Long newValue = catTime.get(data.getName());
                    if (newValue != null) {
                        data.setPieValue(newValue);
                    }
                }
            }
        }

        // KATEGORIJE
        if (categoryStats != null) {
            Map<String, Long> catTime = analyticsWorker.getTimeByCategory();

            if (categoryStats.getChildren().size() != catTime.size()) {
                categoryStats.getChildren().clear();
                for (Map.Entry<String, Long> entry : catTime.entrySet()) {
                    String formattedTime = ProductivityBuddy.formatTime(entry.getValue());
                    categoryStats.getChildren().add(createCategoryRow(entry.getKey(), formattedTime));
                }
            } else {
                int i = 0;
                for (Map.Entry<String, Long> entry : catTime.entrySet()) {
                    HBox row = (HBox) categoryStats.getChildren().get(i);
                    Label lblTime = (Label) row.getChildren().get(2);
                    lblTime.setText(ProductivityBuddy.formatTime(entry.getValue()));
                    i++;
                }
            }
        }
    }

    private HBox createCategoryRow(String category, String time) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("category-row");

        Region dot = new Region();
        dot.setMinSize(10, 10);
        dot.setMaxSize(10, 10);
        String dotColor = ProcessCategory.fromDisplayName(category).getColor();
        dot.setStyle("-fx-background-color: " + dotColor + "; -fx-background-radius: 5;");

        Label lblName = new Label(category);
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        lblName.setPrefWidth(120);

        Label lblTime = new Label(time);
        lblTime.getStyleClass().add("time-label");
        lblTime.setPrefWidth(110);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnDetails = new Button("Details  \u203a");
        btnDetails.getStyleClass().add("btn-small");
        btnDetails.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                app.navigateToCategory(category);
            }
        });

        row.getChildren().addAll(dot, lblName, lblTime, spacer, btnDetails);
        return row;
    }

}
