//Control del Ciclo de vida de la toma de datos ejecutandose en un hilo secundario
//Lee bloques periodicamente desde SignalSource y va construyendo un objeto MonitoringSystem
//Ejecución de la toma de datos de los sensores en segundo plano--> Garantía de que la interfaz gráfica del paciente no se congele
// durabte el tiempo qeu dure la prueba de monitorización (CONCURRENCIA)
package patient;

import common.SampleBlock;
import common.SessionInfo;
import common.Message;


import java.time.LocalDateTime;
//Esta clase funciona con un hilo aparte para que el hilo principal de PatientApp quede libre esperando a que el paciente
//pulse ENTER para parar.

//El ciclo de esta clase es:
//1.Arranca el dispositivo
//2.Repite el bucle: lee el bloque de 100 muestras, lo envía al servidor como SAMPLE_BLOCK y comprueba si hay que parar
//3.Al terminar llama a finish() para que el dispositivo marque la sesion como completa o incompleta y envie al servidor STOPMONITORING

public class MonitoringSessionController implements Runnable {

    //USER, TIMEOUT Y DEVICE ERROR se envian al servidor en STOP MONITORING
    //CONNECTION LOST no se envia por que es el servidor el que no es alcanzable
    public enum EndReason {USER, TIMEOUT, DEVICEERROR, CONNECTIONLOST}

    private static final int BLOCK_SIZE = 100; //Muestras por bloque/mensaje--> 1000Hz son 100 ms de señal --> 10 mensajes por segundo
    private static final int MAX_CONSECUTIVE_FAILURES = 3; //lecturas fallidas seguidas toleradas antes de que el BITalino se de por desconectado
    private static final String UNITS = "ADC_10bit"; //Unidades de los valores en bruto del BITalino, luego se hace la conversion
    private final SignalSource signalSource;//SIMULATED O BITALINO
    private final ServerConnection connection; //socket TCP abierto con el servidor--> durante la sesion solo este hilo lo usa
    private final String sessionToken; //token que devuelve el servidor en el LOGIN que demuetsra la autentificaion del paciente
    private final String patientId;
    private final int durationSeconds;
    private final int samplingRateHz;
    private final int[] channels;

    //Estado interno y sesion resultante

    private final SessionInfo info;
    private volatile boolean cancelRequested;
    private volatile EndReason endReason;
    private int requestCounter = 0;
    private volatile boolean running;

    public MonitoringSessionController(SignalSource signalSource, ServerConnection connection, String sessionToken, String patientId, String sessionId, int durationSeconds, int samplingRateHz, int[] channels) {
        this.signalSource = signalSource;
        this.connection = connection;
        this.sessionToken = sessionToken;
        this.patientId = patientId;
        this.durationSeconds = durationSeconds;
        this.samplingRateHz = samplingRateHz;
        this.channels = channels;
        this.info = new SessionInfo(sessionId, patientId, null, samplingRateHz, channels);
        this.cancelRequested = false;
        this.running = false;
    }

