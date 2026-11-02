package protocol;

import common.Message;
import common.MessageCodec;
import common.ProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para el codificador/decodificador del protocolo JSON (MessageCodec).
 */
public class MessageCodecTest {

    @Test
    @DisplayName("Serializa y deserializa un mensaje basico conservando todos los atributos")
    public void testEncodeAndDecodeBasicMessage() throws Exception {
        Message original = new Message("HELLO", "req-001");
        original.setPatientId("PATIENT-101");
        original.setStatus("READY");

        // 1. Codificar a JSON
        String rawJson = MessageCodec.encode(original);

        assertNotNull(rawJson);
        assertTrue(rawJson.contains("\"type\":\"HELLO\"") || rawJson.contains("HELLO"));

        // 2. Deserializar desde el string
        Message decoded = MessageCodec.decode(rawJson);

        assertNotNull(decoded);
        assertEquals("HELLO", decoded.getType());
        assertEquals("req-001", decoded.getRequestId());
        assertEquals("PATIENT-101", decoded.getPatientId());
        assertEquals("READY", decoded.getStatus());
    }

    @Test
    @DisplayName("Serializa y deserializa fechas LocalDateTime en formato ISO-8601")
    public void testLocalDateTimeSerialization() throws Exception {
        Message original = new Message("SYMPTOM_REPORT", "req-002");
        LocalDateTime now = LocalDateTime.now();
        original.setTimeStamp(now);

        String json = MessageCodec.encode(original);
        Message decoded = MessageCodec.decode(json);

        assertNotNull(decoded.getTimeStamp());
        // Se compara año, mes, dia, hora, minuto y segundo (evitando discrepancias de micro/nanosegundos de Gson)
        assertEquals(original.getTimeStamp().getYear(), decoded.getTimeStamp().getYear());
        assertEquals(original.getTimeStamp().getMonth(), decoded.getTimeStamp().getMonth());
        assertEquals(original.getTimeStamp().getDayOfMonth(), decoded.getTimeStamp().getDayOfMonth());
        assertEquals(original.getTimeStamp().getHour(), decoded.getTimeStamp().getHour());
        assertEquals(original.getTimeStamp().getMinute(), decoded.getTimeStamp().getMinute());
        assertEquals(original.getTimeStamp().getSecond(), decoded.getTimeStamp().getSecond());
    }

    @Test
    @DisplayName("Preserva los datos clinicos del mapa dinámico (temblores, rigidez, medicación)")
    public void testClinicalDataMapPreserved() throws Exception {
        Message original = new Message("SYMPTOM_REPORT", "req-003");
        original.setPatientId("PATIENT-PARKINSON-01");
        original.addData("tremorSeverity", 4);
        original.addData("rigidityLevel", "HIGH");
        original.addData("medicationTaken", true);

        String json = MessageCodec.encode(original);
        Message decoded = MessageCodec.decode(json);

        assertNotNull(decoded.getData());
        assertEquals(4, ((Number) decoded.getData().get("tremorSeverity")).intValue());
        assertEquals("HIGH", decoded.getData().get("rigidityLevel"));
        assertEquals(true, decoded.getData().get("medicationTaken"));
    }

    @Test
    @DisplayName("Lanza ProtocolException si se recibe un JSON vacio, nulo o malformado")
    public void testInvalidJsonThrowsProtocolException() {
        assertThrows(ProtocolException.class, () -> MessageCodec.decode(null));
        assertThrows(ProtocolException.class, () -> MessageCodec.decode(""));
        assertThrows(ProtocolException.class, () -> MessageCodec.decode("   "));
        assertThrows(ProtocolException.class, () -> MessageCodec.decode("{ esto no es un json valido }"));
    }
}