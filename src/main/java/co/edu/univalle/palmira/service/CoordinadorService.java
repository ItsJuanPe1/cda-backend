package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.CatalogoItemDto;
import co.edu.univalle.palmira.dto.CoordinadorDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de negocio para la gestión de coordinadores de programa académico.
 * <p>
 * Cada coordinador vincula un {@link Docente} existente con un {@link Programa} y su correo
 * institucional de coordinación. Un docente puede coordinar múltiples programas (ej. DIU y NOC),
 * pero cada programa tiene como máximo un coordinador activo a la vez.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CoordinadorService {

    private final CoordinadorRepository coordinadorRepository;
    private final DocenteRepository docenteRepository;
    private final ProgramaRepository programaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TipoContratoRepository tipoContratoRepository;
    private final UsuarioAccesoService usuarioAccesoService;

    @Transactional(readOnly = true)
    public List<CoordinadorDto.Response> getAll() {
        return coordinadorRepository.findAllWithDetails()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoordinadorDto.Response getById(Integer id) {
        Coordinador coordinador = coordinadorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));
        return toResponse(coordinador);
    }

    @Transactional(readOnly = true)
    public CoordinadorDto.Response getByEmail(String email) {
        String cleanEmail = email.trim().toLowerCase();
        Coordinador coordinador = coordinadorRepository.findByCorreoInstitucionalWithDetails(cleanEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con correo: " + cleanEmail));
        return toResponse(coordinador);
    }

    @Transactional(readOnly = true)
    public List<CoordinadorDto.Response> getByPrograma(String codeProgram) {
        String normalizedCode = codeProgram.trim().toUpperCase();
        return coordinadorRepository.findByProgramaCodeProgram(normalizedCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoItemDto> getCategorias() {
        return categoriaRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(cat -> new CatalogoItemDto(cat.getId(), cat.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoItemDto> getTiposContrato() {
        return tipoContratoRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(tc -> new CatalogoItemDto(tc.getId(), tc.getNombre()))
                .toList();
    }

    @Transactional
    public CoordinadorDto.Response create(CoordinadorDto.CreateRequest request) {
        Docente docente = docenteRepository.findById(request.docenteId())
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id: " + request.docenteId()));
        if (Boolean.FALSE.equals(docente.getEstado())) {
            throw new BadRequestException("No se puede designar como coordinador a un docente inactivo");
        }

        String progCode = request.codeProgram().trim().toUpperCase();
        Programa programa = programaRepository.findById(progCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + progCode));

        boolean activo = request.activo() == null || request.activo();
        if (activo && coordinadorRepository.existsByProgramaCodeProgramAndActivoTrue(progCode)) {
            throw new ConflictException("El programa " + progCode + " ya tiene un coordinador activo asignado");
        }

        String emailInst = request.correoInstitucional().trim().toLowerCase();

        Coordinador coordinador = Coordinador.builder()
                .docente(docente)
                .programa(programa)
                .correoInstitucional(emailInst)
                .activo(activo)
                .build();

        Coordinador guardado = coordinadorRepository.save(coordinador);
        sincronizarUsuario(null, guardado);
        return toResponse(guardado);
    }

    @Transactional
    public CoordinadorDto.Response update(Integer id, CoordinadorDto.UpdateRequest request) {
        Coordinador coordinador = coordinadorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));

        String correoAnterior = coordinador.getCorreoInstitucional();

        if (request.docenteId() != null) {
            Docente docente = docenteRepository.findById(request.docenteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id: " + request.docenteId()));
            if (Boolean.FALSE.equals(docente.getEstado())) {
                throw new BadRequestException("No se puede designar como coordinador a un docente inactivo");
            }
            coordinador.setDocente(docente);
        }

        if (request.codeProgram() != null && !request.codeProgram().trim().isEmpty()) {
            String progCode = request.codeProgram().trim().toUpperCase();
            Programa programa = programaRepository.findById(progCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + progCode));
            coordinador.setPrograma(programa);
        }

        if (request.correoInstitucional() != null && !request.correoInstitucional().trim().isEmpty()) {
            coordinador.setCorreoInstitucional(request.correoInstitucional().trim().toLowerCase());
        }

        if (request.activo() != null) {
            coordinador.setActivo(request.activo());
        }

        if (Boolean.TRUE.equals(coordinador.getActivo()) && coordinador.getPrograma() != null
                && coordinadorRepository.existsByProgramaCodeProgramAndActivoTrueAndIdNot(
                        coordinador.getPrograma().getCodeProgram(), coordinador.getId())) {
            throw new ConflictException(
                    "El programa " + coordinador.getPrograma().getCodeProgram() + " ya tiene otro coordinador activo");
        }

        Coordinador guardado = coordinadorRepository.save(coordinador);
        sincronizarUsuario(correoAnterior, guardado);
        return toResponse(guardado);
    }

    @Transactional
    public CoordinadorDto.Response cambiarEstado(Integer id, Boolean activo) {
        Coordinador coordinador = coordinadorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));
        boolean nuevoEstado = activo != null ? activo : true;
        if (nuevoEstado && coordinador.getPrograma() != null
                && coordinadorRepository.existsByProgramaCodeProgramAndActivoTrueAndIdNot(
                        coordinador.getPrograma().getCodeProgram(), coordinador.getId())) {
            throw new ConflictException(
                    "El programa " + coordinador.getPrograma().getCodeProgram() + " ya tiene otro coordinador activo");
        }
        coordinador.setActivo(nuevoEstado);
        Coordinador guardado = coordinadorRepository.save(coordinador);
        actualizarEstadoUsuarioSiCorresponde(guardado.getCorreoInstitucional());
        return toResponse(guardado);
    }

    /**
     * RN-13: Nada se borra físicamente. La eliminación desactiva el registro para conservar el historial.
     */
    @Transactional
    public void delete(Integer id) {
        Coordinador coordinador = coordinadorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));
        coordinador.setActivo(false);
        coordinadorRepository.save(coordinador);
        actualizarEstadoUsuarioSiCorresponde(coordinador.getCorreoInstitucional());
    }

    /** Refleja el correo de coordinación en la tabla usuarios (rol coordinador). */
    private void sincronizarUsuario(String correoAnterior, Coordinador c) {
        boolean tieneAlgunaActiva = coordinadorRepository
                .findAllByCorreoInstitucionalWithDetails(c.getCorreoInstitucional())
                .stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.getActivo()));
        usuarioAccesoService.sincronizar(
                correoAnterior,
                c.getNombreCompleto(),
                c.getCorreoInstitucional(),
                c.getCorreo(),
                c.getTelefono(),
                AuthConstants.ROL_COORDINADOR,
                tieneAlgunaActiva || Boolean.TRUE.equals(c.getActivo()));
    }

    /** Mantiene el acceso activo si el correo de coordinación sigue activo en otro programa (ej. DIU/NOC). */
    private void actualizarEstadoUsuarioSiCorresponde(String correoInstitucional) {
        boolean sigueActivoEnOtro = coordinadorRepository
                .findAllByCorreoInstitucionalWithDetails(correoInstitucional)
                .stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.getActivo()));
        usuarioAccesoService.cambiarEstado(correoInstitucional, sigueActivoEnOtro);
    }

    private CoordinadorDto.Response toResponse(Coordinador c) {
        Docente d = c.getDocente();
        Programa p = c.getPrograma();

        return new CoordinadorDto.Response(
                c.getId(),
                d != null ? d.getId() : null,
                c.getIdentificacion(),
                c.getNombreCompleto(),
                c.getCorreo(),
                c.getCorreoInstitucional(),
                d != null ? d.getCorreoInstitucional() : null,
                c.getTelefono(),
                p != null ? p.getCodeProgram() : null,
                p != null ? p.getNameProgram() : null,
                null,
                c.getCategoriaNombre(),
                null,
                c.getTipoContratoNombre(),
                c.getActivo(),
                c.getCreatedAt()
        );
    }
}
