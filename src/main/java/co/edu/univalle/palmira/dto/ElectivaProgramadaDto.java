package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.Jornada;

import java.time.LocalTime;
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
            Integer horasSemanales,
            Integer dia,
            LocalTime horaInicio,
            LocalTime horaFin,
            Integer dia2,
            LocalTime horaInicio2,
            LocalTime horaFin2,
            String horario) {
        public Response(Long cursoId, String codeProgram, String codeEspacio, String nombreEspacio,
                        Integer semestre, String codeCourse, String nombreAsignatura, Integer grupo,
                        String codigoCurso, Integer cupos, Jornada jornada, Long idDocente,
                        String nombreDocente, int docentesAsociados, boolean sinDocentesAsociados,
                        String alerta, Integer horasSemanales) {
            this(cursoId, codeProgram, codeEspacio, nombreEspacio, semestre, codeCourse, nombreAsignatura,
                    grupo, codigoCurso, cupos, jornada, idDocente, nombreDocente, docentesAsociados,
                    sinDocentesAsociados, alerta, horasSemanales, null, null, null, null, null, null, null);
        }
    }

    /** Espacio de electiva obligatorio en un semestre con cohorte confirmada que aún no se ha escogido en el período vigente. */
    public record PendienteItem(String codeEspacio, String nombreEspacio, Integer semestre, boolean esComplementaria) {
    }

    /** Escuela cuyas electivas profesionales puede programar el programa (codigo = prefijo de las asignaturas). */
    public record EscuelaItem(String codigo, String nombre) {
    }

    /**
     * Complementaria del banco. programadaPor: programa que ya la tiene en el período (null si está libre);
     * una complementaria se programa una sola vez, así que la ya programada se muestra pero no se puede elegir.
     */
    public record AsignaturaItem(String codeCourse, String nombre, Integer credits, String programadaPor) {
    }

    /**
     * electivas: las que ocupan un espacio de la malla; complementarias: las que propuso el coordinador;
     * bancoComplementarias: todas las activas, con programadaPor si ya se programó;
     * pendientesObligatorias: espacios de electiva de la malla con cohorte confirmada en el período vigente
     * que el coordinador aún debe escoger (se reinician automáticamente al cambiar de período).
     */
    public record ListaResponse(Long periodoId, boolean plazoAbierto, boolean puedeEditar,
                                List<Response> electivas, List<EscuelaItem> escuelasBanco,
                                List<Response> complementarias, List<AsignaturaItem> bancoComplementarias,
                                List<PendienteItem> pendientesObligatorias) {
        public ListaResponse(Long periodoId, boolean plazoAbierto, boolean puedeEditar,
                             List<Response> electivas, List<EscuelaItem> escuelasBanco,
                             List<Response> complementarias, List<AsignaturaItem> bancoComplementarias) {
            this(periodoId, plazoAbierto, puedeEditar, electivas, escuelasBanco, complementarias,
                    bancoComplementarias, List.of());
        }
    }
}