package server;

import common.Config;
import server.data.FileRepository;
import server.data.FileSignalStore;
import server.data.Repository;
import server.data.RepositoryException;
import server.data.SeedData;
import server.data.SignalStore;
import server.data.ServerAdminFrame;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.SessionInfo;
import common.SessionStatus;
import server.data.Patient;

import javax.swing.*;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Servidor central multihilo para el sistema de telemedicina de Parkinson.
 * Gestiona conexiones TCP concurrentes, sesiones y el ciclo de vida de los sockets
 */
public class HospitalServer {

    // Instancia del logger para imprimir mensajes informativos1].
    private static final Logger LOGGER = Logger.getLogger(HospitalServer.class.getName());

    public static final int PORT = Config.getInt("server.port", 9000);

    // Carpeta donde se guardan en disco pacientes, síntomas, sesiones y señales (sobreviven al reinicio del servidor)
    private static final Path DATA_DIR = Path.of(Config.get("data.dir", "data"));

    // Registro thread-safe para contabilizar y monitorizar los sockets fisicos de los clientes conectados
    public static final ClientRegistry clientRegistry = new ClientRegistry();

    // Gestor logico de usuarios autenticados; administra tokens y evita inicios de sesion duplicados
    public static final SessionManager sessionManager = new SessionManager();

    // Ref al coordinador responsable de detener el servidor y liberar recursos ordenadamente
    private static ShutdownCoordinator shutdownCoordinator;

    public static void main(String[] args) {

        // Persistencia: UNA sola instancia compartida por todos los ClientHandler.
        // Si cada hilo creara la suya, el synchronized de cada una solo protegeria su propia copia del fichero
        Repository repository;
        SignalStore signalStore;
        try {
            repository = new FileRepository(DATA_DIR);
            signalStore = new FileSignalStore(DATA_DIR.resolve("signals"));
            int added = SeedData.loadIfMissing(repository);   // antes del accept(): aun solo existe el hilo main
            LOGGER.info("Data in " + DATA_DIR.toAbsolutePath() + " (" + added + " seed patients added)");

            int recovered = recoverInterruptedSessions(repository);
            if(recovered > 0){
                LOGGER.info(recovered + " interrupted session(s) from a previous run marked INCOMPLETE");
            }
        } catch (RepositoryException e) {
            LOGGER.log(Level.SEVERE, "Could not prepare persistence: server will not start", e);
            return;   // sin persistencia no tiene sentido aceptar pacientes
        }

        // Notifica en la consola del servidor que el proceso de START ha comenzado
        LOGGER.info("Starting Hospital Server on port " + PORT + "...");

        // TRY-WITH-RESOURCES: Instancia el ServerSocket en el puerto 9000 y garantiza que se cierre
        // automaticamente al finalizar o si se produce una excepcion no recuperable
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Inicializa el coordinador de apagado pasandole el ServerSocket y la lista de clientes activos
            shutdownCoordinator = new ShutdownCoordinator(serverSocket, clientRegistry, signalStore);

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
            String adminPassword = Config.get("admin.password", "admin123");
            SwingUtilities.invokeLater(() -> {
                ServerAdminFrame adminFrame = new ServerAdminFrame(
                        PORT,
                        clientRegistry,
                        repository,
                        shutdownCoordinator,
                        adminPassword
                );
                adminFrame.setVisible(true);
            });
            // BUCLE: Continua aceptando clientes mientras el coordinador no reciba orden de parada.
            while (shutdownCoordinator.isRunning()) {

                // Bloquea la ejecucion temporalmente hasta que un cliente (paciente o doctor) solicita conexion TCP.
                Socket clientSocket = serverSocket.accept();

                // Encapsula el socket del cliente aceptado en un manejador independiente de tareas
                ClientHandler handler = new ClientHandler(clientSocket, repository, signalStore);

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
        // main no termina hasta que el apagado haya acabado:
        // stopServer es synchronized, así que espera a que la consola termine el suyo.
        // Si el bucle terminó por un error inesperado, aquí se hace el apagado completo.
        if (shutdownCoordinator != null) {
            shutdownCoordinator.stopServer();
        }
    }

    /**
     * Función que ve al arrancar toda sesión que esté en RECORDING
     * Significa que es de una sesión anterior, Se marca INCOMPLETE
     */

    private static int recoverInterruptedSessions (Repository repository) throws RepositoryException{
        int recovered = 0;
        for (Patient patient : repository.findAllPatients()){
            for (SessionInfo info : repository.findSessions(patient.getPatientId())) {
                if (info.getStatus() != SessionStatus.RECORDING) {
                    continue;   // ya terminada (COMPLETED o INCOMPLETE): no se toca
                }
                info.markIncomplete(lastWriteTime(info));
                repository.updateSession(info);
                recovered++;
                LOGGER.info("Session " + info.getSessionId() + " was left RECORDING: marked INCOMPLETE");
            }
        }
        return recovered;
    }

    /**
     *Hora de la última escritura del CSV de la sesión. Lo utilizamos para cierres abruptos del server
     */
    private static LocalDateTime lastWriteTime(SessionInfo info){
        LocalDateTime end = info.getStartTime();
        if(info.getSignalFile() != null){
            try{
                Instant modified = Files.getLastModifiedTime(Path.of(info.getSignalFile())).toInstant();
                end = LocalDateTime.ofInstant(modified, ZoneId.systemDefault());
            }catch (IOException e) {
                LOGGER.warning("Could not read " + info.getSignalFile() + ": using start time as end time");
            }
        }
        if (end.isBefore(info.getStartTime())){
            end = info.getStartTime();
        }
        return end;
    }

    // Metodo getter para que otras clases o tests puedan consultar o invocar el estado del coordinador de apagado
    public static ShutdownCoordinator getShutdownCoordinator() {
        return shutdownCoordinator;
    }
}