    @Override
    public void run() {
        running = true;//se marca antes de arrancar el dispositivo para que si el paciente pulsa parar en ese instnate, la orden no se pierda

        try{

            //Intenta iniciar la adquisicion en la fuente de señal (BITALINO O SIMULADOR)
            if (!signalSource.startAcquisition(channels)) {
                System.out.println("[Monitoring] Could not start the acquisition. Check the device.");
                finish(EndReason.DEVICEERROR);//SE CIERRA DE FORMA ORDENADA Y SE AVISA AL SERVIDOR PARA QUE NO QUEE LA SESION ABIERTA
                return;
            }
            info.setStartTime(LocalDateTime.now()); //hora de inicio de la grabacion, ahora
            System.out.println("[Monitoring] Recording started (" + durationSeconds + " s). Press Enter to stop.");

            long totalExpectedSamples = (long) durationSeconds * samplingRateHz;
            long collectedSamples = 0;       //muestras ya enviadas al servidor; tambien sirve como numero de secuencia (firstSeq) del siguiente bloque
            int consecutiveFailures = 0;     //lecturas fallidas seguidas; vuelve a 0 con cada lectura correcta
            EndReason reason = EndReason.TIMEOUT;   //valor por defecto: si el bucle termina por su condicion, es que se cumplio la duracion

            //Bucle principal: leer un bloque, enviarlo y repetir hasta tener todas las muestras previstas
            while (collectedSamples < totalExpectedSamples) {
                //1) ¿Ha pedido el paciente parar? Se comprueba al inicio de cada vuelta (como mucho tarda un bloque, ~100 ms, en reaccionar)
                if (cancelRequested || Thread.currentThread().isInterrupted()) {   //isInterrupted: por si PatientApp interrumpe el hilo al cerrarse
                    reason = EndReason.USER;
                    break;
                }

                //2) Leer un bloque: con el BITalino real esta llamada BLOQUEA hasta tener BLOCK_SIZE muestras (por eso no hace falta sleep)
                SampleBlock block = signalSource.readBlock(BLOCK_SIZE, info.getSessionId());

                //3) Lectura fallida: readBlock devuelve null si hay error de Bluetooth; puede ser un fallo puntual o una desconexion
                if (block == null || block.getEmgSamples() == null || block.getEmgSamples().isEmpty()) {
                    consecutiveFailures++;
                    System.out.println("[Monitoring] Read failed (" + consecutiveFailures + "/" + MAX_CONSECUTIVE_FAILURES + ")");
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {   //varios fallos seguidos = la placa se ha apagado o se ha perdido el Bluetooth
                        System.out.println("[Monitoring] The BITalino seems to be disconnected. Stopping the session.");
                        reason = EndReason.DEVICEERROR;
                        break;   //sale del bucle; sin esto se quedaria reintentando para siempre (el bug de la version anterior)
                    }
                    continue;   //vuelve al principio del while e intenta leer otra vez
                }
                consecutiveFailures = 0;   //lectura correcta: se reinicia el contador, asi un fallo aislado no acumula

                //4) Enviar el bloque al servidor; si falla, no tiene sentido seguir grabando datos que no se van a guardar
                if (!sendBlock(block, collectedSamples)) {
                    reason = EndReason.CONNECTIONLOST;
                    break;
                }
                collectedSamples += block.getEmgSamples().size();   //se suman las muestras realmente recibidas (puede ser menos de BLOCK_SIZE)
            }

            finish(reason);   //cierre controlado comun a todas las formas de terminar
    }catch (RuntimeException e){
            System.out.println("[Monitoring]Unexpected acquisition error: "+e);
            if(endReason==null){
                finish(EndReason.DEVICEERROR);
            }
        }finally {
            running=false;
        }
    }

    //Construye y envia un SAMPLE_BLOCK. Devuelve false si se pierde la conexion o el servidor rechaza el bloque
    private boolean sendBlock(SampleBlock block, long firstSeq) {
        Message msg = new Message("SAMPLE_BLOCK", nextRequestId());   //tipo de comando del protocolo + id unico para emparejar la respuesta
        msg.setPatientId(patientId);                     //patientId fuera de data, como el resto de mensajes
        msg.addData("sessionToken", sessionToken);       //el servidor rechaza (UNAUTHENTICATED) si falta o no es valido
        msg.addData("sessionId", info.getSessionId());   //a que sesion pertenece el bloque -> el servidor lo escribe en el CSV de esa sesion
        msg.addData("firstSeq", firstSeq);               //numero de la primera muestra del bloque: tiempo de la muestra i = inicio + (firstSeq + i) / fs
        msg.addData("samplingRateHz", samplingRateHz);   //referencia temporal: necesaria para convertir numero de muestra en segundos
        msg.addData("units", UNITS);                     //unidades de los valores (requisito: los datos deben indicar sus unidades)
        msg.addData("emg", block.getEmgSamples());       //canal A1: EMG (rigidez muscular)
        msg.addData("accX", block.getAccX());            //canal A2: acelerometro eje X (temblor)
        msg.addData("accY", block.getAccY());            //canal A3: acelerometro eje Y
        msg.addData("accZ", block.getAccZ());            //canal A4: acelerometro eje Z

        try {
            Message response = connection.send(msg);   //envia el JSON + '\n' y espera la respuesta del servidor (bloquea hasta recibirla)
            if ("ERROR".equals(response.getType())) {  //el servidor ha recibido el bloque pero no lo acepta (sesion desconocida, token invalido, etc.)
                System.out.println("[Monitoring] The server rejected the data: " + response.getData());
                return false;
            }
            return true;   //ACK: el bloque esta guardado en el servidor
        } catch (
                Exception e) {   //IOException (conexion caida, servidor apagado) o ProtocolException (respuesta mal formada)
            System.out.println("[Monitoring] Connection with the server lost: " + e.getMessage());
            return false;
        }
    }

