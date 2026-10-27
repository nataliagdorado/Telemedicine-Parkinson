package server;

import common.Message;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Enrutador de comandos del servidor.
 * Recibe los mensajes entrantes por TCP y despacha la logica
 * segun la accion solicitada (LOGIN, SYMPTOM_REPORT, etc.).
 */
public class CommandRouter {
    private static final Logger LOGGER = Logger.getLogger(CommandRouter.class.getName());

    // A VER, para q el router use este gestor
    private final SessionManager sessionManager;

    public CommandRouter() {
        this.sessionManager = new SessionManager();
    }

    public CommandRouter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
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
        // Recibe el reporte de rigidez, temblores o medicacion
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(request.getPatientId());
        response.setStatus("RECORDED");
        response.addData("status", "SAVED");
        response.addData("message", "Symptom report registered for clinical evaluation");
        return response;
    }

    private Message handleStartMonitoring(Message request) {
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(request.getPatientId());
        response.setStatus("MONITORING_ACTIVE");
        response.addData("status", "STARTED");
        response.addData("sessionId", "SESS-" + System.currentTimeMillis());
        return response;
    }

    private Message handleStopMonitoring(Message request) {
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(request.getPatientId());
        response.setStatus("MONITORING_STOPPED");
        response.addData("status", "STOPPED");
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
}