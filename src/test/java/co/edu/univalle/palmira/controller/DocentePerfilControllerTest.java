package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.service.DocenteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HU-015 / HU-022 C6: los endpoints de "Mi perfil" usan siempre el correo del JWT.
 */
@ExtendWith(MockitoExtension.class)
class DocentePerfilControllerTest {

    private static final String CORREO = "docente.prueba@correounivalle.edu.co";

    @Mock
    private DocenteService docenteService;

    @Mock
    private Authentication authentication;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private DocenteController docenteController;

    private void sesionDocente() {
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn(CORREO);
    }

    @Test
    @DisplayName("HU-022 C6: GET /api/docentes/me consulta el perfil con el correo del JWT")
    void miPerfil_usaCorreoDelJwt() {
        sesionDocente();

        docenteController.miPerfil(authentication);

        verify(docenteService).obtenerPorCorreo(CORREO);
    }

    @Test
    @DisplayName("HU-015 C2: PUT /api/docentes/me/contacto actualiza el contacto del docente autenticado")
    void actualizarContacto_usaCorreoDelJwt() {
        sesionDocente();
        DocenteDto.ContactoRequest req = new DocenteDto.ContactoRequest("ana@example.com", "3001234567");

        docenteController.actualizarContacto(authentication, req);

        verify(docenteService).actualizarContacto(CORREO, req);
    }

    @Test
    @DisplayName("Sin autenticación responde 401 «Sesión no válida o expirada»")
    void sinAutenticacion_401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> docenteController.miPerfil(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Sesión no válida o expirada", ex.getReason());
        verifyNoInteractions(docenteService);
    }
}
