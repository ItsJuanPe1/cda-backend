package co.edu.univalle.palmira.model.converter;

import co.edu.univalle.palmira.model.enums.TipoDocente;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoDocenteConverter implements AttributeConverter<TipoDocente, String> {

    @Override
    public String convertToDatabaseColumn(TipoDocente attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TipoDocente convertToEntityAttribute(String dbData) {
        return TipoDocente.fromValue(dbData);
    }
}
