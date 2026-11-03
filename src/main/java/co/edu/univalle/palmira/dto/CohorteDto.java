package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

public class CohorteDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotNull(message = "El semestre es obligatorio")
        @Min(value = 1, message = "El semestre debe ser mayor o igual a 1")
        @Max(value = 20, message = "El semestre no puede superar 20")
        private Integer semestre;

        @NotNull(message = "La jornada es obligatoria")
        private Jornada jornada;

        @NotBlank(message = "El número de resolución es obligatorio")
        private String numeroResolucion;

        @Builder.Default
        private Boolean confirmada = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveBatchRequest {
        @NotNull(message = "La lista de cohortes es obligatoria")
        private List<Request> cohortes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long periodoId;
        private String codeProgram;
        private String nombrePrograma;
        private Integer semestre;
        private Jornada jornada;
        private String numeroResolucion;
        private Boolean confirmada;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PropuestaResponse {
        private String codeProgram;
        private String nombrePrograma;
        private Jornada jornada;
        private Integer duracionDiurna;
        private Integer duracionNocturna;
        private String ingreso;
        private String advertenciaPeriodoAnterior;
        private List<PropuestaCohorteItem> cohortesPropuestas;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PropuestaCohorteItem {
        private Integer semestre;
        private Jornada jornada;
        private String numeroResolucion;
        private Boolean enExtincion;
    }
}
