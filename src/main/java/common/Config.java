package common;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

// Con esta clase, si cambio de ordenador o de puerto, solo hay que editar el archivo de texto externo sin recompilar nada
// (esto está porque lo pide Alberto en su documento)
// Carga parámetros externos desde config/config.properties (server.port, server.host, data.dir, admin.password)
public class Config {
    private static final Properties properties = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("config/config.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("Aviso: No se pudo cargar config/config.properties. Usando valores por defecto.");
        }
    }

    // Prioridad: 1) propiedad del sistema (-Dclave=valor o System.setProperty, la usan los tests)
    //            2) config/config.properties   3) valor por defecto
    public static String get(String key, String defaultValue) {
        String override = System.getProperty(key);
        if (override != null) {
            return override;
        }
        return properties.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}