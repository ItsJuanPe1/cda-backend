package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;
import lombok.*;

import java.time.Instant;
import java.util.List;


public class ProgramacionDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GenerarRequest {
        @Builder.Default
        private boolean sobrescribir = false;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResumenResponse {
        private long totalCursos;
        private List<ResumenProgramaItem> programas;
        private String mensaje;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResumenProgramaItem {
        private String codeProgram;
        private String nombrePrograma;
        private long totalCursos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CursoResponse {
        private Long id;
        private Long periodoId;
        private String codeProgram;
        private String nombrePrograma;
        private String codeCourse;
        private String nombreAsignatura;
        private Integer credits;
        /** Horas semanales requeridas para elegir docente, sin planificar horarios. */
        private Integer horasSemanales;
        private Integer semestre;
        private Jornada jornada;
        private String numeroResolucion;
        private Integer grupo;
        /** HU-059: cupos de estudiantes del curso/grupo; null si aún no se registran. */
        private Integer cupos;
        private Long idDocente;
        private String nombreDocente;
        private String estado;
        /** HU-047: CÓDIGO-GRUPO, por ejemplo MAT101-50; null si el curso está excluido. */
        private String codigoCurso;
        private String justificacionExclusion;
        private String excluidoPor;
        private Instant fechaExclusion;
        /** HU-047 criterio 4: se calcula al consultar. */
        private boolean requiereRevision;
        private String motivoRevision;
    }

    /** HU-047: justificación para excluir o reactivar un curso. La validación está en el servicio. */
    public record JustificacionRequest(String justificacion) {}

    //HU-059

    /** Cupos de un curso/grupo dentro de la solicitud de actualización. */
    public record CupoItemRequest(Long cursoId, Integer cupos) {}

    /** HU-059: actualización de cupos de los cursos activos de un período. */
    public record ActualizarCuposRequest(Long periodoId, List<CupoItemRequest> cursos) {}

    /** HU-059: parámetros que el frontend usa para habilitar o bloquear la edición. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CuposConfigResponse {
        private Integer maxPorGrupo;
        private boolean plazoAbierto;
        private boolean puedeEditar;
        private boolean admin;
    }

    /** HU-059: resultado de guardar los cupos. */
    public record ActualizarCuposResponse(List<CursoResponse> cursos, String mensaje) {}

    /** HU-047: cómo quedan los grupos de una asignatura en el periodo (vista previa o resultado). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AjusteGruposResponse {
        private Long cursoId;
        private Long periodoId;
        private String codeCourse;
        private String nombreAsignatura;
        /** El curso afectado tiene docente asignado: el frontend avisa antes de confirmar. */
        private boolean tieneDocente;
        private List<GrupoCambio> cursos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GrupoCambio {
        private Long id;
        private String codeProgram;
        private String nombrePrograma;
        private Jornada jornada;
        private Integer grupoAntes;
        private Integer grupoDespues;
        /** Excluido después del ajuste. */
        private boolean excluido;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AsignarDocenteRequest {
        private Long idDocente;
        private Boolean reemplazarDocente;
        private Long periodoId;
        private String codeProgram;
        private String codeCourse;
        private Integer semestre;
    }
}
