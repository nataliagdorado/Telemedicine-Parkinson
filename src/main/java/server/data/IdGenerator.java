package server.data;

import java.util.UUID;

/**
 * Genera identificadores únicos para reportes, sesiones y notas.
 * Los ids de paciente NO salen de aquí: los asigna el hospital (SeedData)
 *
 * Usa UUID aleatorio: no guarda estado, así que es seguro para hilos y no
 * se repite aunque el servidor se reinicie
 */

public final class IdGenerator {

    private IdGenerator() {}   // solo métodos estáticos: no se instancia

    public static String newReportId()  { return next("REP"); }

    public static String newSessionId() { return next("SES"); }

    public static String newNoteId()    { return next("NOTE"); }

    private static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}