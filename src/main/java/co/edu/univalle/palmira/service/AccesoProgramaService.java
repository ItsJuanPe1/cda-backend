package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resuelve, a partir del token de la sesión, si quien llama es administrador
 * o coordinador y, en ese caso, de qué programa es. La usan la carga docente
 * y la asignación de docentes a cursos.
 */
@Service
@RequiredArgsConstructor
public class AccesoProgramaService {

    public static final String MSG_FUERA_DE_PROGRAMA = "Solo puede asignar docentes a cursos de su programa";
    public static final String MSG_SIN_PROGRAMA = "Su cuenta de coordinador no tiene un programa activo asociado";
    public static final String MSG_SIN_PERMISO = "No tiene permisos para esta acción";

    private static final Set<String> ROLES_ADMIN = Set.of("ROLE_ADMIN", "ADMIN");
    private static final Set<String> ROLES_COORDINADOR = Set.of("ROLE_COORDINADOR", "COORDINADOR");

    private final CoordinadorRepository coordinadorRepository;

    /** codeProgram es null cuando admin es true. */
    public record Alcance(boolean admin, String codeProgram) {}

    @Transactional(readOnly = true)
    public Alcance alcanceActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException(MSG_SIN_PERMISO);
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (roles.stream().anyMatch(ROLES_ADMIN::contains)) {
            return new Alcance(true, null);
        }
        if (roles.stream().noneMatch(ROLES_COORDINADOR::contains)) {
            throw new AccessDeniedException(MSG_SIN_PERMISO);
        }

        String correo = auth.getPrincipal() instanceof Jwt jwt ? jwt.getClaimAsString("email") : null;
        if (correo == null || correo.isBlank()) {
            throw new AccessDeniedException(MSG_SIN_PROGRAMA);
        }
        List<Coordinador> activos = buscarCoordinacionesActivas(correo);
        if (activos.isEmpty()) {
            throw new AccessDeniedException(MSG_SIN_PROGRAMA);
        }
        return new Alcance(false, activos.get(0).getPrograma().getCodeProgram());
    }

    /** Indica si el usuario autenticado (admin o coordinador) tiene permiso sobre el programa indicado. */
    @Transactional(readOnly = true)
    public boolean tieneAccesoAPrograma(String codeProgram) {
        if (codeProgram == null || codeProgram.isBlank()) {
            return false;
        }
        Alcance alcance = alcanceActual();
        if (alcance.admin()) {
            return true;
        }
        if (alcance.codeProgram() != null && alcance.codeProgram().equalsIgnoreCase(codeProgram)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt.getClaimAsString("email") : null;
        if (correo == null || correo.isBlank()) {
            return false;
        }
        return buscarCoordinacionesActivas(correo).stream()
                .anyMatch(c -> c.getPrograma().getCodeProgram().equalsIgnoreCase(codeProgram));
    }

    /** El admin pasa siempre; el coordinador solo si el programa es uno de los suyos. */
    @Transactional(readOnly = true)
    public void exigirProgramaPropio(String codeProgram) {
        if (!tieneAccesoAPrograma(codeProgram)) {
            throw new AccessDeniedException(MSG_FUERA_DE_PROGRAMA);
        }
    }

    private List<Coordinador> buscarCoordinacionesActivas(String correo) {
        List<Coordinador> lista = coordinadorRepository.findAllByCorreoInstitucionalWithDetails(correo);
        if (lista != null && !lista.isEmpty()) {
            return lista.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActivo())
                            && c.getPrograma() != null
                            && c.getPrograma().getCodeProgram() != null)
                    .toList();
        }
        return coordinadorRepository.findByCorreoInstitucionalWithDetails(correo)
                .filter(c -> Boolean.TRUE.equals(c.getActivo())
                        && c.getPrograma() != null
                        && c.getPrograma().getCodeProgram() != null)
                .map(List::of)
                .orElse(List.of());
    }
}
