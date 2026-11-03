package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;
import lombok.*;

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
        private Integer semestre;
        private Jornada jornada;
        private String numeroResolucion;
        private Integer grupo;
        private Long idDocente;
        private String nombreDocente;
        private String estado;
    }
}
