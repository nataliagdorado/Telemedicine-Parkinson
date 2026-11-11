package ui;

import common.Message;
import common.MessageCodec;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Bridge {
    private final WebEngine webEngine;
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9000;

    // Conexión persistente mantenida durante toda la sesión
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // Estado del usuario autenticado
    private String currentToken = null;
    private String currentUserId = null;
    private String currentRole = null;

    public Bridge(WebEngine webEngine) {
        this.webEngine = webEngine;
    }

    private synchronized void ensureConnected() throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Handshake inicial obligatorio
            Message hello = new Message("HELLO", "req-hello");
            out.println(MessageCodec.encode(hello));
            in.readLine(); // Consumir respuesta CONNECTED
        }
    }

    public void login(String userId, String role) {
        new Thread(() -> {
            try {
                ensureConnected();

                Message loginMsg = new Message("LOGIN", "req-login");
                loginMsg.setPatientId(userId);
                loginMsg.addData("id", userId);
                loginMsg.addData("role", role);
                out.println(MessageCodec.encode(loginMsg));

                String rawResp = in.readLine();
                Message resp = MessageCodec.decode(rawResp);

                boolean success = "SUCCESS".equalsIgnoreCase(resp.getStatus())
                        || "LOGIN_RESPONSE".equalsIgnoreCase(resp.getType());

                if (success && resp.getData() != null) {
                    this.currentToken = (String) resp.getData().get("sessionToken");
                    this.currentUserId = userId;
                    this.currentRole = (String) resp.getData().get("role");

                    Platform.runLater(() -> {
                        webEngine.executeScript(String.format(
                                "onLoginSuccess('%s', '%s', '%s');",
                                currentUserId, currentRole, currentToken
                        ));
                    });
                } else {
                    String errorMsg = "Credenciales no válidas";
                    if (resp.getData() != null && resp.getData().get("error") != null) {
                        errorMsg = (String) resp.getData().get("error");
                    }
                    final String err = errorMsg;
                    Platform.runLater(() -> webEngine.executeScript(String.format("onLoginError('%s');", err)));
                }

            } catch (Exception e) {
                Platform.runLater(() -> webEngine.executeScript("onLoginError('No se pudo conectar al servidor');"));
            }
        }).start();
    }

    /**
     * Envío del reporte de síntomas usando el token de sesión autenticado
     */
    public void sendSymptomReport(int tremor, int rigidity, boolean medication, boolean freezing) {
        new Thread(() -> {
            try {
                ensureConnected();

                Message reportMsg = new Message("SYMPTOM_REPORT", "req-report");
                reportMsg.setPatientId(this.currentUserId);
                reportMsg.addData("sessionToken", this.currentToken);
                reportMsg.addData("tremorLevel", tremor);
                reportMsg.addData("rigidityLevel", rigidity);
                reportMsg.addData("medicationTaken", medication);
                reportMsg.addData("freezingEpisodes", freezing);

                out.println(MessageCodec.encode(reportMsg));

                String rawResp = in.readLine();
                Message resp = MessageCodec.decode(rawResp);

                boolean saved = "RECORDED".equalsIgnoreCase(resp.getStatus())
                        || (resp.getData() != null && "SAVED".equals(resp.getData().get("status")));

                Platform.runLater(() -> {
                    webEngine.executeScript(String.format("onReportResult(%b);", saved));
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    webEngine.executeScript("onReportResult(false);");
                });
            }
        }).start();
    }
}