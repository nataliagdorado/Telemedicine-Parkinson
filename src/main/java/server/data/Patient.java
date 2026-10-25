//POJOS QUE REPRESENTAN LA INFO QUE GUARDA EL SERVIDOR (FICHEROS O BASE DE DATOS)

//Modela el perfil clinico e identificador unico del paciente en el hospi
//Requisito R6

package server.data;

import common.SessionInfo;

import java.io.Serializable;

public class Patient implements Serializable {
    private static final long serialVersionUID=1L;

    private String patientId;
    private String name;
    private int age;
    private String medicationSchedule;

    public Patient(){}

    public Patient(String patientId, String name, int age, String medicationSchedule) {
        this.patientId = patientId;
        this.name = name;
        this.age = age;
        this.medicationSchedule = medicationSchedule;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public String getMedicationSchedule() {
        return medicationSchedule;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void setMedicationSchedule(String medicationSchedule) {
        this.medicationSchedule = medicationSchedule;
    }

    @Override
    public String toString() {
        return "Patient{" +
                "patientId='" + patientId + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", medicationSchedule='" + medicationSchedule + '\'' +
                '}';
    }
}