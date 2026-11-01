package server.data;

import common.SampleBlock;
import common.SessionInfo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda las muestras de cada sesión en su propio CSV:
 *   data/signals/<Nombre_Paciente>_<yyyyMMdd_HHmmss>_<sessionId>.csv
 *
 */
public class FileSignalStore implements SignalStore {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String COLUMNS = "nSeq,EMG,ACC_X,ACC_Y,ACC_Z";

    /** Estado de una grabación abierta: su fichero y el número de la siguiente muestra.
     * Hago esta clase que lee los bloques de BITalino dentro de esta clase porque es la única que lo va a usar
     * */
    private static final class Recording {
        final BufferedWriter writer;
        long nextSample = 0;

        Recording(BufferedWriter writer) {
            this.writer = writer;
        }
    }

    private final Path signalsDir;
    private final Map<String, Recording> open = new ConcurrentHashMap<>();

    public FileSignalStore   (Path signalsDir) throws RepositoryException {
        try {
            Files.createDirectories(signalsDir);
        } catch (IOException e) {
            throw new RepositoryException("No se pudo crear la carpeta de señales " + signalsDir, e);
        }
        this.signalsDir = signalsDir;
    }

    @Override
    public String openRecording(SessionInfo info, Patient patient) throws RepositoryException {
        if (info == null || info.getSessionId() == null || info.getStartTime() == null) {
            throw new RepositoryException("Sesión sin id o sin hora de inicio");
        }
        if (patient == null) {
            throw new RepositoryException("Falta el paciente de la sesión " + info.getSessionId());
        }
        if (open.containsKey(info.getSessionId())) {
            throw new RepositoryException("La sesión " + info.getSessionId() + " ya está abierta");
        }

        String fileName = sanitize(patient.getName()) + "_"
                + info.getStartTime().withNano(0).format(FILE_DATE) + "_"
                + info.getSessionId() + ".csv";
        Path file = signalsDir.resolve(fileName);

        try {
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);                 // falla si ya existe: nunca sobrescribe ==> nos funciona cómo segunda barrera porque dos hilos pueden hacer ContainsKey a la vez y pasar
            writer.write("# patient=" + patient.getName() + "; patientId=" + patient.getPatientId());
            writer.newLine();
            writer.write("# session=" + info.getSessionId() + "; start=" + info.getStartTime() + "; fs=" + info.getSamplingRateHz() + " Hz; channels=" + Arrays.toString(info.getChannels()));
            writer.newLine();
            writer.write(COLUMNS);
            writer.newLine();
            writer.flush();
            open.put(info.getSessionId(), new Recording(writer));
        } catch (IOException e) {
            throw new RepositoryException("No se pudo crear el fichero de señal " + file, e);
        }
        return file.toString();
    }

    @Override
    public void appendBlock(SampleBlock block) throws RepositoryException {
        if (block == null) {
            throw new RepositoryException("Bloque vacío");
        }
        Recording rec = open.get(block.getSessionId());
        if (rec == null) {
            throw new RepositoryException("La sesión " + block.getSessionId() + " no está abierta");
        }

        List<Integer> emg = block.getEmgSamples();
        List<Integer> x = block.getAccX();
        List<Integer> y = block.getAccY();
        List<Integer> z = block.getAccZ();
        if (emg == null || x == null || y == null || z == null || x.size() != emg.size() || y.size() != emg.size() || z.size() != emg.size()) {
            throw new RepositoryException("Bloque con canales de distinta longitud en la sesión " + block.getSessionId());
        }

        synchronized (rec) {                                        // cerrojo de ESTA sesión
            try {
                for (int i = 0; i < emg.size(); i++) {
                    rec.writer.write(rec.nextSample++ + "," + emg.get(i) + "," + x.get(i) + "," + y.get(i) + "," + z.get(i));
                    rec.writer.newLine();
                }
                rec.writer.flush();                                 // a disco ya: sobrevive a una desconexión
            } catch (IOException e) {
                throw new RepositoryException("No se pudo escribir en la sesión " + block.getSessionId(), e);
            }
        }
    }

    @Override
    public void closeRecording(String sessionId) throws RepositoryException {
        Recording rec = open.remove(sessionId);
        if (rec == null) {
            return;                                                 // ya cerrada: no es un error
        }
        synchronized (rec) {
            try {
                rec.writer.close();
            } catch (IOException e) {
                throw new RepositoryException("No se pudo cerrar la sesión " + sessionId, e);
            }
        }
    }

    @Override
    public void close() throws RepositoryException {
        RepositoryException first = null;
        for (String sessionId : List.copyOf(open.keySet())) {
            try {
                closeRecording(sessionId);
            } catch (RepositoryException e) {
                if (first == null) {
                    first = e;                                      // seguimos cerrando las demás
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /**
     * TODO: Pijada que me ha dicho claude que meta, a ver, mola pero no es muy necesaria. Ya me decís si queréis que lo quite o no
     * "José Núñez" -> "Jose_Nunez": sin tildes, espacios ni caracteres prohibidos en Windows. */
    static String sanitize(String name) {
        if (name == null) {
            return "paciente";
        }
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String safe = noAccents.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return safe.isEmpty() ? "paciente" : safe;
    }
}

