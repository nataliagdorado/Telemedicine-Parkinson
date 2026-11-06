package server;

import common.*;
import server.data.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * Enrutador de comandos del servidor.
 * Recibe los mensajes entrantes por TCP y despacha la logica
 * segun la accion solicitada (LOGIN, SYMPTOM_REPORT, etc.).
 */
public class CommandRouter {
    private static final Logger LOGGER = Logger.getLogger(CommandRouter.class.getName());

    private static final Set<Integer> VALID_RATES = Set.of(1, 10, 100, 1000);

    // A VER, para q el router use este gestor
    private final SessionManager sessionManager;
    private final Repository repository;
    private final SignalStore signalStore;

    // Unico constructor: el router no funciona sin sesiones ni persistencia, asi que se exige todo, si siguiesemos con el constructor vacío, alguien podría llamar al router sin tener los requisitos listos
    public CommandRouter(SessionManager sessionManager, Repository repository, SignalStore signalStore) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");  // Por lo que explico arriba también pongo el requireNonNull para que salten errores si la conexión de hace mal y no se pierdan datos
        this.repository = Objects.requireNonNull(repository, "repository");
        this.signalStore = Objects.requireNonNull(signalStore, "signalStore");
    }
    public Message route(Message request) {
        if (request == null) {
            return buildErrorResponse("UNKNOWN", "Request cannot be null");
        }

        String action = request.getType();
        if (action == null || action.trim().isEmpty()) {
            return buildErrorResponse(request.getRequestId(), "Missing action type");
        }

        LOGGER.info(() -> "Processing action [" + action + "] from client/patient: " + request.getPatientId());

        switch (action.toUpperCase()) {
            case "HELLO":
                return handleHello(request);

            case "LOGIN":
                return handleLogin(request);

            case "SYMPTOM_REPORT":
                return handleSymptomReport(request);

            case "START_MONITORING":
                return handleStartMonitoring(request);

            case "SAMPLE_BLOCK":
                return handleSampleBlock(request);

            case "STOP_MONITORING":
                return handleStopMonitoring(request);

            case "PING":
                return handlePing(request);
            case "LOGOUT":
                String token = request.getData() != null ? (String) request.getData().get("sessionToken") : null;
                sessionManager.invalidateSession(token);
                Message logoutResp = new Message("ACK", request.getRequestId());
                logoutResp.setStatus("LOGGED_OUT");
                return logoutResp;

            default:
                LOGGER.warning(() -> "Unknown command requested: " + action);
                return buildErrorResponse(request.getRequestId(), "Command not supported: " + action);
        }
    }

    private Message handleHello(Message request) {
        Message response = new Message("ACK", request.getRequestId());
        response.setStatus("CONNECTED");
        response.addData("status", "CONNECTED");
        response.addData("serverTime", System.currentTimeMillis());
        return response;
    }

    private Message handleLogin(Message request) {
        Map<String, Object> data = request.getData();
        String role = data != null && data.get("role") != null ? String.valueOf(data.get("role")) : "PATIENT";
        String id = request.getPatientId() != null ? request.getPatientId() : (data != null && data.get("id") != null ? String.valueOf(data.get("id")) : "UNKNOWN");

        if ("UNKNOWN".equals(id) || id.trim().isEmpty()) {
            return buildErrorResponse(request.getRequestId(), "Authentication failed: Missing ID");
        }

        // Creamos la sesion de verdad en el SessionManager
        String sessionToken = sessionManager.createSession(id, role);

        Message response = new Message("LOGIN_RESPONSE", request.getRequestId());
        response.setPatientId(id);
        response.setStatus("SUCCESS");
        response.addData("authenticated", true);
        response.addData("role", role);
        response.addData("sessionToken", sessionToken);
        return response;
    }

    private Message handleSymptomReport(Message request) {
        // Compruebo si el mensaje trae datos
        if (request.getData() == null || request.getData().isEmpty()){
            return buildErrorResponse(request.getRequestId(), "Symptom report without data");
        }

        // Convertir el mapa de data n un SymptomReport
        SymptomReport report;
        try {
            report = MessageCodec.fromJson(MessageCodec.toJson(request.getData()), SymptomReport.class);
        } catch (ProtocolException e) {
            return buildErrorResponse(request.getRequestId(), "Malformed symptom report: "+ e.getMessage());
        }

        // Datos del servidor
        report.setPatientId(request.getPatientId());
        report.setReportId(IdGenerator.newReportId());  //id único
        if (report.getTimeStamp() == null) {
            report.setTimeStamp(LocalDateTime.now());    // si el cliente no manda fecha, se usa la de recepcion
        }

        // Una validación más en el servidor
        List<String> errors = SymptomValidator.validate(report);
        if (!errors.isEmpty()) {
            return buildErrorResponse(request.getRequestId(), "Invalid symptom report: " + String.join("; ", errors));
        }

        //Guardar
        try{
            repository.saveSymptomReport(report);
        }catch (RepositoryException e){
            LOGGER.warning("Could not save the report of "+ report.getPatientId()+ ": " + e.getMessage());
            return buildErrorResponse(request.getRequestId(), "Could not save report: " + e.getMessage());
        }

        //Si tot ha ido bien ahora se pone en saved
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(report.getPatientId());
        response.setStatus("RECORDED");
        response.addData("status", "SAVED");
        response.addData("reportId", report.getReportId());
        response.addData("message", "Symptom report registered for clinical evaluation");
        return response;

    }

    private Message handleStartMonitoring(Message request) {
        String patientId = request.getPatientId();
        Map<String, Object> data = request.getData();

        Patient patient;
        try{
            Optional<Patient> found = repository.findPatient(patientId);
            if(found.isEmpty()){
                return buildErrorResponse(request.getRequestId(), "Unknown patient: " + patientId);
            }
            patient = found.get();
        }catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), "Could not read patient: " + e.getMessage());
        }

        //Leer y validar los parámetros de la grabación
        Integer samplingRate = readInt(data, "samplingRateHz");
        int[] channels = readIntArray(data, "channels");
        if (samplingRate == null || !VALID_RATES.contains(samplingRate)) {
            return buildErrorResponse(request.getRequestId(), "Invalid samplingRateHz (allowed: 1, 10, 100, 1000)");
        }
        if (channels == null || channels.length == 0) {
            return buildErrorResponse(request.getRequestId(), "Missing channels");
        }

        //Crear la sesion: el id lo genera el servidor
        SessionInfo info = new SessionInfo(IdGenerator.newSessionId(),patientId,LocalDateTime.now(),samplingRate,channels);

        // 4) Abrir el CSV y registrar la sesion
        try {
            String file = signalStore.openRecording(info, patient);
            info.setSignalFile(file);
            try {
                repository.saveSession(info);
            } catch (RepositoryException e) {
                signalStore.closeRecording(info.getSessionId());
                throw e;
            }
        } catch (RepositoryException e) {
            LOGGER.warning("Could not start session for " + patientId + ": " + e.getMessage());
            return buildErrorResponse(request.getRequestId(), "Could not start session: " + e.getMessage());
        }

        LOGGER.info("Session " + info.getSessionId() + " started for " + patientId);

        // 5) Responder con el sessionId que el paciente usara en cada SAMPLE_BLOCK
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(patientId);
        response.setStatus("MONITORING_ACTIVE");
        response.addData("status", "STARTED");
        response.addData("sessionId", info.getSessionId());
        return response;
    }

    private Message handleSampleBlock(Message request){
        Map<String, Object> data = request.getData();

        String sessionId = null;
        if (data != null && data.get("sessionId") instanceof String) {
            sessionId = (String) data.get("sessionId");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return buildErrorResponse(request.getRequestId(), "Sample block without sessionId");
        }
        List<Integer> emg = readIntList(data, "emg");
        List<Integer> accX = readIntList(data, "accX");
        List<Integer> accY = readIntList(data, "accY");
        List<Integer> accZ = readIntList(data, "accZ");
        if (emg == null || accX == null || accY == null || accZ == null) {
            return buildErrorResponse(request.getRequestId(), "Sample block with missing or invalid channels");
        }

        // 3) Escribir en el CSV de esa sesion
        SampleBlock block = new SampleBlock(sessionId, emg, accX, accY, accZ);
        try {
            signalStore.appendBlock(block);   // falla si la sesion no esta abierta o los canales no miden lo mismo
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), "Could not store samples: " + e.getMessage());
        }

        // 4) Confirmar: para el paciente, un ACK significa "bloque guardado, manda el siguiente"
        Message response = new Message("ACK", request.getRequestId());
        response.setStatus("STORED");
        response.addData("sessionId", sessionId);
        response.addData("samples", emg.size());
        return response;
    }

    private Message handleStopMonitoring(Message request) {
        Map<String, Object> data = request.getData();

        String sessionId = readString(data, "sessionId");
        String reason = readString(data, "reason");
        if (sessionId == null || sessionId.isBlank()) {
            return buildErrorResponse(request.getRequestId(), "Stop without sessionId");
        }

        SessionInfo info;
        try {
            Optional<SessionInfo> found = repository.findSession(sessionId);
            if (found.isEmpty()) {
                return buildErrorResponse(request.getRequestId(), "Unknown session: " + sessionId);
            }
            info = found.get();
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), "Could not read session: " + e.getMessage());
        }

        if (info.getStatus() == SessionStatus.RECORDING) {
            try {
                // Cerrar el CSV ANTES de marcar la sesion como terminada
                signalStore.closeRecording(sessionId);

                // Estado final según el motivo
                LocalDateTime end = LocalDateTime.now();
                if ("USER".equals(reason) || "TIMEOUT".equals(reason)) {
                    info.complete(end);          // final valido -> COMPLETED
                } else {
                    info.markIncomplete(end);    // DEVICEERROR, o motivo desconocido/ausente -> INCOMPLETE
                }

                // Guardar endTime y estado en sessions.jsonl
                repository.updateSession(info);
            } catch (RepositoryException e) {
                LOGGER.warning("Could not stop session " + sessionId + ": " + e.getMessage());
                return buildErrorResponse(request.getRequestId(), "Could not stop session: " + e.getMessage());
            }
            LOGGER.info("Session " + sessionId + " finished: " + info.getStatus() + " (reason " + reason + ")");
        }

        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(info.getPatientId());
        response.setStatus("MONITORING_STOPPED");
        response.addData("status", "STOPPED");
        response.addData("sessionStatus", info.getStatus().name());
        response.addData("durationSeconds", info.getDurationSeconds());
        return response;
    }

    private Message handlePing(Message request) {
        Message response = new Message("PONG", request.getRequestId());
        response.setStatus("OK");
        return response;
    }

    private Message buildErrorResponse(String requestId, String detail) {
        Message error = new Message("ERROR", requestId);
        error.setStatus("FAILED");
        error.addData("error", detail);
        return error;
    }

    private static Integer readInt(Map<String, Object> data, String key){
        if(data == null){
            return null;
        }
        Object value = data.get(key);
        if (value instanceof Number){
            return((Number)value).intValue();
        }
        return null;
    }

    private static int[] readIntArray(Map<String, Object> data, String key){
        if (data == null || !(data.get(key) instanceof List)){
            return null;
        }
        List<?> list = (List<?>) data.get(key);
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Number)) {
                return null;                                   // algun elemento no es un numero: lista no valida
            }
            result[i] = ((Number) list.get(i)).intValue();
        }
        return result;
    }

    // Lista JSON de numeros -> List<Integer>: [512.0, 515.0] -> [512, 515]
    private static List<Integer> readIntList(Map<String, Object> data, String key) {
        if (data == null || !(data.get(key) instanceof List)) {
            return null;
        }
        List<?> list = (List<?>) data.get(key);
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Number)) {
                return null;                          // algun valor no es un numero: bloque no valido
            }
            result.add(((Number) item).intValue());
        }
        return result;
    }

    private static String readString(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}