    //Cierre controlado: parar el dispositivo, cerrar la sesion y avisar al servidor
    private void finish(EndReason reason) {
        signalSource.stopAcquisition();   //para el envio de muestras de la placa pero mantiene el Bluetooth abierto para otra sesion
        this.endReason = reason;          //se guarda para que PatientApp pueda consultar como termino (getEndReason)

        LocalDateTime end = LocalDateTime.now();   //hora de fin de la sesion
        if (info.getStartTime() == null) {
            info.setStartTime(end);   //no llego a empezar (fallo en startAcquisition): inicio = fin, para que SessionInfo acepte la hora de fin
        }
        if (reason == EndReason.USER || reason == EndReason.TIMEOUT) {
            info.complete(end);        //fin de tiempo o boton "parar": ambos son finales validos segun el enunciado -> COMPLETED
        } else {
            info.markIncomplete(end);  //desconexion del BITalino o del servidor: los datos recibidos se conservan pero la sesion queda INCOMPLETE
        }

        //Si la conexion con el servidor sigue viva, se le avisa del fin de la sesion para que cierre el CSV y guarde la hora de fin
        if (reason != EndReason.CONNECTIONLOST) {   //con la conexion caida no se intenta: fallaria seguro (el servidor la marcara INCOMPLETE por su lado)
            Message stop = new Message("STOP_MONITORING", nextRequestId());
            stop.setPatientId(patientId);
            stop.addData("sessionToken", sessionToken);
            stop.addData("sessionId", info.getSessionId());   //que sesion se cierra
            stop.addData("reason", reason.name());            //"USER", "TIMEOUT" o "DEVICE_ERROR": el servidor decide con esto COMPLETED o INCOMPLETE
            try {
                connection.send(stop);
            } catch (Exception e) {   //si falla aqui, la sesion local ya esta cerrada; solo se informa al paciente
                System.out.println("[Monitoring] Could not notify the end of the session: " + e.getMessage());
            }
        }

        System.out.println("[Monitoring] Session finished: " + reason + " (" + info.getStatus() + ")");
        running = false;   //ultimo paso: a partir de aqui isRunning() devuelve false y PatientApp sabe que el hilo ha terminado
    }

    //Genera ids de peticion unicos dentro de la sesion, ej. "SES-3f2a...-1", "SES-3f2a...-2"...
    private String nextRequestId() {
        requestCounter++;
        return info.getSessionId() + "-" + requestCounter;
    }

    //Detencion manual de la grabacion (Enter en consola o boton de la GUI). La llama OTRO hilo, no el de la grabacion:
    //solo levanta la bandera; el bucle de run() la ve en su siguiente vuelta y termina de forma ordenada
    public void cancelRecording() {
        this.cancelRequested = true;
    }

    //Metadatos de la sesion (id, inicio, fin, estado) para mostrarlos al paciente o guardarlos
    public SessionInfo getSessionInfo() {
        return info;
    }

    //Motivo de fin (null mientras sigue grabando)
    public EndReason getEndReason() {
        return endReason;
    }

    //¿sigue en ejecucion? PatientApp lo puede usar para esperar a que termine la grabacion
    public boolean isRunning() {
        return running;
    }
}

