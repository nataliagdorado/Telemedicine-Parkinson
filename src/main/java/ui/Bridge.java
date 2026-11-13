package ui;

import common.Message;
import common.MessageCodec;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import common.SymptomReport;
import server.data.FileRepository;
import server.data.Patient;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import common.SessionInfo;
import server.data.ClinicalNote;
import java.time.LocalDateTime;
import java.util.UUID;
import patient.MonitoringSessionController;
import patient.ServerConnection;
import patient.SignalSource;
import patient.SimulatedSignalSource;
import java.util.List;
import java.util.Map;

public class Bridge {
    private final WebEngine webEngine;
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String currentToken = null;
    private String currentUserId = null;
    private String currentRole = null;


    private MonitoringSessionController activeController;
    private ServerConnection monitoringConnection;

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
                FileRepository repo = new FileRepository(Paths.get("data"));
                Optional<Patient> opt = repo.findPatient(this.currentUserId);
                List<SymptomReport> reports = repo.findSymptomReports(this.currentUserId);
                List<SessionInfo> sessions = repo.findSessions(this.currentUserId);

                if (opt.isPresent()) {
                    Patient p = opt.get();
                    String profileJson = MessageCodec.toJson(p);
                    String reportsJson = MessageCodec.toJson(reports);
                    String sessionsJson = MessageCodec.toJson(sessions);

                    Platform.runLater(() -> {
                        webEngine.executeScript(String.format(
                                "renderPatientDashboard(%s, %s, %s);", profileJson, reportsJson, sessionsJson
                        ));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void updatePatientProfile(String newName, String newMedSchedule) {
        new Thread(() -> {
            try {
                FileRepository repo = new FileRepository(Paths.get("data"));
                List<Patient> all = repo.findAllPatients();
                for (Patient p : all) {
                    if (p.getPatientId().equals(this.currentUserId)) {
                        p.setName(newName);
                        p.setMedicationSchedule(newMedSchedule);
                        break;
                    }
                }
                // Reescribir el fichero actualizado
                java.nio.file.Path file = Paths.get("data", "patients.jsonl");
                StringBuilder sb = new StringBuilder();
                for (Patient p : all) {
                    sb.append(MessageCodec.toJson(p)).append(System.lineSeparator());
                }
                java.nio.file.Files.writeString(file, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);

                loadPatientDashboardData();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // PARA EL DOCTOR SI ES QUE LO PONEMOS AL FINAL
    public void loadDoctorCohort() {
        new Thread(() -> {
            try {
                FileRepository repo = new FileRepository(Paths.get("data"));
                List<Patient> patients = repo.findAllPatients();
                String json = MessageCodec.toJson(patients);

                Platform.runLater(() -> {
                    webEngine.executeScript(String.format("renderDoctorCohort(%s);", json));
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void loadPatientRecordForDoctor(String patientId) {
        new Thread(() -> {
            try {
                FileRepository repo = new FileRepository(Paths.get("data"));
                Optional<Patient> opt = repo.findPatient(patientId);
                List<SymptomReport> reports = repo.findSymptomReports(patientId);
                List<SessionInfo> sessions = repo.findSessions(patientId);
                List<ClinicalNote> notes = repo.findClinicalNotes(patientId);

                if (opt.isPresent()) {
                    String pJson = MessageCodec.toJson(opt.get());
                    String rJson = MessageCodec.toJson(reports);
                    String sJson = MessageCodec.toJson(sessions);
                    String nJson = MessageCodec.toJson(notes);

                    Platform.runLater(() -> {
                        webEngine.executeScript(String.format(
                                "renderDoctorPatientDetail(%s, %s, %s, %s);",
                                pJson, rJson, sJson, nJson
                        ));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void saveDoctorObservation(String patientId, String recordRef, String observationText) {
        new Thread(() -> {
            try {
                FileRepository repo = new FileRepository(Paths.get("data"));
                String noteId = "NOTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String formattedText = (recordRef != null && !recordRef.isBlank())
                        ? "[" + recordRef + "] " + observationText
                        : observationText;

                //  constructor de 4 parámetros que  inicializa los campos y el timestamp
                ClinicalNote note = new ClinicalNote(
                        noteId,
                        patientId,
                        this.currentUserId != null ? this.currentUserId : "doctor",
                        formattedText
                );
                repo.saveClinicalNote(note);

                // Recargar el expediente del paciente para mostrar la nueva nota al instante
                loadPatientRecordForDoctor(patientId);
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
                monitoringConnection.connect(); // Si pide host y puerto, usa connect(SERVER_HOST, SERVER_PORT)

                Message hello = new Message("HELLO", "req-mon-hello");
                monitoringConnection.send(hello);

                int[] channels = new int[]{0, 1, 2, 3};
                Message startMsg = new Message("START_MONITORING", "req-start-" + System.currentTimeMillis());
                startMsg.setPatientId(this.currentUserId);
                startMsg.addData("sessionToken", this.currentToken);
                startMsg.addData("samplingRateHz", samplingRateHz);
                startMsg.addData("channels", List.of(0, 1, 2, 3));

                Message startResp = monitoringConnection.send(startMsg);
                if ("ERROR".equalsIgnoreCase(startResp.getType())) {
                    String err = startResp.getData() != null ? String.valueOf(startResp.getData().get("error")) : "Server rejected session";
                    Platform.runLater(() -> webEngine.executeScript(String.format("onCaptureError('%s');", err)));
                    return;
                }

                String sessionId = String.valueOf(startResp.getData().get("sessionId"));

                // Guardar el comentario del paciente asociado a esta sesión si escribió algo
                if (trialNotes != null && !trialNotes.isBlank()) {
                    FileRepository repo = new FileRepository(Paths.get("data"));
                    String noteId = "CTX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    ClinicalNote contextNote = new ClinicalNote(
                            noteId,
                            this.currentUserId,
                            "Patient (" + this.currentUserId + ")",
                            "[Session #" + sessionId + "] " + trialNotes.trim()
                    );
                    repo.saveClinicalNote(contextNote);
                }

                SignalSource source = new SimulatedSignalSource();
                source.connect("00:21:08:35:15:00", samplingRateHz);

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
}