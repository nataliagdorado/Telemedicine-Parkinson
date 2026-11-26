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

    // Comandos que solo se aceptan después del LOGIN (llevan sessionToken)
    private static final Set<String> PROTECTED_COMMANDS = Set.of("SYMPTOM_REPORT", "START_MONITORING", "SAMPLE_BLOCK", "STOP_MONITORING","GET_MY_RECORD","UPDATE_PROFILE");
    // Comandos del médico
    private static final Set<String> DOCTOR_COMMANDS = Set.of("LIST_PATIENTS", "GET_PATIENT_RECORD", "ADD_CLINICAL_NOTE", "GET_SESSION_SIGNAL");

    // A VER, para q el router use este gestor
    private final SessionManager sessionManager;
    private final Repository repository;
    private final SignalStore signalStore;

    private final Map<String, SessionInfo> openSessions = new HashMap<>();
    private final Map<String, Long> expectedSeq = new HashMap<>();

    // Unico constructor: el router no funciona sin sesiones ni persistencia, asi que se exige todo, si siguiesemos con el constructor vacío, alguien podría llamar al router sin tener los requisitos listos
    public CommandRouter(SessionManager sessionManager, Repository repository, SignalStore signalStore) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");  // Por lo que explico arriba también pongo el requireNonNull para que salten errores si la conexión de hace mal y no se pierdan datos
        this.repository = Objects.requireNonNull(repository, "repository");
        this.signalStore = Objects.requireNonNull(signalStore, "signalStore");
    }

    // Devuelve el usuario al que pertenece el token, o null si falta o no es válido
    private String authenticatedUser(Message request) {
        String token = readString(request.getData(), "sessionToken");
        if (token == null) {
            return null;
        }
        SessionManager.ActiveSession session = sessionManager.getSession(token);
        if (session == null) {
            return null;
        }
        session.touch();   // actualiza el último acceso
        return session.getUserId();
    }

    public Message route(Message request) {
        if (request == null) {
            return buildErrorResponse("UNKNOWN", ErrorCode.BAD_REQUEST, "Request cannot be null");
        }

        String action = request.getType();
        if (action == null || action.trim().isEmpty()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.BAD_REQUEST, "Missing action type");
        }

        if ("SAMPLE_BLOCK".equalsIgnoreCase(action)) {
            LOGGER.fine(() -> "Processing action [" + action + "] from client/patient: " + request.getPatientId());
        } else {
            LOGGER.info(() -> "Processing action [" + action + "] from client/patient: " + request.getPatientId());
        }

        String command = action.toUpperCase();

        // Comandos protegidos: el paciente se identifica por su token, no por lo que dice el mensaje
        if (PROTECTED_COMMANDS.contains(command)) {
            String userId = authenticatedUser(request);
            if (userId == null) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED,
                        "Login required: missing or invalid sessionToken");
            }
            // El token es válido: ahora, ¿es de un paciente?
            SessionManager.ActiveSession session = sessionManager.getSession(readString(request.getData(), "sessionToken"));
            if (session == null) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED,
                        "Login required: missing or invalid sessionToken");
            }
            if (!Role.PATIENT.name().equals(session.getRole())) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.FORBIDDEN,
                        "Only patients can use " + command);
            }
            if (request.getPatientId() != null && !request.getPatientId().equals(userId)) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED,
                        "patientId does not match the session token");
            }
            request.setPatientId(userId);   // desde aqui, el patientId es el del token
        }

        if (DOCTOR_COMMANDS.contains(command)) {
            String token = readString(request.getData(), "sessionToken");
            SessionManager.ActiveSession session = null;
            if (token != null) {
                session = sessionManager.getSession(token);
            }
            if (session == null) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED,
                        "Login required: missing or invalid sessionToken");
            }
            if (!Role.DOCTOR.name().equals(session.getRole())) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.FORBIDDEN,
                        "Only doctors can use " + command);
            }
            session.touch();   // actualiza el último acceso, igual que authenticatedUser()
        }

        switch (command) {
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

            case "GET_MY_RECORD":
                return handleGetMyRecord(request);

            case "UPDATE_PROFILE":
                return handleUpdateProfile(request);

            case "LIST_PATIENTS":
                return handleListPatients(request);

            case "GET_PATIENT_RECORD":
                return handleGetPatientRecord(request);

            case "ADD_CLINICAL_NOTE":
                return handleAddClinicalNote(request);

            case "GET_SESSION_SIGNAL":
                return handleGetSessionSignal(request);

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
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNSUPPORTED_TYPE, "Command not supported: " + action);
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
        Role role = Role.PATIENT;
        String roleText = readString(data, "role");
        if (roleText != null) {
            try {
                role = Role.valueOf(roleText.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD,
                        "Unknown role: " + roleText + " (allowed: PATIENT, DOCTOR, ADMIN)");
            }
        }

        String id = request.getPatientId() != null
                ? request.getPatientId()
                : (data != null && data.get("id") != null ? String.valueOf(data.get("id")) : "UNKNOWN");
        String password = readString(data, "password");

        if ("UNKNOWN".equals(id) || id.trim().isEmpty()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED, "Authentication failed: Missing ID");
        }
        if (password == null || password.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED, "Authentication failed: Password required");
        }

        if (role == Role.PATIENT) {
            try {
                Optional<Patient> found = repository.findPatient(id);
                if (found.isEmpty()) {
                    return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_PATIENT, "Unknown patient: " + id);
                }
                Patient patient = found.get();
                if (!AdminAuth.verifyPassword(password, patient.getPasswordHash(), patient.getPasswordSalt())) {
                    return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED, "Invalid credentials");
                }
            } catch (RepositoryException e) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR,
                        "Could not read patient: " + e.getMessage());
            }
        } else if (role == Role.DOCTOR || role == Role.ADMIN) {
            // Credencial maestra del especialista (doctor / admin2026)
            if (!"doctor".equalsIgnoreCase(id) || !"admin2026".equals(password)) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNAUTHENTICATED, "Invalid specialist credentials");
            }
        }

        String sessionToken = sessionManager.createSession(id, role.name());

        Message response = new Message("LOGIN_RESPONSE", request.getRequestId());
        response.setPatientId(id);
        response.setStatus("SUCCESS");
        response.addData("authenticated", true);
        response.addData("role", role.name());
        response.addData("sessionToken", sessionToken);
        return response;
    }

    private Message handleSymptomReport(Message request) {
        // Compruebo si el mensaje trae datos
        if (request.getData() == null || request.getData().isEmpty()){
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Symptom report without data");
        }

        // Comprobar que llegan los campos obligatorios ANTES de convertir: si no, Gson pondria 0/false
        List<String> problems = checkSymptomFields(request.getData());
        if (!problems.isEmpty()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD,
                    "Incomplete symptom report: " + String.join("; ", problems));
        }

        // Convertir el mapa de data n un SymptomReport
        SymptomReport report;
        try {
            report = MessageCodec.fromJson(MessageCodec.toJson(request.getData()), SymptomReport.class);
        } catch (ProtocolException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.BAD_REQUEST, "Malformed symptom report: "+ e.getMessage());
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
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD, "Invalid symptom report: " + String.join("; ", errors));
        }

        String sessionId = report.getSessionId();
        if (sessionId != null) {
            if (sessionId.isBlank()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD,
                        "sessionId cannot be empty (omit it for a report outside a session)");
            }
            try {
                Optional<SessionInfo> found = repository.findSession(sessionId);
                if (found.isEmpty() || !found.get().getPatientId().equals(report.getPatientId())) {
                    return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_SESSION,
                            "Unknown session: " + sessionId);
                }
            } catch (RepositoryException e) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR,
                        "Could not read session: " + e.getMessage());
            }
        }

        //Guardar
        try{
            repository.saveSymptomReport(report);
        }catch (RepositoryException e){
            LOGGER.warning("Could not save the report of "+ report.getPatientId()+ ": " + e.getMessage());
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not save report: " + e.getMessage());
        }

        //Si tot ha ido bien ahora se pone en saved
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(report.getPatientId());
        response.setStatus("RECORDED");
        response.addData("status", "SAVED");
        response.addData("reportId", report.getReportId());
        if (report.getSessionId() != null) {
            response.addData("sessionId", report.getSessionId());   // el cliente confirma a qué sesión ha quedado asociado
        }
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
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_PATIENT, "Unknown patient: " + patientId);
            }
            patient = found.get();
        }catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not read patient: " + e.getMessage());
        }

        //Leer y validar los parámetros de la grabación
        Integer samplingRate = readInt(data, "samplingRateHz");
        int[] channels = readIntArray(data, "channels");
        if (samplingRate == null || !VALID_RATES.contains(samplingRate)) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD, "Invalid samplingRateHz (allowed: 1, 10, 100, 1000)");
        }
        if (channels == null || channels.length == 0) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Missing channels");
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
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not start session: " + e.getMessage());
        }

        LOGGER.info("Session " + info.getSessionId() + " started for " + patientId);
        openSessions.put(info.getSessionId(), info);   // esta conexión es la dueña de la sesión
        expectedSeq.put(info.getSessionId(), 0L);

        // Nota opcional del paciente sobre esta prueba: la guarda el servidor, no la GUI
        String notes = readString(data, "notes");
        boolean noteSaved = false;
        if (notes != null && !notes.isBlank()) {
            ClinicalNote note = new ClinicalNote(
                    IdGenerator.newNoteId(),
                    patientId,
                    "Patient (" + patientId + ")",
                    "[Session " + info.getSessionId() + "] " + notes.trim());
            try {
                repository.saveClinicalNote(note);
                noteSaved = true;
            } catch (RepositoryException e) {
                LOGGER.warning("Session " + info.getSessionId() + " started, but its note could not be saved: " + e.getMessage());
            }
        }

        // 5) Responder con el sessionId que el paciente usara en cada SAMPLE_BLOCK
        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(patientId);
        response.setStatus("MONITORING_ACTIVE");
        response.addData("status", "STARTED");
        response.addData("sessionId", info.getSessionId());
        response.addData("noteSaved", noteSaved);
        return response;
    }

    private Message handleSampleBlock(Message request){
        Map<String, Object> data = request.getData();

        String sessionId = null;
        if (data != null && data.get("sessionId") instanceof String) {
            sessionId = (String) data.get("sessionId");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Sample block without sessionId");
        }
        // Solo se aceptan bloques de sesiones abiertas por esta misma conexión
        SessionInfo session = openSessions.get(sessionId);
        if (session == null) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_SESSION,
                    "Unknown session: " + sessionId);
        }

        // El bloque debe venir a en las mismas unidades y frequencia que su sesión
        String units = readString(data, "units");
        if(units == null){
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD,
                    "Sample block without units");
        }
        if (!SignalStore.UNITS.equals(units)) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD,
                    "Unsupported units: " + units + " (expected " + SignalStore.UNITS + ")");
        }

        Integer rate = readInt(data, "samplingRateHz");
        if (rate == null) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD,
                    "Sample block without samplingRateHz");
        }
        if (rate != session.getSamplingRateHz()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD,
                    "samplingRateHz " + rate + " does not match the session (" + session.getSamplingRateHz() + " Hz)");
        }

        Long firstSeq = readLong(data, "firstSeq");
        if (firstSeq == null) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD,
                    "Sample block without firstSeq");
        }
        long expected = expectedSeq.get(sessionId);
        if (firstSeq != expected) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD,
                    "Unexpected firstSeq " + firstSeq + " (expected " + expected + "): duplicated or missing block");
        }

        List<Integer> emg = readIntList(data, "emg");
        List<Integer> accX = readIntList(data, "accX");
        List<Integer> accY = readIntList(data, "accY");
        List<Integer> accZ = readIntList(data, "accZ");
        if (emg == null || accX == null || accY == null || accZ == null) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Sample block with missing or invalid channels");
        }

        // 3) Escribir en el CSV de esa sesion
        SampleBlock block = new SampleBlock(sessionId, emg, accX, accY, accZ);
        try {
            signalStore.appendBlock(block);   // falla si la sesion no esta abierta o los canales no miden lo mismo
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not store samples: " + e.getMessage());
        }

        expectedSeq.put(sessionId, expected + emg.size());

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
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Stop without sessionId");
        }

        SessionInfo info;
        try {
            Optional<SessionInfo> found = repository.findSession(sessionId);
            if (found.isEmpty()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_SESSION, "Unknown session: " + sessionId);
            }
            info = found.get();
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not read session: " + e.getMessage());
        }
        // Una sesion solo la puede cerrar su paciente
        if (!request.getPatientId().equals(info.getPatientId())) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_SESSION,
                    "Unknown session: " + sessionId);
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
                return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not stop session: " + e.getMessage());
            }
            LOGGER.info("Session " + sessionId + " finished: " + info.getStatus() + " (reason " + reason + ")");
        }
        openSessions.remove(sessionId);   // esta conexión ya no la tiene abierta
        expectedSeq.remove(sessionId);

        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(info.getPatientId());
        response.setStatus("MONITORING_STOPPED");
        response.addData("status", "STOPPED");
        response.addData("sessionStatus", info.getStatus().name());
        response.addData("durationSeconds", info.getDurationSeconds());
        return response;
    }

    // Historial del paciente autenticado: perfil, reportes de síntomas y sesiones
    private Message handleGetMyRecord(Message request) {
        String patientId = request.getPatientId();   // viene del token, no del mensaje
        try {
            Optional<Patient> found = repository.findPatient(patientId);
            if (found.isEmpty()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_PATIENT, "Unknown patient: " + patientId);
            }

            Message response = new Message("ACK", request.getRequestId());
            response.setPatientId(patientId);
            response.setStatus("RECORD_SENT");
            response.addData("patient", publicCopy(found.get()));
            response.addData("symptomReports", repository.findSymptomReports(patientId));
            response.addData("sessions", repository.findSessions(patientId));
            return response;
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not read record: " + e.getMessage());
        }
    }

    // El paciente autenticado cambia su nombre y/o su pauta de medicación
    private Message handleUpdateProfile(Message request) {
        String patientId = request.getPatientId();   // viene del token
        Map<String, Object> data = request.getData();

        String newName = readString(data, "name");
        String newSchedule = readString(data, "medicationSchedule");
        if (newName == null && newSchedule == null) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD,
                    "Nothing to update: send name and/or medicationSchedule");
        }
        if (newName != null && newName.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD, "name cannot be empty");
        }
        if (newSchedule != null && newSchedule.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.INVALID_FIELD, "medicationSchedule cannot be empty");
        }

        try {
            Optional<Patient> found = repository.findPatient(patientId);
            if (found.isEmpty()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_PATIENT, "Unknown patient: " + patientId);
            }
            Patient patient = found.get();
            if (newName != null) {
                patient.setName(newName.trim());
            }
            if (newSchedule != null) {
                patient.setMedicationSchedule(newSchedule.trim());
            }
            repository.updatePatient(patient);
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not update profile: " + e.getMessage());
        }

        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(patientId);
        response.setStatus("PROFILE_UPDATED");
        return response;
    }

    // Médico: lista de todos los pacientes (sin contraseñas) para elegir uno
    private Message handleListPatients(Message request) {
        List<Patient> publicPatients = new ArrayList<>();
        try {
            for (Patient patient : repository.findAllPatients()) {
                publicPatients.add(publicCopy(patient));
            }
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not read patients: " + e.getMessage());
        }

        Message response = new Message("ACK", request.getRequestId());
        response.setStatus("PATIENTS_SENT");
        response.addData("patients", publicPatients);
        return response;
    }

    // Médico: historial completo de un paciente (perfil sin contraseña, reportes, sesiones y notas)
    private Message handleGetPatientRecord(Message request) {
        String patientId = request.getPatientId();   // el paciente que el médico quiere consultar
        if (patientId == null || patientId.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Missing patientId");
        }
        try {
            Optional<Patient> found = repository.findPatient(patientId);
            if (found.isEmpty()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_PATIENT, "Unknown patient: " + patientId);
            }

            Message response = new Message("ACK", request.getRequestId());
            response.setPatientId(patientId);
            response.setStatus("RECORD_SENT");
            response.addData("patient", publicCopy(found.get()));
            response.addData("symptomReports", repository.findSymptomReports(patientId));
            response.addData("sessions", repository.findSessions(patientId));
            response.addData("clinicalNotes", repository.findClinicalNotes(patientId));
            return response;
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not read record: " + e.getMessage());
        }
    }

    // Médico: añade una nota clínica a un paciente, sin modificar sus mediciones
    private Message handleAddClinicalNote(Message request) {
        String patientId = request.getPatientId();              // paciente al que va la nota (del mensaje)
        String doctorId = authenticatedUser(request);           // autor de la nota (del token)
        Map<String, Object> data = request.getData();
        String observation = readString(data, "observation");
        String recordRef = readString(data, "recordRef");       // opcional: sesión o reporte al que se refiere

        if (patientId == null || patientId.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Missing patientId");
        }
        if (observation == null || observation.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Missing observation");
        }

        String text = observation.trim();
        if (recordRef != null && !recordRef.isBlank()) {
            text = "[" + recordRef.trim() + "] " + text;
        }

        ClinicalNote note = new ClinicalNote(IdGenerator.newNoteId(), patientId, doctorId, text);
        try {
            if (repository.findPatient(patientId).isEmpty()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_PATIENT, "Unknown patient: " + patientId);
            }
            repository.saveClinicalNote(note);
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not save note: " + e.getMessage());
        }

        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(patientId);
        response.setStatus("NOTE_SAVED");
        response.addData("noteId", note.getNoteId());
        response.addData("timestamp", note.getTimestamp().toString());
        return response;
    }

    // Médico: muestras guardadas de una sesión, con los metadatos necesarios para interpretarlas
    private Message handleGetSessionSignal(Message request) {
        String sessionId = readString(request.getData(), "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.MISSING_FIELD, "Missing sessionId");
        }

        SessionInfo info;
        SampleBlock samples;
        try {
            Optional<SessionInfo> found = repository.findSession(sessionId);
            if (found.isEmpty()) {
                return buildErrorResponse(request.getRequestId(), ErrorCode.UNKNOWN_SESSION, "Unknown session: " + sessionId);
            }
            info = found.get();
            samples = signalStore.readRecording(info);
        } catch (RepositoryException e) {
            return buildErrorResponse(request.getRequestId(), ErrorCode.STORAGE_ERROR, "Could not read signal: " + e.getMessage());
        }

        Message response = new Message("ACK", request.getRequestId());
        response.setPatientId(info.getPatientId());
        response.setStatus("SIGNAL_SENT");
        response.addData("sessionId", sessionId);
        response.addData("sessionStatus", info.getStatus().name());
        response.addData("startTime", info.getStartTime());
        response.addData("samplingRateHz", info.getSamplingRateHz());
        response.addData("channels", info.getChannels());
        response.addData("units", SignalStore.UNITS);
        response.addData("totalSamples", samples.getEmgSamples().size());
        response.addData("emg", samples.getEmgSamples());
        response.addData("accX", samples.getAccX());
        response.addData("accY", samples.getAccY());
        response.addData("accZ", samples.getAccZ());
        return response;
    }

    /**
     * Se llama desde el finally de ClientHandler: las sesiones abiertas de esta conexión pasan a INCOMPLETE
     */

    public void closeOpenSessions(){
        for (String sessionId : openSessions.keySet()){
            try {
                signalStore.closeRecording(sessionId);
                Optional<SessionInfo> found = repository.findSession(sessionId);
                if (found.isPresent() && found.get().getStatus() == SessionStatus.RECORDING){
                    SessionInfo info = found.get();
                    info.markIncomplete(LocalDateTime.now());
                    repository.updateSession(info);
                    LOGGER.info("Session " + sessionId + " marked INCOMPLETE (client disconnected)");
                }
            } catch (RepositoryException e) {
                // Seguimos con las demás: un fallo no debe dejar el resto abiertas
                LOGGER.warning("Could not close session " + sessionId + ": " + e.getMessage());
            }
        }
        openSessions.clear();
        expectedSeq.clear();
    }

    private Message handlePing(Message request) {
        Message response = new Message("PONG", request.getRequestId());
        response.setStatus("OK");
        return response;
    }

    private Message buildErrorResponse(String requestId, ErrorCode code, String detail) {
        Message error = new Message("ERROR", requestId);
        error.setStatus("FAILED");
        error.addData("code", code.name());   // codigo fijo, documentado en el protocolo
        error.addData("error", detail);       // texto para la persona: PatientApp lo muestra tal cual
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
    // Copia del paciente para enviar por la red: mismos datos, pero sin passwordHash ni passwordSalt
    private static Patient publicCopy(Patient patient) {
        return new Patient(patient.getPatientId(), patient.getName(), patient.getBirthDate(),
                patient.getDiagnosisYear(), patient.getMedicationSchedule());
    }

    private static Long readLong(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
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

    // Campos obligatorios de un SYMPTOM_REPORT. Si faltan, Gson pondria 0 o false sin avisar
    private static List<String> checkSymptomFields(Map<String, Object> data) {
        List<String> problems = new ArrayList<>();
        checkInteger(data, "rigidityLevel", problems);
        checkInteger(data, "tremorLevel", problems);
        checkBoolean(data, "medicationTaken", problems);
        checkBoolean(data, "freezingEpisodes", problems);
        return problems;
    }

    private static void checkInteger(Map<String, Object> data, String key, List<String> problems) {
        Object value = data.get(key);
        if (value == null) {
            problems.add(key + " is missing");
        } else if (!(value instanceof Number)) {
            problems.add(key + " must be a number");
        } else if (((Number) value).doubleValue() % 1 != 0) {
            problems.add(key + " must be a whole number");
        }
    }

    private static void checkBoolean(Map<String, Object> data, String key, List<String> problems) {
        Object value = data.get(key);
        if (value == null) {
            problems.add(key + " is missing");
        } else if (!(value instanceof Boolean)) {
            problems.add(key + " must be true or false");
        }
    }
}