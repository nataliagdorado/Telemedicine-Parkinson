package server;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registro thread-safe de conexiones cliente activas en el servidor TCP.
 */
public class ClientRegistry {
    private static final Logger LOGGER = Logger.getLogger(ClientRegistry.class.getName());

    // Conjunto concurrente de clientes conectados
    private final Set<ClientHandler> connectedClients = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void register(ClientHandler client) {
        if (client != null) {
            connectedClients.add(client);
            LOGGER.info(() -> "Client registered in registry. Active sockets: " + connectedClients.size());
        }
    }

    public void unregister(ClientHandler client) {
        if (client != null) {
            connectedClients.remove(client);
            LOGGER.info(() -> "Client removed from registry. Active sockets: " + connectedClients.size());
        }
    }

    public int getActiveClientCount() {
        return connectedClients.size();
    }

    public Set<ClientHandler> getConnectedClients() {
        return Collections.unmodifiableSet(connectedClients);
    }
}