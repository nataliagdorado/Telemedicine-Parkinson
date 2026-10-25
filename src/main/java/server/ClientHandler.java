package server;

import common.Message;
import common.MessageCodec;
import common.ProtocolException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dedicated worker thread that handles communication with a single connected client.
 */
public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        long threadId = Thread.currentThread().getId();
        HospitalServer.activeClients.add(this);
        LOGGER.info("[Thread " + threadId + "] Client session started for: " + socket.getRemoteSocketAddress());

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String jsonLine;

            // Continuously process incoming messages until connection terminates
            while ((jsonLine = reader.readLine()) != null) {
                try {
                    Message request = MessageCodec.decode(jsonLine);
                    LOGGER.info("[Thread " + threadId + "] Received: type=" + request.getType()
                            + ", requestId=" + request.getRequestId());

                    // Dispatch request to handle the specific command
                    Message response = processMessage(request);
                    writer.println(MessageCodec.encode(response));

                } catch (ProtocolException pe) {
                    LOGGER.warning("[Thread " + threadId + "] Protocol error: " + pe.getMessage());
                    Message errorResponse = new Message("ERROR", "none");
                    errorResponse.addData("code", "BAD_REQUEST");
                    errorResponse.addData("message", pe.getMessage());
                    writer.println(MessageCodec.encode(errorResponse));
                }
            }

            LOGGER.info("[Thread " + threadId + "] Client disconnected cleanly.");

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "[Thread " + threadId + "] Client connection error: " + ex.getMessage());
        } finally {
            HospitalServer.activeClients.remove(this);
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error closing client socket", ex);
            }
            LOGGER.info("[Thread " + threadId + "] Worker thread terminated.");
        }
    }

    /**
     * Evaluates incoming message types and returns an appropriate response.
     */
    private Message processMessage(Message request) {
        Message response = new Message("ACK", request.getRequestId());

        switch (request.getType()) {
            case "HELLO":
                response.addData("status", "CONNECTED");
                response.addData("protocolVersion", "1.0");
                break;

            case "SYMPTOM_REPORT":
                // Persistence layer call will be integrated here
                response.addData("status", "SAVED");
                response.addData("reportId", "REP-" + System.currentTimeMillis());
                break;

            case "SESSION_START":
                response.addData("status", "SESSION_INITIALIZED");
                response.addData("sessionId", "SES-" + System.currentTimeMillis());
                break;

            default:
                response.setType("ERROR");
                response.addData("code", "UNSUPPORTED_TYPE");
                response.addData("message", "Unknown message type: " + request.getType());
                break;
        }

        return response;
    }
}