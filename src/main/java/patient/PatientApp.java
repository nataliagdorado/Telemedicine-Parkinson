package patient;

import common.Config;
import common.Message;
import common.SymptomReport;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point for the Patient application.
 *
 * CONTIENE EL MAIN
 * ServerConnection: Conecta con el servidor ( con reintento si es que no esta disponible)
 * LOGIN: identificacion del paciente (recibe un sessionToken)
 * MENU
 */
public class PatientApp {
    //Parametros de adquisicion leidos de config/config.properties (si falta alguno se usa el valor por defecto)
    private static final String SIGNAL_SOURCE = Config.get("signal.source", "simulated");   //"bitalino" (placa real) o "simulated"
    private static final String BITALINO_MAC = Config.get("bitalino.mac", "98:D3:31:FD:3B:8C"); //direccion MAC de la placa
    private static final int SAMPLING_RATE = Config.getInt("bitalino.fs", 1000);             //Hz: el BITalino admite 1, 10, 100 o 1000
    private static final int DURATION_SECONDS = Config.getInt("session.duration", 60);      //duracion maxima de cada sesion
    private static final int[] CHANNELS = parseChannels(Config.get("bitalino.channels", "0,1,2,3")); //A1 EMG, A2-A4 ACC X/Y/Z

    private final Scanner scanner=new Scanner(System.in);//El unico scanner qeu debe haber en toda la app sobre system in. Sera el que se le pase al formulario
    private final ServerConnection connection=new ServerConnection();
    private String patientId;
    private String sessionToken; //Token que se devuelve al servidor en el LOGIN y va en todos los mensajes posteriores
    private int requestCounter=0;

    public static void main(String[] args) {
        new PatientApp().start(); //Crea un objeto para evitar el static
    }

    private void start(){
        System.out.println("=== PARKINSON TELEMONITORING SYSTEM - Patient Application ===");
        System.out.println("Signal source: "+ SIGNAL_SOURCE+ "| "+ SAMPLING_RATE+ " Hz | session: "+ DURATION_SECONDS+ " s");

        try {

            while (true) {
                if (!connectWithRetry()) {//el paciente decide no reintentar la conexión por lo que sale sin excepción
                    System.out.println("Goodbye!");
                    return;
                }
                try {
                    if (login()) {
                        mainMenu();
                    }
                    return;

                } catch (IOException e) {//el servidor se cae a la mitad (p.ej. lo apaga el admin)
                    System.out.println("Connection with the server LOST: " + e.getMessage());
                    System.out.println("Your previously sent data is safe on the server. Please, restart the app.");
                    connection.close();
                    String answer = readLine("Reconnect with the server? (y/n): ");
                    if (answer == null || !answer.equalsIgnoreCase("y")) {
                        return;
                    }
                    //Prinicipio del while, nueva connexion y log in--> el TOKEN anterio ya no vale
                }
            }
        }finally {
            connection.close();
            System.out.println("Patient application closed");
        }
    }


    //CONEXIÓN Y LOGIN


    //1.Intento de conexion. Si el servidor no esta disponible, informa y deja reintentar
    private boolean connectWithRetry(){
        while (true){
            try {
                connection.connect();
                System.out.println("Connected to the hospital server.");
                return true;
            }catch (IOException e){//server apagado, o IP/puerto incorectos
                System.out.println("The hospital server is not available("+e.getMessage()+").");
                String answer=readLine("Retry? (y/n): ");
                if (answer == null || !answer.equalsIgnoreCase("y")) {
                    return false;
                }
            }
        }
    }

    //PEDIR EL ID DEL PACIENTE HASTA QUE EL SERVIDOR LO ACEPTA. DEVOLVERA FLASE SI EL PACIENTE ESCRIBE EXIT:
    private boolean login() throws IOException{
        while (true){
            String id=readLine("Introduce Patient ID, or \"exit\"): ");
            if (id == null || id.equalsIgnoreCase("exit")) {
                return false;
            }
            if (id.isBlank()) {   //validacion local: no se molesta al servidor con un ID vacio
                System.out.println("The patient ID cannot be empty.");
                continue;
            }

            Message login=new Message("LOGIN", nextRequestId());
            login.setPatientId(id);

            login.addData("role","PATIENT");
            Message response=send(login);

            if (isError(response)){

                System.out.println("Login rejected: "+ errorText(response));
                continue;
            }
            this.patientId=id;
            this.sessionToken=(String) response.getData().get("sessionToken");
            System.out.println("Welcome, "+patientId+".");
            return true;
        }
    }

    //MENU PRINCIPAL:
    private void mainMenu() throws IOException{
        while (true){
            System.out.println();
            System.out.println("1. Send symptom report");
            System.out.println("2. Start monitoring session (" + SIGNAL_SOURCE + ", " + DURATION_SECONDS + " s)");
            System.out.println("3. Exit");
            String option = readLine("Choose an option: ");
            if (option == null) {   //entrada cerrada: se sale como si hubiera elegido 3
                option = "3";
            }

            switch (option) {
                case "1":
                    sendSymptomReport();
                    break;
                case "2":
                    runMonitoringSession();
                    break;
                case "3":
                    logout();
                    return;
                default:
                    System.out.println("Invalid option. Write 1, 2 or 3.");   //entrada no valida: se rechaza y se vuelve a mostrar el menu
            }
        }
    }

