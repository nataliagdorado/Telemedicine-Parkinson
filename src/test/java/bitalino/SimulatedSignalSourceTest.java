package bitalino;
import common.SampleBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import patient.SimulatedSignalSource;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimulatedSignalSourceTest {
    /** Crea el simulador con el constructor de tests (package-private en patient), sin esperas de tiempo real. */
    static SimulatedSignalSource simulator(double tremorHz, int amplitude, int disconnectAfterSeconds) throws Exception {
        Constructor<SimulatedSignalSource> c = SimulatedSignalSource.class
                .getDeclaredConstructor(double.class, int.class, int.class, boolean.class);
        c.setAccessible(true);
        SimulatedSignalSource s = c.newInstance(tremorHz, amplitude, disconnectAfterSeconds, false);
        s.connect("SIMULATED", 1000);
        s.startAcquisition(new int[]{0, 1, 2, 3});
        return s;
    }

    /** Amplitud de la componente de frecuencia f en la senal (un solo punto de la transformada de Fourier). */
    static double magnitudeAt(List<Integer> signal, double f, int fs) {
        double re = 0, im = 0;
        for (int n = 0; n < signal.size(); n++) {
            double v = signal.get(n) - 512;
            re += v * Math.cos(2 * Math.PI * f * n / fs);
            im -= v * Math.sin(2 * Math.PI * f * n / fs);
        }
        return Math.sqrt(re * re + im * im) / signal.size();
    }

    @Test
    @DisplayName("Cada bloque trae las 4 senales con el numero de muestras pedido y valores de 10 bits (0-1023)")
    void blockHasFourSignalsInAdcRange() throws Exception {
        SampleBlock block = simulator(5, 60, 0).readBlock(100, "SES-sim");
        assertNotNull(block);
        for (List<Integer> signal : List.of(block.getEmgSamples(), block.getAccX(), block.getAccY(), block.getAccZ())) {
            assertEquals(100, signal.size());
            for (int v : signal) {
                assertTrue(v >= 0 && v <= 1023, "valor fuera de rango: " + v);
            }
        }
    }

    @Test
    @DisplayName("El acelerometro simulado tiene su componente dominante en la frecuencia del temblor (5 Hz)")
    void accelerometerOscillatesAtTremorFrequency() throws Exception {
        SampleBlock block = simulator(5, 60, 0).readBlock(2000, "SES-sim");   //2 s a 1000 Hz
        double at5 = magnitudeAt(block.getAccX(), 5, 1000);
        double at2 = magnitudeAt(block.getAccX(), 2, 1000);
        double at10 = magnitudeAt(block.getAccX(), 10, 1000);
        assertTrue(at5 > 5 * at2 && at5 > 5 * at10,
                "5 Hz=" + at5 + " 2 Hz=" + at2 + " 10 Hz=" + at10);
    }

    @Test
    @DisplayName("Desconexion programada: tras N segundos readBlock() devuelve null")
    void scheduledDisconnection() throws Exception {
        SimulatedSignalSource s = simulator(5, 60, 1);   //se desconecta tras 1 s = 1000 muestras
        for (int i = 0; i < 10; i++) {                   //10 bloques de 100 = 1 s: todos correctos
            assertNotNull(s.readBlock(100, "SES-sim"), "bloque " + i);
        }
        assertNull(s.readBlock(100, "SES-sim"));         //a partir de aqui la placa "no responde"
        assertNull(s.readBlock(100, "SES-sim"));
    }


}
