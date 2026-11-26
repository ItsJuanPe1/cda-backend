package co.edu.univalle.palmira.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum TipoDocente {
    NOMBRADO,
    OCASIONAL,
    CATEDRA,
    HORA_CATEDRA;

    @JsonCreator
    public static TipoDocente fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT)
                .replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
                .replace(' ', '_');
        if ("HORA_CATEDRA".equals(norm)) {
            return HORA_CATEDRA;
        }
        return TipoDocente.valueOf(norm);
    }
}
