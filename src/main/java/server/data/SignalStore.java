package server.data;

import common.SampleBlock;
import common.SessionInfo;
/**
 * Guarda las muestras del BITalino en un fichero por sesión a medida que llegan.
 * Requisito obligatorio: fichero con el nombre del paciente y la fecha/hora
 *
 * Ciclo de vida de una grabación
 *  openRecording  ->  appendBlock (n veces)  ->  closeRecording
 *
 * Para hilos: cada paciente (hilo) graba a la vez, cada uno en su fichero
 * Cada bloque se escribe en el disco al llegar (flush): si el paciente se desconecta, se mantiene lo recibido
 */
public interface SignalStore extends AutoCloseable{
    /**
     * Crea el fichero de la sesión y escribe su cabecera.
     * @return la ruta del fichero creado, para guardarla en SessionInfo.signalFile
     */
    String openRecording(SessionInfo info, Patient patient) throws RepositoryException;

    /** Añade un bloque al fichero de su sesión. Falla si la sesión no está abierta. */
    void appendBlock(SampleBlock block) throws RepositoryException;

    /** Cierra el fichero de la sesión. Llamarlo dos veces no es un error. */
    void closeRecording(String sessionId) throws RepositoryException;

    /** Cierra todos los ficheros abiertos (al apagar el servidor). */
    @Override
    void close() throws RepositoryException;
}
