ackage network;

import common.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import patient.ServerConnection;
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
}