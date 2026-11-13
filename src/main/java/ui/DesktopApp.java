package ui;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;

public class DesktopApp extends Application {

    // Guardamos la referencia como atributo de clase para que el Garbage Collector NO la borre
    private Bridge bridge;

    @Override
    public void start(Stage primaryStage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        // Instanciamos el puente una sola vez y lo mantenemos vivo
        this.bridge = new Bridge(engine);

        // Inyectar el puente cada vez que la página carga o se recarga con F5
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaBridge", this.bridge);
            }
        });

        // Cargar el HTML local para edición en vivo
        File sourceHtml = new File("src/main/resources/web/index.html");
        if (sourceHtml.exists()) {
            engine.load(sourceHtml.toURI().toString());
        } else {
            engine.load(getClass().getResource("/web/index.html").toExternalForm());
        }

        Scene scene = new Scene(webView, 1280, 800);

        // Atajo F5 para recargar diseño sin reiniciar
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                engine.reload();
            }
        });

        primaryStage.setTitle("TremorLink - Telemedicine Platform");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}