//Clase para la conversion a unidades dísicas de los datos del BITalino
// Las unidades fisicas se utiulizaran en el resumen de la sesion y estarán documentadas

package patient;

public class SignalProcessor {

    public static final int ADC_RESOLUTION = 1024;     //2^10 niveles
    public static final double VCC_VOLTS = 3.3;        //tension de alimentacion del BITalino
    public static final double EMG_GAIN = 1009;        //ganancia del sensor EMG del BITalino

    private SignalProcessor(){

    }
    //EMG en milivoltios
    public static double adcToEmgMiliVolts(int adc){
        return ((double) adc/ADC_RESOLUTION-0.5)*VCC_VOLTS/EMG_GAIN*1000.0;
    }

    //Aceleracion en G
    public static double adcToAccG(int adc, int cMin, int cMax){
        if (cMax <= cMin) {
            throw new IllegalArgumentException("Invalid accelerometer calibration: Cmax must be greater than Cmin");
        }
        return 2.0 * (adc - cMin) / (cMax - cMin) - 1.0;
    }

    //Pasar una variacion del ADC a G
    public static double accAdcStepToG(int cMin, int cMax){
        if(cMax<=cMin){
            throw new IllegalArgumentException("Invalid accelerometer calibration: Cmax must be greater than Cmin");

        }
        return 2.0 / (cMax - cMin);
    }

}