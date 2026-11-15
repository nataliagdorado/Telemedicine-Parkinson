package common;

public final class SymptomCatalog {

    private SymptomCatalog() {}   // clase de constantes: no se instancia

    public static final int  MIN_LEVEL = 1;   //
    public static final int  MAX_LEVEL = 5;   //
    public static final int  MAX_NOTES_LENGTH = 500;
    public static final long MAX_HOURS_SINCE_DOSE = 24;
    private static final String[] LEVEL_LABELS = {"Normal", "Muy leve", "Leve", "Moderado", "Grave"};

    public static String labelFor(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Nivel fuera de escala: " + level);
        }
        return LEVEL_LABELS[level - 1];
    }
}