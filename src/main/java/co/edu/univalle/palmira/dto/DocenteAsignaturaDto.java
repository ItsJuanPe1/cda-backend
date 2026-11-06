package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.OrigenAsociacion;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class DocenteAsignaturaDto {

    /** HU-049: asociación individual docente-asignatura. La facultad se deriva del docente. */
    public record CreateRequest(
            @JsonProperty("docente_id")
            @NotNull(message = "El docente es obligatorio")
            Long docenteId,

            @JsonProperty("code_course")
            @NotBlank(message = "La asignatura es obligatoria")
            String codeCourse
    ) {}

    public record Response(
            @JsonProperty("id") Long id,
            @JsonProperty("docente_id") Long docenteId,
            @JsonProperty("docente_codigo") String docenteCodigo,
            @JsonProperty("docente_nombre") String docenteNombre,
            @JsonProperty("correo_institucional") String correoInstitucional,
            @JsonProperty("numero_documento") String numeroDocumento,
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("name_course") String nameCourse,
            @JsonProperty("id_facultad") Integer idFacultad,
            @JsonProperty("nombre_facultad") String nombreFacultad,
            @JsonProperty("periodo_id") Long periodoId,
            @JsonProperty("periodo_nombre") String periodoNombre,
            @JsonProperty("origen") OrigenAsociacion origen,
            @JsonProperty("activo") Boolean activo,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("desactivado_at") Instant desactivadoAt
    ) {}
}
