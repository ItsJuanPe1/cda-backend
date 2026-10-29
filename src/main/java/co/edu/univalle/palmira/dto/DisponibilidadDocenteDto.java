package co.edu.univalle.palmira.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class DisponibilidadDocenteDto {
    private DisponibilidadDocenteDto() {}

    public record Request(
            @NotNull(message = "El día es obligatorio") Integer dia,
            @NotNull(message = "La hora de inicio es obligatoria") LocalTime horaInicio,
            @NotNull(message = "La hora de fin es obligatoria") LocalTime horaFin) {}

    public record Item(Long id, Integer dia, LocalTime horaInicio, LocalTime horaFin) {}

    public record Response(
            String estado,
            String mensaje,
            List<Item> horarios,
            Instant plazoDocenteInicio,
            Instant plazoDocenteFin) {
        public Response(String estado, String mensaje, List<Item> horarios) {
            this(estado, mensaje, horarios, null, null);
        }
    }
}
