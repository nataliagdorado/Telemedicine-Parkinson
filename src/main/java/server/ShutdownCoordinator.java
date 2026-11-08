package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;
import server.data.RepositoryException;
import server.data.SignalStore;

/**
 * Requisito 8: Mechanism to close the server and release network resources.
 * Basado en el ciclo de vida de Sockets TCP y liberacion de puertos.
 */
public class ShutdownCoordinator {
    private static final Logger LOGGER = Logger.getLogger(ShutdownCoordinator.class.getName());

    private static final long CLIENTS_WAIT = 5000;

    private final ServerSocket serverSocket;
    private final ClientRegistry clientRegistry;
    private final SignalStore signalStore;
    private volatile boolean running = true;

    public ShutdownCoordinator(ServerSocket serverSocket, ClientRegistry clientRegistry, SignalStore signalStore) {
        this.serverSocket = serverSocket;
        this.clientRegistry = clientRegistry;
        this.signalStore = signalStore;
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

        // 3. Esperar a que cada ClientHandler acabe su finally (marca sus sesiones INCOMPLETE)
        waitForClients();

        // 4. Cerrar los CSV que sigan abiertos (de handlers que no llegaron a tiempo)
        if (signalStore != null) {
            try {
                signalStore.close();
                LOGGER.info("Signal files closed.");
            } catch (RepositoryException e) {
                LOGGER.log(Level.WARNING, "Error closing signal files: " + e.getMessage());
            }
        }

        LOGGER.info("Server shutdown completed cleanly.");
    }

    private void waitForClients(){
        long deadline = System.currentTimeMillis() + CLIENTS_WAIT;
        while(clientRegistry != null && clientRegistry.getActiveClientCount() > 0){
            if(System.currentTimeMillis() >= deadline){
                LOGGER.warning(clientRegistry.getActiveClientCount() + " client(s) did not finish in time");
                return;
            }
            try{
                Thread.sleep(100);
            }catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // se respeta la interrupción y se deja de esperar
                return;
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}