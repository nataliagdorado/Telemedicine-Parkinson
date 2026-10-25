package patient.bitalino;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection; // <-- Proviene de BlueCove
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

public class BITalino {
    private StreamConnection connection = null;
    private DataInputStream iStream = null;
    private DataOutputStream oStream = null;

    private int samplingRate = 1000;
    private int[] analogChannels = null;
    private boolean isAcquiring = false;

    public BITalino() {
    }

    public Vector<RemoteDevice> findDevices() throws BITalinoException {
        try {
            LocalDevice localDevice = LocalDevice.getLocalDevice();
            DiscoveryAgent agent = localDevice.getDiscoveryAgent();
            RemoteDevice[] devices = agent.retrieveDevices(DiscoveryAgent.PREKNOWN);
            Vector<RemoteDevice> deviceList = new Vector<>();
            if (devices != null) {
                for (RemoteDevice device : devices) {
                    deviceList.add(device);
                }
            }
            return deviceList;
        } catch (Exception e) {
            throw new BITalinoException(BITalinoErrorTypes.BT_DEVICE_NOT_CONNECTED);
        }
    }

    public void open(String macAddress, int samplingRate) throws BITalinoException {
        if (connection != null) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_IDLE);
        }
        this.samplingRate = samplingRate;
        try {
            String url = "btspp://" + macAddress.replace(":", "").replace("-", "") + ":1;authenticate=false;encrypt=false;master=false";
            connection = (StreamConnection) Connector.open(url);
            iStream = connection.openDataInputStream();
            oStream = connection.openDataOutputStream();

            // Pausa de estabilización tras abrir la conexión
            Thread.sleep(2000);
        } catch (Exception e) {
            throw new BITalinoException(BITalinoErrorTypes.PORT_COULD_NOT_BE_OPENED);
        }
    }

    public void start(int[] channels) throws BITalinoException {
        if (connection == null) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_INITIALIZED);
        }
        if (isAcquiring) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_IDLE);
        }

        this.analogChannels = channels;
        int command = 0;

        // Configurar la frecuencia de muestreo mediante comandos de bitwise
        if (samplingRate == 1) command = 0x03;
        else if (samplingRate == 10) command = 0x43;
        else if (samplingRate == 100) command = 0x83;
        else if (samplingRate == 1000) command = 0xC3;

        try {
            oStream.write(command);
            oStream.flush();

            // Activar canales analógicos
            int channelBitmask = 0;
            for (int channel : channels) {
                channelBitmask |= (1 << channel);
            }
            int startCommand = (channelBitmask << 2) | 0x01;
            oStream.write(startCommand);
            oStream.flush();

            isAcquiring = true;
        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }
    }

    public Frame[] read(int numberOfSamples) throws BITalinoException {
        if (!isAcquiring) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_IN_ACQUISITION_MODE);
        }

        Frame[] frames = new Frame[numberOfSamples];
        int numChannels = analogChannels.length;

        // Cálculo del número de bytes por trama según el número de canales
        int bytesPerFrame = (numChannels <= 4) ? 3 : 4;
        byte[] buffer = new byte[numberOfSamples * bytesPerFrame];

        try {
            int totalRead = 0;
            while (totalRead < buffer.length) {
                int read = iStream.read(buffer, totalRead, buffer.length - totalRead);
                if (read == -1) break;
                totalRead += read;
            }

            // Decodificación binaria de cada trama obtenida
            for (int i = 0; i < numberOfSamples; i++) {
                frames[i] = new Frame();
                int offset = i * bytesPerFrame;

                // Extraer secuencia y canales digitales/analógicos del buffer
                frames[i].seq = (buffer[offset] & 0xF0) >> 4;
                frames[i].digital[0] = (buffer[offset] & 0x08) >> 3;
                frames[i].digital[1] = (buffer[offset] & 0x04) >> 2;
                frames[i].digital[2] = (buffer[offset] & 0x02) >> 1;
                frames[i].digital[3] = (buffer[offset] & 0x01);

                if (numChannels > 0) frames[i].analog[analogChannels[0]] = ((buffer[offset + 1] & 0xFF) << 2) | ((buffer[offset + 2] & 0xC0) >> 6);
                if (numChannels > 1) frames[i].analog[analogChannels[1]] = ((buffer[offset + 2] & 0x3F) << 4) | ((buffer[offset + 3] & 0xF0) >> 4);
                if (numChannels > 2) frames[i].analog[analogChannels[2]] = ((buffer[offset + 3] & 0x0F) << 6) | ((buffer[offset + 4] & 0xFC) >> 2);
                if (numChannels > 3) frames[i].analog[analogChannels[3]] = ((buffer[offset + 4] & 0x03) << 8) | (buffer[offset + 5] & 0xFF);
            }

            return frames;

        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }
    }

    public void stop() throws BITalinoException {
        if (!isAcquiring) {
            return;
        }
        try {
            oStream.write(0x00); // Comando de parada
            oStream.flush();
            isAcquiring = false;
        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }
    }

    public void close() throws BITalinoException {
        try {
            if (iStream != null) iStream.close();
            if (oStream != null) oStream.close();
            if (connection != null) connection.close();
        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        } finally {
            iStream = null;
            oStream = null;
            connection = null;
            isAcquiring = false;
        }
    }
}
