package common;

public enum SessionStatus {
    RECORDING,    // grabando
    COMPLETED,    // terminó por tiempo o el paciente la paró (ambos válidos según el enunciado)
    INCOMPLETE    // cortada: desconexión, caída del cliente, apagado del servidor
}
