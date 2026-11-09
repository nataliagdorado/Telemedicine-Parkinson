package bitalino;
import common.SampleBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import patient.SignalProcessor;
import patient.SimulatedSignalSource;
import patient.TremorAnalyzer;

import static org.junit.jupiter.api.Assertions.*;

public class TremorAnalyzerTest {
    /** Analiza 'seconds' segundos generados por el simulador con un temblor de tremorHz. */
    static TremorAnalyzer.Result analyseSimulated(double tremorHz, int seconds) throws Exception {
        SimulatedSignalSource sim = SimulatedSignalSourceTest.simulator(tremorHz, 60, 0);
        TremorAnalyzer analyzer = new TremorAnalyzer(1000);
        for (int i = 0; i < seconds * 10; i++) {            //bloques de 100 muestras = 0.1 s
            SampleBlock block = sim.readBlock(100, "SES-test");
            analyzer.addBlock(block);
        }
        return analyzer.analyze(null);
    }

    @Test
    @DisplayName("EMG: 512 = 0 mV y los extremos del ADC dan aprox. +-1.64 mV")
    void emgConversion() {
        assertEquals(0.0, SignalProcessor.adcToEmgMiliVolts(512), 1e-9);
        assertEquals(1.632, SignalProcessor.adcToEmgMiliVolts(1023), 0.001);
        assertEquals(-1.635, SignalProcessor.adcToEmgMiliVolts(0), 0.001);
    }

    @Test
    @DisplayName("ACC: con la calibracion, Cmin = -1 g, Cmax = +1 g y el punto medio = 0 g")
    void accConversion() {
        assertEquals(-1.0, SignalProcessor.adcToAccG(400, 400, 600), 1e-9);
        assertEquals(1.0, SignalProcessor.adcToAccG(600, 400, 600), 1e-9);
        assertEquals(0.0, SignalProcessor.adcToAccG(500, 400, 600), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> SignalProcessor.adcToAccG(500, 600, 400));
    }

    @Test
    @DisplayName("Temblor simulado de 5 Hz: frecuencia dominante ~5 Hz y dentro del rango de reposo (4-6 Hz)")
    void detectsRestTremorFrequency() throws Exception {
        TremorAnalyzer.Result r = analyseSimulated(5.0, 10);
        assertTrue(r.hasFrequency());
        assertEquals(5.0, r.getDominantFrequencyHz(), 0.2);
        assertTrue(r.isInRestTremorBand());
        assertEquals(10.0, r.getAnalysedSeconds(), 0.01);
    }

    @Test
    @DisplayName("Temblor simulado de 8 Hz: se detecta 8 Hz y queda fuera del rango de reposo")
    void detectsOtherFrequency() throws Exception {
        TremorAnalyzer.Result r = analyseSimulated(8.0, 10);
        assertEquals(8.0, r.getDominantFrequencyHz(), 0.2);
        assertFalse(r.isInRestTremorBand());
    }

    @Test
    @DisplayName("Sesion de menos de 2 s: no se da frecuencia (no seria fiable)")
    void shortSessionHasNoFrequency() throws Exception {
        TremorAnalyzer.Result r = analyseSimulated(5.0, 1);
        assertFalse(r.hasFrequency());
        assertTrue(r.toSummary().contains("not available"));
    }

}
