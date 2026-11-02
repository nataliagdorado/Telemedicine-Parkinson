package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Servidor central multihilo para el sistema de telemedicina de Parkinson.
 * Gestiona conexiones TCP concurrentes, sesiones y el ciclo de vida de los sockets
 */
public class HospitalServer {

    // Instancia del logger para imprimir mensajes informativos1].
    private static final Logger LOGGER = Logger.getLogger(HospitalServer.class.getName());

    public static final int PORT = 9000;

    // Registro thread-safe para contabilizar y monitorizar los sockets fisicos de los clientes conectados
    public static final ClientRegistry clientRegistry = new ClientRegistry();

    // Gestor logico de usuarios autenticados; administra tokens y evita inicios de sesion duplicados
    public static final SessionManager sessionManager = new SessionManager();

    // Ref al coordinador responsable de detener el servidor y liberar recursos ordenadamente
    private static ShutdownCoordinator shutdownCoordinator;

    public static void main(String[] args) {
        // Notifica en la consola del servidor que el proceso de START ha comenzado
        LOGGER.info("Starting Hospital Server on port " + PORT + "...");

        // TRY-WITH-RESOURCES: Instancia el ServerSocket en el puerto 9000 y garantiza que se cierre
        // automaticamente al finalizar o si se produce una excepcion no recuperable
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Inicializa el coordinador de apagado pasandole el ServerSocket y la lista de clientes activos
            shutdownCoordinator = new ShutdownCoordinator(serverSocket, clientRegistry);

            // atrapar señales de apagado
            shutdownCoordinator.registerShutdownHook();

            // Crea la consola interactiva de administracion pasandole los registros y el coordinador
            AdminConsole adminConsole = new AdminConsole(clientRegistry, sessionManager, shutdownCoordinator);

            // Envuelve la consola en un hilo secundario y lo marca como Daemon para que no bloquee el cierre del programa
            Thread consoleThread = new Thread(adminConsole, "AdminConsole-Thread");
            consoleThread.setDaemon(true);

            // Inicia la ejecucion de la consola interactiva en paralelo al bucle
            consoleThread.start();

            LOGGER.info("Server listening for incoming connections on port " + PORT);

            // BUCLE: Continua aceptando clientes mientras el coordinador no reciba orden de parada.
            while (shutdownCoordinator.isRunning()) {

                // Bloquea la ejecucion temporalmente hasta que un cliente (paciente o doctor) solicita conexion TCP.
                Socket clientSocket = serverSocket.accept();

                // Encapsula el socket del cliente aceptado en un manejador independiente de tareas
                ClientHandler handler = new ClientHandler(clientSocket);

                // Dispara un hilo dedicado exclusivo para ese cliente; permite atender a multiples usuarios concurrentemente.
                new Thread(handler).start();
            }

        } catch (IOException e) {
            // Si el ServerSocket se cerro a traves del ShutdownCoordinator, es una salida limpia esperada
            if (shutdownCoordinator != null && !shutdownCoordinator.isRunning()) {
                LOGGER.info("ServerSocket closed gracefully.");
            } else {
                // Si la excepcion ocurrio de forma inesperada (por ejemplo, puerto ocupado), se registra el error
                LOGGER.warning("Server exception: " + e.getMessage());
            }
        }
    }

    // Metodo getter para que otras clases o tests puedan consultar o invocar el estado del coordinador de apagado
    public static ShutdownCoordinator getShutdownCoordinator() {
        return shutdownCoordinator;
    }
}