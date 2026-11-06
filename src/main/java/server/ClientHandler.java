package server;

import common.Message;
import common.MessageCodec;
import common.ProtocolException;

import server.data.Repository;
import server.data.SignalStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;
    private final CommandRouter router;

    public ClientHandler(Socket socket, Repository repository, SignalStore signalStore) {
        this.socket = socket;
        // Router con el SessionManager compartido del servidor (el que ve la AdminConsole) y la persistencia compartida
        this.router = new CommandRouter(HospitalServer.sessionManager, repository, signalStore);
    }

    @Override
    public void run() {
        long threadId = Thread.currentThread().getId();
        LOGGER.info("[Thread " + threadId + "] Client session started for: " + socket.getRemoteSocketAddress());

        //Añadida la siguiente línea después. Al arrancar para conectar ClientRegistry y ClientHandler
        HospitalServer.clientRegistry.register(this);
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)
        ) {
            String jsonLine;

            // Procesar mensajes entrantes hasta que se cierre la conexion
            while ((jsonLine = reader.readLine()) != null) {
                if (jsonLine.trim().isEmpty()) {
                    continue;
                }

                try {
                    Message request = MessageCodec.decode(jsonLine);
                    LOGGER.info("[Thread " + threadId + "] Received: type=" + request.getType()
                            + ", requestId=" + request.getRequestId());

                    // Despachar la peticion al CommandRouter desacoplado
                    Message response = router.route(request);
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

            // Conectar ClientRegistry en HospitalServer y ClientHandler
            HospitalServer.clientRegistry.unregister(this);
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

    public void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing client socket: " + e.getMessage());
        }
    }
}