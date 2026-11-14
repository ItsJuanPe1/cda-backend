package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;

import java.util.List;

/** Electivas profesionales programadas manualmente por el coordinador. */
public class ElectivaProgramadaDto {

    private ElectivaProgramadaDto() {
    }

    /** codeEspacio: espacio de la malla (ELE-PROF-I, ...); codeCourse: electiva del banco. */
    public record CrearRequest(String codeProgram, String codeEspacio, String codeCourse, Integer cupos) {
    }

    /** Los campos nulos no se cambian. */
    public record ModificarRequest(String codeCourse, Integer cupos) {
    }

    public record Response(
            Long cursoId,
            String codeProgram,
            String codeEspacio,
            String nombreEspacio,
            Integer semestre,
            String codeCourse,
            String nombreAsignatura,
            Integer grupo,
            String codigoCurso,
            Integer cupos,
            Jornada jornada,
            Long idDocente,
            String nombreDocente,
            int docentesAsociados,
            boolean sinDocentesAsociados,
            /** HU-051: mensaje para el admin cuando no hay docentes asociados; null si los hay. */
            String alerta) {
    }

    public record ListaResponse(Long periodoId, boolean plazoAbierto, boolean puedeEditar,
                                List<Response> electivas) {
    }
}