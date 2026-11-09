package bitalino;
import common.SampleBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import patient.BitalinoSignalSource;
import patient.bitalino.BITalino;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class BitalinoSignalSourceTest {
    /** Crea un BitalinoSignalSource "conectado y grabando" cuyo BITalino lee del array de bytes dado. */
    static BitalinoSignalSource sourceWith(byte[] stream) throws Exception {
        BitalinoSignalSource source = new BitalinoSignalSource();
        BITalino device = (BITalino) get(source, "bitalino");
        SignalProcessingTest.set(device, "iStream", new DataInputStream(new ByteArrayInputStream(stream)));
        SignalProcessingTest.set(device, "analogChannels", new int[]{0, 1, 2, 3});
        SignalProcessingTest.set(device, "isAcquiring", true);
        device.setReadTimeoutMillis(200);
        SignalProcessingTest.set(source, "connected", true);
        SignalProcessingTest.set(source, "recording", true);
        SignalProcessingTest.set(source, "activeChannels", new int[]{0, 1, 2, 3});
        return source;
    }

    static Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    @Test
    @DisplayName("readBlock() reparte cada canal en su lista: A1->EMG, A2->ACC X, A3->ACC Y, A4->ACC Z")
    void readBlockMapsChannels() throws Exception {
        int n = 100;
        byte[] stream = new byte[7 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(SignalProcessingTest.encode4(i % 16, 500 + i, 600, 700, 800), 0, stream, 7 * i, 7);
        }
        SampleBlock block = sourceWith(stream).readBlock(n, "SES-test");
        assertNotNull(block);
        assertEquals("SES-test", block.getSessionId());
        assertEquals(n, block.getEmgSamples().size());
        assertEquals(n, block.getAccX().size());
        assertEquals(n, block.getAccY().size());
        assertEquals(n, block.getAccZ().size());
        for (int i = 0; i < n; i++) {
            assertEquals(500 + i, (int) block.getEmgSamples().get(i));
            assertEquals(600, (int) block.getAccX().get(i));
            assertEquals(700, (int) block.getAccY().get(i));
            assertEquals(800, (int) block.getAccZ().get(i));
        }
    }

    @Test
    @DisplayName("readBlock() devuelve null si la lectura falla (el controlador lo cuenta como fallo)")
    void readBlockReturnsNullOnError() throws Exception {
        SampleBlock block = sourceWith(new byte[0]).readBlock(100, "SES-test");   //sin datos -> READ_TIMEOUT
        assertNull(block);
    }

    @Test
    @DisplayName("startAcquisition() rechaza una configuracion que no tenga exactamente 4 canales")
    void startRejectsWrongChannelCount() throws Exception {
        BitalinoSignalSource source = new BitalinoSignalSource();
        SignalProcessingTest.set(source, "connected", true);   //se simula que ya hay conexion
        assertFalse(source.startAcquisition(new int[]{0, 1, 2}));
        assertFalse(source.isRecording());
    }


}
