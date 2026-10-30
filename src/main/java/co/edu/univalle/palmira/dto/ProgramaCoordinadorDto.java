package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;
import com.fasterxml.jackson.annotation.JsonProperty;

// HU-037: programa académico con su coordinador (docs/contratos/HU-037.md)
public class ProgramaCoordinadorDto {

    public record Response(
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("name_program") String nameProgram,
            @JsonProperty("jornada") Jornada jornada,
            @JsonProperty("tiene_coordinador") boolean tieneCoordinador,
            // null cuando el programa está «Sin coordinador»
            @JsonProperty("coordinador") CoordinadorResumen coordinador
    ) {}

    public record CoordinadorResumen(
            @JsonProperty("id") Integer id,
            @JsonProperty("nombre_completo") String nombreCompleto,
            @JsonProperty("correo_institucional") String correoInstitucional
    ) {}
}
