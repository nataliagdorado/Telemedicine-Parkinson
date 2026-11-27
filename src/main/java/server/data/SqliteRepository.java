package server.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import common.SymptomReport;
import common.SymptomValidator;
import java.time.LocalDateTime;
import common.SessionInfo;
import common.SessionStatus;

public class SqliteRepository implements Repository {

    private static final Logger LOGGER = Logger.getLogger(SqliteRepository.class.getName());
    private static final String SCHEMA_RESOURCE = "/schema.sql";

    private final Connection connection;

    public SqliteRepository(Path dbFile) throws RepositoryException{
        Path parent = dbFile.toAbsolutePath().getParent();
        try{
            if(parent != null){
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RepositoryException("No se pudo crear la carpeta de " + dbFile, e);
        }

        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath()); // Le dice a JDBC qué driver usar y qué fichero abrir
        }catch (SQLException e) {
            throw new RepositoryException("No se pudo abrir la base de datos " + dbFile, e);
        }

        try {
            try (Statement st = connection.createStatement()){
                st.execute("PRAGMA foreign_keys = ON"); // Activa la comprobación de las claves foráneas
            }
            runSchema();
        }catch (SQLException | IOException e) {
            closeQuietly();
            throw new RepositoryException("No se pudo inicializar la base de datos " + dbFile, e);
        }
        LOGGER.info("Base de datos SQLite lista en " + dbFile.toAbsolutePath());

    }

    private void runSchema() throws IOException, SQLException{
        String script;
        try (InputStream in = SqliteRepository.class.getResourceAsStream(SCHEMA_RESOURCE)){
            if (in == null){
                throw new IOException("No se encuentra " + SCHEMA_RESOURCE + " en resources");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Quitamos las líneas de comentario para que un ';' dentro de ellas no corte una sentencia
        StringBuilder sinComentarios = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                sinComentarios.append(line).append('\n');
            }
        }

        try (Statement st = connection.createStatement()) {
            for (String sentencia : sinComentarios.toString().split(";")) {
                if (!sentencia.isBlank()) {
                    st.execute(sentencia);
                }
            }
        }
    }

    @Override
    public synchronized void close() throws RepositoryException{
        try{
            connection.close();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo cerrar la base de datos", e);
        }
    }

    private void closeQuietly(){
        try {
            connection.close();
        } catch (SQLException ignored) {
            // ya estamos tratando otro error: este no aporta nada
        }
    }

    // ---------- Helpers ----------

    private static void requireId(String id, String field) throws RepositoryException {
        if (id == null || id.isBlank()) {
            throw new RepositoryException("Falta " + field);
        }
    }

    /** LocalDate, LocalDateTime... a texto ISO. null se queda en null (columna NULL). */
    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /** setInt no admite null: para un Integer opcional hay que usar setNull. */
    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Texto ISO de la BD a LocalDateTime. NULL se queda en null. */
    private static LocalDateTime toDateTime(String text) {
        if (text == null) {
            return null;
        }
        return LocalDateTime.parse(text);
    }

    /** SQLite no tiene boolean: se guarda 1 (true) o 0 (false). */
    private static void setBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
        if (value) {
            ps.setInt(index, 1);
        } else {
            ps.setInt(index, 0);
        }
    }

    /** int[] {0,1,2,3} -> "0,1,2,3". Un array vacío se guarda como "". */
    private static String channelsToText(int[] channels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < channels.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(channels[i]);
        }
        return sb.toString();
    }

    /** "0,1,2,3" -> int[] {0,1,2,3}. "" o NULL -> array vacío (igual que SessionInfo). */
    private static int[] textToChannels(String text) {
        if (text == null || text.isBlank()) {
            return new int[0];
        }
        String[] parts = text.split(",");
        int[] channels = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            channels[i] = Integer.parseInt(parts[i].trim());
        }
        return channels;
    }

    // ---------- Pacientes ----------

    @Override
    public synchronized void savePatient(Patient patient) throws RepositoryException{
        if (patient == null){
            throw new RepositoryException("Falta patientId");
        }
        requireId(patient.getPatientId(), "patientId");
        String sql = "INSERT INTO patients (patient_id, name, birth_date, diagnosis_year, "
                + "medication_schedule, password_hash, password_salt) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patient.getPatientId());
            ps.setString(2, patient.getName());
            ps.setString(3, toText(patient.getBirthDate()));
            setNullableInt(ps, 4, patient.getDiagnosisYear());
            ps.setString(5, patient.getMedicationSchedule());
            ps.setString(6, patient.getPasswordHash());
            ps.setString(7, patient.getPasswordSalt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar el paciente " + patient.getPatientId()
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updatePatient(Patient patient) throws RepositoryException {
        if (patient == null) {
            throw new RepositoryException("Falta patientId");
        }
        requireId(patient.getPatientId(), "patientId");
        String sql = "UPDATE patients SET name = ?, birth_date = ?, diagnosis_year = ?, "
                + "medication_schedule = ?, password_hash = ?, password_salt = ? WHERE patient_id = ?";
        int filas;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patient.getName());
            ps.setString(2, toText(patient.getBirthDate()));
            setNullableInt(ps, 3, patient.getDiagnosisYear());
            ps.setString(4, patient.getMedicationSchedule());
            ps.setString(5, patient.getPasswordHash());
            ps.setString(6, patient.getPasswordSalt());
            ps.setString(7, patient.getPatientId());
            filas = ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo actualizar el paciente " + patient.getPatientId(), e);
        }
        if (filas == 0) {
            throw new RepositoryException("No existe el paciente " + patient.getPatientId());
        }
    }

    @Override
    public synchronized Optional<Patient> findPatient(String patientId) throws RepositoryException {
        String sql = "SELECT * FROM patients WHERE patient_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPatient(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo leer el paciente " + patientId, e);
        }
    }

    @Override
    public synchronized List<Patient> findAllPatients() throws RepositoryException {
        String sql = "SELECT * FROM patients ORDER BY patient_id";
        List<Patient> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapPatient(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron leer los pacientes", e);
        }
        return result;
    }

    /** Convierte la fila actual del ResultSet en un Patient. */
    private static Patient mapPatient(ResultSet rs) throws SQLException {
        LocalDate birthDate = null;
        String birthText = rs.getString("birth_date");
        if (birthText != null) {
            birthDate = LocalDate.parse(birthText);
        }

        Integer diagnosisYear = rs.getInt("diagnosis_year");
        if (rs.wasNull()) {
            diagnosisYear = null;
        }

        return new Patient(
                rs.getString("patient_id"),
                rs.getString("name"),
                birthDate,
                diagnosisYear,
                rs.getString("medication_schedule"),
                rs.getString("password_hash"),
                rs.getString("password_salt"));
    }

    // ---------- Reportes de síntomas ----------

    @Override
    public synchronized void saveSymptomReport(SymptomReport report) throws RepositoryException {
        List<String> errors = SymptomValidator.validate(report);
        if (!errors.isEmpty()) {
            throw new RepositoryException("Reporte inválido: " + errors);
        }
        requireId(report.getReportId(), "reportId");
        String sql = "INSERT INTO symptom_reports (report_id, patient_id, session_id, time_stamp, "
                + "rigidity_level, tremor_level, medication_taken, last_dose_time, freezing_episodes, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, report.getReportId());
            ps.setString(2, report.getPatientId());
            ps.setString(3, report.getSessionId());
            ps.setString(4, toText(report.getTimeStamp()));
            ps.setInt(5, report.getRigidityLevel());
            ps.setInt(6, report.getTremorLevel());
            setBoolean(ps, 7, report.isMedicationTaken());
            ps.setString(8, toText(report.getLastDoseTime()));
            setBoolean(ps, 9, report.isFreezingEpisodes());
            ps.setString(10, report.getNotes());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar el reporte " + report.getReportId()
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<SymptomReport> findSymptomReports(String patientId) throws RepositoryException {
        String sql = "SELECT * FROM symptom_reports WHERE patient_id = ? ORDER BY time_stamp";
        List<SymptomReport> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapReport(rs));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron leer los reportes de " + patientId, e);
        }
        return result;
    }

    private static SymptomReport mapReport(ResultSet rs) throws SQLException {
        SymptomReport r = new SymptomReport();
        r.setReportId(rs.getString("report_id"));
        r.setPatientId(rs.getString("patient_id"));
        r.setSessionId(rs.getString("session_id"));
        r.setTimeStamp(toDateTime(rs.getString("time_stamp")));
        r.setRigidityLevel(rs.getInt("rigidity_level"));
        r.setTremorLevel(rs.getInt("tremor_level"));
        r.setMedicationTaken(rs.getInt("medication_taken") == 1);
        r.setLastDoseTime(toDateTime(rs.getString("last_dose_time")));
        r.setFreezingEpisodes(rs.getInt("freezing_episodes") == 1);
        r.setNotes(rs.getString("notes"));
        return r;
    }

    // ---------- Sesiones de monitorización ----------

    @Override
    public synchronized void saveSession(SessionInfo info) throws RepositoryException {
        if (info == null) {
            throw new RepositoryException("Falta sessionId");
        }
        requireId(info.getSessionId(), "sessionId");
        String sql = "INSERT INTO sessions (session_id, patient_id, start_time, end_time, "
                + "sampling_rate_hz, channels, signal_file, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, info.getSessionId());
            ps.setString(2, info.getPatientId());
            ps.setString(3, toText(info.getStartTime()));
            ps.setString(4, toText(info.getEndTime()));
            ps.setInt(5, info.getSamplingRateHz());
            ps.setString(6, channelsToText(info.getChannels()));
            ps.setString(7, info.getSignalFile());
            ps.setString(8, info.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar la sesión " + info.getSessionId()
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updateSession(SessionInfo info) throws RepositoryException {
        if (info == null) {
            throw new RepositoryException("Falta sessionId");
        }
        requireId(info.getSessionId(), "sessionId");
        String sql = "UPDATE sessions SET start_time = ?, end_time = ?, sampling_rate_hz = ?, "
                + "channels = ?, signal_file = ?, status = ? WHERE session_id = ?";
        int filas;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, toText(info.getStartTime()));
            ps.setString(2, toText(info.getEndTime()));
            ps.setInt(3, info.getSamplingRateHz());
            ps.setString(4, channelsToText(info.getChannels()));
            ps.setString(5, info.getSignalFile());
            ps.setString(6, info.getStatus().name());
            ps.setString(7, info.getSessionId());
            filas = ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo actualizar la sesión " + info.getSessionId(), e);
        }
        if (filas == 0) {
            throw new RepositoryException("No existe la sesión " + info.getSessionId());
        }
    }

    @Override
    public synchronized Optional<SessionInfo> findSession(String sessionId) throws RepositoryException {
        String sql = "SELECT * FROM sessions WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo leer la sesión " + sessionId, e);
        }
    }

    @Override
    public synchronized List<SessionInfo> findSessions(String patientId) throws RepositoryException {
        String sql = "SELECT * FROM sessions WHERE patient_id = ? ORDER BY start_time";
        List<SessionInfo> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSession(rs));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron leer las sesiones de " + patientId, e);
        }
        return result;
    }

    /**
     * SessionInfo no tiene setStatus/setEndTime: el estado se restaura con sus
     * propias transiciones (complete / markIncomplete), que además validan los datos.
     */
    private static SessionInfo mapSession(ResultSet rs) throws SQLException {
        String sessionId = rs.getString("session_id");
        SessionInfo info = new SessionInfo(
                sessionId,
                rs.getString("patient_id"),
                toDateTime(rs.getString("start_time")),
                rs.getInt("sampling_rate_hz"),
                textToChannels(rs.getString("channels")));
        info.setSignalFile(rs.getString("signal_file"));

        LocalDateTime end = toDateTime(rs.getString("end_time"));
        try {
            SessionStatus status = SessionStatus.valueOf(rs.getString("status"));
            if (status == SessionStatus.COMPLETED) {
                info.complete(end);
            } else if (status == SessionStatus.INCOMPLETE) {
                info.markIncomplete(end);
            }
            // RECORDING: es el estado inicial de SessionInfo, no hay que hacer nada
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new SQLException("Sesión " + sessionId + " con datos incoherentes en la BD", e);
        }
        return info;
    }

    // ---------- Notas clínicas ----------

    @Override
    public synchronized void saveClinicalNote(ClinicalNote note) throws RepositoryException {
        if (note == null) {
            throw new RepositoryException("Falta noteId");
        }
        requireId(note.getNoteId(), "noteId");
        String sql = "INSERT INTO clinical_notes (note_id, patient_id, doctor_id, timestamp, observation) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, note.getNoteId());
            ps.setString(2, note.getPatientId());
            ps.setString(3, note.getDoctorId());
            ps.setString(4, toText(note.getTimestamp()));
            ps.setString(5, note.getObservation());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar la nota " + note.getNoteId()
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<ClinicalNote> findClinicalNotes(String patientId) throws RepositoryException {
        String sql = "SELECT * FROM clinical_notes WHERE patient_id = ? ORDER BY timestamp";
        List<ClinicalNote> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapNote(rs));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron leer las notas de " + patientId, e);
        }
        return result;
    }

    /** Constructor vacío + setters: el de 5 argumentos de ClinicalNote ignora sus parámetros. */
    private static ClinicalNote mapNote(ResultSet rs) throws SQLException {
        ClinicalNote n = new ClinicalNote();
        n.setNoteId(rs.getString("note_id"));
        n.setPatientId(rs.getString("patient_id"));
        n.setDoctorId(rs.getString("doctor_id"));
        n.setTimestamp(toDateTime(rs.getString("timestamp")));
        n.setObservation(rs.getString("observation"));
        return n;
    }
}
