package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.entity.EstadoPeriodo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class PeriodoDto {

    private PeriodoDto() {
    }

    // DEPENDE-HU:plazos
    public record Plazo(OffsetDateTime inicio, OffsetDateTime fin) {
    }

    // DEPENDE-HU:plazos
    public record Plazos(Plazo docentes, Plazo coordinadores) {
    }

    public record Resumen(
            Long id,
            String nombre,
            EstadoPeriodo estado,
            LocalDate inicioSemestre,
            LocalDate finSemestre,
            OffsetDateTime fechaDesactivacion
    ) {
    }

    public record Detalle(
            Long id,
            String nombre,
            EstadoPeriodo estado,
            String numeroResolucion,
            String enlaceResolucion,
            LocalDate inicioSemestre,
            LocalDate finSemestre,
            LocalDate fechaAdicion,
            LocalDate fechaCancelacion,
            Plazos plazos,
            OffsetDateTime fechaDesactivacion
    ) {
    }
}