//POJOS QUE REPRESENTAN LA INFO QUE GUARDA EL SERVIDOR (FICHEROS O BASE DE DATOS)

//Modela el perfil clinico e identificador unico del paciente en el hospi
//Requisito R6

package server.data;

import common.SessionInfo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

public class Patient implements Serializable {
    private static final long serialVersionUID=1L;

    private String patientId;
    private String name;
    private LocalDate birthDate;
    private Integer diagnosisYear;
    private String medicationSchedule;
    private String passwordHash;
    private String passwordSalt;

    public Patient(){}
// @Sarah no te borro esto todavia para no romperte los test pero habria q borrar
    public Patient(String patientId, String name, LocalDate birthDate, Integer diagnosisYear, String medicationSchedule) {
        this.patientId = patientId;
        this.name = name;
        this.birthDate = birthDate;
        this.diagnosisYear = diagnosisYear;
        this.medicationSchedule = medicationSchedule;
    }
    // Constructor completo con password
    public Patient(String patientId, String name, LocalDate birthDate, Integer diagnosisYear,
                   String medicationSchedule, String passwordHash, String passwordSalt) {
        this.patientId = patientId;
        this.name = name;
        this.birthDate = birthDate;
        this.diagnosisYear = diagnosisYear;
        this.medicationSchedule = medicationSchedule;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
    }


    public String getPatientId() {
        return patientId;
    }
    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public String getName() {
        return name;
    }

    public Integer getAgeAt(LocalDate date) {
        if (birthDate == null || date == null) {
            return null;
        }
        return Period.between(birthDate, date).getYears();
    }
    public Integer getAge() {
        return getAgeAt(LocalDate.now());
    }

    public Integer getYearsSinceDiagnosis() {
        if (diagnosisYear == null) {
            return null;
        }
        return LocalDate.now().getYear() - diagnosisYear;
    }
    public LocalDate getBirthDate() { return birthDate; }

    public Integer getDiagnosisYear() { return diagnosisYear; }

    public String getMedicationSchedule() {
        return medicationSchedule;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public void setDiagnosisYear(Integer diagnosisYear) { this.diagnosisYear = diagnosisYear; }

    public void setMedicationSchedule(String medicationSchedule) {
        this.medicationSchedule = medicationSchedule;
    }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    @Override
    public String toString() {
        return "Patient{" +
                "patientId='" + patientId + '\'' +
                ", name='" + name + '\'' +
                ", birthDate=" + birthDate +
                ", age=" + getAge() +
                ", diagnosisYear=" + diagnosisYear +
                ", medicationSchedule='" + medicationSchedule + '\'' +
                '}';
    }
}