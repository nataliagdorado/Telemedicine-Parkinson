package bitalino;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import patient.bitalino.BITalino;
import patient.bitalino.BITalinoErrorTypes;
import patient.bitalino.BITalinoException;
import patient.bitalino.Frame;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de la decodificacion de tramas del BITalino sin necesidad de la placa:
 * se construyen tramas binarias con el mismo formato que envia el dispositivo y se inyectan como stream.
 */
class SignalProcessingTest {

    /** Codifica una trama de 4 canales (7 bytes) con el formato del BITalino, CRC incluido. */
    static byte[] encode4(int seq, int a1, int a2, int a3, int a4) {
        byte[] b = new byte[7];
        // bytes desde el final: b[6]=seq|crc, b[5]=digitales|A1 alto, ...
        b[5] = (byte) ((a1 >> 6) & 0x0F);
        b[4] = (byte) (((a1 & 0x3F) << 2) | ((a2 >> 8) & 0x03));
        b[3] = (byte) (a2 & 0xFF);
        b[2] = (byte) ((a3 >> 2) & 0xFF);
        b[1] = (byte) (((a3 & 0x03) << 6) | ((a4 >> 4) & 0x3F));
        b[0] = (byte) ((a4 & 0x0F) << 4);
        b[6] = (byte) ((seq & 0x0F) << 4);
        b[6] |= (byte) crc(b);
        return b;
    }

