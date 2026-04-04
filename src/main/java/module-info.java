module org.productivity_buddy {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires com.github.oshi;
    requires org.slf4j;

    opens org.productivity_buddy to javafx.fxml;
    opens org.productivity_buddy.view to javafx.fxml;
    opens org.productivity_buddy.model to javafx.fxml;
    exports org.productivity_buddy;
    exports org.productivity_buddy.config;
    exports org.productivity_buddy.model;
    exports org.productivity_buddy.service;
    exports org.productivity_buddy.view;
    exports org.productivity_buddy.tasks;
    exports org.productivity_buddy.workers;
}
