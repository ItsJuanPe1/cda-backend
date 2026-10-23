package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PeriodoAcademicoDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeriodoAcademicoService {
    private final PeriodoAcademicoRepository repository;

    @Transactional(readOnly = true)
    public List<PeriodoAcademicoDto.Response> listar() {
        return repository.findAllByOrderByFechaInicioSemestreDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PeriodoAcademicoDto.Response vigente() {
        return repository.findFirstByVigenteTrue().map(this::toResponse).orElse(null);
    }

    @Transactional
    public PeriodoAcademicoDto.Response crear(PeriodoAcademicoDto.Request request) {
        validar(request, null);
        repository.marcarVigenteComoHistorial();
        PeriodoAcademico periodo = fromRequest(request);
        periodo.setVigente(true);
        return toResponse(repository.save(periodo));
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
        copy(request, periodo);
        return toResponse(repository.save(periodo));
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
                p.getPlazoCoordinadorInicio(), p.getPlazoCoordinadorFin(), p.getEnlaceResolucion(), p.isVigente(), p.getVersion());
    }
}
