package common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * POJO compartido para transmisión por red TCP/IP entre Cliente y Servidor.
 * Actúa como envoltorio genérico compatible con JSON y Serializable.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    // --- Campos de Natalia (Protocolo de red & Tests) ---
    private String type;               // Acción / Tipo: LOGIN, SYMPTOM_REPORT, HELLO, ACK...
    private String requestId;          // Identificador único para el ACK
    private Map<String, Object> data;  // Mapa de clave-valor genérico para datos

    // --- Campos de Sarah (Control clínico & POJOs) ---
    private String action;             // Sinónimo de type
    private String patientId;          // ID del paciente asociado
    private LocalDateTime timeStamp;   // Marca temporal
    private String status;             // PENDING, CONNECTED, ERROR, etc.
    private Object payload;            // POJO adjunto (SymptomReport, SessionInfo...)

    // Constructor por defecto
    public Message() {
        this.data = new HashMap<>();
        this.timeStamp = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Constructor de Natalia (usado por el cliente de red y tests)
    public Message(String type, String requestId) {
        this.type = type;
        this.action = type; // Sincroniza con el campo de Sarah
        this.requestId = requestId;
        this.data = new HashMap<>();
        this.timeStamp = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Constructor de Sarah
    public Message(String action, String patientId, Object payload) {
        this.action = action;
        this.type = action; // Sincroniza con el campo de Natalia
        this.patientId = patientId;
        this.payload = payload;
        this.data = new HashMap<>();
        this.timeStamp = LocalDateTime.now();
        this.status = "PENDING";
    }

    // --- Métodos de Natalia ---
    public String getType() {
        return type != null ? type : action;
    }

    public void setType(String type) {
        this.type = type;
        this.action = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void addData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
    }

    // --- Métodos de Sarah ---
    public String getAction() {
        return action != null ? action : type;
    }

    public void setAction(String action) {
        this.action = action;
        this.type = action;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type='" + getType() + '\'' +
                ", requestId='" + requestId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", status='" + status + '\'' +
                ", timeStamp=" + timeStamp +
                ", data=" + data +
                ", payload=" + payload +
                '}';
    }
}