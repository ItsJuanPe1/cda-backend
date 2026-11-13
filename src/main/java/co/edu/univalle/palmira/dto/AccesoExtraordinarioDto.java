package co.edu.univalle.palmira.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class AccesoExtraordinarioDto {

    public record Request(
            @JsonProperty("fecha_fin")
            @NotNull(message = "La fecha y hora de fin son obligatorias")
            Instant fechaFin,

            @JsonProperty("motivo")
            @NotBlank(message = "El motivo es obligatorio")
            @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
            String motivo) {}

    public record Respuesta(
            @JsonProperty("id") Long id,
            @JsonProperty("docente_id") Long docenteId,
            @JsonProperty("fecha_inicio") Instant fechaInicio,
            @JsonProperty("fecha_fin") Instant fechaFin,
            @JsonProperty("motivo") String motivo,
            @JsonProperty("otorgado_por") String otorgadoPor) {}
}