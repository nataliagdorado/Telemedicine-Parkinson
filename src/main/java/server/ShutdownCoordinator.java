package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Requisito 8: Mechanism to close the server and release network resources.
 * Basado en el ciclo de vida de Sockets TCP y liberacion de puertos.
 */
public class ShutdownCoordinator {
    private static final Logger LOGGER = Logger.getLogger(ShutdownCoordinator.class.getName());

    private final ServerSocket serverSocket;
    private final ClientRegistry clientRegistry;
    private volatile boolean running = true;

    public ShutdownCoordinator(ServerSocket serverSocket, ClientRegistry clientRegistry) {
        this.serverSocket = serverSocket;
        this.clientRegistry = clientRegistry;
    }

    /**
     * Registra el hook en la JVM para atrapar senales de parada (Ctrl+C, terminacion).
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "ShutdownCoordinator-Thread"));
    }

    /**
     * Cierra el ServerSocket y desconecta a todos los clientes activos ordenadamente.
     */
    public synchronized void stopServer() {
        if (!running) {
            return;
        }
        running = false;
        LOGGER.info("Stopping hospital server and releasing resources...");

        // 1. Cerrar ServerSocket para dejar de admitir conexiones
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                LOGGER.info("ServerSocket closed on port: " + serverSocket.getLocalPort());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing ServerSocket: " + e.getMessage());
            }
        }

        // 2. Cerrar sockets de clientes activos para liberar puertos
        if (clientRegistry != null) {
            for (ClientHandler client : clientRegistry.getConnectedClients()) {
                if (client != null) {
                    client.closeSocket();
                }
            }
        }

        LOGGER.info("Server shutdown completed cleanly.");
    }

    public boolean isRunning() {
        return running;
    }
}