package co.edu.univalle.palmira.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerAccessDeniedTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accessDenied_responde403ConElMensaje() {
        ResponseEntity<Map<String, Object>> res = handler.handleAccessDenied(
                new AccessDeniedException("Solo puede asignar docentes a cursos de su programa"));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertEquals("Solo puede asignar docentes a cursos de su programa", res.getBody().get("message"));
    }

    @Test
    void accessDenied_conMensajePorDefectoDeSpring_usaMensajeEnEspanol() {
        ResponseEntity<Map<String, Object>> res = handler.handleAccessDenied(
                new AccessDeniedException("Access Denied"));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertEquals("No tiene permisos para esta acción", res.getBody().get("message"));
    }
}