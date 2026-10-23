package co.edu.univalle.palmira.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;

public final class PeriodoAcademicoDto {
    private PeriodoAcademicoDto() {}

    public record Request(
            @NotBlank(message = "El nombre del período es obligatorio")
            @Size(max = 20, message = "El nombre del período no puede superar 20 caracteres")
            String nombre,
            @NotBlank(message = "El número de resolución es obligatorio")
            String numeroResolucion,
            @NotNull(message = "La fecha de resolución es obligatoria") LocalDate fechaResolucion,
            @NotNull(message = "La fecha de inicio del semestre es obligatoria") LocalDate fechaInicioSemestre,
            @NotNull(message = "La fecha de fin del semestre es obligatoria") LocalDate fechaFinSemestre,
            @NotNull(message = "La fecha de inicio de adiciones es obligatoria") LocalDate fechaAdicionInicio,
            @NotNull(message = "La fecha de fin de adiciones es obligatoria") LocalDate fechaAdicionFin,
            @NotNull(message = "La fecha de inicio de cancelaciones es obligatoria") LocalDate fechaCancelacionInicio,
            @NotNull(message = "La fecha de fin de cancelaciones es obligatoria") LocalDate fechaCancelacionFin,
            @NotNull(message = "El inicio del plazo docente es obligatorio") Instant plazoDocenteInicio,
            @NotNull(message = "El fin del plazo docente es obligatorio") Instant plazoDocenteFin,
            Instant plazoCoordinadorInicio,
            Instant plazoCoordinadorFin,
            @Size(max = 2048, message = "El enlace es demasiado largo") String enlaceResolucion,
            Long version
    ) {}

    public record Response(
            Long id, String nombre, String numeroResolucion, LocalDate fechaResolucion,
            LocalDate fechaInicioSemestre, LocalDate fechaFinSemestre,
            LocalDate fechaAdicionInicio, LocalDate fechaAdicionFin,
            LocalDate fechaCancelacionInicio, LocalDate fechaCancelacionFin,
            Instant plazoDocenteInicio, Instant plazoDocenteFin,
            Instant plazoCoordinadorInicio, Instant plazoCoordinadorFin,
            String enlaceResolucion, boolean vigente, long version
    ) {}
}
