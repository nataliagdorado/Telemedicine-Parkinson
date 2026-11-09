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
    @DisplayName("Una trama con CRC incorrecto hace que read() lance INVALID_DATA (como la API oficial)")
    void detectsCrcError() throws Exception {
        byte[] good = encode4(3, 100, 200, 300, 400);
        byte[] bad = good.clone();
        bad[3] ^= 0x01;   //se corrompe un bit
        byte[] stream = new byte[21];
        System.arraycopy(good, 0, stream, 0, 7);
        System.arraycopy(bad, 0, stream, 7, 7);
        System.arraycopy(good, 0, stream, 14, 7);
        BITalino b = deviceWith(stream, new int[]{0, 1, 2, 3});
        BITalinoException e = assertThrows(BITalinoException.class, () -> b.read(3));
        assertEquals(BITalinoErrorTypes.INVALID_DATA, e.getType());
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
}