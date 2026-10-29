package common;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;  // [NUEVO]
import java.util.function.Function;
/**
 * Codificador y decodificador JSON para el protocolo de red.
 * Incluye adaptador para serializar LocalDateTime de forma segura en Java 17.
 */
public class MessageCodec {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
                    return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(LOCAL_DATE_TIME));
                }
            })
            .registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                        throws JsonParseException {
                    return parseDate(json, text -> LocalDateTime.parse(text, LOCAL_DATE_TIME));
                }
            })

            .registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {
                @Override
                public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
                    return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(LOCAL_DATE));
                }
            })
            // [NUEVO] texto -> LocalDate
            .registerTypeAdapter(LocalDate.class, new JsonDeserializer<LocalDate>() {
                @Override
                public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                        throws JsonParseException {
                    return parseDate(json, text -> LocalDate.parse(text, LOCAL_DATE));
                }
            })
            .create();

    // [NUEVO] Convierte texto en fecha. Si el formato es inválido, lanza JsonParseException
    // (y no DateTimeParseException) para que decode() la capture.
    private static <T> T parseDate(JsonElement json, Function<String, T> parser) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive()) {
            throw new JsonParseException("Se esperaba una fecha como texto y llegó: " + json);
        }
        String text = json.getAsString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return parser.apply(text);
        } catch (DateTimeParseException e) {
            throw new JsonParseException("Fecha con formato inválido: " + text, e);
        }
    }


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
        } catch (JsonParseException e) {
            throw new ProtocolException("Malformed JSON string: " + json, e);
        }
    }

    // [NUEVO] Convierte cualquier objeto a JSON con las mismas reglas de fechas
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    // [NUEVO] Convierte JSON en un objeto de la clase indicada (Patient, SymptomReport...)
    public static <T> T fromJson(String json, Class<T> type) throws ProtocolException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonParseException e) {
            throw new ProtocolException("Malformed JSON string: " + json, e);
        }
    }
}