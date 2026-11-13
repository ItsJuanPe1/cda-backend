package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** HU: el docente consulta los cursos que tiene asignados en el período vigente. */
public class MisCursosDto {

    public record Periodo(
            @JsonProperty("id") Long id,
            @JsonProperty("nombre") String nombre) {}

    public record Curso(
            @JsonProperty("curso_id") Long cursoId,
            @JsonProperty("codigo_grupo") String codigoGrupo,
            @JsonProperty("nombre_asignatura") String nombreAsignatura,
            @JsonProperty("nombre_programa") String nombrePrograma,
            @JsonProperty("jornada") String jornada,
            @JsonProperty("horas") int horas,
            @JsonProperty("sin_horas") boolean sinHoras) {}

    public record Respuesta(
            @JsonProperty("periodo") Periodo periodo,
            @JsonProperty("total_horas") int totalHoras,
            @JsonProperty("limite") int limite,
            @JsonProperty("disponibles") int disponibles,
            @JsonProperty("cursos") List<Curso> cursos) {}
}