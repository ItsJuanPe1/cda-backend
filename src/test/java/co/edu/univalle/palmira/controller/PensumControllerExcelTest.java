package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.service.MallaExcelService;
import co.edu.univalle.palmira.service.PensumService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** HU-035: endpoint de descarga de la malla en Excel. */
@ExtendWith(MockitoExtension.class)
class PensumControllerExcelTest {

    @Mock
    private PensumService pensumService;
    @Mock
    private MallaExcelService mallaExcelService;

    @InjectMocks
    private PensumController controller;

    @Test
    @DisplayName("Criterio 2: responde un .xlsx como adjunto con el nombre descriptivo")
    void criterio2_respondeXlsxAdjunto() {
        byte[] contenido = {1, 2, 3};
        when(mallaExcelService.exportar("2724-DIU", null)).thenReturn(
                new MallaExcelService.Archivo("malla_tecnologia-en-desarrollo-de-software_diurna.xlsx", contenido));

        ResponseEntity<byte[]> response = controller.descargarExcel("2724-DIU", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getHeaders().getContentType().toString());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment"));
        assertTrue(disposition.contains("malla_tecnologia-en-desarrollo-de-software_diurna.xlsx"));
        assertEquals(3, response.getHeaders().getContentLength());
        assertArrayEquals(contenido, response.getBody());
    }

    @Test
    @DisplayName("Criterio 2: pasa la versión del historial que está en pantalla")
    void criterio2_pasaLaVersion() {
        when(mallaExcelService.exportar("2724-DIU", 1)).thenReturn(
                new MallaExcelService.Archivo("malla_x_diurna_historial.xlsx", new byte[]{9}));

        ResponseEntity<byte[]> response = controller.descargarExcel("2724-DIU", 1);

        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("malla_x_diurna_historial.xlsx"));
    }

    @Test
    @DisplayName("Administrador y coordinador pueden descargar")
    void rolesPermitidos() throws NoSuchMethodException {
        PreAuthorize pre = PensumController.class
                .getMethod("descargarExcel", String.class, Integer.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(pre);
        assertTrue(pre.value().contains("ROLE_ADMIN"));
        assertTrue(pre.value().contains("ROLE_COORDINADOR"));
        assertFalse(pre.value().contains("DOCENTE"));
    }
}
