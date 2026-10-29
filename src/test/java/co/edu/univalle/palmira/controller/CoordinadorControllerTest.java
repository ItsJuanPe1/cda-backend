package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.CoordinadorDto;
import co.edu.univalle.palmira.service.CoordinadorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoordinadorControllerTest {

    @Mock
    private CoordinadorService coordinadorService;

    @Mock
    private Authentication authentication;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private CoordinadorController coordinadorController;

    @Test
    @DisplayName("GET /api/coordinadores/me - Retorna perfil del coordinador autenticado vía JWT")
    void getMiPerfil_Exitoso() {
        String correo = "coordinador@correounivalle.edu.co";
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn(correo);

        CoordinadorDto.Response mockResponse = new CoordinadorDto.Response(
                1,
                "111222",
                "Juan Pérez",
                "juan@gmail.com",
                correo,
                "3001234567",
                "3743",
                "Tecnología en Desarrollo de Software",
                1,
                "Nombrado",
                1,
                "Tiempo Completo",
                true,
                null
        );

        when(coordinadorService.getByEmail(correo)).thenReturn(mockResponse);

        ResponseEntity<CoordinadorDto.Response> response = coordinadorController.getMiPerfil(authentication);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Juan Pérez", response.getBody().nombreCompleto());
        assertEquals("3743", response.getBody().codeProgram());
    }

    @Test
    @DisplayName("GET /api/coordinadores/me - Falla si no hay autenticación")
    void getMiPerfil_NoAutenticado() {
        assertThrows(ResponseStatusException.class, () -> coordinadorController.getMiPerfil(null));
    }
}

