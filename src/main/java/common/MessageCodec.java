package common;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;


// Natalia metió en el pom la liberia Gson para transformar objetos en texto de una sola linea y viceversa
public class MessageCodec {
    private static final Gson gson = new Gson();

    /**
     * Convierte un objeto Message en una línea de texto JSON.
     */
    public static String encode(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("El mensaje no puede ser nulo");
        }
        return gson.toJson(message);
    }

    /**
     * Convierte una línea de texto JSON en un objeto Message.
     */
    public static Message decode(String jsonLine) throws ProtocolException {
        if (jsonLine == null || jsonLine.trim().isEmpty()) {
            throw new ProtocolException("Mensaje vacío recibido");
        }
        try {
            Message msg = gson.fromJson(jsonLine, Message.class);
            if (msg.getType() == null) {
                throw new ProtocolException("El mensaje recibido no tiene campo 'type'");
            }
            return msg;
        } catch (JsonSyntaxException e) {
            throw new ProtocolException("Formato JSON corrupto o mal formado: " + jsonLine, e);
        }
    }
}