    static int crc(byte[] b) {
        int x = 0;
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            if (i == b.length - 1) v &= 0xF0;
            for (int bit = 7; bit >= 0; bit--) {
                x <<= 1;
                if ((x & 0x10) != 0) x ^= 0x03;
                x ^= (v >> bit) & 0x01;
            }
        }
        return x & 0x0F;
    }

    static BITalino deviceWith(byte[] stream, int[] channels) throws Exception {
        BITalino b = new BITalino();
        set(b, "iStream", new DataInputStream(new ByteArrayInputStream(stream)));
        set(b, "analogChannels", channels);
        set(b, "isAcquiring", true);
        return b;
    }

    static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    @Test
    @DisplayName("4 canales: cada trama ocupa 7 bytes y los valores se decodifican correctamente")
    void decodesFourChannels() throws Exception {
        int n = 100;
        byte[] stream = new byte[7 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(encode4(i % 16, 512 + i, 1023 - i, i, 300), 0, stream, 7 * i, 7);
        }
        Frame[] frames = deviceWith(stream, new int[]{0, 1, 2, 3}).read(n);
        assertEquals(n, frames.length);
        for (int i = 0; i < n; i++) {
            assertEquals(i % 16, frames[i].seq, "seq de la trama " + i);
            assertEquals(512 + i, frames[i].analog[0]);
            assertEquals(1023 - i, frames[i].analog[1]);
            assertEquals(i, frames[i].analog[2]);
            assertEquals(300, frames[i].analog[3]);
        }
    }
    @Test
    @DisplayName("Trama con CRC incorrecto: se descarta y read() se resincroniza con la siguiente trama valida")
    void resyncAfterCorruptedFrame() throws Exception {
        byte[] good1 = encode4(1, 100, 200, 300, 400);
        byte[] bad = encode4(2, 111, 222, 333, 444);
        bad[3] ^= 0x01;   //se corrompe un bit: esta trama no pasa el CRC
        byte[] good3 = encode4(3, 500, 600, 700, 800);
        byte[] stream = new byte[21];
        System.arraycopy(good1, 0, stream, 0, 7);
        System.arraycopy(bad, 0, stream, 7, 7);
        System.arraycopy(good3, 0, stream, 14, 7);
        Frame[] frames = deviceWith(stream, new int[]{0, 1, 2, 3}).read(2);
        assertEquals(1, frames[0].seq);
        assertEquals(100, frames[0].analog[0]);
        assertEquals(3, frames[1].seq);            //la corrupta se ha saltado: la segunda trama leida es good3
        assertEquals(500, frames[1].analog[0]);
        assertEquals(800, frames[1].analog[3]);
    }

    @Test
    @DisplayName("Lectura desalineada (sobra 1 byte al principio): read() se resincroniza y decodifica bien el resto")
    void resyncAfterMisalignment() throws Exception {
        int n = 50;
        byte[] stream = new byte[1 + 7 * n];
        stream[0] = (byte) 0x5A;   //byte suelto que desplaza todas las tramas siguientes
        for (int i = 0; i < n; i++) {
            System.arraycopy(encode4(i % 16, 500 + i, 600, 700, 800), 0, stream, 1 + 7 * i, 7);
        }
        Frame[] frames = deviceWith(stream, new int[]{0, 1, 2, 3}).read(n - 1);
        for (int i = 0; i < frames.length; i++) {
            int expected = i + (frames[0].analog[0] - 500);   //la primera valida puede ser la 0 o la 1
            assertEquals(500 + expected, frames[i].analog[0]);
            assertEquals(600, frames[i].analog[1]);
        }
    }

    @Test
    @DisplayName("Datos que nunca forman una trama valida: read() se rinde con INVALID_DATA (no se queda en bucle)")
    void giveUpOnGarbage() throws Exception {
        byte garbage = findGarbageByte();
        byte[] stream = new byte[7 * 20];
        java.util.Arrays.fill(stream, garbage);
        BITalino b = deviceWith(stream, new int[]{0, 1, 2, 3});
        BITalinoException e = assertThrows(BITalinoException.class, () -> b.read(1));
        assertEquals(BITalinoErrorTypes.INVALID_DATA, e.getType());
    }

    /** Busca un valor de byte tal que 7 bytes iguales NUNCA forman una trama con CRC correcto. */
    static byte findGarbageByte() {
        for (int v = 1; v < 256; v++) {
            byte[] f = new byte[7];
            java.util.Arrays.fill(f, (byte) v);
            int sent = f[6] & 0x0F;
            if (crc(f) != sent) {
                return (byte) v;
            }
        }
        throw new AssertionError("no se encontro ningun byte basura");
    }



    @Test
    @DisplayName("Stream cerrado (read devuelve -1) lanza LOST_COMMUNICATION en vez de devolver ceros")
    void detectsDisconnection() throws Exception {
        BITalino b = deviceWith(new byte[7 * 10], new int[]{0, 1, 2, 3});   //solo hay 10 tramas y se piden 100
        b.setReadTimeoutMillis(0);   //sin tiempo maximo: se llega a read() y este devuelve -1 al acabarse los datos
        BITalinoException e = assertThrows(BITalinoException.class, () -> b.read(100));
        assertEquals(BITalinoErrorTypes.LOST_COMUNICATION, e.getType());
    }

    @Test
    @DisplayName("Placa que deja de enviar datos: read() lanza READ_TIMEOUT en vez de quedarse bloqueado")
    void detectsReadTimeout() throws Exception {
        java.io.PipedOutputStream sender = new java.io.PipedOutputStream();
        java.io.PipedInputStream receiver = new java.io.PipedInputStream(sender);   //conectado pero nunca se escribe nada
        BITalino b = deviceWith(new byte[0], new int[]{0, 1, 2, 3});
        set(b, "iStream", new DataInputStream(receiver));
        b.setReadTimeoutMillis(200);
        long start = System.currentTimeMillis();
        BITalinoException e = assertThrows(BITalinoException.class, () -> b.read(100));
        long elapsed = System.currentTimeMillis() - start;
        assertEquals(BITalinoErrorTypes.READ_TIMEOUT, e.getType());
        if (elapsed > 2000) {
            throw new AssertionError("Tardo demasiado en detectar el timeout: " + elapsed + " ms");
        }
    }

    @Test
    @DisplayName("open() rechaza una frecuencia no valida sin intentar conectar por Bluetooth")
    void openRejectsInvalidSamplingRate() {
        BITalino b = new BITalino();
        BITalinoException e = assertThrows(BITalinoException.class, () -> b.open("98:D3:31:FD:3B:8C", 500));
        assertEquals(BITalinoErrorTypes.INVALID_PARAMETER, e.getType());
    }

    @Test
    @DisplayName("close() deja el objeto limpio aunque falle al cerrar un recurso")
    void closeAlwaysResetsState() throws Exception {
        BITalino b = deviceWith(new byte[0], new int[]{0, 1, 2, 3});
        set(b, "iStream", new DataInputStream(new java.io.InputStream() {
            @Override public int read() { return -1; }
            @Override public void close() throws java.io.IOException { throw new java.io.IOException("fallo simulado"); }
        }));
        BITalinoException e = assertThrows(BITalinoException.class, b::close);
        assertEquals(BITalinoErrorTypes.UNDEFINED_ERROR, e.getType());
        BITalinoException again = assertThrows(BITalinoException.class, () -> b.read(1));   //ya no esta adquiriendo
        assertEquals(BITalinoErrorTypes.DEVICE_NOT_IN_ACQUISITION_MODE, again.getType());
    }
}