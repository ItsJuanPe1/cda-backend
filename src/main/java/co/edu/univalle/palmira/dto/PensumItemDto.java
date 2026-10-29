package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

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
            Boolean activo,
            Integer version,
            Boolean vigente,
            @JsonProperty("horas_clase") Integer horasClase,
            @JsonProperty("horas_independiente") Integer horasIndependiente,
            @JsonProperty("tipo_asignatura") String tipoAsignatura,
            @JsonProperty("descripcion") String descripcion,
            @JsonProperty("jornada") String jornada,
            @JsonProperty("periodo_oferta") String periodoOferta,
            @JsonProperty("numero_resolucion") String numeroResolucion,
            @JsonProperty("numero_semestres") Integer numeroSemestres,
            @JsonProperty("editada") Boolean editada,
            @JsonProperty("fecha_edicion") LocalDateTime fechaEdicion
    ) {
        public Response(
                String codeProgram,
                String codeCourse,
                String nameCourse,
                Integer credits,
                Integer semestre,
                Boolean isElective,
                Boolean esComplementaria,
                Boolean esBasica,
                String facultad,
                Boolean activo,
                Integer version,
                Boolean vigente
        ) {
            this(codeProgram, codeCourse, nameCourse, credits, semestre, isElective,
                    esComplementaria, esBasica, facultad, activo, version, vigente,
                    null, null, null, null, null, null, null, null, false, null);
        }

        public Response(
                String codeProgram,
                String codeCourse,
                String nameCourse,
                Integer credits,
                Integer semestre,
                Boolean isElective,
                Boolean esComplementaria,
                Boolean esBasica,
                String facultad,
                Boolean activo,
                Integer version,
                Boolean vigente,
                Integer horasClase,
                Integer horasIndependiente,
                String tipoAsignatura,
                String descripcion,
                String jornada,
                String periodoOferta
        ) {
            this(codeProgram, codeCourse, nameCourse, credits, semestre, isElective,
                    esComplementaria, esBasica, facultad, activo, version, vigente,
                    horasClase, horasIndependiente, tipoAsignatura, descripcion, jornada, periodoOferta,
                    null, null, false, null);
        }
    }

    public record SaveItem(
            @JsonProperty("code_course") @NotBlank(message = "El código de la asignatura es obligatorio") String codeCourse,
            @NotNull(message = "El semestre es obligatorio") @Min(1) @Max(20) Integer semestre,
            @JsonProperty("name_course") String nameCourse,
            Integer credits,
            @JsonProperty("horas_clase") Integer horasClase,
            @JsonProperty("horas_independiente") Integer horasIndependiente,
            @JsonProperty("tipo_asignatura") String tipoAsignatura,
            @JsonProperty("original_code_course") String originalCodeCourse
    ) {
        public SaveItem(String codeCourse, Integer semestre) {
            this(codeCourse, semestre, null, null, null, null, null, null);
        }
    }

    public record SaveRequest(
            @JsonProperty("code_program") @NotBlank(message = "El código del programa es obligatorio") String codeProgram,
            @NotNull(message = "Debe incluir las asignaturas de la malla") List<SaveItem> items,
            @JsonProperty("name_program") String nameProgram,
            @JsonProperty("descripcion") String descripcion,
            @JsonProperty("jornada") String jornada,
            @JsonProperty("periodo_oferta") String periodoOferta,
            @JsonProperty("numero_semestre") Integer numeroSemestre,
            @JsonProperty("numero_resolucion") String numeroResolucion
    ) {
        public SaveRequest(String codeProgram, List<SaveItem> items) {
            this(codeProgram, items, null, null, null, null, null, null);
        }

        public SaveRequest(
                String codeProgram,
                List<SaveItem> items,
                String nameProgram,
                String descripcion,
                String jornada,
                String periodoOferta,
                Integer numeroSemestre
        ) {
            this(codeProgram, items, nameProgram, descripcion, jornada, periodoOferta, numeroSemestre, null);
        }
    }

    public record HistorySummary(
            Integer version,
            LocalDateTime fechaDesactivacion,
            Integer totalAsignaturas,
            @JsonProperty("numero_resolucion") String numeroResolucion,
            @JsonProperty("numero_semestres") Integer numeroSemestres,
            @JsonProperty("editada") Boolean editada,
            @JsonProperty("fecha_edicion") LocalDateTime fechaEdicion
    ) {
        public HistorySummary(
                Integer version,
                LocalDateTime fechaDesactivacion,
                Integer totalAsignaturas
        ) {
            this(version, fechaDesactivacion, totalAsignaturas, null, null, false, null);
        }
    }

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
    ) {
    }

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
    ) {
    }
}
