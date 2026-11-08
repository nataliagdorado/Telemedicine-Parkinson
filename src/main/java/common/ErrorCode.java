package common;

public enum ErrorCode {
    BAD_REQUEST,            // Mensaje mal formado o JSON invalido
    UNSUPPORTED_TYPE,       // Comando no reconocido en el protocolo
    MISSING_FIELD,          // Falta un campo obligatorio o tiene un tipo incorrecto
    INVALID_FIELD,          // Campo presente pero fuera de rango (ej. rigidez fuera de 0-4)
    UNAUTHENTICATED,        // Peticion sin login o con token no valido
    UNKNOWN_PATIENT,        // El paciente no existe en el hospital
    UNKNOWN_SESSION,        // La sesion de monitorizacion no existe
    STORAGE_ERROR,          // El servidor no pudo leer o guardar los datos
    SERVER_SHUTTING_DOWN    // El administrador esta apagando el servidor

}