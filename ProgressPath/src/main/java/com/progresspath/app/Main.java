package com.progresspath.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
