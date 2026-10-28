package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.ProgramaDto;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.service.ProgramaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HU-040: respuestas HTTP de /api/programas. Datos de prueba ficticios.
 */
@ExtendWith(MockitoExtension.class)
class ProgramaControllerTest {

    @Mock
    private ProgramaService programaService;

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
}
