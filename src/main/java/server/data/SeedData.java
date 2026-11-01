package server.data;

import java.time.LocalDate;
import java.util.List;

/**
 * Pacientes iniciales (ficticios) que el hospital tiene dados de alta.
 * El servidor los carga al arrancar: sin ellos no se podría guardar ningún reporte,
 * porque el proyecto no tiene pantalla de registro de pacientes.
 */
public final class SeedData {

    private SeedData() {}   // solo métodos estáticos: no se instancia

    /** Lista nueva en cada llamada: Patient es mutable y no conviene compartir instancias. */
    static List<Patient> demoPatients() {
        return List.of(
                new Patient("P001", "José Núñez",  LocalDate.of(1955, 3, 12),  2018, "Levodopa 8:00, 14:00, 20:00"),
                new Patient("P002", "Ana Ruiz",    LocalDate.of(1961, 7, 2),   2021, "Levodopa 9:00, 21:00"),
                new Patient("P003", "Carmen Peña", LocalDate.of(1948, 11, 25), 2012, "Levodopa 8:00, 12:00, 16:00, 20:00; Rasagilina 8:00"),
                new Patient("P004", "Luis García", LocalDate.of(1970, 1, 30),  2024, "Pramipexol 9:00, 21:00")
        );
    }
    /**
     * Da de alta los pacientes de prueba que todavía no existan.
     * Idempotente: se puede llamar en cada arranque sin duplicar nada.
     *
     * @return cuántos pacientes se han añadido (0 si ya estaban todos)
     */
    public static int loadIfMissing(Repository repo) throws RepositoryException {
        if (repo == null) {
            throw new RepositoryException("Falta el repositorio");
        }
        int added = 0;
        for (Patient p : demoPatients()) {
            if (repo.findPatient(p.getPatientId()).isEmpty()) {
                repo.savePatient(p);
                added++;
            }
        }
        return added;
    }
}