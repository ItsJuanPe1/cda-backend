package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AccesoProgramaServiceTest {

    @Mock
    CoordinadorRepository coordinadorRepository;

    AccesoProgramaService service;

    @BeforeEach
    void setUp() {
        service = new AccesoProgramaService(coordinadorRepository);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void sesion(String correo, String... authorities) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").claim("sub", "u");
        if (correo != null) {
            b.claim("email", correo);
        }
        Jwt jwt = b.build();
        List<SimpleGrantedAuthority> auts = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, auts));
    }

    private Coordinador coordinador(String codeProgram, Boolean activo) {
        Coordinador c = mock(Coordinador.class);
        lenient().when(c.getActivo()).thenReturn(activo);
        if (codeProgram != null) {
            Programa p = mock(Programa.class);
            lenient().when(p.getCodeProgram()).thenReturn(codeProgram);
            lenient().when(c.getPrograma()).thenReturn(p);
        }
        return c;
    }

    @Test
    void admin_noTieneRestriccion() {
        sesion("admin@correounivalle.edu.co", "ROLE_ADMIN");

        AccesoProgramaService.Alcance alcance = service.alcanceActual();

        assertTrue(alcance.admin());
        assertNull(alcance.codeProgram());
        assertDoesNotThrow(() -> service.exigirProgramaPropio("3846-DIU"));
    }

    @Test
    void coordinador_enSuPrograma_pasa() {
        sesion("coord@correounivalle.edu.co", "ROLE_COORDINADOR");
        Coordinador coordinador = coordinador("3846-DIU", true);
        lenient().when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coord@correounivalle.edu.co"))
                .thenReturn(Optional.of(coordinador));

        AccesoProgramaService.Alcance alcance = service.alcanceActual();

        assertFalse(alcance.admin());
        assertEquals("3846-DIU", alcance.codeProgram());
        assertDoesNotThrow(() -> service.exigirProgramaPropio("3846-diu"));
    }

    @Test
    void coordinador_enOtroPrograma_lanzaAccessDenied() {
        sesion("coord@correounivalle.edu.co", "ROLE_COORDINADOR");
        Coordinador coordinador = coordinador("3846-DIU", true);
        lenient().when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coord@correounivalle.edu.co"))
                .thenReturn(Optional.of(coordinador));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.exigirProgramaPropio("3700-DIU"));

        assertEquals("Solo puede asignar docentes a cursos de su programa", ex.getMessage());
    }

    @Test
    void coordinador_sinProgramaOInactivo_lanzaAccessDenied() {
        sesion("coord@correounivalle.edu.co", "ROLE_COORDINADOR");

        Coordinador inactivo = coordinador("3846-DIU", false);
        lenient().when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coord@correounivalle.edu.co"))
                .thenReturn(Optional.of(inactivo));
        assertThrows(AccessDeniedException.class, () -> service.alcanceActual());

        Coordinador sinPrograma = coordinador(null, true);
        lenient().when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coord@correounivalle.edu.co"))
                .thenReturn(Optional.of(sinPrograma));
        assertThrows(AccessDeniedException.class, () -> service.alcanceActual());
    }

    @Test
    void coordinador_correoNoRegistrado_lanzaAccessDenied() {
        sesion("otro@correounivalle.edu.co", "ROLE_COORDINADOR");
        lenient().when(coordinadorRepository.findByCorreoInstitucionalWithDetails("otro@correounivalle.edu.co"))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.alcanceActual());
    }

    @Test
    void coordinador_sinClaimEmail_lanzaAccessDenied() {
        sesion(null, "ROLE_COORDINADOR");

        assertThrows(AccessDeniedException.class, () -> service.alcanceActual());
    }

    @Test
    void sinRolValido_lanzaAccessDenied() {
        sesion("docente@correounivalle.edu.co", "ROLE_DOCENTE");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.alcanceActual());

        assertEquals("No tiene permisos para esta acción", ex.getMessage());
    }

    @Test
    void sinSesion_lanzaAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> service.alcanceActual());
    }
}