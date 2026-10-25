package common;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

// Con esta clase, si cambio de ordenador o de puerto, solo hay que editar el archivo de texto externo sin recompilar nada
// (esto está porque lo pide Alberto en su documento)
public class Config {
    private static final Properties properties = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("config/config.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("Aviso: No se pudo cargar config/config.properties. Usando valores por defecto.");
        }
    }

    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}