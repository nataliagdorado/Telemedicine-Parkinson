package server.data;

import common.MessageCodec;
import common.ProtocolException;
import common.SessionInfo;
import common.SymptomReport;
import common.SymptomValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementación de Repository con ficheros de texto.
 *
 * Un fichero por tabla, un objeto JSON por línea (formato JSON Lines):
 * data/patients.jsonl   data/symptoms.jsonl   data/sessions.jsonl   data/notes.jsonl
 *
 * Concurrencia: todos los métodos públicos son synchronized (un único cerrojo)
 * El servidor debe crear UNA sola instancia y compartirla entre todos los ClientHandler
 */

public class FileRepository implements Repository {
    private final Path patientsFile;
    private final Path symptomsFile;
    private final Path sessionsFile;
    private final Path notesFile;

    public FileRepository(Path baseDir) throws RepositoryException {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RepositoryException("No se pudo crear la carpeta de datos " + baseDir, e);
        }
        /**
         * Construye SOLO la dirección data/patients.jsonl. No crea el fichero: solo apunta dónde estará.
         * Se creará la primera vez que se escriba algo
         *
         * No crea ficheros vacíos innecesarios. Si nadie escribe nunca una nota clínica,
         * notes.jsonl no llega a existir, y no pasa nada.
         */
        this.patientsFile = baseDir.resolve("patients.jsonl");
        this.symptomsFile = baseDir.resolve("symptoms.jsonl");
        this.sessionsFile = baseDir.resolve("sessions.jsonl");
        this.notesFile    = baseDir.resolve("notes.jsonl");
    }

    // ----- Escribir un objeto ----- //

    /**
     * @param file
     * @param obj
     * Pongo obj por qué es un metodo común para todos los objetos, no solo para paciente
     * @throws RepositoryException
     */
    private void append (Path file, Object obj) throws RepositoryException{
        String line = MessageCodec.toJson(obj) + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RepositoryException("No se pudo escribir en "+ file, e);
        }
    }
    /** Lee todas las líneas del fichero y las convierte en objetos. Si no existe, lista vacía.
     * T es un tipo genérico que se rellena con el objeto que se le pase
     * */
    private <T> List <T> readAll(Path file, Class<T> type) throws RepositoryException{
        List<T> result = new ArrayList<>();
        if (Files.notExists(file)){
            return result;
        }
        List<String> lines;
        try{
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RepositoryException("No se pudo leer "+ file, e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                result.add(MessageCodec.fromJson(line, type));
            } catch (ProtocolException e) {
                throw new RepositoryException(file.getFileName() + ", línea " + (i + 1) + " corrupta", e);
            }
        }
        return result;
    }

    /** Reescribe el fichero entero. Primero a un .tmp y luego lo sustituye de golpe.
     * append() solo sabe añadir líneas al final. Pero updateSession() tiene que cambiar una
     * línea del medio (la sesión pasa de RECORDING a COMPLETED), y un fichero de texto no se puede editar
     * por dentro. Así que se reescribe entero con la línea ya cambiada. Solo lo usa updateSession().
     * */
    private void rewrite(Path file, List<?> objects) throws RepositoryException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        StringBuilder sb = new StringBuilder();
        for (Object o : objects) {
            sb.append(MessageCodec.toJson(o)).append(System.lineSeparator());
        }
        try {
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RepositoryException("No se pudo reescribir " + file, e);
        }
    }

    // Comprobaciones reutilizables //
    private void requirePatient(String patientId) throws RepositoryException {
        if (findPatient(patientId).isEmpty()) {
            throw new RepositoryException("No existe el paciente " + patientId);
        }
    }

    private static void requireId(String id, String field) throws RepositoryException {
        if (id == null || id.isBlank()) {
            throw new RepositoryException("Falta " + field);
        }
    }

    /**
     * Los parámetros que hay dentro de requiredId, que sé que son raros, son como un if
     * anidado raro que se llama ternario que hace que no salte nullPointer exception si no, que sale uno de los errores que manejo en la
     * clase de Repository exceptions
     *    --> Ternario (if-else en una línea): si patient es null pasa null a requireId en vez de llamar
     *        a null.getPatientId(), que daría NullPointerException. Así ambos casos dan "Falta patientId".
     * synchronized: solo un hilo (un ClientHandler) puede ejecutar a la vez un metodo de esta clase,
     * para que dos pacientes no lean y escriban el mismo fichero al mismo tiempo (p. ej. dos altas del mismo id).
     * @param patient
     * @throws RepositoryException
     */
    @Override
    public synchronized void savePatient(Patient patient) throws RepositoryException {
        requireId(patient == null ? null : patient.getPatientId(), "patientId");
        if (findPatient(patient.getPatientId()).isPresent()) {
            throw new RepositoryException("Ya existe el paciente " + patient.getPatientId());
        }
        append(patientsFile, patient);
    }

    @Override
    public synchronized Optional<Patient> findPatient(String patientId) throws RepositoryException {
        return readAll(patientsFile, Patient.class).stream()
                .filter(patient -> patient.getPatientId().equals(patientId))
                .findFirst(); // Devuelve un optional = si el filtro no encuentra a nadie, en vez de saltar un error, simplemente devuelve un objeto vacío
    }

    @Override
    public synchronized List<Patient> findAllPatients() throws RepositoryException {
        List<Patient> all = readAll(patientsFile, Patient.class);
        all.sort(Comparator.comparing(Patient::getPatientId)); // ordena a los pacientes por id, con el comparing
        return all;
    }

    // ----- Reportes de síntomas ----- //

    @Override
    public synchronized void saveSymptomReport(SymptomReport report) throws RepositoryException {
        List<String> errors = SymptomValidator.validate(report);   // equivale a los CHECK de la BD
        if (!errors.isEmpty()) {
            throw new RepositoryException("Reporte inválido: " + errors);
        }
        requireId(report.getReportId(), "reportId");
        requirePatient(report.getPatientId());                     // equivale a la FOREIGN KEY
        boolean repeated = readAll(symptomsFile, SymptomReport.class).stream()
                .anyMatch(r -> r.getReportId().equals(report.getReportId())); // devuelve true si se cumple la condición de que haya uno repetido
        if (repeated) {
            throw new RepositoryException("Ya existe el reporte " + report.getReportId());
        }
        append(symptomsFile, report);
    }

    @Override
    public synchronized List<SymptomReport> findSymptomReports(String patientId) throws RepositoryException {
        return readAll(symptomsFile, SymptomReport.class).stream()
                .filter(r -> r.getPatientId().equals(patientId))
                .sorted(Comparator.comparing(SymptomReport::getTimeStamp))
                .collect(Collectors.toList()); // Los pasa de Stram a List
    }

    // ----- Sesiones de monitorización ----- //

    @Override
    public synchronized void saveSession(SessionInfo info) throws RepositoryException {
        requireId(info == null ? null : info.getSessionId(), "sessionId");
        requirePatient(info.getPatientId());
        if (findSession(info.getSessionId()).isPresent()) {
            throw new RepositoryException("Ya existe la sesión " + info.getSessionId());
        }
        append(sessionsFile, info);
    }

    @Override
    public synchronized void updateSession(SessionInfo info) throws RepositoryException {
        requireId(info == null ? null : info.getSessionId(), "sessionId");
        List<SessionInfo> all = readAll(sessionsFile, SessionInfo.class);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getSessionId().equals(info.getSessionId())) {
                all.set(i, info);           // sustituye la versión vieja por la nueva
                rewrite(sessionsFile, all);
                return;
            }
        }
        throw new RepositoryException("No existe la sesión " + info.getSessionId());
    }

    @Override
    public synchronized Optional<SessionInfo> findSession(String sessionId) throws RepositoryException {
        return readAll(sessionsFile, SessionInfo.class).stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .findFirst();
    }

    @Override
    public synchronized List<SessionInfo> findSessions(String patientId) throws RepositoryException {
        return readAll(sessionsFile, SessionInfo.class).stream()
                .filter(s -> s.getPatientId().equals(patientId))
                .sorted(Comparator.comparing(SessionInfo::getStartTime))
                .collect(Collectors.toList());
    }

    // ----- Notas clínicas ----- //

    @Override
    public synchronized void saveClinicalNote(ClinicalNote note) throws RepositoryException {
        requireId(note == null ? null : note.getNoteId(), "noteId");
        requirePatient(note.getPatientId());
        append(notesFile, note);
    }

    @Override
    public synchronized List<ClinicalNote> findClinicalNotes(String patientId) throws RepositoryException {
        return readAll(notesFile, ClinicalNote.class).stream()
                .filter(n -> n.getPatientId().equals(patientId))
                .sorted(Comparator.comparing(ClinicalNote::getTimestamp))
                .collect(Collectors.toList());
    }

}