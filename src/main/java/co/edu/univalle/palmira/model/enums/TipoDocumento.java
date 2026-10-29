package co.edu.univalle.palmira.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoDocumento {
    CC,
    CE,
    PP;

    @JsonCreator
    public static TipoDocumento fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (TipoDocumento tipo : TipoDocumento.values()) {
            if (tipo.name().equalsIgnoreCase(value.trim())) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de documento no válido: '" + value + "'. Valores permitidos: CC, CE, PP");
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
