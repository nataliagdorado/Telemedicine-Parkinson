package doctor;

import common.MessageCodec;
import common.SessionInfo;
import common.SymptomReport;
import server.data.ClinicalNote;
import server.data.FileRepository;
import server.data.Patient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DoctorService {

    private final Path dataDir;

    public DoctorService() {
        this.dataDir = Paths.get("data");
    }

    public String getCohortJson() throws Exception {
        FileRepository repo = new FileRepository(dataDir);
        List<Patient> patients = repo.findAllPatients();
        return MessageCodec.toJson(patients);
    }

    public String[] getPatientRecordJsons(String patientId) throws Exception {
        FileRepository repo = new FileRepository(dataDir);
        Optional<Patient> opt = repo.findPatient(patientId);
        if (opt.isEmpty()) {
            return null;
        }
        List<SymptomReport> reports = repo.findSymptomReports(patientId);
        List<SessionInfo> sessions = repo.findSessions(patientId);
        List<ClinicalNote> notes = repo.findClinicalNotes(patientId);

        return new String[]{
                MessageCodec.toJson(opt.get()),
                MessageCodec.toJson(reports),
                MessageCodec.toJson(sessions),
                MessageCodec.toJson(notes)
        };
    }

    public void addClinicalObservation(String patientId, String doctorId, String recordRef, String observationText) throws Exception {
        FileRepository repo = new FileRepository(dataDir);
        String noteId = "NOTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String formattedText = (recordRef != null && !recordRef.isBlank())
                ? "[" + recordRef + "] " + observationText
                : observationText;

        ClinicalNote note = new ClinicalNote(
                noteId,
                patientId,
                (doctorId != null && !doctorId.isBlank()) ? doctorId : "doctor",
                formattedText
        );
        repo.saveClinicalNote(note);
    }

    // @TODO revisar esto con TremorAnalyzer de Sarah
    public WaveformPayload getSessionWaveform(String sessionId) throws Exception {
        Path csvPath = dataDir.resolve("signals").resolve(sessionId + ".csv");
        if (Files.notExists(csvPath)) {
            return null;
        }

        // Obtener la frecuencia real de muestreo de la sesión (por defecto 1000 Hz)
        FileRepository repo = new FileRepository(dataDir);
        int samplingRateHz = repo.findSession(sessionId)
                .map(SessionInfo::getSamplingRateHz)
                .orElse(1000);

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);

        // 1. Alimentar TremorAnalyzer con TODAS las muestras del CSV
        patient.TremorAnalyzer analyzer = new patient.TremorAnalyzer(samplingRateHz);
        List<Integer> batchEmg = new ArrayList<>(500);
        List<Integer> batchX = new ArrayList<>(500);
        List<Integer> batchY = new ArrayList<>(500);
        List<Integer> batchZ = new ArrayList<>(500);

        // 2. Muestreo visual (hasta 600 puntos) para dibujar fluido en el <canvas>
        List<Integer> visualEmg = new ArrayList<>();
        List<Integer> visualAccX = new ArrayList<>();
        int step = Math.max(1, (lines.size() - 1) / 600);

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length >= 5) {
                try {
                    int emg = Integer.parseInt(parts[1].trim());
                    int ax = Integer.parseInt(parts[2].trim());
                    int ay = Integer.parseInt(parts[3].trim());
                    int az = Integer.parseInt(parts[4].trim());

                    batchEmg.add(emg);
                    batchX.add(ax);
                    batchY.add(ay);
                    batchZ.add(az);

                    if (batchEmg.size() >= 500) {
                        analyzer.addBlock(new common.SampleBlock(sessionId, batchEmg, batchX, batchY, batchZ));
                        batchEmg = new ArrayList<>(500);
                        batchX = new ArrayList<>(500);
                        batchY = new ArrayList<>(500);
                        batchZ = new ArrayList<>(500);
                    }

                    if ((i - 1) % step == 0) {
                        visualEmg.add(emg);
                        visualAccX.add(ax);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (!batchEmg.isEmpty()) {
            analyzer.addBlock(new common.SampleBlock(sessionId, batchEmg, batchX, batchY, batchZ));
        }

        // Ejecutar análisis espectral (DFT) y energético (RMS)
        // Calibración típica del acelerómetro BITalino (0..1023 ADC -> ±1.5g / ±3g)
        patient.TremorAnalyzer.Result result = analyzer.analyze(new int[]{400, 624});

        java.util.Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("analysedSeconds", Math.round(result.getAnalysedSeconds() * 10.0) / 10.0);
        metrics.put("dominantHz", result.hasFrequency() ? Math.round(result.getDominantFrequencyHz() * 10.0) / 10.0 : null);
        metrics.put("inRestBand", result.isInRestTremorBand());
        metrics.put("emgRmsMv", Double.isNaN(result.getEmgRmsMillivolts()) ? null : Math.round(result.getEmgRmsMillivolts() * 1000.0) / 1000.0);
        metrics.put("accRmsG", Double.isNaN(result.getAccRmsG()) ? null : Math.round(result.getAccRmsG() * 1000.0) / 1000.0);
        metrics.put("accRmsAdc", Math.round(result.getAccRmsAdc() * 10.0) / 10.0);

        int totalSamples = Math.max(0, lines.size() - 1);
        return new WaveformPayload(
                MessageCodec.toJson(visualEmg),
                MessageCodec.toJson(visualAccX),
                totalSamples,
                MessageCodec.toJson(metrics)
        );
    }

    public record WaveformPayload(String emgJson, String accJson, int totalSamples, String metricsJson) {}

}