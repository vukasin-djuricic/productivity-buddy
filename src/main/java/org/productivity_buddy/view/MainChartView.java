package org.productivity_buddy.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.productivity_buddy.model.ProcessCategory;
import org.productivity_buddy.model.ProcessRegistry;
import org.productivity_buddy.ProductivityBuddy;
import org.productivity_buddy.workers.AnalyticsWorker;

import java.util.Map;

public class MainChartView implements RefreshablePanel {

    private final ProcessRegistry registry;
    private final AnalyticsWorker analyticsWorker;
    private final ProductivityBuddy app;

    private PieChart overallChart;
    private VBox categoryStats;

    public MainChartView(ProcessRegistry registry, AnalyticsWorker analyticsWorker, ProductivityBuddy app) {
        this.registry = registry;
        this.analyticsWorker = analyticsWorker;
        this.app = app;
    }

    public Node createRightPanel() {
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
        refreshUI();
        return rightPane;
    }

    public void refreshUI() {
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
