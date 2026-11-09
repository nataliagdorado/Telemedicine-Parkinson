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

    public BITalino() {
    }
    public void setReadTimeoutMillis(long millis){
        this.readTimeoutMillis= Math.max(0,millis); //se queda con el numero mayor entre 0 y el que se ponga en el setter para que nunca se pueda establecer un numero negativo
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

    //este metodo devuelve una muestra con todos los valores de todos los canales en un mismo instante
    public Frame[] read(int numberOfSamples)throws BITalinoException{
        if(!isAcquiring){//la clase start lo debería poner en true, si no= error
            throw new BITalinoException(BITalinoErrorTypes.DEVICE_NOT_IN_ACQUISITION_MODE);
        }

        int numChannels = analogChannels.length;//array de canales que guardó start
        int bytesPerFrame = frameSize(numChannels);//para saber cuantos bytes ocupa cada trama
        byte[] buffer = new byte[numberOfSamples * bytesPerFrame]; //Buffer de tamañpo exacto

        try{
            int totalRead=0; //cuantos bytes llevamos leidos
            long lastDataTime=System.currentTimeMillis();//se toma el "ahora" como punto de partida
            while (totalRead<buffer.length){//hasta que se llene el buffer
                if(readTimeoutMillis>0&&iStream.available()==0) {

                    //cuantos datos hay esperando para leer sin bloquearse
                    if (System.currentTimeMillis() - lastDataTime > readTimeoutMillis) {
                        throw new BITalinoException(BITalinoErrorTypes.READ_TIMEOUT);
                    }
                    Thread.sleep(2);//si no se ha superado el tiempo, esperamos 2 ms y volvemos al principio del while y volvemos a preguntar
                    //en esos 2 segundos los datos no se pierden por que se quedan guardados en el buffer dle bluetooth y se leen eb la siguiente vuekta
                    continue;
                }
                int read= iStream.read(buffer, totalRead, buffer.length - totalRead);
                //iStream es el flujo de entrada por donde llega la conexion de Bluetooth, por donde llegan los Bytes a la placa
                if(read==-1){
                    throw  new BITalinoException(BITalinoErrorTypes.LOST_COMUNICATION);
                }
                totalRead+=read;
                lastDataTime=System.currentTimeMillis();//como han llegaod datos se reinicia la cuenta
            }
        }catch (IOException e){ //el bluetooth puede lanzar una exception IO
            throw new BITalinoException(BITalinoErrorTypes.LOST_COMUNICATION);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();// lanzra excepcion si alguine interrumpe el hilo( por ejemplo, si alguien cierra la app)
            throw new BITalinoException(BITalinoErrorTypes.UNDEFINED_ERROR);
        }

        Frame[] frames = new Frame[numberOfSamples];//crea el array donde iran las tramas decodificadas (al principio null)
        for (int i = 0; i < numberOfSamples; i++) {
            frames[i] = decodeFrame(buffer, i * bytesPerFrame, bytesPerFrame, numChannels);
            if (frames[i].seq < 0) { //si seq es menor que 0, la trama ha llegaod corrupta
                throw new BITalinoException(BITalinoErrorTypes.INVALID_DATA);
            }
        }
        return frames;
    }

    //Bytes por trama segun eñ numero de canales analogicos activos
    static int frameSize(int numChannels) {

            return (int) Math.ceil((12.0 + 10.0 * numChannels) / 8.0);
            //cada trma tiene 12 bits fijos mas 10 bits por canal analogico / 8 pasar de bits a bytes
            //math.ceil redondea hacia arriba

    }

    //Decodifica una trama
    //Comrpueba el CRC--> si falla (seq=-1)--> INVALID DATA
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

        int[] values = new int[6];
        if (numChannels > 0) values[0] = (((buf[j - 1] & 0x0F) << 6) | ((buf[j - 2] & 0xFC) >> 2)) & 0x3FF;
        if (numChannels > 1) values[1] = (((buf[j - 2] & 0x03) << 8) | (buf[j - 3] & 0xFF)) & 0x3FF;
        if (numChannels > 2) values[2] = (((buf[j - 4] & 0xFF) << 2) | ((buf[j - 5] & 0xC0) >> 6)) & 0x3FF;
        if (numChannels > 3) values[3] = (((buf[j - 5] & 0x3F) << 4) | ((buf[j - 6] & 0xF0) >> 4)) & 0x3FF;
        if (numChannels > 4) values[4] = (((buf[j - 6] & 0x0F) << 2) | ((buf[j - 7] & 0xC0) >> 6)) & 0x3F;
        if (numChannels > 5) values[5] = (buf[j - 7] & 0x3F);

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
