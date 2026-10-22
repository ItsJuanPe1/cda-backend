package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.CoordinadorDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoordinadorService {

    private final CoordinadorRepository coordinadorRepository;
    private final ProgramaRepository programaRepository;

    @Transactional(readOnly = true)
    public List<CoordinadorDto.Response> getAll() {
        return coordinadorRepository.findAllWithPrograma()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoordinadorDto.Response getById(Integer id) {
        Coordinador coordinador = coordinadorRepository.findByIdWithPrograma(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinador no encontrado con id: " + id));
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

        Coordinador coordinador = Coordinador.builder()
                .identificacion(idDoc)
                .nombreCompleto(request.nombreCompleto().trim())
                .correo(request.correo() != null ? request.correo().trim().toLowerCase() : null)
                .correoInstitucional(emailInst)
                .telefono(request.telefono() != null ? request.telefono().trim() : null)
                .programa(programa)
                .categoria(request.categoria() != null && !request.categoria().trim().isEmpty() ? request.categoria().trim() : "Auxiliar")
                .tipoContrato(request.tipoContrato() != null && !request.tipoContrato().trim().isEmpty() ? request.tipoContrato().trim() : "Nombrado")
                .activo(request.activo() != null ? request.activo() : true)
                .build();

        return toResponse(coordinadorRepository.save(coordinador));
    }

    @Transactional
    public CoordinadorDto.Response update(Integer id, CoordinadorDto.UpdateRequest request) {
        Coordinador coordinador = coordinadorRepository.findById(id)
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

        if (request.categoria() != null && !request.categoria().trim().isEmpty()) {
            coordinador.setCategoria(request.categoria().trim());
        }

        if (request.tipoContrato() != null && !request.tipoContrato().trim().isEmpty()) {
            coordinador.setTipoContrato(request.tipoContrato().trim());
        }

        if (request.activo() != null) {
            coordinador.setActivo(request.activo());
        }

        return toResponse(coordinadorRepository.save(coordinador));
    }

    @Transactional
    public void delete(Integer id) {
        if (!coordinadorRepository.existsById(id)) {
            throw new ResourceNotFoundException("Coordinador no encontrado con id: " + id);
        }
        coordinadorRepository.deleteById(id);
    }

    private CoordinadorDto.Response toResponse(Coordinador c) {
        Programa p = c.getPrograma();
        return new CoordinadorDto.Response(
                c.getId(),
                c.getIdentificacion(),
                c.getNombreCompleto(),
                c.getCorreo(),
                c.getCorreoInstitucional(),
                c.getTelefono(),
                p != null ? p.getCodeProgram() : null,
                p != null ? p.getNameProgram() : null,
                c.getCategoria() != null ? c.getCategoria() : "Auxiliar",
                c.getTipoContrato() != null ? c.getTipoContrato() : "Nombrado",
                c.getActivo(),
                c.getCreatedAt()
        );
    }
}
