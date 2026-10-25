//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Symptom report modela la info subjetiva autorinformada por el paciente (R2 de la prac)

package common;
import java.io.Serializable;
import java.time.LocalDateTime;


public class SymptomReport implements Serializable{
    private static final long serialVersionUID = 1L;

    private String patientID;
    private String reportId;
    private LocalDateTime timeStamp;
    private int rigidityLevel;
    private int tremorLevel;
    private boolean freezingEpisodes;
    private String notes;

    public SymptomReport(){
        this.timeStamp=LocalDateTime.now();
    }

    public SymptomReport(String patientID, String reportId, int rigidityLevel, int tremorLevel, boolean freezingEpisodes, String notes) {
        this.patientID = patientID;
        this.reportId = reportId;
        this.timeStamp = LocalDateTime.now();
        this.rigidityLevel = rigidityLevel;
        this.tremorLevel = tremorLevel;
        this.freezingEpisodes = freezingEpisodes;
        this.notes = notes;
    }

    public String getPatientID() {
        return patientID;
    }

    public String getReportId() {
        return reportId;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public int getRigidityLevel() {
        return rigidityLevel;
    }

    public int getTremorLevel() {
        return tremorLevel;
    }

    public boolean isFreezingEpisodes() {
        return freezingEpisodes;
    }

    public String getNotes() {
        return notes;
    }

    public void setPatientID(String patientID) {
        this.patientID = patientID;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void setRigidityLevel(int rigidityLevel) {
        this.rigidityLevel = rigidityLevel;
    }

    public void setTremorLevel(int tremorLevel) {
        this.tremorLevel = tremorLevel;
    }

    public void setFreezingEpisodes(boolean freezingEpisodes) {
        this.freezingEpisodes = freezingEpisodes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "SymptomReport{" +
                "patientID='" + patientID + '\'' +
                ", reportId='" + reportId + '\'' +
                ", timeStamp=" + timeStamp +
                ", rigidityLevel=" + rigidityLevel +
                ", tremorLevel=" + tremorLevel +
                ", freezingEpisodes=" + freezingEpisodes +
                ", notes='" + notes + '\'' +
                '}';
    }
}