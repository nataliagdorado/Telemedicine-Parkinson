package common;

public enum ErrorCode {
    BAD_REQUEST,            // Mensaje mal formado o JSON inválido
    UNSUPPORTED_TYPE,       // Comando no reconocido en el protocolo
    UNAUTHENTICATED,        // Intento de enviar datos sin hacer login previo
    INVALID_FIELD,          // Algún dato fuera de rango (ej. rigidez fuera de 1-5)
    UNKNOWN_SESSION,        // Intento de enviar muestras a una sesión que no existe
    SERVER_SHUTTING_DOWN    // El administrador está apagando el servidor
}