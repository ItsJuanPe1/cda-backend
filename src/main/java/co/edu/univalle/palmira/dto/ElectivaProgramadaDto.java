package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;

import java.util.List;

/** Electivas profesionales programadas manualmente por el coordinador. */
public class ElectivaProgramadaDto {

    private ElectivaProgramadaDto() {
    }

    /**
     * codeEspacio: espacio de la malla (ELE-PROF-I, ...) o «COMPLEMENTARIA» para una complementaria que
     * propone el coordinador sin espacio de malla; codeCourse: electiva del banco. jornada solo aplica a la
     * propuesta y es obligatoria (DIURNA o NOCTURNA): no se programa en la jornada del programa.
     */
    public record CrearRequest(String codeProgram, String codeEspacio, String codeCourse, Integer cupos,
                               String jornada) {
        public CrearRequest(String codeProgram, String codeEspacio, String codeCourse, Integer cupos) {
            this(codeProgram, codeEspacio, codeCourse, cupos, null);
        }
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
            String alerta,
            /** Horas semanales de la asignatura (para validar la carga del docente al designarlo). */
            Integer horasSemanales) {
    }

    /** Escuela cuyas electivas profesionales puede programar el programa (codigo = prefijo de las asignaturas). */
    public record EscuelaItem(String codigo, String nombre) {
    }

    /** Complementaria disponible para proponer. */
    public record AsignaturaItem(String codeCourse, String nombre, Integer credits) {
    }

    /**
     * electivas: las que ocupan un espacio de la malla; complementarias: las que propuso el coordinador;
     * bancoComplementarias: las que aún se pueden proponer (una complementaria se programa una sola vez en
     * el período); escuelasBanco vacío: el programa no tiene escuela y el banco profesional va por facultad.
     */
    public record ListaResponse(Long periodoId, boolean plazoAbierto, boolean puedeEditar,
                                List<Response> electivas, List<EscuelaItem> escuelasBanco,
                                List<Response> complementarias, List<AsignaturaItem> bancoComplementarias) {
    }
}
