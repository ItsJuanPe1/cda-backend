package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProgramaDto {

    public record Response(
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("name_program") String nameProgram,
            @JsonProperty("numero_semestre") Integer numeroSemestre,
            @JsonProperty("id_facultad_programa") Integer idFacultadPrograma,
            @JsonProperty("total_asignaturas") Long totalAsignaturas
    ) {}

    public record CreateRequest(
            @JsonProperty("code_program")
            @NotBlank(message = "El código del programa es obligatorio")
            @Size(max = 50, message = "El código no puede exceder 50 caracteres")
            String codeProgram,

            @JsonProperty("name_program")
            @NotBlank(message = "El nombre del programa es obligatorio")
            @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
            String nameProgram,

            @JsonProperty("numero_semestre")
            @Min(value = 1, message = "El número de semestres debe ser al menos 1")
            @Max(value = 20, message = "El número de semestres no puede exceder 20")
            Integer numeroSemestre,

            @JsonProperty("id_facultad_programa")
            Integer idFacultadPrograma
    ) {}

    public record UpdateRequest(
            @JsonProperty("name_program")
            String nameProgram,

            @JsonProperty("numero_semestre")
            @Min(value = 1, message = "El número de semestres debe ser al menos 1")
            @Max(value = 20, message = "El número de semestres no puede exceder 20")
            Integer numeroSemestre,

            @JsonProperty("id_facultad_programa")
            Integer idFacultadPrograma
    ) {}
}
