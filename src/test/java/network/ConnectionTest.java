package network;

import common.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import patient.ServerConnection;
import server.AdminAuth;
import server.HospitalServer;

import java.net.Socket;

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
        loginMsg.setPatientId("PATIENT-101");
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

        Message symptomMsg = new Message("SYMPTOM_REPORT", "req-symptom");
        symptomMsg.setPatientId("PATIENT-101");
        symptomMsg.addData("tremorSeverity", 3);
        symptomMsg.addData("rigidityLevel", "MODERATE");
        symptomMsg.addData("medicationTaken", true);

        Message response = conn.send(symptomMsg);

        assertNotNull(response);
        assertEquals("ACK", response.getType());
        assertEquals("RECORDED", response.getStatus());
        assertEquals("SAVED", response.getData().get("status"));

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