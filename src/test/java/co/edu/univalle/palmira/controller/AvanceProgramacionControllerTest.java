package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.AvanceProgramacionDto;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.DocenteSinDisponibilidad;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Periodo;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Programa;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Resumen;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Totales;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Ventanas;
import co.edu.univalle.palmira.service.AvanceProgramacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvanceProgramacionControllerTest {

    static final Long PERIODO = 4L;

    @Mock
    private AvanceProgramacionService avanceService;

    @InjectMocks
    private AvanceProgramacionController controller;

    private Resumen resumen() {
        return new Resumen(
                new Periodo(PERIODO, "2026-2", "ACTIVO",
                        new Ventanas(Instant.now(), Instant.now(), null, null, true, false)),
                new Totales(10, 6, 3, 1, 0, 2),
                List.of(new Programa("4014-DIU", "Sistemas", 10, 6, 3, 1, 0)),
                List.of(new DocenteSinDisponibilidad(3L, "D003", "Ana Pérez",
                        "ana@univalle.edu.co", "Ingeniería", "Sistemas")),
                Instant.now());
    }

    @Test
    void obtenerAvance_responde200ConResumen() {
        when(avanceService.obtenerAvance(PERIODO)).thenReturn(resumen());

        ResponseEntity<AvanceProgramacionDto.Resumen> response = controller.obtenerAvance(PERIODO);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().totales().generados());
    }

    @Test
    void descargarExcel_respondeXlsxConNombreAdjunto() {
        when(avanceService.generarExcel(PERIODO)).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = controller.descargarExcel(PERIODO);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                response.getHeaders().getContentType());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("avance-programacion-4-"));
        assertTrue(disposition.contains(".xlsx"));
    }

    @Test
    void endpoints_soloAdmin() throws NoSuchMethodException {
        PreAuthorize tablero = AvanceProgramacionController.class
                .getMethod("obtenerAvance", Long.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize excel = AvanceProgramacionController.class
                .getMethod("descargarExcel", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(tablero);
        assertNotNull(excel);
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')", tablero.value());
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')", excel.value());
    }
}
