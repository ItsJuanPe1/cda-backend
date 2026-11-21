package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record EvaluacionDocenteDto(
        @JsonProperty("docente_id") Long docenteId,
        @JsonProperty("code_course") String codeCourse,
        @JsonProperty("periodo_id") Long periodoId,
        @JsonProperty("periodo_nombre") String periodoNombre,
        @JsonProperty("calificacion") BigDecimal calificacion) {

    public record Request(
            @JsonProperty("periodoId") Long periodoId,
            @JsonProperty("codeCourse") String codeCourse,
            @JsonProperty("calificacion") BigDecimal calificacion) {
    }

    public record Curso(
            @JsonProperty("docente_id") Long docenteId,
            @JsonProperty("periodo_id") Long periodoId,
            @JsonProperty("periodo_nombre") String periodoNombre,
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("nombre_asignatura") String nombreAsignatura,
            @JsonProperty("calificacion") BigDecimal calificacion,
            @JsonProperty("asignatura_registrada") boolean asignaturaRegistrada) {
    }

    public record Resumen(
            @JsonProperty("docente_id") Long docenteId,
            @JsonProperty("periodo_id") Long periodoId,
            @JsonProperty("periodo_nombre") String periodoNombre,
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("calificacion") BigDecimal calificacion) {
    }

    public record DetalleImportacion(
            @JsonProperty("numero_documento") String numeroDocumento,
            @JsonProperty("nombre_docente") String nombreDocente,
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("nombre_asignatura") String nombreAsignatura,
            @JsonProperty("cantidad_grupos") int cantidadGrupos,
            @JsonProperty("calificacion") BigDecimal calificacion,
            @JsonProperty("etiqueta") String etiqueta) {
    }

    public record ImportacionExcelResponse(
            @JsonProperty("periodo_id") Long periodoId,
            @JsonProperty("periodo_nombre") String periodoNombre,
            @JsonProperty("total_filas_leidas") int totalFilasLeidas,
            @JsonProperty("total_registros_guardados") int totalRegistrosGuardados,
            @JsonProperty("total_promediados") int totalPromediados,
            @JsonProperty("total_no_evaluados") int totalNoEvaluados,
            @JsonProperty("errores") java.util.List<String> errores,
            @JsonProperty("detalles") java.util.List<DetalleImportacion> detalles) {
    }
}
