package database;

/**
 * Programa de prueba para ver si la clase de File repository funciona y
 * puedo ver la carpeta con los archivos en mi ordenador no que sea temporal,
 * cómo en el test
 */

import common.SessionInfo;
import common.SymptomReport;
import server.data.ClinicalNote;
import server.data.FileRepository;
import server.data.IdGenerator;
import server.data.Patient;
import server.data.RepositoryException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Stream;

public class RepositoryDemo {

    // Carpeta donde se guardan los datos. Relativa = dentro del proyecto.
    private static final Path DATA_DIR = Path.of("data-demo");

    public static void main(String[] args) throws RepositoryException, IOException {
        deleteFolder(DATA_DIR);                      // empezar de cero en cada ejecución
        FileRepository repo = new FileRepository(DATA_DIR);

        // Pacientes
        repo.savePatient(new Patient("P001", "José Núñez", LocalDate.of(1955, 3, 12), 2018, "Levodopa 8:00, 14:00, 20:00"));
        repo.savePatient(new Patient("P002", "Ana Ruiz", LocalDate.of(1961, 7, 2), 2021, "Levodopa 9:00, 21:00"));

        // Reportes de síntomas
        repo.saveSymptomReport(new SymptomReport("P001", IdGenerator.newReportId(), 2, 3, true,
                LocalDateTime.now().minusMinutes(45), false, "Peor por la tarde, temblor en mano derecha"));
        repo.saveSymptomReport(new SymptomReport("P002", IdGenerator.newReportId(), 1, 1, true,
                LocalDateTime.now().minusHours(2), true, "Un bloqueo al salir de casa"));

        // Sesión: empieza (RECORDING) y termina (COMPLETED) -> prueba updateSession
        SessionInfo session = new SessionInfo(IdGenerator.newSessionId(), "P001",
                LocalDateTime.now().minusMinutes(1), 100, new int[]{0, 1, 2, 3});
        repo.saveSession(session);
        session.complete(LocalDateTime.now());
        repo.updateSession(session);

        // Nota del médico
        repo.saveClinicalNote(new ClinicalNote(IdGenerator.newNoteId(), "P001", "D001", "Revisar pauta de la tarde"));

        // Leer lo guardado
        System.out.println("Datos guardados en: " + DATA_DIR.toAbsolutePath());
        System.out.println("Pacientes: " + repo.findAllPatients().size());
        System.out.println("Reportes de P001: " + repo.findSymptomReports("P001"));
        System.out.println("Sesiones de P001: " + repo.findSessions("P001"));
    }

    /** Borra la carpeta y todo su contenido, si existe. */
    private static void deleteFolder(Path dir) throws IOException {
        if (Files.notExists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())   // primero los ficheros, después la carpeta
                    .forEach(p -> p.toFile().delete());
        }
    }
}
