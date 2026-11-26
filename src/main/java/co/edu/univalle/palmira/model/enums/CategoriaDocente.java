package co.edu.univalle.palmira.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum CategoriaDocente {
    AUXILIAR,
    ASISTENTE,
    ASOCIADO,
    TITULAR;

    @JsonCreator
    public static CategoriaDocente fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT)
                .replaceFirst("^PROFESOR\\s+", "");
        return CategoriaDocente.valueOf(norm);
    }
}
