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
    private long readTimeoutMillis=3000;//Tiempo maximo sin recibir datos durante read() (3 segundos)
    private long commandPauseMillis = 1000;  //pausa tras cada comando enviado a la placa

    //Maximo de bytes que se descartan intentando resincronizar tras una trama corrupta (10 tramas de 7 bytes)
    private static final int MAX_RESYNC_BYTES = 70;

    public BITalino() {
    }
    public void setReadTimeoutMillis(long millis){
        this.readTimeoutMillis= Math.max(0,millis); //se queda con el numero mayor entre 0 y el que se ponga en el setter para que nunca se pueda establecer un numero negativo
    }
    public void setCommandPauseMillis(long millis){
        this.commandPauseMillis=Math.max(0,millis);
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
        if(macAddress==null||macAddress.isBlank()){
            throw new BITalinoException(BITalinoErrorTypes.INVALID_PARAMETER);
        }
        String mac = macAddress.trim().replace(":", "").replace("-", "");
        if (mac.length() != 12) {   //una MAC tiene 6 bytes = 12 cifras hexadecimales
            throw new BITalinoException(BITalinoErrorTypes.INVALID_PARAMETER);
        }
        int rateCode = samplingRateCode(samplingRate);   //lanza INVALID_PARAMETER si no es 1, 10, 100 o 1000
        this.samplingRate = samplingRate;

        try {
            connection=(StreamConnection)Connector.open("btspp://" + mac + ":1", Connector.READ_WRITE);
            iStream = connection.openDataInputStream();
            oStream = connection.openDataOutputStream();

            // Pausa de estabilización tras abrir la conexión
            Thread.sleep(2000);

            sendCommand((rateCode<<6)|0x03);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeQuietly();
            throw new BITalinoException(BITalinoErrorTypes.PORT_COULD_NOT_BE_OPENED);
        }catch (Exception e){
            closeQuietly();//si falló a la mitad
            throw new BITalinoException(BITalinoErrorTypes.PORT_COULD_NOT_BE_OPENED);
        }
    }
    //Codifo de 2 bits de cada frequencia admitida por BITalino
    private static int samplingRateCode(int samplingRate) throws BITalinoException {
        switch (samplingRate) {
            case 1:    return 0;
            case 10:   return 1;
            case 100:  return 2;
            case 1000: return 3;
            default:   throw new BITalinoException(BITalinoErrorTypes.INVALID_PARAMETER);   //el BITalino solo admite estas 4
        }
    }
    //envía un comando de 1 byte a la placa
    private void sendCommand(int command) throws IOException, InterruptedException {
        oStream.write(command);
        oStream.flush();
        if (commandPauseMillis > 0) {
            Thread.sleep(commandPauseMillis);
        }
    }

    public void start(int[] channels) throws BITalinoException {
        if (connection == null) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_INITIALIZED);
        }
        if (isAcquiring) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_IDLE);
        }
        //La aplicacion solo trabaja con 1 a 4 canales (A1-A4), todos de 10 bits
        if (channels == null || channels.length < 1 || channels.length > 4) {
            throw new BITalinoException(BITalinoErrorTypes.INVALID_PARAMETER);
        }
        for (int channel : channels) {
            if (channel < 0 || channel > 3) {   //0 = A1 ... 3 = A4
                throw new BITalinoException(BITalinoErrorTypes.INVALID_PARAMETER);
            }
        }

        this.analogChannels = channels.clone();
        try {
            // Activar canales analógicos: un bit por canal, desplazado 2 posiciones, y 01 = "start en modo live"
            int channelBitmask = 0;
            for (int channel : analogChannels) {
                channelBitmask |= (1 << channel);
            }
            int startCommand = (channelBitmask << 2) | 0x01;
            sendCommand(startCommand);

            isAcquiring = true;
        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.LOST_COMUNICATION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }
    }

    //este metodo devuelve una muestra con todos los valores de todos los canales en un mismo instante
    public Frame[] read(int numberOfSamples)throws BITalinoException{
        if (!isAcquiring) {
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_IN_ACQUISITION_MODE);
        }

        int numChannels = analogChannels.length;
        int bytesPerFrame = frameSize(numChannels);
        byte[] frameBytes = new byte[bytesPerFrame];   //los bytes de UNA trama (7 con 4 canales)
        Frame[] frames = new Frame[numberOfSamples];

        for (int i = 0; i < numberOfSamples; i++) {
            readFully(frameBytes, 0, bytesPerFrame);
            Frame f = decodeFrame(frameBytes, 0, bytesPerFrame, numChannels);

            //CRC incorrecto: la lectura se ha desalineado (se perdio o sobro algun byte por el Bluetooth).
            //Igual que la API Java del curso, se resincroniza: se descarta el byte mas antiguo, se lee uno nuevo
            //y se vuelve a comprobar, hasta encontrar de nuevo el principio de una trama valida.
            int discarded = 0;
            while (f.seq < 0) {
                if (discarded >= MAX_RESYNC_BYTES) {   //no aparece ninguna trama valida: los datos no son del BITalino
                    throw new BITalinoException(BITalinoErrorTypes.INVALID_DATA);
                }
                System.arraycopy(frameBytes, 1, frameBytes, 0, bytesPerFrame - 1);   //todo se mueve 1 posicion a la izquierda
                readFully(frameBytes, bytesPerFrame - 1, 1);                         //el byte nuevo entra por el final
                discarded++;
                f = decodeFrame(frameBytes, 0, bytesPerFrame, numChannels);
            }
            frames[i] = f;
        }
        return frames;
    }
    private void readFully(byte[] buf, int off, int len) throws BITalinoException {
        try {
            int totalRead = 0;
            long lastDataTime = System.currentTimeMillis();   //momento en que llegaron datos por ultima vez
            while (totalRead < len) {
                //Si no hay bytes esperando, no se llama a read() (se quedaria bloqueado si la placa ya no envia nada):
                //se espera un poco y se comprueba si se ha superado el tiempo maximo sin datos
                if (readTimeoutMillis > 0 && iStream.available() == 0) {
                    if (System.currentTimeMillis() - lastDataTime > readTimeoutMillis) {
                        throw new BITalinoException(BITalinoErrorTypes.READ_TIMEOUT);
                    }
                    Thread.sleep(2);   //a 1000 Hz llegan ~7 bytes/ms: 2 ms de espera no pierde datos (quedan en el buffer del Bluetooth)
                    continue;
                }
                int read = iStream.read(buf, off + totalRead, len - totalRead);
                if (read == -1) {   //fin de stream = el BITalino se ha apagado o se ha perdido el Bluetooth
                    throw new BITalinoException(BITalinoErrorTypes.LOST_COMUNICATION);
                }
                totalRead += read;
                lastDataTime = System.currentTimeMillis();   //han llegado datos: se reinicia la cuenta del tiempo maximo
            }
        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.LOST_COMUNICATION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   //se conserva la marca de interrupcion para que el controlador la vea y pare
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }
    }

    //Bytes por trama segun eñ numero de canales analogicos activos
    static int frameSize(int numChannels) {
        return (int) Math.ceil((12.0 + 10.0 * numChannels) / 8.0);
        //cada trma tiene 12 bits fijos mas 10 bits por canal analogico / 8 pasar de bits a bytes
        //math.ceil redondea hacia arriba
    }

    //Decodifica una trama
    //Comprueba el CRC--> si falla (seq=-1)--> read() intenta resincronizar
    //Saca el seq y las entradas digitales
    //Saca el A1...A4-->frame-analog[canal]
    //Devuelve Frame[100]
    //*crc= deteccion de corrupcion de tramas (interferencias)
    //Funcionalidad del CRC: La placa al enviar prepara la trama con los datos  y calcula el CRC (lo mete en el ultimo byte)
    //envia la trama por bluetooth
    //el programa recibe la trama, saca el CRC que mando la placa y vuelve a calcular el CRC con lso datos recibidos
    //el programa los compara, si son iguales, la trama es correcta, si son distintos, es incorrecta.
    private Frame decodeFrame(byte[] buf, int offset, int size, int numChannels) {
        Frame f = new Frame();//crea un frame vacío
        int j = offset + size - 1;   //ultimo byte de la trama--> lectura del finla hacia atras= orden


        int crc = buf[j] & 0x0F;
        int x = 0;
        for (int i = 0; i < size; i++) {
            int b = buf[offset + i] & 0xFF;
            if (i == size - 1) {
                b &= 0xF0;
            }
            for (int bit = 7; bit >= 0; bit--) {
                x <<= 1;
                if ((x & 0x10) != 0) {
                    x ^= 0x03;
                }
                x ^= (b >> bit) & 0x01;
            }
        }
        boolean crcOk = (x & 0x0F) == crc;

        f.seq = crcOk ? ((buf[j] & 0xF0) >> 4) : -1;
        f.digital[0] = (buf[j - 1] >> 7) & 0x01;
        f.digital[1] = (buf[j - 1] >> 6) & 0x01;
        f.digital[2] = (buf[j - 1] >> 5) & 0x01;
        f.digital[3] = (buf[j - 1] >> 4) & 0x01;

        int[] values = new int[4];
        if (numChannels > 0) values[0] = (((buf[j - 1] & 0x0F) << 6) | ((buf[j - 2] & 0xFC) >> 2)) & 0x3FF;
        if (numChannels > 1) values[1] = (((buf[j - 2] & 0x03) << 8) | (buf[j - 3] & 0xFF)) & 0x3FF;
        if (numChannels > 2) values[2] = (((buf[j - 4] & 0xFF) << 2) | ((buf[j - 5] & 0xC0) >> 6)) & 0x3FF;
        if (numChannels > 3) values[3] = (((buf[j - 5] & 0x3F) << 4) | ((buf[j - 6] & 0xF0) >> 4)) & 0x3FF;

        for (int k = 0; k < numChannels; k++) {
            f.analog[analogChannels[k]] = values[k];
        }
        return f;
    }

    public void stop() throws BITalinoException {
        if (!isAcquiring) {
            return;
        }
        try {
            sendCommand(0x00); // Comando de parada
        } catch (IOException e) {
            throw new BITalinoException(BITalinoErrorTypes.LOST_COMUNICATION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }finally {
            isAcquiring=false; //aunque falle el envío, para la app, la adquisición ya ha terminado
        }
    }

    public void close() throws BITalinoException {
        boolean failed=false;
        //Cada recurso se cierra en su propio try `para que si uno falla, el resto se cierre igualmente
        try {
            if(iStream!=null)iStream.close();
        }catch (IOException e){
            failed=true;
        }
        try {
            if(oStream!=null)oStream.close();
        }catch (IOException e){
            failed=true;
        }
        try {
            if(connection!=null)connection.close();
        }catch (IOException e){
            failed=true;
        }

        iStream=null;
        oStream=null;
        connection=null;
        isAcquiring=false;
        if(failed){
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }
    }
    private void closeQuietly(){
        try{
            close();
        }catch (BITalinoException ignored) {

        }
    }
}