//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Symptom report modela la info subjetiva autorinformada por el paciente (R2 de la prac)

package common;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;


public class SymptomReport implements Serializable{
    private static final long serialVersionUID = 1L;

    private String patientId;
    private String reportId;
    private LocalDateTime timeStamp;
    private int rigidityLevel;
    private int tremorLevel;
    private boolean medicationTaken;
    private LocalDateTime lastDoseTime;
    private boolean freezingEpisodes;
    private String notes;

    public SymptomReport(){
        this.timeStamp=LocalDateTime.now();
    }

    public SymptomReport(String patientId, String reportId, int rigidityLevel, int tremorLevel, boolean medicationTaken,LocalDateTime lastDoseTime, boolean freezingEpisodes, String notes) {
        this.patientId = patientId;
        this.reportId = reportId;
        this.timeStamp = LocalDateTime.now();
        this.rigidityLevel = rigidityLevel;
        this.tremorLevel = tremorLevel;
        this.medicationTaken = medicationTaken;
        this.lastDoseTime = lastDoseTime;
        this.freezingEpisodes = freezingEpisodes;
        this.notes = notes;
    }

    public String getPatientId() {
        return patientId;
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

    public boolean isMedicationTaken() {
        return medicationTaken;
    }

    public LocalDateTime getLastDoseTime() {
        return lastDoseTime;
    }

    public Long getMinutesSinceLastDose() {   // calculado, no guardado
        if (lastDoseTime == null) {
            return null;
        } else {
            return Duration.between(lastDoseTime, timeStamp).toMinutes();
        }
    }

    public boolean isFreezingEpisodes() {
        return freezingEpisodes;
    }

    public String getNotes() {
        return notes;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
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

    public void setMedicationTaken(boolean medicationTaken) {
        this.medicationTaken = medicationTaken;
    }

    public void setLastDoseTime(LocalDateTime lastDoseTime) {
        this.lastDoseTime = lastDoseTime;
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
                "patientID='" + patientId + '\'' +
                ", reportId='" + reportId + '\'' +
                ", timeStamp=" + timeStamp +
                ", rigidityLevel=" + rigidityLevel +
                ", tremorLevel=" + tremorLevel +
                ", isMedicationTaken=" + medicationTaken +
                ", minutesSinceLastDose=" + getMinutesSinceLastDose() +
                ", freezingEpisodes=" + freezingEpisodes +
                ", notes='" + notes + '\'' +
                '}';
    }
}