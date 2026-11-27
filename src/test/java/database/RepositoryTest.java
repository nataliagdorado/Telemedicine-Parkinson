package database;

import common.SymptomReport;
import org.junit.jupiter.api.Test;
import server.data.FileRepository;
import server.data.Repository;
import server.data.RepositoryException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileRepository: hereda los tests del contrato (AbstractRepositoryTest)
 * y añade los suyos propios del formato JSON Lines (C8).
 */
class RepositoryTest extends AbstractRepositoryTest {

    @Override
    protected Repository open(Path dir) throws RepositoryException {
        return new FileRepository(dir);
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