//Adquisicion con Hardware Real
//Implementa la Lectura real del dispositivo BITalino reutilizando las llamadas nativas (open. start, read, stop y close)
//Convierte los Frames[] en POJOS: SampleBlock

package patient;
import common.SampleBlock;
import patient.bitalino.*;
import java.util.ArrayList;
import java.util.List;

public class BitalinoSignalSource implements SignalSource{

    private final BITalino bitalino;
    private boolean connected;
    private boolean recording;
    private int[] activeChannels;

    public BitalinoSignalSource() {
        this.bitalino = new BITalino();
        this.connected = false;
        this.recording = false;
    }
    @Override
    public boolean connect(String macAddress, int samplingRate){
        try{
            bitalino.open(macAddress,samplingRate);
            this.connected=true;
            return true;
        }catch (BITalinoException e){
            System.err.println("Error connecting to BITalino: "+ e.getMessage());
            this.connected = false;
            return false;
        }
    }

    @Override
    public boolean startAcquisition(int[] channels) {
        if (!connected) {
            System.err.println("No se puede iniciar la captura: BITalino no está conectado.");
            return false;
        }
        try {
            this.activeChannels = channels;
            bitalino.start(channels);
            this.recording = true;
            return true;
        } catch (BITalinoException e) {
            System.err.println("Error al iniciar adquisición en BITalino: " + e.getMessage());
            return false;
        }
    }
    @Override
    public SampleBlock readBlock(int numberOfSamples, String sessionId) {
        if (!recording) {
            return null;
        }
        try {
            Frame[] frames = bitalino.read(numberOfSamples);
            if (frames == null || frames.length == 0) {
                return null;
            }

            List<Integer> emgSamples = new ArrayList<>();
            List<Integer> accX = new ArrayList<>();
            List<Integer> accY = new ArrayList<>();
            List<Integer> accZ = new ArrayList<>();

            for (Frame frame : frames) {
                // Asignación de canales según configuración de Párkinson:
                // Canal A1 (índice 0): EMG de rigidez muscular
                // Canales A2, A3, A4 (índices 1, 2, 3): Acelerómetro X, Y, Z
                if (activeChannels.length > 0) emgSamples.add(frame.analog[activeChannels[0]]);
                if (activeChannels.length > 1) accX.add(frame.analog[activeChannels[1]]);
                if (activeChannels.length > 2) accY.add(frame.analog[activeChannels[2]]);
                if (activeChannels.length > 3) accZ.add(frame.analog[activeChannels[3]]);
            }

            return new SampleBlock(sessionId, emgSamples, accX, accY, accZ);

        } catch (BITalinoException e) {
            System.err.println("Error al leer muestras de BITalino: " + e.getMessage());
            return null;
        }
    }
    @Override
    public void stopAcquisition() {
        if (recording) {
            try {
                bitalino.stop();
            } catch (BITalinoException e) {
                System.err.println("Error al detener la adquisición: " + e.getMessage());
            } finally {
                this.recording = false;
            }
        }
    }
    @Override
    public void disconnect() {
        stopAcquisition();
        if (connected) {
            try {
                bitalino.close();
            } catch (BITalinoException e) {
                System.err.println("Error al cerrar conexión con BITalino: " + e.getMessage());
            } finally {
                this.connected = false;
            }
        }
    }
    @Override
    public boolean isConnected() { return connected; }

    @Override
    public boolean isRecording() { return recording; }

}