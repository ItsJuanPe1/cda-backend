package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramaCoordinadorDto;
import co.edu.univalle.palmira.dto.ProgramaDto;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.service.ProgramaCoordinadorService;
import co.edu.univalle.palmira.service.ProgramaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HU-040 y HU-037: respuestas HTTP de /api/programas. Datos de prueba ficticios.
 */
@ExtendWith(MockitoExtension.class)
class ProgramaControllerTest {

    @Mock
    private ProgramaService programaService;

    @Mock
    private ProgramaCoordinadorService programaCoordinadorService;

    @InjectMocks
    private ProgramaController programaController;

    @Test
    @DisplayName("POST /api/programas - Criterio 1: crea el programa y responde 201")
    void crearResponde201() {
        ProgramaDto.CreateRequest req = new ProgramaDto.CreateRequest(
                "9002", "Programa Prueba", "AMBAS", 10, 11, "SEMESTRAL", null, null);
        ProgramaDto.Response creado = new ProgramaDto.Response(
                "9002", "Programa Prueba", Jornada.AMBAS, 10, 11, TipoIngreso.SEMESTRAL, EstadoPrograma.ACTIVO, null, 0L);
        when(programaService.create(req)).thenReturn(creado);

        ResponseEntity<ProgramaDto.Response> r = programaController.create(req);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertEquals(creado, r.getBody());
    }

    @Test
    @DisplayName("DELETE /api/programas/{code} - RN-13: desactiva el programa y responde 204")
    void eliminarDesactivaYResponde204() {
        ResponseEntity<Void> r = programaController.delete("9001");

        assertEquals(HttpStatus.NO_CONTENT, r.getStatusCode());
        verify(programaService).desactivar("9001");
    }

    @Test
    @DisplayName("GET /api/programas/coordinadores - HU-037 criterio 1: responde 200 con los programas y su coordinador")
    void listarConCoordinadorResponde200() {
        List<ProgramaCoordinadorDto.Response> lista = List.of(
                new ProgramaCoordinadorDto.Response("9001", "Programa Prueba", Jornada.DIURNA, false, null));
        when(programaCoordinadorService.listar("prueba", true)).thenReturn(lista);

        ResponseEntity<List<ProgramaCoordinadorDto.Response>> r = programaController.getConCoordinador("prueba", true);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(lista, r.getBody());
    }

    @Test
    @DisplayName("GET /api/programas/coordinadores - HU-037 criterio 4: sin programas responde 200 con lista vacía")
    void listarConCoordinadorVacioResponde200() {
        when(programaCoordinadorService.listar(null, false)).thenReturn(List.of());

        ResponseEntity<List<ProgramaCoordinadorDto.Response>> r = programaController.getConCoordinador(null, false);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(List.of(), r.getBody());
    }

    @Test
    @DisplayName("GET /api/programas/coordinadores - HU-037: solo el administrador tiene acceso")
    void listarConCoordinadorSoloAdmin() throws NoSuchMethodException {
        PreAuthorize regla = ProgramaController.class
                .getMethod("getConCoordinador", String.class, boolean.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(regla);
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')", regla.value());
    }
}
