//Adquisicion con Hardware Real
//Implementa la Lectura real del dispositivo BITalino reutilizando las llamadas nativas (open. start, read, stop y close)
//Convierte los Frames[] en POJOS: SampleBlock

package patient;
import common.SampleBlock;
import patient.bitalino.*;
import java.util.ArrayList;
import java.util.List;
import common.Config;
//Capa intermedia entre la placa BITalino y el resto de la app
//Captura las exepciones de Bitalino y la convierte en true, false o null que es lo que espera la interfaz
//Convierte los frames en SampleBlocks que es lo que se envía al servidor
public class BitalinoSignalSource implements SignalSource{
    private static final int REQUIRED_CHANNELS=4;

    private final BITalino bitalino;
    private boolean connected;
    private boolean recording;
    private int[] activeChannels;

    public BitalinoSignalSource() {
        this.bitalino = new BITalino();
        this.bitalino.setReadTimeoutMillis(Config.getInt("bitalino.readTimeoutMs",3000));
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
            System.err.println("[BITalino] Cannot start the acquisition: The device is not connected.");
            return false;
        }
        if(channels==null||channels.length!=REQUIRED_CHANNELS){
            System.out.println("[BITalino]bitalino.channels must contain excatly "+REQUIRED_CHANNELS + " channels (EMG, ACC X, ACC Y, ACC Z). Check config.properties.");
            return false;
        }
        try {
            this.activeChannels = channels.clone();//copia propia para que no afecte la modificacion del array externo
            bitalino.start(channels);// se le pasa la copia para que la placa y esta clase usen exactamente los mismos canales
            this.recording = true;
            return true;
        } catch (BITalinoException e) {
            System.out.println("[BITalino] Could not start the acquisition: "+ e.getType());
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

            List<Integer> emgSamples = new ArrayList<>(frames.length);
            List<Integer> accX = new ArrayList<>(frames.length);
            List<Integer> accY = new ArrayList<>(frames.length);
            List<Integer> accZ = new ArrayList<>(frames.length);

            for (Frame frame : frames) {
                // Asignación de canales según configuración de Párkinson:
                // Canal A1 (índice 0): EMG de rigidez muscular
                // Canales A2, A3, A4 (índices 1, 2, 3): Acelerómetro X, Y, Z
                emgSamples.add(frame.analog[activeChannels[0]]);
                accX.add(frame.analog[activeChannels[1]]);
                accY.add(frame.analog[activeChannels[2]]);
                accZ.add(frame.analog[activeChannels[3]]);
            }

            return new SampleBlock(sessionId, emgSamples, accX, accY, accZ);

        } catch (BITalinoException e) {
            System.out.println("[BITalino] Read error: " + e.getType());
            return null;
        }
    }
    @Override
    public void stopAcquisition() {
        if (recording) {
            try {
                bitalino.stop();
            } catch (BITalinoException e) {
                System.out.println("[BITalino] Could not send the stop comand" + e.getType());
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
                System.out.println("[BITalino] Error while closing the Bluetooth connection: " + e.getType());
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