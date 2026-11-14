package co.edu.univalle.palmira.dto;

import java.time.Instant;
import java.util.List;

public class AvanceProgramacionDto {

    public record Ventanas(
            Instant docenteInicio,
            Instant docenteFin,
            Instant coordinadorInicio,
            Instant coordinadorFin,
            boolean docenteAbierta,
            boolean coordinadorAbierta) {
    }

    public record Periodo(
            Long id,
            String nombre,
            String estado,
            Ventanas ventanas) {
    }

    public record Totales(
            long generados,
            long conDocente,
            long pendientesAsignacion,
            long pendientesContratacion,
            long excluidos,
            long docentesSinDisponibilidad) {
    }

    public record Programa(
            String codeProgram,
            String nombrePrograma,
            long generados,
            long conDocente,
            long pendientesAsignacion,
            long pendientesContratacion,
            long excluidos) {
    }

    public record DocenteSinDisponibilidad(
            Long id,
            String codigo,
            String nombreCompleto,
            String correoInstitucional,
            String facultad,
            String programa) {
    }

    public record CursoPendiente(
            String codeProgram,
            String nombrePrograma,
            String codeCourse,
            String nombreAsignatura,
            Integer semestre,
            String jornada,
            Integer grupo,
            String numeroResolucion,
            String tipoPendiente) {
    }

    public record Resumen(
            Periodo periodo,
            Totales totales,
            List<Programa> programas,
            List<DocenteSinDisponibilidad> docentesSinDisponibilidad,
            Instant generadoEn) {
    }
}
