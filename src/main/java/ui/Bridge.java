package ui;

import common.*;
import doctor.DoctorService;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import patient.*;
import java.util.List;


public class Bridge {
    private final WebEngine webEngine;
    private static final String SERVER_HOST = Config.get("server.ip", "127.0.0.1");
    private static final int SERVER_PORT = Config.getInt("server.port", 9000);

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String currentToken = null;
    private String currentUserId = null;
    private String currentRole = null;


    private MonitoringSessionController activeController;
    private ServerConnection monitoringConnection;

    private final DoctorService doctorService = new DoctorService();

    public Bridge(WebEngine webEngine) {
        this.webEngine = webEngine;
    }

    private synchronized void ensureConnected() throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Message hello = new Message("HELLO", "req-hello");
            out.println(MessageCodec.encode(hello));
            in.readLine();
        }
    }

    // Envía un mensaje por el socket del Bridge y espera su respuesta.
    // synchronized: el socket lo comparten varios hilos y cada pregunta debe recibir SU respuesta
    private synchronized Message request(Message msg) throws Exception {
        ensureConnected();
        out.println(MessageCodec.encode(msg));
        String raw = in.readLine();
        if (raw == null) {
            socket.close();   // el servidor cerró: la próxima vez ensureConnected abrirá una conexión nueva
            throw new java.io.IOException("Server closed the connection");
        }
        return MessageCodec.decode(raw);
    }

    // Gson lee los números del JSON como Double: los pasa a Integer para TremorAnalyzer
    private static List<Integer> toIntList(Object jsonList) {
        List<Integer> result = new java.util.ArrayList<>();
        for (Object value : (List<?>) jsonList) {
            result.add(((Number) value).intValue());
        }
        return result;
    }

    /* Nota de Natalia: En ciertos métodos de esta clase pone 0 Usages, pero están inyectados desde el Webview con
    window.JavaBridge, se llaman desde JavaScript y no desde Java
     */
    public void login(String userId, String password, String role) {
        new Thread(() -> {
            try {
                ensureConnected();

                Message loginMsg = new Message("LOGIN", "req-login");
                loginMsg.setPatientId(userId);
                loginMsg.addData("id", userId);
                loginMsg.addData("password", password);
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
                e.printStackTrace();
                Platform.runLater(() -> webEngine.executeScript("onLoginError('No se pudo conectar al servidor (¿Está arrancado?)');"));
            }
        }).start();
    }

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
                e.printStackTrace();
                Platform.runLater(() -> webEngine.executeScript("onReportResult(false);"));
            }
        }).start();
    }


    // para la segunda pantalla y el tema de editar tu perfil
    public void loadPatientDashboardData() {
        new Thread(() -> {
            try {
                Message req = new Message("GET_MY_RECORD", "req-record-" + System.currentTimeMillis());
                req.addData("sessionToken", this.currentToken);
                Message resp = request(req);

                if ("ERROR".equalsIgnoreCase(resp.getType())) {
                    System.out.println("[Bridge] GET_MY_RECORD rejected: " + resp.getData());
                    return;
                }

                String profileJson = MessageCodec.toJson(resp.getData().get("patient"));
                String reportsJson = MessageCodec.toJson(resp.getData().get("symptomReports"));
                String sessionsJson = MessageCodec.toJson(resp.getData().get("sessions"));

                Platform.runLater(() -> {
                    webEngine.executeScript(String.format(
                            "renderPatientDashboard(%s, %s, %s);", profileJson, reportsJson, sessionsJson
                    ));
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void updatePatientProfile(String newName, String newMedSchedule) {
        new Thread(() -> {
            try {
                Message req = new Message("UPDATE_PROFILE", "req-profile-" + System.currentTimeMillis());
                req.addData("sessionToken", this.currentToken);
                if (newName != null && !newName.isBlank()) {
                    req.addData("name", newName.trim());
                }
                if (newMedSchedule != null && !newMedSchedule.isBlank()) {
                    req.addData("medicationSchedule", newMedSchedule.trim());
                }
                Message resp = request(req);

                if ("ERROR".equalsIgnoreCase(resp.getType())) {
                    System.out.println("[Bridge] UPDATE_PROFILE rejected: " + resp.getData());
                }
                loadPatientDashboardData();   // refresca la pantalla con lo que hay en el servidor
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    public void startSignalCapture(int samplingRateHz, int durationSeconds, String trialNotes) {
        if (activeController != null && activeController.isRunning()) {
            return;
        }

        new Thread(() -> {
            try {
                monitoringConnection = new ServerConnection();
                monitoringConnection.connect();

                Message hello = new Message("HELLO", "req-mon-hello");
                monitoringConnection.send(hello);

                int[] channels = new int[]{0, 1, 2, 3};
                Message startMsg = new Message("START_MONITORING", "req-start-" + System.currentTimeMillis());
                startMsg.setPatientId(this.currentUserId);
                startMsg.addData("sessionToken", this.currentToken);
                startMsg.addData("samplingRateHz", samplingRateHz);
                startMsg.addData("channels", List.of(0, 1, 2, 3));
                if (trialNotes != null && !trialNotes.isBlank()) {
                    startMsg.addData("notes", trialNotes.trim());   // la nota la guarda el servidor
                }

                Message startResp = monitoringConnection.send(startMsg);
                if ("ERROR".equalsIgnoreCase(startResp.getType())) {
                    String err = startResp.getData() != null ? String.valueOf(startResp.getData().get("error")) : "Server rejected session";
                    Platform.runLater(() -> webEngine.executeScript(String.format("onCaptureError('%s');", err)));
                    return;
                }

                String sessionId = String.valueOf(startResp.getData().get("sessionId"));

                String sourceType = Config.get("signal.source", "simulated");
                String mac = Config.get("bitalino.mac", "00:21:08:35:15:00");
                SignalSource source = "bitalino".equalsIgnoreCase(sourceType)
                        ? new BitalinoSignalSource()
                        : new SimulatedSignalSource();

                if (!source.connect(mac, samplingRateHz)) {
                    Platform.runLater(() -> webEngine.executeScript(
                            String.format("onCaptureError('Could not connect to BITalino (%s)');", mac)
                    ));
                    monitoringConnection.close();
                    return;
                }

                activeController = new MonitoringSessionController(
                        source,
                        monitoringConnection,
                        this.currentToken,
                        this.currentUserId,
                        sessionId,
                        durationSeconds,
                        samplingRateHz,
                        channels
                );

                Thread recordingThread = new Thread(activeController, "BITalino-Recording-Thread");
                recordingThread.start();

                Platform.runLater(() -> {
                    webEngine.executeScript(String.format("onCaptureStarted('%s', %d, %d);", sessionId, samplingRateHz, durationSeconds));
                });

                recordingThread.join();

                source.disconnect();
                monitoringConnection.close();

                String finalStatus = activeController.getSessionInfo().getStatus().name();
                Platform.runLater(() -> {
                    webEngine.executeScript(String.format("onCaptureFinished('%s', '%s');", sessionId, finalStatus));
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> webEngine.executeScript("onCaptureError('Connection failed during monitoring');"));
            }
        }).start();
    }

    public void stopSignalCapture() {
        if (activeController != null && activeController.isRunning()) {
            activeController.cancelRecording();
        }
    }


    // para enganchar con doctor
    public void loadDoctorCohort() {
        new Thread(() -> {
            try {
                Message req = new Message("LIST_PATIENTS", "req-cohort-" + System.currentTimeMillis());
                req.addData("sessionToken", this.currentToken);
                Message resp = request(req);

                if ("ERROR".equalsIgnoreCase(resp.getType())) {
                    System.out.println("[Bridge] LIST_PATIENTS rejected: " + resp.getData());
                    return;
                }

                String json = MessageCodec.toJson(resp.getData().get("patients"));
                Platform.runLater(() -> webEngine.executeScript(String.format("renderDoctorCohort(%s);", json)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void loadPatientRecordForDoctor(String patientId) {
        new Thread(() -> {
            try {
                Message req = new Message("GET_PATIENT_RECORD", "req-patient-record-" + System.currentTimeMillis());
                req.setPatientId(patientId);                       // el paciente que el médico ha elegido
                req.addData("sessionToken", this.currentToken);
                Message resp = request(req);

                if ("ERROR".equalsIgnoreCase(resp.getType())) {
                    System.out.println("[Bridge] GET_PATIENT_RECORD rejected: " + resp.getData());
                    return;
                }

                String profileJson = MessageCodec.toJson(resp.getData().get("patient"));
                String reportsJson = MessageCodec.toJson(resp.getData().get("symptomReports"));
                String sessionsJson = MessageCodec.toJson(resp.getData().get("sessions"));
                String notesJson = MessageCodec.toJson(resp.getData().get("clinicalNotes"));

                Platform.runLater(() -> webEngine.executeScript(String.format(
                        "renderDoctorPatientDetail(%s, %s, %s, %s);",
                        profileJson, reportsJson, sessionsJson, notesJson
                )));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void saveDoctorObservation(String patientId, String recordRef, String observationText) {
        new Thread(() -> {
            try {
                Message req = new Message("ADD_CLINICAL_NOTE", "req-note-" + System.currentTimeMillis());
                req.setPatientId(patientId);                        // paciente al que va la nota
                req.addData("sessionToken", this.currentToken);     // el servidor saca de aquí el autor
                req.addData("observation", observationText);
                if (recordRef != null && !recordRef.isBlank()) {
                    req.addData("recordRef", recordRef);
                }
                Message resp = request(req);

                if ("ERROR".equalsIgnoreCase(resp.getType())) {
                    System.out.println("[Bridge] ADD_CLINICAL_NOTE rejected: " + resp.getData());
                }
                loadPatientRecordForDoctor(patientId);   // refresca la ficha con lo que hay en el servidor
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void loadSignalWaveformForDoctor(String sessionId) {
        new Thread(() -> {
            try {
                Message req = new Message("GET_SESSION_SIGNAL", "req-signal-" + System.currentTimeMillis());
                req.addData("sessionToken", this.currentToken);
                req.addData("sessionId", sessionId);
                Message resp = request(req);

                if ("ERROR".equalsIgnoreCase(resp.getType())) {
                    String err = String.valueOf(resp.getData().get("error"));
                    String safeErr = err.replace("\\", "\\\\").replace("'", "\\'");   // para no romper el string de JS
                    Platform.runLater(() -> webEngine.executeScript(
                            String.format("onSignalWaveformError('%s');", safeErr)));
                    return;
                }

                List<Integer> emg = toIntList(resp.getData().get("emg"));
                if (emg.isEmpty()) {
                    Platform.runLater(() -> webEngine.executeScript(
                            String.format("onSignalWaveformError('Session %s has no samples');", sessionId)));
                    return;
                }
                int samplingRateHz = ((Number) resp.getData().get("samplingRateHz")).intValue();

                DoctorService.WaveformPayload wf = doctorService.buildWaveform(sessionId, samplingRateHz, emg,
                        toIntList(resp.getData().get("accX")),
                        toIntList(resp.getData().get("accY")),
                        toIntList(resp.getData().get("accZ")));

                Platform.runLater(() -> webEngine.executeScript(String.format(
                        "renderDoctorWaveformModal('%s', %s, %s, %d, %s);",
                        sessionId, wf.emgJson(), wf.accJson(), wf.totalSamples(), wf.metricsJson()
                )));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> webEngine.executeScript("onSignalWaveformError('Failed to load the session signal');"));
            }
        }).start();
    }
}