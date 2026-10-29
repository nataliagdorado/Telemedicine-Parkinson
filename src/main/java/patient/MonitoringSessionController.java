//Control del Ciclo de vida de la toma de datos ejecutandose en un hilo secundario
//Lee bloques periodicamente desde SignalSource y va construyendo un objeto MonitoringSystem
//Ejecución de la toma de datos de los sensores en segundo plano--> Garantía de que la interfaz gráfica del paciente no se congele
// durabte el tiempo qeu dure la prueba de monitorización (CONCURRENCIA)
package patient;

import common.SampleBlock;
import common.SessionInfo;
import patient.bitalino.*;
import server.data.MonitoringSession;

import java.time.LocalDateTime;

public class MonitoringSessionController implements Runnable{
    private final SignalSource signalSource;//SIMULATED O BITALINO
    private final String patientId;
    private final String sessionId;
    private final int durationSeconds;
    private final int samplingRateHz;
    private final int[] channels;
    //Estado interno y sesion resultante
    private MonitoringSession activeSession;
    private volatile boolean running;

    public MonitoringSessionController(SignalSource signalSource, String patientId, String sessionId, int durationSeconds, int samplingRateHz, int[] channels) {
        this.signalSource = signalSource;
        this.patientId = patientId;
        this.sessionId = sessionId;
        this.durationSeconds = durationSeconds;
        this.samplingRateHz = samplingRateHz;
        this.channels = channels;
        this.running = false;
    }
    @Override
    public void run() {
        //Intenta iniciar la adquisicion en la fuente de señal (BITALINO O SIMULADOR)
        if (!signalSource.startAcquisition(channels)) {
            System.err.println("Error iniciando la adquisición en el controlador de sesión.");
            return;
        }
        //Inicializa el objeto de sesion con los metadatos y la hora de inciio
        SessionInfo info = new SessionInfo(sessionId, patientId, LocalDateTime.now(), samplingRateHz, channels);
        activeSession = new MonitoringSession(info, null);
        running = true;
        // Control del bucle
        int totalExpectedSamples = durationSeconds * samplingRateHz;
        int collectedSamples = 0;
        int blockSize = 100; // Bloques de 100 muestras

        //Bucle prinicipal para una lectura de muestras contínua
        while (running && collectedSamples < totalExpectedSamples) {
            SampleBlock block = signalSource.readBlock(blockSize, sessionId);
            if (block != null) {
                activeSession.addSampleBlock(block);//Añade a la sesion el bloque de muestras
                collectedSamples += block.getEmgSamples().size();
            }

            try {
                Thread.sleep(100); // Pausas de 100 ms para NO saturar la CPU
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        //Finalizacion de la grabacion limpia
        signalSource.stopAcquisition();
        LocalDateTime end = LocalDateTime.now();
        if (Thread.currentThread().isInterrupted()) {
            activeSession.getInfo().markIncomplete(end);   // el hilo se cortó
        } else {
            activeSession.getInfo().complete(end);         // fin de tiempo o botón "parar"
        }
        running = false;
    }

    //Cancelacion o detencion de la grabacion manual (BOTON GUI)
    public void cancelRecording() {
        this.running = false;
    }

    public MonitoringSession getActiveSession() {
        return activeSession;
    }
    //¿sigue en ejecuciÓn?
    public boolean isRunning() {
        return running;
    }
}