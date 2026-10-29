//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Almacenamiento de metadatos de la prueba de monitorizacion
//Requisito R4 de la prac

package common;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

public class SessionInfo implements Serializable {
    private static final long serialVersionUID=1L;

    private String sessionId;
    private String patientId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int samplingRateHz;

    private int[] channels;

    private String signalFile;
    private SessionStatus status = SessionStatus.RECORDING;


    public SessionInfo(String sessionId, String patientId, LocalDateTime now, int samplingRateHz){
    }

    public SessionInfo(String sessionId, String patientId, LocalDateTime startTime, int samplingRateHz, int[] channels) {
        this.sessionId = sessionId;
        this.patientId = patientId;
        this.startTime = startTime;
        this.samplingRateHz = samplingRateHz;
        this.channels = (channels == null) ? new int[0] : channels.clone();
    }

    // ---------- Transiciones de estado ----------

    private void finish(LocalDateTime end, SessionStatus newStatus) {
        if (status != SessionStatus.RECORDING) {
            throw new IllegalStateException("La sesión " + sessionId + " ya está " + status);
        }
        if (end == null || (startTime != null && end.isBefore(startTime))) {
            throw new IllegalArgumentException("Hora de fin no válida: " + end);
        }
        this.endTime = end;
        this.status = newStatus;
    }

    /** El paciente paró la grabación o se cumplió la duración. */
    public void complete(LocalDateTime end) {
        finish(end, SessionStatus.COMPLETED);
    }

    /** La grabación se cortó (desconexión, error, apagado). */
    public void markIncomplete(LocalDateTime end) {
        finish(end, SessionStatus.INCOMPLETE);
    }

    public Long getDurationSeconds() {
        return (startTime == null || endTime == null) ? null
                : Duration.between(startTime, endTime).getSeconds();
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

    public int[] getChannels()         { return channels == null ? new int[0] : channels.clone(); }

    public String getSignalFile()      { return signalFile; }

    public SessionStatus getStatus()   { return status; }

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
    } /** TODO SARAH: Comentarselo a Sarah que lo quiero cambiar  */

    public void setSamplingRateHz(int samplingRateHz) {
        this.samplingRateHz = samplingRateHz;
    }

    public void setChannels(int[] channels)          { this.channels = channels == null ? new int[0] : channels.clone(); }

    public void setSignalFile(String signalFile)     { this.signalFile = signalFile; }

    @Override
    public String toString() {
        return "SessionInfo{" +
                "sessionId='" + sessionId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", samplingRateHz=" + samplingRateHz +
                ", channels=" + Arrays.toString(channels) +
                ", signalFile='" + signalFile + '\'' +
                ", status=" + status +
                '}';
    }
}