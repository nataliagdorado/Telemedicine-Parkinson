package server;

import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Requisito 8: Interfaz de consola del servidor y mecanismo de parada interactiva.
 * Permite al administrador monitorizar el estado y cerrar el servidor ordenadamente.
 */
public class AdminConsole implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AdminConsole.class.getName());

    private final ClientRegistry clientRegistry;
    private final SessionManager sessionManager;
    private final ShutdownCoordinator shutdownCoordinator;

    public AdminConsole(ClientRegistry clientRegistry, SessionManager sessionManager, ShutdownCoordinator shutdownCoordinator) {
        this.clientRegistry = clientRegistry;
        this.sessionManager = sessionManager;
        this.shutdownCoordinator = shutdownCoordinator;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== HOSPITAL SERVER ADMIN CONSOLE READY ===");
        System.out.println("Commands available: [status], [clients], [stop], [help]");

        while (shutdownCoordinator.isRunning()) {
            if (!scanner.hasNextLine()) {
                break;
            }

            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "status":
                    System.out.println("[SERVER STATUS]");
                    System.out.println(" - Server port: " + HospitalServer.PORT);
                    System.out.println(" - Active TCP sockets: " + clientRegistry.getActiveClientCount());
                    System.out.println(" - Active patient/doctor sessions: " + sessionManager.getActiveSessionCount());
                    break;

                case "clients":
                    System.out.println("[CONNECTED CLIENTS]");
                    System.out.println(" - Total registered sockets: " + clientRegistry.getActiveClientCount());
                    break;

                case "stop":
                case "quit":
                case "exit":
                    System.out.println("Shutdown command received from admin console. Stopping server...");
                    shutdownCoordinator.stopServer();
                    return;

                case "help":
                    System.out.println("Available commands:");
                    System.out.println("  status  - Displays active sockets and authenticated sessions");
                    System.out.println("  clients - Lists total connected client sockets");
                    System.out.println("  stop    - Gracefully stops the server and frees network ports");
                    break;

                default:
                    if (!command.isEmpty()) {
                        System.out.println("Unknown command: '" + command + "'. Type 'help' for options.");
                    }
                    break;
            }
        }
    }
}