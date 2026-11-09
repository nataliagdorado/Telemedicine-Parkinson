//Generacion de pruebas sintéticas de EMG y acelerometro
package patient;
import common.Config;
import common.SampleBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;




public class SimulatedSignalSource implements SignalSource{
    private static final int ADC_MAX=1023; //valores de 10 bits (2^10-1 como el BITalino real)
    private static final int BASELINE=512; // rango del ADC de la señal en reposo

    //PARAMETROS DE LA SIMULACION:
    private final double tremorHz;
    private final int tremorAmplitude;
    private final int disconnectAfterSeconds; //simulacion de que la placa se apaga tras N segundos de grabación
    private final boolean realTime;


    //Tanto connected como recording con false ya que estamos en proceso de simulacion
    private boolean connected=false;
    private boolean recording=false;
    private volatile boolean disconnected=false;
    private int samplingRate=1000;
    private long sampleIndex=0;
    private final Random random= new Random();

    public SimulatedSignalSource(){
        this(Config.getDouble("simulated.tremorHz",5.0),
                Config.getInt("simulated.tremorAmplitude",60),
                Config.getInt("simulated.disconnectAfterSeconds",0),
                true);
    }

    SimulatedSignalSource(double tremorHz, int tremorAmplitude, int disconnectAfterSeconds, boolean realTime){
        if(tremorHz>0){
            this.tremorHz=tremorHz;
        }else {
            this.tremorHz=5.0;
        }
        this.tremorAmplitude = Math.max(0, tremorAmplitude);
        this.disconnectAfterSeconds = Math.max(0, disconnectAfterSeconds);
        this.realTime = realTime;

    }
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
        this.sampleIndex = 0;//cada sesion empieza en t = 0
        if (disconnectAfterSeconds > 0) {
            System.out.println("[Simulator] The device will be disconnected after " + disconnectAfterSeconds + " s (simulated.disconnectAfterSeconds).");
        }
        return true;//confirmacion de la adquisicion en directo
    }

    @Override
    public SampleBlock readBlock(int numberOfSamples, String sessionId) {
        if (!recording||disconnected) return null;//Si la grabación no se inició = null

        if(disconnectAfterSeconds>0 && sampleIndex>= (long) disconnectAfterSeconds*samplingRate){
            System.out.println("[Simulator] Simulated BITalino disconnection");
            simulateDisconnection();
            return null;
        }
        if (realTime) {

            try {
                Thread.sleep((long) numberOfSamples * 1000 / samplingRate);//milisegundos=muestras*1000/Hz
            } catch (InterruptedException e) {//alguien interrunpio el hilo como por ejemplo el cierre de la app
                Thread.currentThread().interrupt();
                return null;
            }
        }

        //4 listas dinamicas, 1 por cada valor que almacenamos de lectura de datos
        List<Integer> emgSamples = new ArrayList<>(numberOfSamples);
        List<Integer> accX = new ArrayList<>(numberOfSamples);
        List<Integer> accY = new ArrayList<>(numberOfSamples);
        List<Integer> accZ = new ArrayList<>(numberOfSamples);

        for (int i = 0; i < numberOfSamples; i++) {
            double t=(double) sampleIndex/samplingRate;
            double tremor=Math.sin(2*Math.PI*tremorHz*t);

            //Temblor y ruido
            accX.add(clamp(BASELINE + tremorAmplitude * tremor + noise(10)));
            accY.add(clamp(BASELINE + 0.6 * tremorAmplitude * tremor + noise(10)));
            accZ.add(clamp(BASELINE + 0.3 * tremorAmplitude * tremor + noise(10)));

            //Ruido de fondo + base de actividad de contraccion muscular
            double burst = Math.max(0, tremor);                         //solo la mitad positiva: 0 entre rafagas
            emgSamples.add(clamp(BASELINE + noise(15 + 80 * burst)));

            sampleIndex++;
        }
        //Retorna una nueva instancia de SampleBlock (POJO)
        return new SampleBlock(sessionId, emgSamples, accX, accY, accZ);
    }

    private double noise (double sigma){
        return random.nextGaussian()*sigma;
    }

    private static int clamp(double value){
        long rounded=Math.round(value);
        return (int)Math.max(0,Math.min(ADC_MAX,rounded));
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