    //Enviar y validar los sintomas:
    private void sendSymptomReport() throws IOException{
        SymptomFormController form= new SymptomFormController(scanner);
        SymptomReport report=form.askForReport(patientId);
        if(report==null){
            return;
        }
        Message msg=SymptomFormController.buildMessage(report, nextRequestId(),sessionToken);
        Message response=send(msg);
        if(isError(response)){
            System.out.println("The server rejected the report: "+errorText(response));
        }else{
            System.out.println("Report sent and stored correctly.");
        }
    }

    //Conexion con el dispositivo, pedir una sesion al servidor y graacion en un hilo aparte:
    private void runMonitoringSession() throws IOException {
        //a) Conectar la fuente de señal (BITalino real o simulador, segun config.properties)
        SignalSource source;
        if (SIGNAL_SOURCE.equalsIgnoreCase("bitalino")) {
            source = new BitalinoSignalSource();
            System.out.println("Connecting to BITalino " + BITALINO_MAC + "...");
        } else {
            source = new SimulatedSignalSource();
        }
        if (!source.connect(BITALINO_MAC, SAMPLING_RATE)) {   //placa apagada, fuera de alcance o MAC incorrecta
            System.out.println("Could not connect to the device. Check that it is on and paired, then try again.");
            return;   //no se ha pedido sesion al servidor, asi que no queda nada abierto
        }

        try {
            //b) Pedir al servidor que abra una sesion: el servidor asigna el sessionId
            Message start = new Message("START_MONITORING", nextRequestId());
            start.setPatientId(patientId);
            start.addData("sessionToken", sessionToken);
            start.addData("samplingRateHz", SAMPLING_RATE);
            start.addData("channels", toList(CHANNELS));
            start.addData("durationSeconds", DURATION_SECONDS);
            Message response = send(start);
            if (isError(response)) {
                System.out.println("The server could not start the session: " + errorText(response));
                return;
            }
            String sessionId = null;
            if(response.getData()!=null){// solo se lee la session ID si la respuesta realmente trae datos
                sessionId=(String) response.getData().get("sessionId");
            }
            if(sessionId==null){ // si la tespuesta no tiene sesionId no se graba
                System.out.println("The server did not assign a session identifier so the session was not started.");
                return;
            }

            //c) Lanzar la grabacion en un hilo secundario
            MonitoringSessionController controller = new MonitoringSessionController(source, connection,
                    sessionToken, patientId, sessionId, DURATION_SECONDS, SAMPLING_RATE, CHANNELS);
            Thread recordingThread = new Thread(controller, "recording-" + sessionId);
            recordingThread.start();

            //d) Este hilo (el principal) espera al Enter del paciente para parar.
            //   Si la sesion termina sola (tiempo o error), el controlador lo muestra y el Enter solo sirve para volver al menu
            readLine("");
            if (controller.isRunning()) {
                controller.cancelRecording();   //levanta la bandera: el hilo de grabacion termina en su siguiente vuelta
            }
            recordingThread.join();             //esperar a que el hilo cierre la sesion y envie STOP antes de seguir usando la conexion

            System.out.println("Session " + controller.getSessionInfo().getSessionId() + ": "
                    + controller.getEndReason() + ", " + controller.getSessionInfo().getDurationSeconds() + " s recorded.");
            if (controller.getEndReason() == MonitoringSessionController.EndReason.CONNECTIONLOST) {
                throw new IOException("the server stopped responding during the session");   //lo captura start()
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            source.disconnect();   //se libera el Bluetooth siempre, termine como termine la sesion
        }
    }

    //Opcion 3: avisar al servidor de que el paciente se va (si falla da igual: se cierra igualmente)
    private void logout() {
        Message logout = new Message("LOGOUT", nextRequestId());
        logout.setPatientId(patientId);
        logout.addData("sessionToken", sessionToken);
        try {
            send(logout);
        } catch (IOException ignored) {
        }
        System.out.println("Logged out.");
    }

    //UTILIDADES:

    private Message send(Message msg) throws IOException {
        try {
            return connection.send(msg);
        } catch (common.ProtocolException e) {
            throw new IOException("invalid response from the server", e);
        }
    }

    private boolean isError(Message response) {
        return "ERROR".equals(response.getType());
    }

    private String errorText(Message response) {
        if (response.getData() == null) {
            return "unknown error";
        }
        Object text = response.getData().get("message");
        if (text == null) {
            text = response.getData().get("error");
        }
        return text == null ? "unknown error" : text.toString();
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine().trim();
    }

    private String nextRequestId() {
        requestCounter++;
        return "req-" + requestCounter;
    }
    private static int[] parseChannels(String text) {
        try {
            String[] parts = text.split(",");
            int[] result = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            return result;
        } catch (NumberFormatException e) {
            System.out.println("Invalid bitalino.channels in config.properties, using 0,1,2,3");
            return new int[]{0, 1, 2, 3};
        }
    }

    //lISTA PARA QUE gSON LO ENVIE COMO INETGER A JSON
    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>();
        for (int v : values) {
            list.add(v);
        }
        return list;
    }



}


