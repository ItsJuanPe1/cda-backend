package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.CoordinadorDto;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta del perfil de un coordinador por su correo institucional (Mi
 * perfil).
 */
@Service
@RequiredArgsConstructor
public class CoordinadorPerfilService {

    private final CoordinadorRepository coordinadorRepository;

    @Transactional(readOnly = true)
    public CoordinadorDto.Response obtenerPorCorreo(String correo) {
        Coordinador c = coordinadorRepository
                .findByCorreoInstitucionalWithDetails(correo.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró un coordinador con el correo " + correo));

        return new CoordinadorDto.Response(
                c.getId(),
                c.getIdentificacion(),
                c.getNombreCompleto(),
                c.getCorreo(),
                c.getCorreoInstitucional(),
                c.getTelefono(),
                c.getPrograma() != null ? c.getPrograma().getCodeProgram() : null,
                c.getPrograma() != null ? c.getPrograma().getNameProgram() : null,
                c.getCategoria() != null ? c.getCategoria().getId() : null,
                c.getCategoria() != null ? c.getCategoria().getNombre() : null,
                c.getTipoContrato() != null ? c.getTipoContrato().getId() : null,
                c.getTipoContrato() != null ? c.getTipoContrato().getNombre() : null,
                c.getActivo(),
                c.getCreatedAt());
    }
}