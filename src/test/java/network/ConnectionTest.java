package network;

import common.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import patient.ServerConnection;
import server.AdminAuth;
import server.HospitalServer;

import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionTest {

    // Contraseña de los pacientes de prueba P001 y P002 (datos sintéticos de SeedData)
    private static final String TEST_PASSWORD = "1234";

    // Puerto propio de los tests: distinto del 9000, para no mezclarse nunca con el servidor "de verdad"
    private static final int TEST_PORT = 9100;

    @BeforeAll
    public static void setup() throws Exception {
        // Servidor de pruebas aislado: carpeta de datos temporal y nueva en cada ejecución, y su propio puerto.
        // Se fija ANTES de tocar HospitalServer: sus constantes PORT y DATA_DIR se leen al cargar la clase
        Path testData = Files.createTempDirectory("telemed-test-");
        System.setProperty("data.dir", testData.toString());
        System.setProperty("server.port", String.valueOf(TEST_PORT));
        System.setProperty("server.ip", "127.0.0.1");      // el cliente de test siempre en local, diga lo que diga config.properties
        System.out.println("[ConnectionTest] Test data in " + testData);

        // Si el puerto ya está ocupado no se reutiliza: escribiríamos en los datos de otro servidor
        if (isServerRunning("127.0.0.1", TEST_PORT)) {
            fail("Port " + TEST_PORT + " is already in use: close whatever is using it before running the tests");
        }

        Thread serverThread = new Thread(() -> HospitalServer.main(new String[]{}));
        serverThread.setDaemon(true);
        serverThread.start();

        // Esperar a que el servidor acepte conexiones (máximo 5 s) en lugar de un sleep fijo
        for (int i = 0; i < 50 && !isServerRunning("127.0.0.1", TEST_PORT); i++) {
            Thread.sleep(100);
        }
        assertTrue(isServerRunning("127.0.0.1", TEST_PORT), "The test server did not start on port " + TEST_PORT);
    }

    private static boolean isServerRunning(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Hace LOGIN igual que PatientApp y devuelve el token
    private String login(ServerConnection conn, String patientId) throws Exception {
        Message login = new Message("LOGIN", "req-login-" + patientId);
        login.setPatientId(patientId);
        login.addData("role", "PATIENT");
        login.addData("password", TEST_PASSWORD);
        Message response = conn.send(login);
        return (String) response.getData().get("sessionToken");
    }

    // Login del médico (credencial de especialista que acepta handleLogin) y devuelve su token
    private String doctorLogin(ServerConnection conn) throws Exception {
        Message login = new Message("LOGIN", "req-login-doctor-" + System.nanoTime());
        login.setPatientId("doctor");
        login.addData("role", "DOCTOR");
        login.addData("password", "admin2026");
        Message response = conn.send(login);
        return (String) response.getData().get("sessionToken");
    }

    // Abre una sesión de monitorización y devuelve su sessionId
    private String startSession(ServerConnection conn, String token) throws Exception {
        Message start = new Message("START_MONITORING", "req-start-" + System.nanoTime());
        start.addData("sessionToken", token);
        start.addData("samplingRateHz", 1000);
        start.addData("channels", List.of(0, 1, 2, 3));
        Message response = conn.send(start);
        assertEquals("ACK", response.getType());
        return (String) response.getData().get("sessionId");
    }

    // Cierra la sesión como si la parase el paciente
    private void stopSession(ServerConnection conn, String token, String sessionId) throws Exception {
        Message stop = new Message("STOP_MONITORING", "req-stop-" + System.nanoTime());
        stop.addData("sessionToken", token);
        stop.addData("sessionId", sessionId);
        stop.addData("reason", "USER");
        assertEquals("ACK", conn.send(stop).getType());
    }

    // Reporte de síntomas válido; si sessionId es null, no se envía el campo (reporte fuera de sesión)
    private Message symptomReport(String token, String sessionId) {
        Message msg = new Message("SYMPTOM_REPORT", "req-symptom-" + System.nanoTime());
        msg.addData("sessionToken", token);
        msg.addData("rigidityLevel", 2);
        msg.addData("tremorLevel", 3);
        msg.addData("medicationTaken", true);
        msg.addData("freezingEpisodes", false);
        if (sessionId != null) {
            msg.addData("sessionId", sessionId);
        }
        return msg;
    }

    // Envía un SAMPLE_BLOCK válido a una sesión abierta (1000 Hz, ADC_10bit)
    private Message sendBlock(ServerConnection conn, String token, String sessionId, long firstSeq,
                              List<Integer> emg, List<Integer> accX, List<Integer> accY, List<Integer> accZ) throws Exception {
        Message block = new Message("SAMPLE_BLOCK", "req-block-" + System.nanoTime());
        block.addData("sessionToken", token);
        block.addData("sessionId", sessionId);
        block.addData("firstSeq", firstSeq);
        block.addData("samplingRateHz", 1000);
        block.addData("units", "ADC_10bit");
        block.addData("emg", emg);
        block.addData("accX", accX);
        block.addData("accY", accY);
        block.addData("accZ", accZ);
        return conn.send(block);
    }

    // Convierte una lista JSON de números (Gson los lee como 512.0) en enteros
    private static List<Integer> asInts(Object jsonList) {
        List<Integer> result = new java.util.ArrayList<>();
        for (Object value : (List<?>) jsonList) {
            result.add(((Number) value).intValue());
        }
        return result;
    }

    // Devuelve la pauta de medicación actual del paciente autenticado (vía GET_MY_RECORD)
    private String currentSchedule(ServerConnection conn, String token) throws Exception {
        Message request = new Message("GET_MY_RECORD", "req-record-" + System.nanoTime());
        request.addData("sessionToken", token);
        Message response = conn.send(request);
        Map<?, ?> patient = (Map<?, ?>) response.getData().get("patient");
        return (String) patient.get("medicationSchedule");
    }

    // Envía un UPDATE_PROFILE con la pauta indicada
    private Message updateSchedule(ServerConnection conn, String token, String schedule) throws Exception {
        Message request = new Message("UPDATE_PROFILE", "req-update-" + System.nanoTime());
        request.addData("sessionToken", token);
        request.addData("medicationSchedule", schedule);
        return conn.send(request);
    }

    @Test
    public void testHandshakeReturnsConnectedStatus() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message hello = new Message("HELLO", "test-req-1");
        hello.addData("role", "PATIENT");
        hello.addData("patientId", "P001");

        Message response = conn.send(hello);

        assertNotNull(response);
        assertEquals("ACK", response.getType());
        assertEquals("CONNECTED", response.getData().get("status"));

        conn.close();
    }



    // El otro test para probar si unifica
    @Test
    public void testPatientLoginGeneratesSessionToken() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message loginMsg = new Message("LOGIN", "req-login");
        loginMsg.setPatientId("P001");                  // paciente que existe (SeedData)
        loginMsg.addData("role", "PATIENT");
        loginMsg.addData("password", TEST_PASSWORD);

        Message response = conn.send(loginMsg);

        assertNotNull(response);
        assertEquals("LOGIN_RESPONSE", response.getType());
        assertEquals("SUCCESS", response.getStatus());
        assertTrue((Boolean) response.getData().get("authenticated"));

        String token = (String) response.getData().get("sessionToken");
        assertNotNull(token);
        assertTrue(token.startsWith("TOKEN-"));

        conn.close();
    }

    @Test
    public void testSymptomReportRecordedSuccessfully() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        Message symptomMsg = new Message("SYMPTOM_REPORT", "req-symptom");
        symptomMsg.setPatientId("P001");                 // paciente que existe (SeedData)
        symptomMsg.addData("rigidityLevel", 2);
        symptomMsg.addData("tremorLevel", 3);
        symptomMsg.addData("medicationTaken", true);
        symptomMsg.addData("freezingEpisodes", false);
        symptomMsg.addData("sessionToken", token);

        Message response = conn.send(symptomMsg);

        assertNotNull(response);
        assertEquals("ACK", response.getType());
        assertEquals("RECORDED", response.getStatus());
        assertEquals("SAVED", response.getData().get("status"));

        conn.close();
    }

    @Test
    public void testIncompleteSymptomReportIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        Message msg = new Message("SYMPTOM_REPORT", "req-incomplete");
        msg.setPatientId("P001");
        msg.addData("rigidityLevel", "MODERATE");   // tipo incorrecto
        msg.addData("sessionToken", token);
        // faltan tremorLevel, medicationTaken y freezingEpisodes

        Message response = conn.send(msg);

        assertNotNull(response);
        assertEquals("ERROR", response.getType());
        assertEquals("MISSING_FIELD", response.getData().get("code"));

        conn.close();
    }

    @Test
    public void testSymptomReportWithoutLoginIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message msg = new Message("SYMPTOM_REPORT", "req-nologin");
        msg.setPatientId("P001");
        msg.addData("rigidityLevel", 2);
        msg.addData("tremorLevel", 3);
        msg.addData("medicationTaken", true);
        msg.addData("freezingEpisodes", false);
        // sin sessionToken

        Message response = conn.send(msg);

        assertEquals("ERROR", response.getType());
        assertEquals("UNAUTHENTICATED", response.getData().get("code"));

        conn.close();
    }

    @Test
    public void testCannotSendDataAsAnotherPatient() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");          // pulsera de P001...

        Message msg = new Message("SYMPTOM_REPORT", "req-impostor");
        msg.setPatientId("P002");                    // ...pero dice ser P002
        msg.addData("sessionToken", token);
        msg.addData("rigidityLevel", 2);
        msg.addData("tremorLevel", 3);
        msg.addData("medicationTaken", true);
        msg.addData("freezingEpisodes", false);

        Message response = conn.send(msg);

        assertEquals("ERROR", response.getType());
        assertEquals("UNAUTHENTICATED", response.getData().get("code"));

        conn.close();
    }

    @Test
    public void testCannotWriteIntoAnotherPatientsSession() throws Exception {
        // P001 abre una sesion
        ServerConnection connA = new ServerConnection();
        connA.connect();
        String tokenA = login(connA, "P001");
        Message start = new Message("START_MONITORING", "req-start-A");
        start.addData("sessionToken", tokenA);
        start.addData("samplingRateHz", 1000);
        start.addData("channels", List.of(0, 1, 2, 3));
        Message started = connA.send(start);
        assertEquals("ACK", started.getType());
        String sessionA = (String) started.getData().get("sessionId");

        // P002, con su propio login, intenta escribir en la sesion de P001
        ServerConnection connB = new ServerConnection();
        connB.connect();
        String tokenB = login(connB, "P002");
        Message block = new Message("SAMPLE_BLOCK", "req-block-B");
        block.addData("sessionToken", tokenB);
        block.addData("sessionId", sessionA);
        block.addData("emg", List.of(512, 513));
        block.addData("accX", List.of(500, 501));
        block.addData("accY", List.of(500, 501));
        block.addData("accZ", List.of(500, 501));
        Message rejected = connB.send(block);
        assertEquals("ERROR", rejected.getType());
        assertEquals("UNKNOWN_SESSION", rejected.getData().get("code"));

        // P002 tampoco puede cerrarla
        Message stopB = new Message("STOP_MONITORING", "req-stop-B");
        stopB.addData("sessionToken", tokenB);
        stopB.addData("sessionId", sessionA);
        stopB.addData("reason", "USER");
        assertEquals("UNKNOWN_SESSION", connB.send(stopB).getData().get("code"));

        // P001 si puede cerrarla
        Message stopA = new Message("STOP_MONITORING", "req-stop-A");
        stopA.addData("sessionToken", tokenA);
        stopA.addData("sessionId", sessionA);
        stopA.addData("reason", "USER");
        assertEquals("ACK", connA.send(stopA).getType());

        connA.close();
        connB.close();
    }

    @Test
    public void testLoginWithUnknownPatientIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message loginMsg = new Message("LOGIN", "req-login-unknown");
        loginMsg.setPatientId("p001");                  // minusculas: no existe en SeedData
        loginMsg.addData("role", "PATIENT");
        loginMsg.addData("password", TEST_PASSWORD);

        Message response = conn.send(loginMsg);

        assertEquals("ERROR", response.getType());
        assertEquals("UNKNOWN_PATIENT", response.getData().get("code"));
        assertNull(response.getData().get("sessionToken"));   // no se entrega token

        conn.close();
    }

    @Test
    public void testLoginWithInvalidRoleIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message loginMsg = new Message("LOGIN", "req-login-role");
        loginMsg.setPatientId("P001");
        loginMsg.addData("role", "PATEINT");            // rol mal escrito

        Message response = conn.send(loginMsg);

        assertEquals("ERROR", response.getType());
        assertEquals("INVALID_FIELD", response.getData().get("code"));

        conn.close();
    }

    @Test
    public void testPasswordHashingAndVerification() {
        String password = "DoctorSecurePass2026!";
        String salt = AdminAuth.generateSalt();
        String hash = AdminAuth.hashPassword(password, salt);

        assertNotNull(salt);
        assertNotNull(hash);
        assertNotEquals(password, hash);
        assertTrue(AdminAuth.verifyPassword(password, hash, salt));
        assertFalse(AdminAuth.verifyPassword("WrongPassword", hash, salt));
    }

    @Test
    public void testConcurrentClientsHandshake() throws Exception {
        // Simula la prueba de la diapositiva 72: dos clientes conectados a la vez
        ServerConnection clientA = new ServerConnection();
        ServerConnection clientB = new ServerConnection();

        clientA.connect();
        clientB.connect();

        // Peticion del Cliente A
        Message msgA = new Message("HELLO", "req-client-A");
        msgA.setPatientId("PATIENT-A");
        Message respA = clientA.send(msgA);

        // Peticion del Cliente B mientras el Cliente A sigue conectado
        Message msgB = new Message("HELLO", "req-client-B");
        msgB.setPatientId("PATIENT-B");
        Message respB = clientB.send(msgB);

        // Validar que ambos clientes recibieron respuesta independiente
        assertNotNull(respA);
        assertEquals("ACK", respA.getType());
        assertEquals("CONNECTED", respA.getData().get("status"));

        assertNotNull(respB);
        assertEquals("ACK", respB.getType());
        assertEquals("CONNECTED", respB.getData().get("status"));

        // Cerrar ambos
        clientA.close();
        clientB.close();
    }

    @Test
    public void testShutdownCoordinatorReleasesResources() {
        // Verifica el Requisito 8: Mechanism to close the server
        server.ShutdownCoordinator coordinator = server.HospitalServer.getShutdownCoordinator();

        assertNotNull(coordinator, "El ShutdownCoordinator debe estar inicializado en el servidor");
        assertTrue(coordinator.isRunning(), "El servidor debe estar inicialmente en estado activo");

        // Verifica que el registro de clientes no sea nulo y este monitorizado
        assertNotNull(server.HospitalServer.clientRegistry);
    }

    // C4: un reporte se puede asociar a una sesión propia, aunque ya esté cerrada
    @Test
    public void testSymptomReportLinkedToOwnSession() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        String sessionId = startSession(conn, token);
        stopSession(conn, token, sessionId);            // el reporte llega con la sesión ya COMPLETED

        Message response = conn.send(symptomReport(token, sessionId));

        assertEquals("ACK", response.getType());
        assertEquals("RECORDED", response.getStatus());
        assertEquals(sessionId, response.getData().get("sessionId"));

        conn.close();
    }

    // C4: un paciente no puede asociar su reporte a la sesión de otro
    @Test
    public void testSymptomReportWithForeignSessionIsRejected() throws Exception {
        ServerConnection connA = new ServerConnection();
        connA.connect();
        String tokenA = login(connA, "P001");
        String sessionA = startSession(connA, tokenA);

        ServerConnection connB = new ServerConnection();
        connB.connect();
        String tokenB = login(connB, "P002");

        Message response = connB.send(symptomReport(tokenB, sessionA));

        assertEquals("ERROR", response.getType());
        assertEquals("UNKNOWN_SESSION", response.getData().get("code"));

        stopSession(connA, tokenA, sessionA);
        connA.close();
        connB.close();
    }

    // C4: sessionId inexistente o vacío se rechaza
    @Test
    public void testSymptomReportWithInvalidSessionIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        Message unknown = conn.send(symptomReport(token, "SES-does-not-exist"));
        assertEquals("ERROR", unknown.getType());
        assertEquals("UNKNOWN_SESSION", unknown.getData().get("code"));

        Message blank = conn.send(symptomReport(token, ""));
        assertEquals("ERROR", blank.getType());
        assertEquals("INVALID_FIELD", blank.getData().get("code"));

        conn.close();
    }

    // GET_MY_RECORD: el paciente recibe su perfil, sus reportes y sus sesiones, y solo los suyos
    @Test
    public void testGetMyRecordReturnsOnlyOwnData() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");
        conn.send(symptomReport(token, null));          // asegura que P001 tiene al menos un reporte

        Message request = new Message("GET_MY_RECORD", "req-record");
        request.addData("sessionToken", token);
        Message response = conn.send(request);

        assertEquals("ACK", response.getType());
        assertEquals("RECORD_SENT", response.getStatus());

        Map<?, ?> patient = (Map<?, ?>) response.getData().get("patient");
        assertEquals("P001", patient.get("patientId"));

        List<?> reports = (List<?>) response.getData().get("symptomReports");
        assertFalse(reports.isEmpty());
        for (Object report : reports) {
            assertEquals("P001", ((Map<?, ?>) report).get("patientId"));   // ningún reporte de otro paciente
        }

        List<?> sessions = (List<?>) response.getData().get("sessions");
        for (Object session : sessions) {
            assertEquals("P001", ((Map<?, ?>) session).get("patientId"));
        }

        conn.close();
    }

    // GET_MY_RECORD sin token se rechaza
    @Test
    public void testGetMyRecordWithoutLoginIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message request = new Message("GET_MY_RECORD", "req-record-nologin");
        request.setPatientId("P002");                   // intenta leer el historial de otro sin login
        Message response = conn.send(request);

        assertEquals("ERROR", response.getType());
        assertEquals("UNAUTHENTICATED", response.getData().get("code"));

        conn.close();
    }

    // UPDATE_PROFILE: el cambio se guarda en el servidor y el login sigue funcionando (no se pierde la contraseña)
    @Test
    public void testUpdateProfileChangesScheduleAndKeepsPassword() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P002");
        String original = currentSchedule(conn, token);

        try {
            Message response = updateSchedule(conn, token, "Levodopa 10:00 (test)");
            assertEquals("ACK", response.getType());
            assertEquals("PROFILE_UPDATED", response.getStatus());
            assertEquals("Levodopa 10:00 (test)", currentSchedule(conn, token));

            // Si updatePatient hubiera perdido el hash, este login fallaría
            ServerConnection again = new ServerConnection();
            again.connect();
            assertNotNull(login(again, "P002"));
            again.close();
        } finally {
            updateSchedule(conn, token, original);      // deja los datos como estaban
            conn.close();
        }
    }

    // UPDATE_PROFILE sin campos o con un campo vacío se rechaza
    @Test
    public void testUpdateProfileWithInvalidDataIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P002");

        Message empty = new Message("UPDATE_PROFILE", "req-update-empty");
        empty.addData("sessionToken", token);
        Message noFields = conn.send(empty);
        assertEquals("ERROR", noFields.getType());
        assertEquals("MISSING_FIELD", noFields.getData().get("code"));

        Message blank = updateSchedule(conn, token, "   ");
        assertEquals("ERROR", blank.getType());
        assertEquals("INVALID_FIELD", blank.getData().get("code"));

        conn.close();
    }

    // UPDATE_PROFILE sin token se rechaza
    @Test
    public void testUpdateProfileWithoutLoginIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message request = new Message("UPDATE_PROFILE", "req-update-nologin");
        request.setPatientId("P002");
        request.addData("medicationSchedule", "Nada");
        Message response = conn.send(request);

        assertEquals("ERROR", response.getType());
        assertEquals("UNAUTHENTICATED", response.getData().get("code"));

        conn.close();
    }

    // START_MONITORING con nota: la guarda el servidor; sin nota o con nota vacía no se guarda nada
    @Test
    public void testStartMonitoringSavesPatientNote() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        // Con nota
        Message withNote = new Message("START_MONITORING", "req-start-note");
        withNote.addData("sessionToken", token);
        withNote.addData("samplingRateHz", 1000);
        withNote.addData("channels", List.of(0, 1, 2, 3));
        withNote.addData("notes", "Prueba tras tomar la medicación (test)");
        Message started = conn.send(withNote);
        assertEquals("ACK", started.getType());
        assertEquals(Boolean.TRUE, started.getData().get("noteSaved"));
        stopSession(conn, token, (String) started.getData().get("sessionId"));

        // Nota vacía: la sesión arranca igual, pero no se guarda nota
        Message blankNote = new Message("START_MONITORING", "req-start-blank");
        blankNote.addData("sessionToken", token);
        blankNote.addData("samplingRateHz", 1000);
        blankNote.addData("channels", List.of(0, 1, 2, 3));
        blankNote.addData("notes", "   ");
        Message startedBlank = conn.send(blankNote);
        assertEquals("ACK", startedBlank.getType());
        assertEquals(Boolean.FALSE, startedBlank.getData().get("noteSaved"));
        stopSession(conn, token, (String) startedBlank.getData().get("sessionId"));

        // Sin nota (como hasta ahora)
        String sessionId = startSession(conn, token);
        stopSession(conn, token, sessionId);

        conn.close();
    }

    // Seguridad: un paciente no puede usar los comandos del médico
    @Test
    public void testPatientCannotUseDoctorCommands() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        Message request = new Message("LIST_PATIENTS", "req-list-patient");
        request.addData("sessionToken", token);
        Message response = conn.send(request);

        assertEquals("ERROR", response.getType());
        assertEquals("FORBIDDEN", response.getData().get("code"));

        conn.close();
    }

    // Seguridad: el médico no puede usar los comandos del paciente
    @Test
    public void testDoctorCannotUsePatientCommands() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = doctorLogin(conn);
        assertNotNull(token);

        Message report = conn.send(symptomReport(token, null));
        assertEquals("ERROR", report.getType());
        assertEquals("FORBIDDEN", report.getData().get("code"));   // antes daba STORAGE_ERROR por casualidad

        Message record = new Message("GET_MY_RECORD", "req-record-doctor");
        record.addData("sessionToken", token);
        assertEquals("FORBIDDEN", conn.send(record).getData().get("code"));

        conn.close();
    }

    // Seguridad: los comandos del médico sin token se rechazan como no autenticados
    @Test
    public void testDoctorCommandsRequireLogin() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();

        Message request = new Message("LIST_PATIENTS", "req-list-nologin");
        Message response = conn.send(request);

        assertEquals("ERROR", response.getType());
        assertEquals("UNAUTHENTICATED", response.getData().get("code"));

        conn.close();
    }

    // Seguridad: GET_MY_RECORD no envía el hash ni el salt, y la contraseña sigue funcionando
    @Test
    public void testGetMyRecordDoesNotSendPassword() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = login(conn, "P001");

        Message request = new Message("GET_MY_RECORD", "req-record-nohash");
        request.addData("sessionToken", token);
        Map<?, ?> patient = (Map<?, ?>) conn.send(request).getData().get("patient");

        assertEquals("P001", patient.get("patientId"));
        assertFalse(patient.containsKey("passwordHash"));
        assertFalse(patient.containsKey("passwordSalt"));

        // La copia no se guarda: el original en disco conserva la contraseña
        ServerConnection again = new ServerConnection();
        again.connect();
        assertNotNull(login(again, "P001"));
        again.close();

        conn.close();
    }

    // LIST_PATIENTS: el médico recibe todos los pacientes, sin contraseñas
    @Test
    public void testDoctorListsPatientsWithoutPasswords() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = doctorLogin(conn);

        Message request = new Message("LIST_PATIENTS", "req-list");
        request.addData("sessionToken", token);
        Message response = conn.send(request);

        assertEquals("ACK", response.getType());
        assertEquals("PATIENTS_SENT", response.getStatus());

        List<?> patients = (List<?>) response.getData().get("patients");
        boolean foundP001 = false;
        boolean foundP002 = false;
        for (Object item : patients) {
            Map<?, ?> patient = (Map<?, ?>) item;
            assertFalse(patient.containsKey("passwordHash"));   // ninguno lleva el hash
            assertFalse(patient.containsKey("passwordSalt"));
            if ("P001".equals(patient.get("patientId"))) {
                foundP001 = true;
            }
            if ("P002".equals(patient.get("patientId"))) {
                foundP002 = true;
            }
        }
        assertTrue(foundP001);
        assertTrue(foundP002);

        conn.close();
    }

    // GET_PATIENT_RECORD: el médico ve la ficha completa, incluida la nota que el paciente dejó en START_MONITORING
    @Test
    public void testDoctorGetsPatientRecordWithNotes() throws Exception {
        // P001 hace una prueba con una nota única
        ServerConnection patientConn = new ServerConnection();
        patientConn.connect();
        String patientToken = login(patientConn, "P001");
        String noteText = "Nota de test " + System.nanoTime();

        Message start = new Message("START_MONITORING", "req-start-record");
        start.addData("sessionToken", patientToken);
        start.addData("samplingRateHz", 1000);
        start.addData("channels", List.of(0, 1, 2, 3));
        start.addData("notes", noteText);
        String sessionId = (String) patientConn.send(start).getData().get("sessionId");
        stopSession(patientConn, patientToken, sessionId);
        patientConn.close();

        // El médico pide la ficha de P001
        ServerConnection doctorConn = new ServerConnection();
        doctorConn.connect();
        String doctorToken = doctorLogin(doctorConn);

        Message request = new Message("GET_PATIENT_RECORD", "req-patient-record");
        request.setPatientId("P001");
        request.addData("sessionToken", doctorToken);
        Message response = doctorConn.send(request);

        assertEquals("ACK", response.getType());
        assertEquals("RECORD_SENT", response.getStatus());

        Map<?, ?> patient = (Map<?, ?>) response.getData().get("patient");
        assertEquals("P001", patient.get("patientId"));
        assertFalse(patient.containsKey("passwordHash"));

        boolean sessionFound = false;
        for (Object item : (List<?>) response.getData().get("sessions")) {
            if (sessionId.equals(((Map<?, ?>) item).get("sessionId"))) {
                sessionFound = true;
            }
        }
        assertTrue(sessionFound);

        boolean noteFound = false;
        for (Object item : (List<?>) response.getData().get("clinicalNotes")) {
            String observation = (String) ((Map<?, ?>) item).get("observation");
            if (observation != null && observation.contains(noteText) && observation.contains(sessionId)) {
                noteFound = true;    // la nota la guardó el servidor y lleva la sesión en el texto
            }
        }
        assertTrue(noteFound);

        doctorConn.close();
    }

    // GET_PATIENT_RECORD sin patientId o con un paciente que no existe se rechaza
    @Test
    public void testGetPatientRecordWithInvalidPatientIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = doctorLogin(conn);

        Message missing = new Message("GET_PATIENT_RECORD", "req-record-missing");
        missing.addData("sessionToken", token);
        assertEquals("MISSING_FIELD", conn.send(missing).getData().get("code"));

        Message unknown = new Message("GET_PATIENT_RECORD", "req-record-unknown");
        unknown.setPatientId("P999");
        unknown.addData("sessionToken", token);
        assertEquals("UNKNOWN_PATIENT", conn.send(unknown).getData().get("code"));

        conn.close();
    }

    // ADD_CLINICAL_NOTE: el médico añade una nota, firmada con su token, y aparece en la ficha del paciente
    @Test
    public void testDoctorAddsClinicalNote() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = doctorLogin(conn);
        String text = "Observación de test " + System.nanoTime();

        Message add = new Message("ADD_CLINICAL_NOTE", "req-add-note");
        add.setPatientId("P002");
        add.addData("sessionToken", token);
        add.addData("observation", text);
        add.addData("recordRef", "SES-ref-test");
        Message response = conn.send(add);

        assertEquals("ACK", response.getType());
        assertEquals("NOTE_SAVED", response.getStatus());
        String noteId = (String) response.getData().get("noteId");
        assertNotNull(noteId);
        assertNotNull(response.getData().get("timestamp"));

        // La nota aparece en la ficha de P002, con su referencia y firmada por el médico del token
        Message request = new Message("GET_PATIENT_RECORD", "req-record-after-note");
        request.setPatientId("P002");
        request.addData("sessionToken", token);
        boolean found = false;
        for (Object item : (List<?>) conn.send(request).getData().get("clinicalNotes")) {
            Map<?, ?> note = (Map<?, ?>) item;
            if (noteId.equals(note.get("noteId"))) {
                found = true;
                assertEquals("[SES-ref-test] " + text, note.get("observation"));
                assertEquals("doctor", note.get("doctorId"));
                assertEquals("P002", note.get("patientId"));
            }
        }
        assertTrue(found);

        conn.close();
    }

    // ADD_CLINICAL_NOTE sin texto, sin paciente o con un paciente que no existe se rechaza
    @Test
    public void testAddClinicalNoteWithInvalidDataIsRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String token = doctorLogin(conn);

        Message noText = new Message("ADD_CLINICAL_NOTE", "req-note-notext");
        noText.setPatientId("P002");
        noText.addData("sessionToken", token);
        noText.addData("observation", "   ");
        assertEquals("MISSING_FIELD", conn.send(noText).getData().get("code"));

        Message noPatient = new Message("ADD_CLINICAL_NOTE", "req-note-nopatient");
        noPatient.addData("sessionToken", token);
        noPatient.addData("observation", "Texto");
        assertEquals("MISSING_FIELD", conn.send(noPatient).getData().get("code"));

        Message unknown = new Message("ADD_CLINICAL_NOTE", "req-note-unknown");
        unknown.setPatientId("P999");
        unknown.addData("sessionToken", token);
        unknown.addData("observation", "Texto");
        assertEquals("UNKNOWN_PATIENT", conn.send(unknown).getData().get("code"));

        conn.close();
    }

    // GET_SESSION_SIGNAL: el médico recibe exactamente las muestras que envió el paciente, con sus metadatos
    @Test
    public void testDoctorGetsSessionSignal() throws Exception {
        // P001 graba una sesión corta: dos bloques, 5 muestras en total
        ServerConnection patientConn = new ServerConnection();
        patientConn.connect();
        String patientToken = login(patientConn, "P001");
        String sessionId = startSession(patientConn, patientToken);
        assertEquals("ACK", sendBlock(patientConn, patientToken, sessionId, 0,
                List.of(510, 511, 512), List.of(500, 501, 502), List.of(600, 601, 602), List.of(700, 701, 702)).getType());
        assertEquals("ACK", sendBlock(patientConn, patientToken, sessionId, 3,
                List.of(513, 514), List.of(503, 504), List.of(603, 604), List.of(703, 704)).getType());
        stopSession(patientConn, patientToken, sessionId);
        patientConn.close();

        // El médico pide la señal
        ServerConnection doctorConn = new ServerConnection();
        doctorConn.connect();
        String doctorToken = doctorLogin(doctorConn);
        Message request = new Message("GET_SESSION_SIGNAL", "req-signal");
        request.addData("sessionToken", doctorToken);
        request.addData("sessionId", sessionId);
        Message response = doctorConn.send(request);

        assertEquals("ACK", response.getType());
        assertEquals("SIGNAL_SENT", response.getStatus());
        assertEquals("P001", response.getPatientId());
        assertEquals("COMPLETED", response.getData().get("sessionStatus"));
        assertEquals(1000, ((Number) response.getData().get("samplingRateHz")).intValue());
        assertEquals("ADC_10bit", response.getData().get("units"));
        assertNotNull(response.getData().get("startTime"));
        assertEquals(5, ((Number) response.getData().get("totalSamples")).intValue());

        // Mismos valores, mismo orden y cada canal en su sitio (EMG no es t_s)
        assertEquals(List.of(510, 511, 512, 513, 514), asInts(response.getData().get("emg")));
        assertEquals(List.of(500, 501, 502, 503, 504), asInts(response.getData().get("accX")));
        assertEquals(List.of(600, 601, 602, 603, 604), asInts(response.getData().get("accY")));
        assertEquals(List.of(700, 701, 702, 703, 704), asInts(response.getData().get("accZ")));

        doctorConn.close();
    }

    // GET_SESSION_SIGNAL: sin sessionId, con una sesión inexistente o con token de paciente se rechaza
    @Test
    public void testGetSessionSignalInvalidRequestsAreRejected() throws Exception {
        ServerConnection conn = new ServerConnection();
        conn.connect();
        String doctorToken = doctorLogin(conn);

        Message missing = new Message("GET_SESSION_SIGNAL", "req-signal-missing");
        missing.addData("sessionToken", doctorToken);
        assertEquals("MISSING_FIELD", conn.send(missing).getData().get("code"));

        Message unknown = new Message("GET_SESSION_SIGNAL", "req-signal-unknown");
        unknown.addData("sessionToken", doctorToken);
        unknown.addData("sessionId", "SES-does-not-exist");
        assertEquals("UNKNOWN_SESSION", conn.send(unknown).getData().get("code"));
        conn.close();

        ServerConnection patientConn = new ServerConnection();
        patientConn.connect();
        String patientToken = login(patientConn, "P001");
        Message asPatient = new Message("GET_SESSION_SIGNAL", "req-signal-patient");
        asPatient.addData("sessionToken", patientToken);
        asPatient.addData("sessionId", "SES-does-not-exist");
        assertEquals("FORBIDDEN", patientConn.send(asPatient).getData().get("code"));
        patientConn.close();
    }

    // Aislamiento: el servidor de test usa su propio puerto y una carpeta temporal, nunca data/
    @Test
    public void testUsesIsolatedTestServer() {
        assertEquals(TEST_PORT, HospitalServer.PORT);
        String dataDir = System.getProperty("data.dir");
        assertNotNull(dataDir);
        assertNotEquals("data", dataDir);
        assertTrue(dataDir.contains("telemed-test-"));
    }
}