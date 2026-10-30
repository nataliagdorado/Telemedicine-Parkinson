package database;

import common.SymptomReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.data.FileRepository;
import server.data.IdGenerator;
import server.data.Patient;
import server.data.RepositoryException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {

    @TempDir
    Path tempDir;                 // carpeta temporal: JUnit la crea y la borra en cada test

    private FileRepository repo;

    @BeforeEach
    void setUp() throws RepositoryException {
        repo = new FileRepository(tempDir);
        repo.savePatient(new Patient("P001", "José Núñez", LocalDate.of(1955, 3, 12), 2018, "Levodopa 8:00"));
    }

    private SymptomReport report(String patientId, int rigidity) {
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

        FileRepository afterRestart = new FileRepository(tempDir);   // "reiniciar el servidor"
        assertEquals(1, afterRestart.findSymptomReports("P001").size());
        assertEquals("José Núñez", afterRestart.findPatient("P001").orElseThrow().getName());
    }

    @Test
    void invalidReportsAreRejected() {
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(report("P999", 2)));  // paciente inexistente
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(report("P001", 9)));  // rigidez fuera de 0-4
    }
}