package com.progresspath.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader =
                new FXMLLoader(Main.class.getResource("/fxml/main.fxml"));

        Scene scene = new Scene(loader.load(), 1280, 820);

        stage.setTitle("ProgressPath");
        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setResizable(true);

        // Bind the window size to the current screen so the title bar / borders
        // never get clipped on monitors smaller than the requested Scene size.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double maxW = Math.min(1280, screen.getWidth());
        double maxH = Math.min(820, screen.getHeight());
        stage.setWidth(maxW);
        stage.setHeight(maxH);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
