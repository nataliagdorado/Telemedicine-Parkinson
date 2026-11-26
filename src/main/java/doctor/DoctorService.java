package doctor;

import common.MessageCodec;

import java.util.ArrayList;
import java.util.List;

public class DoctorService {


    // Analiza las muestras de una sesión que ha enviado el servidor (GET_SESSION_SIGNAL).
    // Ya no lee ningún fichero: solo calcula las métricas y prepara los puntos de la gráfica
    public WaveformPayload buildWaveform(String sessionId, int samplingRateHz,
                                         List<Integer> emg, List<Integer> accX, List<Integer> accY, List<Integer> accZ) {
        int total = emg.size();

        // 1. Alimentar TremorAnalyzer en bloques de 500, como hacía al leer el CSV
        patient.TremorAnalyzer analyzer = new patient.TremorAnalyzer(samplingRateHz);
        for (int start = 0; start < total; start += 500) {
            int end = Math.min(start + 500, total);
            analyzer.addBlock(new common.SampleBlock(sessionId,
                    new ArrayList<>(emg.subList(start, end)),
                    new ArrayList<>(accX.subList(start, end)),
                    new ArrayList<>(accY.subList(start, end)),
                    new ArrayList<>(accZ.subList(start, end))));
        }

        // 2. Reducir a unos 600 puntos para dibujar fluido en el <canvas>
        List<Integer> visualEmg = new ArrayList<>();
        List<Integer> visualAccX = new ArrayList<>();
        int step = Math.max(1, total / 600);
        for (int i = 0; i < total; i += step) {
            visualEmg.add(emg.get(i));
            visualAccX.add(accX.get(i));
        }

        // 3. Análisis espectral (DFT) y energético (RMS)
        // Calibración típica del acelerómetro BITalino (0..1023 ADC -> ±1.5g / ±3g)
        patient.TremorAnalyzer.Result result = analyzer.analyze(new int[]{400, 624});

        java.util.Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("analysedSeconds", round(result.getAnalysedSeconds(), 10));
        if (result.hasFrequency()) {
            metrics.put("dominantHz", round(result.getDominantFrequencyHz(), 10));
        } else {
            metrics.put("dominantHz", null);
        }
        metrics.put("inRestBand", result.isInRestTremorBand());
        metrics.put("emgRmsMv", round(result.getEmgRmsMillivolts(), 1000));
        metrics.put("accRmsG", round(result.getAccRmsG(), 1000));
        metrics.put("accRmsAdc", round(result.getAccRmsAdc(), 10));

        return new WaveformPayload(
                MessageCodec.toJson(visualEmg),
                MessageCodec.toJson(visualAccX),
                total,
                MessageCodec.toJson(metrics)
        );
    }

    // Redondea a 1/factor (10 → 1 decimal, 1000 → 3 decimales). NaN (dato no calculable) → null
    private static Double round(double value, double factor) {
        if (Double.isNaN(value)) {
            return null;
        }
        return Math.round(value * factor) / factor;
    }

    public record WaveformPayload(String emgJson, String accJson, int totalSamples, String metricsJson) {}

}