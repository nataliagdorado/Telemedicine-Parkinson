package database;

/**
 * Programa de prueba para ver qué ficheros genera FileSignalStore sin necesitar el BITalino:
 * usa el simulador del proyecto (SimulatedSignalSource) y graba dos pacientes a la vez,
 * cada uno en su hilo, en la carpeta data-demo/signals.
 */

import common.SampleBlock;
import common.SessionInfo;
import patient.SimulatedSignalSource;
import server.data.FileSignalStore;
import server.data.IdGenerator;
import server.data.Patient;
import server.data.RepositoryException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SignalStoreDemo {

    private static final Path SIGNALS_DIR = Path.of("data-demo", "signals");
    private static final int FS_HZ = 100;               // frecuencia de muestreo simulada
    private static final int BLOCK_SIZE = 100;          // igual que MonitoringSessionController
    private static final int N_BLOCKS = 10;             // 10 bloques x 100 = 1000 muestras = 10 s a 100 Hz
    private static final int[] CHANNELS = {0, 1, 2, 3}; // EMG + ACC X/Y/Z

    public static void main(String[] args) throws Exception {
        deleteFolder(SIGNALS_DIR);                      // empezar de cero en cada ejecución
        FileSignalStore store = new FileSignalStore(SIGNALS_DIR);

        Patient p1 = new Patient("P001", "José Núñez", LocalDate.of(1955, 3, 12), 2018, "Levodopa 8:00, 14:00, 20:00");
        Patient p2 = new Patient("P002", "Ana / O'Brien", LocalDate.of(1961, 7, 2), 2021, "Levodopa 9:00, 21:00");

        // Un hilo por paciente, como los ClientHandler del servidor
        List<String> files = new ArrayList<>();
        Thread t1 = new Thread(() -> record(store, p1, files), "Hilo-P001");
        Thread t2 = new Thread(() -> record(store, p2, files), "Hilo-P002");
        t1.start();
        t2.start();
        t1.join();                                      // esperar a que los dos terminen
        t2.join();

        // Antes de cerrar: comprobar que el flush por bloque ya ha llevado todo a disco
        System.out.println("\n=== Antes de close(): lo que ya está en disco ===");
        for (String f : files) {
            List<String> lines = Files.readAllLines(Path.of(f));
            System.out.println(Path.of(f).getFileName() + " -> " + (lines.size() - 3) + " muestras");
        }

        store.close();                                  // cierra todas las grabaciones abiertas

        System.out.println("\n=== Contenido de los ficheros ===");
        for (String f : files) {
            List<String> lines = Files.readAllLines(Path.of(f));
            System.out.println("\n--- " + Path.of(f).getFileName());
            lines.subList(0, 6).forEach(System.out::println);           // cabecera + 3 muestras
            System.out.println("...");
            System.out.println(lines.get(lines.size() - 1));            // última muestra
        }
        System.out.println("\nFicheros en: " + SIGNALS_DIR.toAbsolutePath());
    }

    /** Simula una sesión completa de un paciente: abrir, N bloques, sin cerrar (lo hace close()). */
    private static void record(FileSignalStore store, Patient patient, List<String> files) {
        SimulatedSignalSource source = new SimulatedSignalSource();
        source.connect("SIMULADO", FS_HZ);
        source.startAcquisition(CHANNELS);

        SessionInfo info = new SessionInfo(IdGenerator.newSessionId(), patient.getPatientId(),
                LocalDateTime.now(), FS_HZ, CHANNELS);
        try {
            String file = store.openRecording(info, patient);
            synchronized (files) {                      // la lista la comparten los dos hilos
                files.add(file);
            }
            for (int i = 0; i < N_BLOCKS; i++) {
                SampleBlock block = source.readBlock(BLOCK_SIZE, info.getSessionId());
                store.appendBlock(block);
                System.out.println(Thread.currentThread().getName() + ": bloque " + (i + 1) + "/" + N_BLOCKS);
            }
        } catch (RepositoryException e) {
            System.err.println(Thread.currentThread().getName() + ": " + e.getMessage());
        } finally {
            source.stopAcquisition();
        }
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
