//POJOS QUE REPRESENTAN LA INFO QUE GUARDA EL SERVIDOR (FICHEROS O BASE DE DATOS)

//Anotaciones y revisiones del medico sobre las pruebas recibidas
//Soporte a la APP DEL MEDICO (+2 PUNTOS)

package server.data;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ClinicalNote implements Serializable {
    private static final long serialVersionUID=1L;

    private String noteId;
    private String patientId;
    private String doctorId;
    private LocalDateTime timestamp;
    private String observation;

    public ClinicalNote(){
        this.timestamp=LocalDateTime.now();
    }

    public ClinicalNote(String noteId, String patientId, String doctorId, String observation) {
        this.noteId = noteId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.timestamp = LocalDateTime.now();
        this.observation = observation;
    }

    public String getNoteId() {
        return noteId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getObservation() {
        return observation;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    @Override
    public String toString() {
        return "ClinicalNote{" +
                "noteId='" + noteId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", doctorId='" + doctorId + '\'' +
                ", timestamp=" + timestamp +
                ", observation='" + observation + '\'' +
                '}';
    }
}