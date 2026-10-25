//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Actua como paquete envoltorio generico para la red.Indica la accion que ejecutar y lleva el cuerpo de datos
//-->Requisitos R10 y R5 del proyecto

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