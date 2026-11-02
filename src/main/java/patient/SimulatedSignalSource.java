//Generacion de pruebas sintéticas de EMG y acelerometro
package patient;
import common.SampleBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class SimulatedSignalSource implements SignalSource{

    //Tanto connected como recording con false ya que estamos en proceso de simulacion
    private boolean connected=false;
    private boolean recording=false;
    private volatile boolean disconnected=false;
    private int samplingRate=1000;
    private final Random random= new Random();
    @Override
    public boolean connect(String address, int samplingRate) {
        this.connected = true;//simulacion de que el dispositivo esta conectado
        this.disconnected=false;//devuelve inmediatamente true y le dice al controlador que se establecio la conexion con exito
        if(samplingRate > 0){//evita dividir entre 0 en readblock si llega a una frecuencia invalida
            this.samplingRate=samplingRate;
        }
        return true;//le dice al controlador qeu se establecio la conexion con exito
    }

    @Override
    public boolean startAcquisition(int[] channels) {//recibe un array de enteros con los canales que se van a registrar ( 0 EMG y 1,2 y 3 para Acelerometro)
        if (!connected) return false;//comprueba que la fuente esta conectada
        this.recording = true;//transmision activa
        return true;//confirmacion de la adquisicion en directo
    }

    @Override
    public SampleBlock readBlock(int numberOfSamples, String sessionId) {
        if (!recording||disconnected) return null;//Si la grabación no se inició = null

        try{
            Thread.sleep((long) numberOfSamples*1000/samplingRate);//milisegundos=muestras*1000/Hz
        }catch (InterruptedException e){//alguien interrunpio el hilo como por ejemplo el cierre de la app
            Thread.currentThread().interrupt();
            return null;
        }


        //4 listas dinamicas, 1 por cada valor que almacenamos de lectura de datos
        List<Integer> emgSamples = new ArrayList<>();
        List<Integer> accX = new ArrayList<>();
        List<Integer> accY = new ArrayList<>();
        List<Integer> accZ = new ArrayList<>();

        for (int i = 0; i < numberOfSamples; i++) {
            // Valores centrados en 512 +-50 simulando la actividad basal del musculo
            emgSamples.add(512 + random.nextInt(100) - 50);
            // Valores centrados en 512 +-20 simulando la actividad basal del temblor y las aceleraciones
            accX.add(512 + random.nextInt(40) - 20);
            accY.add(512 + random.nextInt(40) - 20);
            accZ.add(512 + random.nextInt(40) - 20);
        }
        //Retorna una nueva instancia de SampleBlock (POJO)
        return new SampleBlock(sessionId, emgSamples, accX, accY, accZ);
    }
    public void simulateDisconnection(){//simula la desconexion  entonces readblock se vuelve null
        //el controlador detectara 3 fallos seguidso y terminara con devicerrror
        this.disconnected=true;
    }

    @Override
    public void stopAcquisition() { this.recording = false; }

    @Override
    public void disconnect() {
        stopAcquisition();
        this.connected = false;
    }

    @Override
    public boolean isConnected() { return connected; }

    @Override
    public boolean isRecording() { return recording; }
}


