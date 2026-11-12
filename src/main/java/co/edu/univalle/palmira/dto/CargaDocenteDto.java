package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CargaDocenteDto {

    public record CursoCarga(
            @JsonProperty("curso_id") Long cursoId,
            @JsonProperty("code_program") String codeProgram,
            @JsonProperty("code_course") String codeCourse,
            @JsonProperty("nombre_asignatura") String nombreAsignatura,
            @JsonProperty("grupo") Integer grupo,
            @JsonProperty("horas") int horas,
            @JsonProperty("sin_horas") boolean sinHoras) {}

    public record Detalle(
            @JsonProperty("docente_id") Long docenteId,
            @JsonProperty("periodo_id") Long periodoId,
            @JsonProperty("total_horas") int totalHoras,
            @JsonProperty("limite") int limite,
            @JsonProperty("disponibles") int disponibles,
            @JsonProperty("cursos") List<CursoCarga> cursos,
            @JsonProperty("otros_programas_horas") int otrosProgramasHoras) {}

    public record Resumen(
            @JsonProperty("docente_id") Long docenteId,
            @JsonProperty("total_horas") int totalHoras,
            @JsonProperty("limite") int limite,
            @JsonProperty("disponibles") int disponibles,
            @JsonProperty("cursos_sin_horas") int cursosSinHoras) {}
}