package server.data;

import common.SessionInfo;
import common.SymptomReport;

import java.util.List;
import java.util.Optional;

/**
 * Almacén de los datos clínicos: pacientes, reportes de síntomas,
 * metadatos de sesiones y notas del médico.
 *
 * Las MUESTRAS de señal no pasan por aquí: van a fichero a través de SignalStore.
 *
 * Contrato que deben cumplir TODAS las implementaciones (FileRepository, SqliteRepository):
 *  - Son seguras para hilos: varios ClientHandler las llaman a la vez.
 *  - Los datos sobreviven a un reinicio del servidor.
 *  - Guardar algo de un paciente que no existe lanza RepositoryException.
 *  - Las listas se devuelven ordenadas por fecha (la más antigua primero)
 *    y nunca son null: si no hay datos, lista vacía.
 */

public interface Repository {

    // ---------- Pacientes ----------

    /** Inserta un paciente nuevo. Falla si ya existe uno con ese id. */
    void savePatient(Patient patient) throws RepositoryException;

    /** El paciente con ese id, o Optional.empty() si no existe. Con esto evitamos encontrarnos con NullPointerException en caso de no encontrar el id del paciente*/
    Optional<Patient> findPatient(String patientId) throws RepositoryException;

    List<Patient> findAllPatients() throws RepositoryException;

    // ---------- Reportes de síntomas (requisito obligatorio) ----------

    /** Guarda un reporte ya validado. Falla si su paciente no existe o el reportId está repetido. */
    void saveSymptomReport(SymptomReport report) throws RepositoryException;

    /** Historial de reportes de un paciente. */
    List<SymptomReport> findSymptomReports(String patientId) throws RepositoryException;

    // ---------- Sesiones de monitorización (solo metadatos) ----------

    /** Registra una sesión que empieza (estado RECORDING). Falla si el sessionId ya existe. */
    void saveSession(SessionInfo info) throws RepositoryException;

    /** Actualiza una sesión existente (al terminar: endTime + estado). Falla si no existe. */
    void updateSession(SessionInfo info) throws RepositoryException;

    Optional<SessionInfo> findSession(String sessionId) throws RepositoryException;

    List<SessionInfo> findSessions(String patientId) throws RepositoryException;

    // ---------- Notas clínicas (app del médico, +2) ----------

    void saveClinicalNote(ClinicalNote note) throws RepositoryException;

    List<ClinicalNote> findClinicalNotes(String patientId) throws RepositoryException;
}