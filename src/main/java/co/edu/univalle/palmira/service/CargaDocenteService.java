package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CargaProperties;
import co.edu.univalle.palmira.dto.CargaDocenteDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carga académica de un docente en un período: suma de las horas de las
 * asignaturas de sus cursos programados, en todos los programas.
 */
@Service
@RequiredArgsConstructor
public class CargaDocenteService {

    private static final long SIN_CURSO = -1L;

    private final CursoProgramacionRepository cursoRepository;
    private final DocenteRepository docenteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final AccesoProgramaService accesoProgramaService;
    private final CargaProperties cargaProperties;

    /**
     * Admin: detalle de todos los programas. Coordinador: detalle solo de su
     * programa; las horas de los demás van agregadas en otrosProgramasHoras.
     */
    @Transactional(readOnly = true)
    public CargaDocenteDto.Detalle consultar(Long docenteId, Long periodoId) {
        validarExistencia(docenteId, periodoId);
        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();

        List<CursoProgramacion> cursos = cursoRepository.findByDocenteYPeriodo(docenteId, periodoId);
        int total = cursos.stream().mapToInt(CargaDocenteService::horasDe).sum();

        List<CargaDocenteDto.CursoCarga> visibles = cursos.stream()
                .filter(c -> alcance.admin()
                        || c.getPrograma().getCodeProgram().equalsIgnoreCase(alcance.codeProgram()))
                .map(CargaDocenteService::toCursoCarga)
                .toList();
        int horasVisibles = visibles.stream().mapToInt(CargaDocenteDto.CursoCarga::horas).sum();

        int limite = cargaProperties.getMaxHorasDocente();
        return new CargaDocenteDto.Detalle(docenteId, periodoId, total, limite,
                Math.max(0, limite - total), visibles, total - horasVisibles);
    }

    /** Totales de todos los docentes con cursos en el período, en una sola consulta. */
    @Transactional(readOnly = true)
    public List<CargaDocenteDto.Resumen> listarPorPeriodo(Long periodoId) {
        if (!periodoRepository.existsById(periodoId)) {
            throw new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId);
        }
        int limite = cargaProperties.getMaxHorasDocente();
        return cursoRepository.totalesPorDocente(periodoId).stream()
                .map(row -> {
                    int total = ((Number) row[1]).intValue();
                    return new CargaDocenteDto.Resumen(((Number) row[0]).longValue(), total, limite,
                            Math.max(0, limite - total), ((Number) row[2]).intValue());
                })
                .toList();
    }

    /**
     * Rechaza (409) si asignar un curso de horasCurso horas dejaría al docente por
     * encima del límite. cursoId se excluye de la suma para que reasignar el mismo
     * curso no cuente doble; null si el curso aún no existe.
     */
    @Transactional(readOnly = true)
    public void validar(Long docenteId, Long periodoId, Long cursoId, int horasCurso) {
        long actual = cursoRepository.sumarHorasExcluyendo(docenteId, periodoId,
                cursoId != null ? cursoId : SIN_CURSO);
        int limite = cargaProperties.getMaxHorasDocente();
        if (actual + horasCurso > limite) {
            throw new ConflictException(String.format(
                    "El docente ya tiene %d h en este período; este curso suma %d h (límite %d h)",
                    actual, horasCurso, limite));
        }
    }

    private void validarExistencia(Long docenteId, Long periodoId) {
        if (!periodoRepository.existsById(periodoId)) {
            throw new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId);
        }
        if (!docenteRepository.existsById(docenteId)) {
            throw new ResourceNotFoundException("Docente no encontrado con id " + docenteId);
        }
    }

    private static int horasDe(CursoProgramacion c) {
        Integer h = c.getAsignatura().getHorasSemanales();
        return h != null ? h : 0;
    }

    private static CargaDocenteDto.CursoCarga toCursoCarga(CursoProgramacion c) {
        return new CargaDocenteDto.CursoCarga(
                c.getId(),
                c.getPrograma().getCodeProgram(),
                c.getAsignatura().getCodeCourse(),
                c.getNombreAsignatura(),
                c.getGrupo(),
                horasDe(c),
                c.getAsignatura().getHorasSemanales() == null);
    }
}