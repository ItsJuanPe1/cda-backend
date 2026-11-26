package co.edu.univalle.palmira.model.converter;

import co.edu.univalle.palmira.model.enums.CategoriaDocente;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CategoriaDocenteConverter implements AttributeConverter<CategoriaDocente, String> {

    @Override
    public String convertToDatabaseColumn(CategoriaDocente attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CategoriaDocente convertToEntityAttribute(String dbData) {
        return CategoriaDocente.fromValue(dbData);
    }
}
