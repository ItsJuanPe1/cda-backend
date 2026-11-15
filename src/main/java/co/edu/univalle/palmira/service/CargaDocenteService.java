package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CargaProperties;
import co.edu.univalle.palmira.dto.CargaDocenteDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de negocio para el cálculo y validación de la carga académica
 * docente.
 * <p>
 * Controla que la suma de horas presenciales asignadas a un docente en un
 * período
 * no supere el tope institucional configurado (20 horas semanales).
 * </p>
 */
@Service
public class CargaDocenteService {

    private static final long SIN_CURSO = -1L;

    private final CursoProgramacionRepository cursoRepository;
    private final DocenteRepository docenteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final AccesoProgramaService accesoProgramaService;
    private final CargaProperties cargaProperties;

    @Autowired
    public CargaDocenteService(
            CursoProgramacionRepository cursoRepository,
            DocenteRepository docenteRepository,
            PeriodoAcademicoRepository periodoRepository,
            AccesoProgramaService accesoProgramaService,
            CargaProperties cargaProperties) {
        this.cursoRepository = cursoRepository;
        this.docenteRepository = docenteRepository;
        this.periodoRepository = periodoRepository;
        this.accesoProgramaService = accesoProgramaService;
        this.cargaProperties = cargaProperties;
    }

    /**
     * Consulta el detalle de carga de un docente en un período académico.
     * <p>
     * Para administradores se detalla la totalidad de cursos en todos los
     * programas.
     * Para coordinadores se muestran en detalle los cursos de su programa propio y
     * las horas de otros programas se agregan en el campo 'otrosProgramasHoras'.
     * </p>
     *
     * @param docenteId identificador del docente
     * @param periodoId identificador del período académico
     * @return desglose completo de carga asignada y horas disponibles
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

    /**
     * Consulta el total de horas acumuladas de todos los docentes que tienen cursos
     * asignados en el período.
     *
     * @param periodoId identificador del período académico
     * @return lista de resúmenes de carga docente
     */
    @Transactional(readOnly = true)
    public List<CargaDocenteDto.Resumen> listarPorPeriodo(Long periodoId) {
        if (!periodoRepository.existsById(periodoId)) {
            throw new ResourceNotFoundException("Periodo academico no encontrado con ID: " + periodoId);
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
     * Valida si un docente puede asumir una asignatura de 'horasCurso' horas
     * semanales sin superar el tope máximo.
     * Lanza una excepción ConflictException si se excede el límite permitido.
     *
     * @param docenteId  identificador del docente
     * @param periodoId  identificador del período
     * @param cursoId    identificador del curso actual (se excluye para no contar
     *                   doble en reasignaciones)
     * @param horasCurso horas semanales que demanda la materia
     * @throws ConflictException si el total acumulado supera el límite de horas
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
