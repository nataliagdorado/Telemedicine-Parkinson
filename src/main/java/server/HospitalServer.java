package server;

import common.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main hospital server that accepts incoming TCP connections from patients and doctors.
 * Uses a thread pool to manage concurrency and tracks connected clients.
 */
public class HospitalServer {

    private static final Logger LOGGER = Logger.getLogger(HospitalServer.class.getName());
    private static final int PORT = Config.getInt("server.port", 9000);

    // Thread-safe registry to track active clients for graceful shutdown
    public static final Set<ClientHandler> activeClients = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        LOGGER.info("Starting Hospital Server on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            LOGGER.info("Server is listening for incoming connections.");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("New connection accepted from: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                threadPool.execute(handler);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Server socket exception occurred", ex);
        } finally {
            threadPool.shutdown();
            LOGGER.info("Server thread pool has been shut down.");
        }
    }
}