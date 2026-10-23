package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PensumItemDto {

    public record Response(
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("name_course") String nameCourse,
            Integer credits,
            Integer semestre,
            @JsonProperty("is_elective") Boolean isElective,
            @JsonProperty("es_complementaria") Boolean esComplementaria,
            @JsonProperty("es_basica") Boolean esBasica,
            String facultad,
            Boolean activo
    ) {}

    public record AddRequest(
            @JsonProperty("code_program")
            @NotBlank(message = "El código del programa es obligatorio")
            String codeProgram,

            @JsonProperty("code_course")
            @NotBlank(message = "El código de la asignatura es obligatorio")
            String codeCourse,

            @NotNull(message = "El semestre es obligatorio")
            @Min(value = 1, message = "El semestre debe ser al menos 1")
            @Max(value = 20, message = "El semestre no puede exceder 20")
            Integer semestre
    ) {}

    public record UpdateSemesterRequest(
            @JsonProperty("code_program")
            @NotBlank(message = "El código del programa es obligatorio")
            String codeProgram,

            @JsonProperty("code_course")
            @NotBlank(message = "El código de la asignatura es obligatorio")
            String codeCourse,

            @NotNull(message = "El nuevo semestre es obligatorio")
            @Min(value = 1, message = "El semestre debe ser al menos 1")
            @Max(value = 20, message = "El semestre no puede exceder 20")
            Integer semestre
    ) {}
}
