package ui;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.net.URL;

public class DesktopApp extends Application {
// Escucha el evento de carga mediante un ChargeListener en LoadWorker. En cuanto el DOM se redenriza (succeeded) inyecta el objeto java en el entorno global
    @Override
    public void start(Stage primaryStage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        // Enlazar Java con el objeto 'window.javaBridge' en cuanto cargue el HTML
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaBridge", new Bridge(engine));
            }
        });

        // Cargar el archivo index.html desde resources
        URL url = getClass().getResource("/web/index.html");
        if (url != null) {
            engine.load(url.toExternalForm());
        } else {
            System.err.println("No se encontró el archivo /web/index.html");
        }

        Scene scene = new Scene(webView, 950, 650);
        primaryStage.setTitle("Telemedicine Parkinson - Clinical Hub");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}