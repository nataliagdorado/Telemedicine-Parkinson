package patient;

import common.Message;

/**
 * Entry point for the Patient application.
 */
public class PatientApp {

    public static void main(String[] args) {
        System.out.println("=== Starting Patient Application ===");
        ServerConnection connection = new ServerConnection();

        try {
            connection.connect();
            System.out.println("Connected to hospital server.");

            // 1. Initial Handshake
            Message helloMsg = new Message("HELLO", "req-001");
            helloMsg.addData("role", "PATIENT");
            helloMsg.addData("patientId", "P001");

            Message helloAck = connection.send(helloMsg);
            System.out.println("Server Handshake Response: " + helloAck.getData());

            // 2. Submit Parkinson's Symptom Report
            Message symptomMsg = new Message("SYMPTOM_REPORT", "req-002");
            symptomMsg.addData("rigidityLevel", 3);
            symptomMsg.addData("minutesSinceLastDose", 40);
            symptomMsg.addData("tremorSeverity", "MODERATE");

            Message symptomAck = connection.send(symptomMsg);
            System.out.println("Server Symptom Ack: " + symptomAck.getData());

        } catch (Exception ex) {
            System.err.println("Communication error: " + ex.getMessage());
        } finally {
            connection.close();
            System.out.println("Patient connection closed.");
        }
    }
}