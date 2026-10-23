package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.CatalogoItemDto;
import co.edu.univalle.palmira.dto.CoordinadorDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Categoria;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.entity.TipoContrato;
import co.edu.univalle.palmira.repository.CategoriaRepository;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import co.edu.univalle.palmira.repository.TipoContratoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoordinadorService {

    private final CoordinadorRepository coordinadorRepository;
    private final ProgramaRepository programaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TipoContratoRepository tipoContratoRepository;

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
        String idDoc = request.identificacion().trim();
        if (coordinadorRepository.existsByIdentificacion(idDoc)) {
            throw new BadRequestException("Ya existe un coordinador registrado con la identificación: " + idDoc);
        }

        String emailInst = request.correoInstitucional().trim().toLowerCase();
        if (coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(emailInst)) {
            throw new BadRequestException("Ya existe un coordinador con el correo institucional: " + emailInst);
        }

        Programa programa = null;
        if (request.codeProgram() != null && !request.codeProgram().trim().isEmpty()) {
            String progCode = request.codeProgram().trim().toUpperCase();
            programa = programaRepository.findById(progCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + progCode));
        }

        Categoria categoria = resolveCategoria(request.idCategoria(), request.categoria());
        TipoContrato tipoContrato = resolveTipoContrato(request.idTipoContrato(), request.tipoContrato());

        Coordinador coordinador = Coordinador.builder()
                .identificacion(idDoc)
                .nombreCompleto(request.nombreCompleto().trim())
                .correo(request.correo() != null ? request.correo().trim().toLowerCase() : null)
                .correoInstitucional(emailInst)
                .telefono(request.telefono() != null ? request.telefono().trim() : null)
                .programa(programa)
                .categoria(categoria)
                .tipoContrato(tipoContrato)
                .activo(request.activo() != null ? request.activo() : true)
                .build();

        return toResponse(coordinadorRepository.save(coordinador));
    }

    @Transactional
    public CoordinadorDto.Response update(Integer id, CoordinadorDto.UpdateRequest request) {
        Coordinador coordinador = coordinadorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));

        if (request.nombreCompleto() != null && !request.nombreCompleto().trim().isEmpty()) {
            coordinador.setNombreCompleto(request.nombreCompleto().trim());
        }

        if (request.correo() != null) {
            coordinador.setCorreo(request.correo().trim().toLowerCase());
        }

        if (request.correoInstitucional() != null && !request.correoInstitucional().trim().isEmpty()) {
            String newInstEmail = request.correoInstitucional().trim().toLowerCase();
            if (!newInstEmail.equalsIgnoreCase(coordinador.getCorreoInstitucional()) &&
                    coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(newInstEmail)) {
                throw new BadRequestException("Ya existe otro coordinador con el correo institucional: " + newInstEmail);
            }
            coordinador.setCorreoInstitucional(newInstEmail);
        }

        if (request.telefono() != null) {
            coordinador.setTelefono(request.telefono().trim());
        }

        if (request.codeProgram() != null) {
            if (request.codeProgram().trim().isEmpty()) {
                coordinador.setPrograma(null);
            } else {
                String progCode = request.codeProgram().trim().toUpperCase();
                Programa programa = programaRepository.findById(progCode)
                        .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + progCode));
                coordinador.setPrograma(programa);
            }
        }

        if (request.idCategoria() != null || request.categoria() != null) {
            coordinador.setCategoria(resolveCategoria(request.idCategoria(), request.categoria()));
        }

        if (request.idTipoContrato() != null || request.tipoContrato() != null) {
            coordinador.setTipoContrato(resolveTipoContrato(request.idTipoContrato(), request.tipoContrato()));
        }

        if (request.activo() != null) {
            coordinador.setActivo(request.activo());
        }

        return toResponse(coordinadorRepository.save(coordinador));
    }

    @Transactional
    public CoordinadorDto.Response cambiarEstado(Integer id, Boolean activo) {
        Coordinador coordinador = coordinadorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));
        coordinador.setActivo(activo != null ? activo : true);
        return toResponse(coordinadorRepository.save(coordinador));
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
    }

    private Categoria resolveCategoria(Integer idCategoria, String nombreCategoria) {
        if (idCategoria != null) {
            return categoriaRepository.findById(idCategoria)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + idCategoria));
        }
        if (nombreCategoria != null && !nombreCategoria.trim().isEmpty()) {
            return categoriaRepository.findByNombreIgnoreCase(nombreCategoria.trim())
                    .orElseGet(() -> categoriaRepository.findByNombreIgnoreCase("Auxiliar")
                            .orElse(null));
        }
        return categoriaRepository.findByNombreIgnoreCase("Auxiliar").orElse(null);
    }

    private TipoContrato resolveTipoContrato(Integer idTipoContrato, String nombreTipoContrato) {
        if (idTipoContrato != null) {
            return tipoContratoRepository.findById(idTipoContrato)
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de contrato no encontrado con ID: " + idTipoContrato));
        }
        if (nombreTipoContrato != null && !nombreTipoContrato.trim().isEmpty()) {
            return tipoContratoRepository.findByNombreIgnoreCase(nombreTipoContrato.trim())
                    .orElseGet(() -> tipoContratoRepository.findByNombreIgnoreCase("Nombrado")
                            .orElse(null));
        }
        return tipoContratoRepository.findByNombreIgnoreCase("Nombrado").orElse(null);
    }

    private CoordinadorDto.Response toResponse(Coordinador c) {
        Programa p = c.getPrograma();
        Categoria cat = c.getCategoria();
        TipoContrato tc = c.getTipoContrato();

        return new CoordinadorDto.Response(
                c.getId(),
                c.getIdentificacion(),
                c.getNombreCompleto(),
                c.getCorreo(),
                c.getCorreoInstitucional(),
                c.getTelefono(),
                p != null ? p.getCodeProgram() : null,
                p != null ? p.getNameProgram() : null,
                cat != null ? cat.getId() : null,
                cat != null ? cat.getNombre() : "Auxiliar",
                tc != null ? tc.getId() : null,
                tc != null ? tc.getNombre() : "Nombrado",
                c.getActivo(),
                c.getCreatedAt()
        );
    }
}
