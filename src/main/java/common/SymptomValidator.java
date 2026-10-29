package common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Valida un SymptomReport. Lo usan el cliente (feedback en el formulario) y el servidor (nunca se fía del cliente). */
public final class SymptomValidator {

    private SymptomValidator() {}

    /** Devuelve TODOS los errores encontrados. Lista vacía = reporte válido. */
    public static List<String> validate(SymptomReport r) {
        List<String> errors = new ArrayList<>();
        if (r == null) {
            errors.add("El reporte está vacío");
            return errors;
        }

        if (r.getPatientId() == null || r.getPatientId().isBlank()) {
            errors.add("Falta el identificador del paciente");
        }

        checkLevel("Rigidez", r.getRigidityLevel(), errors);
        checkLevel("Temblor", r.getTremorLevel(), errors);

        LocalDateTime reportTime = r.getTimeStamp();
        if (reportTime == null) {
            errors.add("Falta la fecha del reporte");
        }

        LocalDateTime dose = r.getLastDoseTime();
        if (dose != null && reportTime != null) {
            if (dose.isAfter(reportTime)) {
                errors.add("La última dosis no puede ser posterior al reporte");
            } else if (Duration.between(dose, reportTime).toHours() > SymptomCatalog.MAX_HOURS_SINCE_DOSE) {
                errors.add("La última dosis es de hace más de " + SymptomCatalog.MAX_HOURS_SINCE_DOSE + " h: revisa la hora");
            }
        }

        if (r.getNotes() != null && r.getNotes().length() > SymptomCatalog.MAX_NOTES_LENGTH) {
            errors.add("Las notas superan " + SymptomCatalog.MAX_NOTES_LENGTH + " caracteres");
        }

        return errors;
    }

    public static boolean isValid(SymptomReport r) {
        return validate(r).isEmpty();
    }

    private static void checkLevel(String name, int value, List<String> errors) {
        if (value < SymptomCatalog.MIN_LEVEL || value > SymptomCatalog.MAX_LEVEL) {
            errors.add(name + " debe estar entre " + SymptomCatalog.MIN_LEVEL + " y " + SymptomCatalog.MAX_LEVEL
                    + " (recibido: " + value + ")");
        }
    }
}