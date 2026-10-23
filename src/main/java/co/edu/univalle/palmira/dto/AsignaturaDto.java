package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AsignaturaDto {

    public record Response(
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("name_course") String nameCourse,
            Integer credits,
            @JsonProperty("is_elective") Boolean isElective,
            @JsonProperty("es_complementaria") Boolean esComplementaria,
            @JsonProperty("es_basica") Boolean esBasica,
            String facultad,
            @JsonProperty("id_facultad") Integer idFacultad,
            Boolean activo
    ) {}

    public record CreateRequest(
            @JsonProperty("code_course")
            @NotBlank(message = "El código de la asignatura es obligatorio")
            @Size(max = 50, message = "El código no puede exceder 50 caracteres")
            String codeCourse,

            @JsonProperty("name_course")
            @NotBlank(message = "El nombre de la asignatura es obligatorio")
            @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
            String nameCourse,

            @Min(value = 0, message = "Los créditos deben ser mayores o iguales a 0")
            Integer credits,

            @JsonProperty("is_elective")
            Boolean isElective,

            @JsonProperty("es_complementaria")
            Boolean esComplementaria,

            @JsonProperty("es_basica")
            Boolean esBasica,

            String facultad,

            @JsonProperty("id_facultad")
            Integer idFacultad
    ) {}

    public record UpdateRequest(
            @JsonProperty("name_course")
            String nameCourse,

            @Min(value = 0, message = "Los créditos deben ser mayores o iguales a 0")
            Integer credits,

            @JsonProperty("is_elective")
            Boolean isElective,

            @JsonProperty("es_complementaria")
            Boolean esComplementaria,

            @JsonProperty("es_basica")
            Boolean esBasica,

            String facultad,

            @JsonProperty("id_facultad")
            Integer idFacultad,

            Boolean activo
    ) {}
}
