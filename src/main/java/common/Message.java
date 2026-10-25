//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Actua como paquete envoltorio generico para la red.Indica la accion que ejecutar y lleva el cuerpo de datos
//-->Requisitos R10 y R5 del proyecto

/*nATALIA NECESITA Cuadrar lo de Sarah con lo suyo
        package common;

import java.util.HashMap;
import java.util.Map;

public class Message {
    private String type; // Qué acción se solicita: LOGIN, SYMPTOM_REPORT etc.
    private String requestId; // Identificador único para que responda con un ACK
    private Map<String, Object> data; // Aquí meto los datos que quiero transmitir

    public Message() {
        this.data = new HashMap<>();
    }

    public Message(String type, String requestId) {
        this.type = type;
        this.requestId = requestId;
        this.data = new HashMap<>();
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public void addData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
    }
}*/
package common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private static final long serialVersionUID=1L;

    private String action;
    private String patientId;
    private LocalDateTime timeStamp;
    private String status;
    private Object payload; //¿que pojo se esta enviando?SymptomReport, SessionInfo...


    public Message(){
        this.timeStamp=LocalDateTime.now();
        this.status="PENDING";
    }

    public Message(String action, String patientId, Object payload) {
        this.action = action;
        this.patientId = patientId;
        this.timeStamp = LocalDateTime.now();
        this.status = "PENDING";
        this.payload = payload;
    }

    public String getAction() {
        return action;
    }

    public String getPatientId() {
        return patientId;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public String getStatus() {
        return status;
    }

    public Object getPayload() {
        return payload;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Message{" +
                "action='" + action + '\'' +
                ", patientId='" + patientId + '\'' +
                ", timeStamp=" + timeStamp +
                ", status='" + status + '\'' +
                ", payload=" + payload +
                '}';
    }


}