package common;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Codificador y decodificador JSON para el protocolo de red.
 * Incluye adaptador para serializar LocalDateTime de forma segura en Java 17.
 */
public class MessageCodec {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
                    return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(FORMATTER));
                }
            })
            .registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                        throws JsonParseException {
                    if (json == null || json.isJsonNull() || json.getAsString().trim().isEmpty()) {
                        return null;
                    }
                    return LocalDateTime.parse(json.getAsString(), FORMATTER);
                }
            })
            .create();

    public static String encode(Message message) {
        if (message == null) {
            return "";
        }
        return GSON.toJson(message);
    }

    public static Message decode(String json) throws ProtocolException {
        if (json == null || json.trim().isEmpty()) {
            throw new ProtocolException("Received empty or null JSON payload");
        }
        try {
            Message msg = GSON.fromJson(json, Message.class);
            if (msg == null) {
                throw new ProtocolException("Failed to parse JSON into Message object");
            }
            return msg;
        } catch (JsonSyntaxException e) {
            throw new ProtocolException("Malformed JSON string: " + json, e);
        }
    }
}