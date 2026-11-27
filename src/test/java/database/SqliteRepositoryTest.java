package database;
import common.SessionInfo;
import common.SessionStatus;
import common.SymptomReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.data.ClinicalNote;
import server.data.Patient;
import server.data.Repository;
import server.data.RepositoryException;
import server.data.SqliteRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void creaElFicheroYSePuedeReabrir() throws Exception {
        Path db = tempDir.resolve("telemed.db");

        SqliteRepository primero = new SqliteRepository(db);
        primero.close();
        assertTrue(Files.exists(db));

        // Segunda apertura sobre el mismo fichero: IF NOT EXISTS no debe fallar
        SqliteRepository segundo = new SqliteRepository(db);
        segundo.close();
    }

    @Test
    void pacientesGuardarBuscarYDuplicados() throws Exception {
        SqliteRepository repo = new SqliteRepository(tempDir.resolve("telemed.db"));

        repo.savePatient(new Patient("P002", "Luis", LocalDate.of(1950, 3, 12), null, "8:00", "h", "s"));
        repo.savePatient(new Patient("P001", "Ana", null, 2015, "9:00", "h", "s"));

        assertEquals("Ana", repo.findPatient("P001").get().getName());
        assertNull(repo.findPatient("P002").get().getDiagnosisYear());   // wasNull funciona
        assertTrue(repo.findPatient("P999").isEmpty());
        assertEquals("P001", repo.findAllPatients().get(0).getPatientId()); // ORDER BY

        assertThrows(RepositoryException.class,
                () -> repo.savePatient(new Patient("P001", "Otra", null, null, null, "h", "s")));
        assertThrows(RepositoryException.class,
                () -> repo.updatePatient(new Patient("P777", "Nadie", null, null, null, "h", "s")));

        repo.close();
    }

    @Test
    void reportesGuardarLeerYRechazos() throws Exception {
        SqliteRepository repo = new SqliteRepository(tempDir.resolve("telemed.db"));
        repo.savePatient(new Patient("P001", "Ana", null, null, null, "h", "s"));

        SymptomReport r = new SymptomReport("P001", "REP-1", 3, 2, true,
                LocalDateTime.now().minusMinutes(45), false, "Peor por la tarde");
        repo.saveSymptomReport(r);

        SymptomReport leido = repo.findSymptomReports("P001").get(0);
        assertEquals(3, leido.getRigidityLevel());
        assertTrue(leido.isMedicationTaken());                     // 1 -> true
        assertNull(leido.getSessionId());                          // C4: sin sesión
        assertEquals(r.getTimeStamp(), leido.getTimeStamp());      // no se pierde la fecha
        assertTrue(repo.findSymptomReports("P999").isEmpty());     // nunca null

        // Rechazos: paciente inexistente (FK), id repetido (PK), nivel fuera de rango (validador)
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(
                new SymptomReport("P999", "REP-2", 3, 2, false, null, false, null)));
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(r));
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(
                new SymptomReport("P001", "REP-3", 9, 2, false, null, false, null)));

        // Sesión inexistente: FileRepository lo aceptaba, SQLite lo rechaza (FK session_id)
        SymptomReport conSesionFalsa = new SymptomReport("P001", "REP-4", 3, 2, false, null, false, null);
        conSesionFalsa.setSessionId("SES-NOEXISTE");
        assertThrows(RepositoryException.class, () -> repo.saveSymptomReport(conSesionFalsa));

        repo.close();
    }

    @Test
    void sesionesGuardarActualizarYLeer() throws Exception {
        SqliteRepository repo = new SqliteRepository(tempDir.resolve("telemed.db"));
        repo.savePatient(new Patient("P001", "Ana", null, null, null, "h", "s"));

        LocalDateTime inicio = LocalDateTime.now().minusMinutes(10);
        SessionInfo s = new SessionInfo("SES-1", "P001", inicio, 100, new int[]{0, 1, 2, 3});
        s.setSignalFile("data/signals/P001_SES-1.csv");
        repo.saveSession(s);
        assertEquals(SessionStatus.RECORDING, repo.findSession("SES-1").get().getStatus());

        // Termina la sesión y se actualiza
        s.complete(inicio.plusMinutes(5));
        repo.updateSession(s);
        SessionInfo leida = repo.findSession("SES-1").get();
        assertEquals(SessionStatus.COMPLETED, leida.getStatus());
        assertEquals(inicio.plusMinutes(5), leida.getEndTime());
        assertArrayEquals(new int[]{0, 1, 2, 3}, leida.getChannels());
        assertEquals("data/signals/P001_SES-1.csv", leida.getSignalFile());

        // Orden por start_time: la más antigua primero aunque se guarde después
        repo.saveSession(new SessionInfo("SES-0", "P001", inicio.minusHours(1), 100, new int[]{0}));
        assertEquals("SES-0", repo.findSessions("P001").get(0).getSessionId());

        // Rechazos
        assertThrows(RepositoryException.class,
                () -> repo.saveSession(new SessionInfo("SES-9", "P999", inicio, 100, new int[]{0})));
        assertThrows(RepositoryException.class, () -> repo.saveSession(s));
        assertThrows(RepositoryException.class,
                () -> repo.updateSession(new SessionInfo("SES-X", "P001", inicio, 100, new int[]{0})));

        // Con la sesión ya existente, un reporte que la referencia sí se acepta (FK session_id)
        SymptomReport r = new SymptomReport("P001", "REP-1", 3, 2, false, null, false, null);
        r.setSessionId("SES-1");
        repo.saveSymptomReport(r);
        assertEquals("SES-1", repo.findSymptomReports("P001").get(0).getSessionId());

        repo.close();
    }

    @Test
    void notasGuardarYLeerOrdenadas() throws Exception {
        SqliteRepository repo = new SqliteRepository(tempDir.resolve("telemed.db"));
        repo.savePatient(new Patient("P001", "Ana", null, null, null, "h", "s"));

        ClinicalNote reciente = new ClinicalNote("NOTE-2", "P001", "doctor", "Mejora del temblor");
        ClinicalNote antigua = new ClinicalNote("NOTE-1", "P001", "doctor", "Primera consulta");
        antigua.setTimestamp(LocalDateTime.now().minusDays(3));
        repo.saveClinicalNote(reciente);
        repo.saveClinicalNote(antigua);

        List<ClinicalNote> notas = repo.findClinicalNotes("P001");
        assertEquals(2, notas.size());
        assertEquals("NOTE-1", notas.get(0).getNoteId());          // ORDER BY timestamp
        assertEquals("doctor", notas.get(0).getDoctorId());

        assertThrows(RepositoryException.class, () -> repo.saveClinicalNote(
                new ClinicalNote("NOTE-3", "P999", "doctor", "x")));            // FK
        assertThrows(RepositoryException.class, () -> repo.saveClinicalNote(reciente)); // PK

        repo.close();
    }

    // Test de aceptación "Application restart": los datos sobreviven al cerrar y reabrir
    @Test
    void losDatosSobrevivenAlReinicio() throws Exception {
        Path db = tempDir.resolve("telemed.db");
        SqliteRepository antes = new SqliteRepository(db);
        antes.savePatient(new Patient("P001", "José Núñez", null, null, null, "h", "s"));
        antes.saveSymptomReport(new SymptomReport("P001", "REP-1", 2, 2, false, null, false, null));
        antes.close();                                              // "apagar el servidor"

        Repository despues = new SqliteRepository(db);              // "reiniciar": se usa como Repository
        assertEquals("José Núñez", despues.findPatient("P001").orElseThrow().getName());
        assertEquals(1, despues.findSymptomReports("P001").size());
        ((SqliteRepository) despues).close();
    }
}
