package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PeriodoAcademicoDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.service.notificacion.RecordatorioCoordinadorService;
import co.edu.univalle.palmira.service.notificacion.RecordatorioDocenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de negocio para la administración de períodos académicos (semestres).
 * <p>
 * Gestiona la vigencia del período activo, fechas de inicio y fin de clases,
 * y los plazos límite para registro de disponibilidad docente y asignación de coordinadores.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PeriodoAcademicoService {
    /** HU-058: entidad de la bitácora para los períodos académicos. */
    public static final String ENTIDAD_PERIODO = "PERIODO";

    private final PeriodoAcademicoRepository repository;
    private final BitacoraService bitacoraService;
    private final RecordatorioDocenteService recordatorioDocenteService;
    private final RecordatorioCoordinadorService recordatorioCoordinadorService;

    @Transactional(readOnly = true)
    public List<PeriodoAcademicoDto.Response> listar() {
        return repository.findAllByOrderByFechaInicioSemestreDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PeriodoAcademicoDto.Response vigente() {
        return repository.findFirstByVigenteTrue()
                .filter(p -> p.isVigente() && (p.getEstado() == null || "VIGENTE".equalsIgnoreCase(p.getEstado())))
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PeriodoAcademicoDto.Response obtenerPorId(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado"));
    }

    @Transactional
    public PeriodoAcademicoDto.Response crear(PeriodoAcademicoDto.Request request) {
        validar(request, null);
        // HU-038 criterio 7: el período que pasa a historial pierde sus avisos pendientes.
        Optional<PeriodoAcademico> anterior = repository.findFirstByVigenteTrue();
        Map<String, Object> anteriorAntes = anterior.map(PeriodoAcademicoService::estadoBitacora).orElse(null);
        anterior.ifPresent(a -> recordatorioDocenteService.cancelarPendientes(a.getId(),
                RecordatorioDocenteService.MOTIVO_PERIODO_HISTORIAL));
        repository.marcarVigenteComoHistorial();
        anterior.ifPresent(a -> bitacoraService.registrar(AccionBitacora.EDITAR, ENTIDAD_PERIODO,
                String.valueOf(a.getId()), a.getId(), anteriorAntes, estadoBitacora(false, "HISTORIAL")));

        PeriodoAcademico periodo = fromRequest(request);
        periodo.setVigente(true);
        periodo.setEstado("VIGENTE");
        PeriodoAcademico guardado = repository.save(periodo);
        PeriodoAcademicoDto.Response respuesta = toResponse(guardado);
        bitacoraService.registrar(AccionBitacora.CREAR, ENTIDAD_PERIODO, String.valueOf(guardado.getId()),
                guardado.getId(), null, respuesta);
        // HU-002 criterio 13: se programan los recordatorios (HU-038 y HU-039).
        recordatorioDocenteService.programarAlCrear(guardado);
        recordatorioCoordinadorService.programarAlCrear(guardado);
        return respuesta;
    }

    @Transactional
    public PeriodoAcademicoDto.Response actualizar(Long id, PeriodoAcademicoDto.Request request) {
        PeriodoAcademico periodo = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado"));
        if (!periodo.isVigente()) {
            throw new BadRequestException("Solo el período vigente se puede editar");
        }
        if (request.version() != null && request.version() != periodo.getVersion()) {
            throw new BadRequestException("El período fue modificado por otro administrador. Recarga la información");
        }
        validar(request, id);
        PeriodoAcademicoDto.Response antes = toResponse(periodo);
        Instant inicioAnterior = periodo.getPlazoDocenteInicio();
        Instant finAnterior = periodo.getPlazoDocenteFin();
        Instant coordinadorInicioAnterior = periodo.getPlazoCoordinadorInicio();
        Instant coordinadorFinAnterior = periodo.getPlazoCoordinadorFin();
        copy(request, periodo);
        PeriodoAcademico guardado = repository.save(periodo);
        PeriodoAcademicoDto.Response despues = toResponse(guardado);
        if (!sinVersion(antes).equals(sinVersion(despues))) {
            bitacoraService.registrar(AccionBitacora.EDITAR, ENTIDAD_PERIODO, String.valueOf(guardado.getId()),
                    guardado.getId(), antes, despues);
        }
        // HU-003 criterio 8 y HU-038 criterio 3: correo de actualización y reprogramación.
        recordatorioDocenteService.alCambiarPlazo(guardado, inicioAnterior, finAnterior);
        recordatorioCoordinadorService.alCambiarPlazo(guardado, coordinadorInicioAnterior, coordinadorFinAnterior);
        return despues;
    }

    @Transactional
    public void desactivar(Long id) {
        PeriodoAcademico periodo = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado"));
        if (!periodo.isVigente()) {
            throw new BadRequestException("Solo el período vigente se puede eliminar");
        }
        Map<String, Object> antes = estadoBitacora(periodo);
        periodo.setVigente(false);
        periodo.setEstado("DESACTIVADO");
        repository.save(periodo);
        // HU-004 criterio 5: se cancelan los recordatorios pendientes.
        recordatorioDocenteService.cancelarPendientes(periodo.getId(),
                RecordatorioDocenteService.MOTIVO_PERIODO_DESACTIVADO);
        bitacoraService.registrar(AccionBitacora.DESACTIVAR, ENTIDAD_PERIODO, String.valueOf(periodo.getId()),
                periodo.getId(), antes, estadoBitacora(false, "DESACTIVADO"));
    }

    private static Map<String, Object> estadoBitacora(PeriodoAcademico p) {
        return estadoBitacora(p.isVigente(), p.getEstado());
    }

    private static Map<String, Object> estadoBitacora(boolean vigente, String estado) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("vigente", vigente);
        datos.put("estado", estado);
        return datos;
    }

    /** Compara dos respuestas sin el número de versión, que cambia en cada guardado. */
    private static PeriodoAcademicoDto.Response sinVersion(PeriodoAcademicoDto.Response r) {
        return new PeriodoAcademicoDto.Response(r.id(), r.nombre(), r.numeroResolucion(), r.fechaResolucion(),
                r.fechaInicioSemestre(), r.fechaFinSemestre(), r.fechaAdicionInicio(), r.fechaAdicionFin(),
                r.fechaCancelacionInicio(), r.fechaCancelacionFin(), r.plazoDocenteInicio(), r.plazoDocenteFin(),
                r.plazoCoordinadorInicio(), r.plazoCoordinadorFin(), r.enlaceResolucion(), r.vigente(), r.estado(), 0L);
    }

    private void validar(PeriodoAcademicoDto.Request r, Long id) {
        if (r.fechaFinSemestre().isBefore(r.fechaInicioSemestre()) || r.fechaFinSemestre().isEqual(r.fechaInicioSemestre())) {
            throw new BadRequestException("La fecha de fin del semestre debe ser posterior a la de inicio");
        }
        if (r.fechaAdicionFin().isBefore(r.fechaAdicionInicio()) || r.fechaCancelacionFin().isBefore(r.fechaCancelacionInicio())) {
            throw new BadRequestException("Las fechas de adición y cancelación deben tener un rango válido");
        }
        if (r.fechaAdicionInicio().isBefore(r.fechaInicioSemestre()) || r.fechaAdicionFin().isAfter(r.fechaFinSemestre())
                || r.fechaCancelacionInicio().isBefore(r.fechaInicioSemestre()) || r.fechaCancelacionFin().isAfter(r.fechaFinSemestre())) {
            throw new BadRequestException("Las fechas de adición y cancelación deben estar dentro del semestre");
        }
        boolean rangesOverlap = !r.fechaAdicionFin().isBefore(r.fechaCancelacionInicio())
                && !r.fechaCancelacionFin().isBefore(r.fechaAdicionInicio());
        if (rangesOverlap) {
            throw new BadRequestException("Las fechas de adición y cancelación no pueden solaparse");
        }
        if (!r.plazoDocenteFin().isAfter(r.plazoDocenteInicio())) {
            throw new BadRequestException("El fin del plazo docente debe ser posterior al inicio");
        }
        if ((r.plazoCoordinadorInicio() == null) != (r.plazoCoordinadorFin() == null)
                || (r.plazoCoordinadorInicio() != null && !r.plazoCoordinadorFin().isAfter(r.plazoCoordinadorInicio()))) {
            throw new BadRequestException("El rango del plazo de coordinadores no es válido");
        }
        if (r.enlaceResolucion() != null && !r.enlaceResolucion().isBlank()) {
            try {
                URI uri = URI.create(r.enlaceResolucion().trim());
                if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Enlace no válido");
            }
        }
        boolean duplicateName = id == null ? repository.existsByNombreIgnoreCase(r.nombre().trim())
                : repository.existsByNombreIgnoreCaseAndIdNot(r.nombre().trim(), id);
        boolean duplicateResolution = id == null ? repository.existsByNumeroResolucionIgnoreCase(r.numeroResolucion().trim())
                : repository.existsByNumeroResolucionIgnoreCaseAndIdNot(r.numeroResolucion().trim(), id);
        if (duplicateName) throw new BadRequestException("El nombre del período ya existe");
        if (duplicateResolution) throw new BadRequestException("El número de resolución ya existe");
    }

    private PeriodoAcademico fromRequest(PeriodoAcademicoDto.Request r) {
        PeriodoAcademico p = new PeriodoAcademico();
        copy(r, p);
        return p;
    }

    private void copy(PeriodoAcademicoDto.Request r, PeriodoAcademico p) {
        p.setNombre(r.nombre().trim());
        p.setNumeroResolucion(r.numeroResolucion().trim());
        p.setFechaResolucion(r.fechaResolucion());
        p.setFechaInicioSemestre(r.fechaInicioSemestre());
        p.setFechaFinSemestre(r.fechaFinSemestre());
        p.setFechaAdicionInicio(r.fechaAdicionInicio());
        p.setFechaAdicionFin(r.fechaAdicionFin());
        p.setFechaCancelacionInicio(r.fechaCancelacionInicio());
        p.setFechaCancelacionFin(r.fechaCancelacionFin());
        p.setPlazoDocenteInicio(r.plazoDocenteInicio());
        p.setPlazoDocenteFin(r.plazoDocenteFin());
        p.setPlazoCoordinadorInicio(r.plazoCoordinadorInicio());
        p.setPlazoCoordinadorFin(r.plazoCoordinadorFin());
        p.setEnlaceResolucion(r.enlaceResolucion() == null || r.enlaceResolucion().isBlank() ? null : r.enlaceResolucion().trim());
    }

    private PeriodoAcademicoDto.Response toResponse(PeriodoAcademico p) {
        return new PeriodoAcademicoDto.Response(p.getId(), p.getNombre(), p.getNumeroResolucion(), p.getFechaResolucion(),
                p.getFechaInicioSemestre(), p.getFechaFinSemestre(), p.getFechaAdicionInicio(), p.getFechaAdicionFin(),
                p.getFechaCancelacionInicio(), p.getFechaCancelacionFin(), p.getPlazoDocenteInicio(), p.getPlazoDocenteFin(),
                p.getPlazoCoordinadorInicio(), p.getPlazoCoordinadorFin(), p.getEnlaceResolucion(),
                p.isVigente(), p.getEstado(), p.getVersion());
    }
}
