module financiai.financiai {
    requires javafx.controls;
    requires javafx.fxml;
    requires itextpdf;
    requires java.sql;
    requires java.desktop;


    opens financiai.financiai to javafx.fxml;
    exports financiai.financiai;
}