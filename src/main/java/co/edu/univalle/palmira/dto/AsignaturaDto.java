package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
            Boolean activo,
            @JsonProperty("horas_semanales") Integer horasSemanales
    ) {
        // Compatibilidad con quien construye la respuesta sin horas
        public Response(String codeCourse, String nameCourse, Integer credits, Boolean isElective,
                        Boolean esComplementaria, Boolean esBasica, String facultad,
                        Integer idFacultad, Boolean activo) {
            this(codeCourse, nameCourse, credits, isElective, esComplementaria, esBasica,
                    facultad, idFacultad, activo, null);
        }
    }

    public record CreateRequest(
            @JsonProperty("code_course")
            @NotBlank(message = "El código de la asignatura es obligatorio")
            @Size(max = 8, message = "El código no puede exceder 8 caracteres")
            @Pattern(regexp = "^[A-Za-z0-9]{1,8}$", message = "El código debe ser alfanumérico y tener máximo 8 caracteres")
            String codeCourse,

            @JsonProperty("name_course")
            @NotBlank(message = "El nombre de la asignatura es obligatorio")
            @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
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

            @JsonProperty("horas_semanales")
            @Min(value = 1, message = "Las horas semanales deben estar entre 1 y 20")
            @Max(value = 20, message = "Las horas semanales deben estar entre 1 y 20")
            Integer horasSemanales
    ) {
        public CreateRequest(String codeCourse, String nameCourse, Integer credits, Boolean isElective,
                             Boolean esComplementaria, Boolean esBasica, String facultad, Integer idFacultad) {
            this(codeCourse, nameCourse, credits, isElective, esComplementaria, esBasica,
                    facultad, idFacultad, null);
        }
    }

    public record UpdateRequest(
            @JsonProperty("name_course")
            @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
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

            Boolean activo,

            @JsonProperty("horas_semanales")
            @Min(value = 1, message = "Las horas semanales deben estar entre 1 y 20")
            @Max(value = 20, message = "Las horas semanales deben estar entre 1 y 20")
            Integer horasSemanales
    ) {
        public UpdateRequest(String nameCourse, Integer credits, Boolean isElective, Boolean esComplementaria,
                             Boolean esBasica, String facultad, Integer idFacultad, Boolean activo) {
            this(nameCourse, credits, isElective, esComplementaria, esBasica, facultad, idFacultad, activo, null);
        }
    }
}