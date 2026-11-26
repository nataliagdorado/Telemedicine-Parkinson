package database;

import common.SymptomReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.data.FileRepository;
import server.data.IdGenerator;
import server.data.Patient;
import server.data.RepositoryException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    // C8: una última línea a medio escribir (corte de luz) se ignora al leer y se elimina antes de la siguiente escritura
    @Test
    void truncatedLastLineIsIgnoredAndRepaired() throws Exception {
        repo.saveSymptomReport(report("P001", 1));
        repo.saveSymptomReport(report("P001", 2));
        Path symptoms = tempDir.resolve("symptoms.jsonl");

        // Simula el corte: media línea al final, sin salto de línea
        Files.writeString(symptoms, "{\"patientId\":\"P001\",\"reportId\":\"REP-cut\",\"timeSt",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        // 1) Se puede leer: la línea cortada se ignora y los dos reportes buenos siguen ahí
        assertEquals(2, repo.findSymptomReports("P001").size());

        // 2) El siguiente guardado repara el fichero y el dato nuevo no se pega a la línea cortada
        SymptomReport after = report("P001", 3);
        repo.saveSymptomReport(after);
        List<SymptomReport> history = repo.findSymptomReports("P001");
        assertEquals(3, history.size());
        assertTrue(history.stream().anyMatch(r -> r.getReportId().equals(after.getReportId())));

        // 3) En el fichero ya no queda rastro de la línea cortada
        for (String line : Files.readAllLines(symptoms, StandardCharsets.UTF_8)) {
            assertFalse(line.contains("REP-cut"));
        }

        // 4) Tras "reiniciar el servidor" todo sigue bien
        assertEquals(3, new FileRepository(tempDir).findSymptomReports("P001").size());
    }

    // C8: una línea corrupta EN MEDIO no es un corte: se avisa con error, no se tapa
    @Test
    void corruptedLineInTheMiddleIsReported() throws Exception {
        repo.saveSymptomReport(report("P001", 1));
        Path symptoms = tempDir.resolve("symptoms.jsonl");
        String goodLine = Files.readAllLines(symptoms, StandardCharsets.UTF_8).get(0);

        // Línea rota seguida de una línea correcta: la rota ya no puede ser un corte de escritura
        Files.writeString(symptoms, "{esto no es json}" + System.lineSeparator() + goodLine + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        assertThrows(RepositoryException.class, () -> repo.findSymptomReports("P001"));
    }
}