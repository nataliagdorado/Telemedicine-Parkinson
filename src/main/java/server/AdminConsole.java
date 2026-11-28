package server;

import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Requisito 8: Interfaz de consola del servidor y mecanismo de parada interactiva.
 * Permite al administrador monitorizar el estado y cerrar el servidor ordenadamente
 * exigiendo autenticación mediante hash + salt y confirmación si hay clientes conectados.
 */
public class AdminConsole implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AdminConsole.class.getName());

    private final ClientRegistry clientRegistry;
    private final SessionManager sessionManager;
    private final ShutdownCoordinator shutdownCoordinator;
    private final String adminHash;
    private final String adminSalt;

    public AdminConsole(ClientRegistry clientRegistry,
                        SessionManager sessionManager,
                        ShutdownCoordinator shutdownCoordinator,
                        String adminHash,
                        String adminSalt) {
        this.clientRegistry = clientRegistry;
        this.sessionManager = sessionManager;
        this.shutdownCoordinator = shutdownCoordinator;
        this.adminHash = adminHash;
        this.adminSalt = adminSalt;
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
                    handleConsoleShutdown(scanner);
                    if (!shutdownCoordinator.isRunning()) {
                        return;
                    }
                    break;

                case "help":
                    System.out.println("Available commands:");
                    System.out.println("  status  - Displays active sockets and authenticated sessions");
                    System.out.println("  clients - Lists total connected client sockets");
                    System.out.println("  stop    - Authenticates admin and gracefully stops the server");
                    break;

                default:
                    if (!command.isEmpty()) {
                        System.out.println("Unknown command: '" + command + "'. Type 'help' for options.");
                    }
                    break;
            }
        }
    }

    private void handleConsoleShutdown(Scanner scanner) {
        System.out.print("Enter Admin Password to stop server cleanly: ");
        if (!scanner.hasNextLine()) {
            return;
        }
        String enteredPassword = scanner.nextLine();

        if (!AdminAuth.verifyPassword(enteredPassword, adminHash, adminSalt)) {
            LOGGER.warning("Failed shutdown attempt from AdminConsole (invalid password).");
            System.out.println("[AUTH FAILED] Invalid administrator password. Shutdown aborted.");
            return;
        }

        int activeClients = clientRegistry != null ? clientRegistry.getActiveClientCount() : 0;
        if (activeClients > 0) {
            System.out.print("WARNING: There are " + activeClients + " active client(s) connected. Shut down anyway? (y/n): ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String confirm = scanner.nextLine().trim();
            if (!confirm.equalsIgnoreCase("y") && !confirm.equalsIgnoreCase("s")) {
                System.out.println("Shutdown aborted by administrator.");
                return;
            }
        }

        System.out.println("Shutdown command authenticated from admin console. Stopping server...");
        shutdownCoordinator.stopServer();
        System.exit(0);
    }
}