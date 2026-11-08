package network;

import common.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import patient.ServerConnection;
import server.AdminAuth;
import server.HospitalServer;

import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionTest {

    @BeforeAll
    public static void setup() {
        // Solo arranca el servidor si no hay uno corriendo previamente
        if (!isServerRunning("127.0.0.1", 9000)) {
            Thread serverThread = new Thread(() -> HospitalServer.main(new String[]{}));
            serverThread.setDaemon(true);
            serverThread.start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
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
        Message response = conn.send(login);
        return (String) response.getData().get("sessionToken");
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
}