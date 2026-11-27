package database;

import common.SessionInfo;
import common.SymptomReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.data.ClinicalNote;
import server.data.IdGenerator;
import server.data.Patient;
import server.data.Repository;
import server.data.RepositoryException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrato de Repository: los MISMOS tests se ejecutan contra todas las implementaciones.
 * Cada subclase solo dice cuál prueba (open). JUnit no ejecuta esta clase por ser abstracta.
 */
abstract class AbstractRepositoryTest {

    @TempDir
    Path tempDir;                 // carpeta temporal: JUnit la crea y la borra en cada test

    protected Repository repo;

    /** Abre la implementación a probar sobre la carpeta dada. */
    protected abstract Repository open(Path dir) throws RepositoryException;

    @BeforeEach
    void setUp() throws RepositoryException {
        repo = open(tempDir);
        repo.savePatient(new Patient("P001", "José Núñez", LocalDate.of(1955, 3, 12), 2018, "Levodopa 8:00"));
    }

    // Cerrar antes de que JUnit borre tempDir: en Windows un .db abierto no se puede borrar
    @AfterEach
    void tearDown() throws RepositoryException {
        repo.close();
    }

    protected SymptomReport report(String patientId, int rigidity) {
        return new SymptomReport(patientId, IdGenerator.newReportId(), rigidity, 2,
                true, LocalDateTime.now().minusMinutes(45), false, "Peor, por la tarde");
    }

    @Test
    void savedReportCanBeReadBack() throws RepositoryException {
        SymptomReport r = report("P001", 3);
        repo.saveSymptomReport(r);

        List<SymptomReport> history = repo.findSymptomReports("P001");
        assertEquals(1, history.size());
        assertEquals(r.getReportId(), history.get(0).getReportId());
        assertEquals(3, history.get(0).getRigidityLevel());
    }

    @Test
    void dataSurvivesRestart() throws RepositoryException {
        repo.saveSymptomReport(report("P001", 1));
        repo.close();                                        // "apagar el servidor"

        Repository afterRestart = open(tempDir);             // "reiniciar el servidor"
        try {
            assertEquals(1, afterRestart.findSymptomReports("P001").size());
            assertEquals("José Núñez", afterRestart.findPatient("P001").orElseThrow().getName());
        } finally {
            afterRestart.close();
        }
    }

    @Test
    void invalidReportsAreRejected() {
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(report("P999", 2)));  // paciente inexistente
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(report("P001", 9)));  // rigidez fuera de 1-5
    }

    @Test
    void unknownPatientIsRejectedAndListsAreNeverNull() throws RepositoryException {
        assertThrows(RepositoryException.class, () -> repo.saveSession(
                new SessionInfo(IdGenerator.newSessionId(), "P999", LocalDateTime.now(), 100, new int[]{0})));
        assertThrows(RepositoryException.class, () -> repo.saveClinicalNote(
                new ClinicalNote(IdGenerator.newNoteId(), "P999", "doctor", "x")));

        assertTrue(repo.findSymptomReports("P999").isEmpty());
        assertTrue(repo.findSessions("P999").isEmpty());
        assertTrue(repo.findClinicalNotes("P999").isEmpty());
    }
}