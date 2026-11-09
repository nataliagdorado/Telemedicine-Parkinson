//analisis del temblor de una sesion de monitorizacion
package patient;

import common.SampleBlock;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TremorAnalyzer {
    public static final double REST_TREMOR_MIN_HZ = 4.0;
    public static final double REST_TREMOR_MAX_HZ = 6.0;

    private static final int ANALYSIS_RATE_HZ = 100;     //el acelerometro se reduce a 100 Hz: de sobra para un temblor < 15 Hz
    private static final double MIN_HZ = 1.0;            //rango de frecuencias en el que se busca el temblor
    private static final double MAX_HZ = 15.0;
    private static final double STEP_HZ = 0.1;           //resolucion de la busqueda
    private static final double MIN_SECONDS = 2.0;       //con menos de 2 s de señal la frecuencia no es fiable

    private final int samplingRateHz;
    private final int original;                        //muestras originales que se promedian en cada muestra de analisis
    private final double analysisRateHz;

    //media de cada gru`po
    private final List<Double>accX=new ArrayList<>();
    private final List<Double>accY=new ArrayList<>();
    private final List<Double>accZ=new ArrayList<>();
    private double partialX,partialY,partialZ; //sumas sel grupo que se promedia
    private int partialCount;

    //no hace falta guardar las muestras, basta con acumular la suma de los cuadrados
    private double emgSumSquaresMv;
    private long emgCount;

    public TremorAnalyzer(int samplingRateHz) {
        this.samplingRateHz = Math.max(1, samplingRateHz);
        this.original = Math.max(1, this.samplingRateHz / ANALYSIS_RATE_HZ);   //1000 Hz -> 10 ; 100 Hz -> 1
        this.analysisRateHz = (double) this.samplingRateHz / original;
    }
    public void addBlock(SampleBlock block) {
        if (block == null || block.getEmgSamples() == null) {
            return;
        }
        int n = block.getEmgSamples().size();
        for (int i = 0; i < n; i++) {
            double emgMv = SignalProcessor.adcToEmgMiliVolts(block.getEmgSamples().get(i));
            emgSumSquaresMv += emgMv * emgMv;
            emgCount++;

            partialX += block.getAccX().get(i);
            partialY += block.getAccY().get(i);
            partialZ += block.getAccZ().get(i);
            partialCount++;
            if (partialCount == original) {            //grupo completo: se guarda la media y se empieza otro
                accX.add(partialX / original);
                accY.add(partialY / original);
                accZ.add(partialZ / original);
                partialX = partialY = partialZ = 0;
                partialCount = 0;
            }
        }
    }
    //resultados con todas las muestras recibidas:
    public Result analyze(int[] accCalibration) {
        double seconds = accX.size() / analysisRateHz;
        double emgRms = emgCount == 0 ? Double.NaN : Math.sqrt(emgSumSquaresMv / emgCount);

        double[] x = centered(accX);
        double[] y = centered(accY);
        double[] z = centered(accZ);
        double accRmsAdc = Math.sqrt(meanSquare(x) + meanSquare(y) + meanSquare(z));   //RMS del vector aceleracion

        double accRmsG = Double.NaN;
        if (accCalibration != null && accCalibration.length == 2 && accCalibration[1] > accCalibration[0]) {
            accRmsG = accRmsAdc * SignalProcessor.accAdcStepToG(accCalibration[0], accCalibration[1]);
        }

        double dominantHz = Double.NaN;
        double maxHz = Math.min(MAX_HZ, analysisRateHz / 2 - STEP_HZ);   //no se puede ver por encima de fs/2 (Nyquist)
        if (seconds >= MIN_SECONDS && maxHz >= MIN_HZ) {
            double bestPower = -1;
            for (double f = MIN_HZ; f <= maxHz + 1e-9; f += STEP_HZ) {
                //Potencia total a la frecuencia f en los tres ejes: el temblor puede ir en cualquier direccion
                double power = powerAt(x, f) + powerAt(y, f) + powerAt(z, f);
                if (power > bestPower) {
                    bestPower = power;
                    dominantHz = f;
                }
            }
        }
        return new Result(seconds, dominantHz, accRmsAdc, accRmsG, emgRms);
    }

    //Resta la media (quita la gravedad y el nivel de reposo: solo interesa la oscilacion)
    private static double[] centered(List<Double> values) {
        double[] out = new double[values.size()];
        double mean = 0;
        for (double v : values) mean += v;
        mean = values.isEmpty() ? 0 : mean / values.size();
        for (int i = 0; i < out.length; i++) out[i] = values.get(i) - mean;
        return out;
    }

    private static double meanSquare(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (double v : values) sum += v * v;
        return sum / values.length;
    }

    //Potencia de la senal a la frecuencia f: un punto de la transformada de Fourier, |X(f)|^2
    private double powerAt(double[] signal, double f) {
        double re = 0, im = 0;
        for (int n = 0; n < signal.length; n++) {
            double angle = 2 * Math.PI * f * n / analysisRateHz;
            re += signal[n] * Math.cos(angle);
            im -= signal[n] * Math.sin(angle);
        }
        return re * re + im * im;
    }

    /** Resultados del analisis de una sesion. */
    public static final class Result {
        private final double analysedSeconds;
        private final double dominantFrequencyHz;   //NaN si la sesion es demasiado corta
        private final double accRmsAdc;
        private final double accRmsG;               //NaN si no hay calibracion
        private final double emgRmsMillivolts;

        Result(double analysedSeconds, double dominantFrequencyHz, double accRmsAdc, double accRmsG, double emgRmsMillivolts) {
            this.analysedSeconds = analysedSeconds;
            this.dominantFrequencyHz = dominantFrequencyHz;
            this.accRmsAdc = accRmsAdc;
            this.accRmsG = accRmsG;
            this.emgRmsMillivolts = emgRmsMillivolts;
        }

        public double getAnalysedSeconds()     { return analysedSeconds; }
        public double getDominantFrequencyHz() { return dominantFrequencyHz; }
        public double getAccRmsAdc()           { return accRmsAdc; }
        public double getAccRmsG()             { return accRmsG; }
        public double getEmgRmsMillivolts()    { return emgRmsMillivolts; }

        public boolean hasFrequency() { return !Double.isNaN(dominantFrequencyHz); }

        public boolean isInRestTremorBand() {
            return hasFrequency() && dominantFrequencyHz >= REST_TREMOR_MIN_HZ && dominantFrequencyHz <= REST_TREMOR_MAX_HZ;
        }

        /** Texto del resumen que se muestra al paciente al terminar la sesion. */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("---------SESSION SUMMARY (informative, not a diagnosis)---------\n");
            sb.append(String.format("Analysed signal:        %.1f s%n", analysedSeconds));
            if (hasFrequency()) {
                sb.append(String.format("Dominant tremor freq.:  %.1f Hz %s%n", dominantFrequencyHz,
                        isInRestTremorBand() ? "(within the 4-6 Hz rest tremor range)" : "(outside the 4-6 Hz rest tremor range)"));
            } else {
                sb.append("Dominant tremor freq.:  not available (session shorter than 2 s)\n");
            }
            if (Double.isNaN(accRmsG)) {
                sb.append(String.format("Tremor amplitude (RMS): %.1f ADC units (no accelerometer calibration)%n", accRmsAdc));
            } else {
                sb.append(String.format("Tremor amplitude (RMS): %.3f g%n", accRmsG));
            }
            if (!Double.isNaN(emgRmsMillivolts)) {
                sb.append(String.format("Muscle activity (EMG RMS): %.3f mV%n", emgRmsMillivolts));
            }
            sb.append("-----------------------------------------------------------------");
            return sb.toString();
        }
    }








}