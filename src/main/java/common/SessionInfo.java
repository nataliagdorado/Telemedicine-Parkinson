//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Almacenamiento de metadatos de la prueba de monitorizacion
//Requisito R4 de la prac

package common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SessionInfo implements Serializable {
    private static final long serialVersionUID=1L;

    private String sessionId;
    private String patientId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int samplingRateHz;

    public SessionInfo(){
    }

    public SessionInfo(String sessionId, String patientId, LocalDateTime startTime, int samplingRateHz) {
        this.sessionId = sessionId;
        this.patientId = patientId;
        this.startTime = startTime;
        this.samplingRateHz = samplingRateHz;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPatientId() {
        return patientId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public int getSamplingRateHz() {
        return samplingRateHz;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setSamplingRateHz(int samplingRateHz) {
        this.samplingRateHz = samplingRateHz;
    }

    @Override
    public String toString() {
        return "SessionInfo{" +
                "sessionId='" + sessionId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", samplingRateHz=" + samplingRateHz +
                '}';
    }
}