package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PeriodoAcademicoDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PeriodoCalendarioPdfServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final PeriodoCalendarioPdfService service = new PeriodoCalendarioPdfService();

    @Test
    void extraeCorrectamenteCalendarioUnivalleYCalculaPlazos4SemanasAntes() {
        String texto = """
                UNIVERSIDAD DEL VALLE
                CONSEJO ACADÉMICO
                RESOLUCIÓN No. 161
                10 de septiembre de 2026
                ARTÍCULO 1°: Modificar parcialmente la Resolución No. 082 del 7 de mayo de 2026,
                período académico Agosto – Diciembre de 2026, incluyendo una fecha adicional
                para el proceso de Adiciones y Cancelaciones durante los días 24 y 25 de septiembre.

                Desarrollo de Semestre:
                Inicio de clases 24 de agosto
                Adición y cancelación de asignaturas 14 y 15 de septiembre
                Cancelación de asignaturas 8 y 9 de octubre
                Finalización de clases 11 de diciembre
                Finalización del Período 18 de diciembre
                """;

        PeriodoAcademicoDto.PdfPreviewResponse r = service.parsearTexto(
                texto, "https://registro.univalle.edu.co/wp-content/uploads/2026/09/RCA-161.pdf");

        assertEquals("Ago-Dic 2026", r.nombre());
        assertEquals("RES-161-2026", r.numeroResolucion());
        assertEquals(LocalDate.of(2026, 9, 10), r.fechaResolucion());
        assertEquals(LocalDate.of(2026, 8, 24), r.fechaInicioSemestre());
        assertEquals(LocalDate.of(2026, 12, 18), r.fechaFinSemestre());
        assertEquals(LocalDate.of(2026, 9, 14), r.fechaAdicionInicio());
        assertEquals(LocalDate.of(2026, 9, 15), r.fechaAdicionFin());
        assertEquals(LocalDate.of(2026, 10, 8), r.fechaCancelacionInicio());
        assertEquals(LocalDate.of(2026, 10, 9), r.fechaCancelacionFin());

        // 4 semanas antes del 24 de agosto = 27 de julio
        // Primeras 2 semanas (docentes): 27 julio 00:00 -> 9 agosto 23:59
        assertEquals(LocalDate.of(2026, 7, 27).atStartOfDay(BOGOTA).toInstant(), r.plazoDocenteInicio());
        assertEquals(LocalDate.of(2026, 8, 9).atTime(LocalTime.of(23, 59)).atZone(BOGOTA).toInstant(), r.plazoDocenteFin());
        // Siguientes 2 semanas (coordinadores): 10 agosto 00:00 -> 23 agosto 23:59
        assertEquals(LocalDate.of(2026, 8, 10).atStartOfDay(BOGOTA).toInstant(), r.plazoCoordinadorInicio());
        assertEquals(LocalDate.of(2026, 8, 23).atTime(LocalTime.of(23, 59)).atZone(BOGOTA).toInstant(), r.plazoCoordinadorFin());

        assertFalse(r.advertencias().isEmpty());
    }

    @Test
    void extraeCorrectamenteDesdePdfRealSiExisteEnScratch() throws Exception {
        Path pdf = Path.of(
                "C:\\Users\\ramir\\.gemini\\antigravity\\brain\\4e7a62fc-22ee-41fb-8ff0-060315592160\\scratch\\calendario_ejemplo.pdf");
        if (!Files.exists(pdf)) {
            return;
        }
        PeriodoAcademicoDto.PdfPreviewResponse r = service.extraerDesdeBytes(
                Files.readAllBytes(pdf),
                "https://registro.univalle.edu.co/wp-content/uploads/2026/09/RCA-161-Modific-Calendario-Sedes-pregrado-II-2026.pdf");

        assertEquals("Ago-Dic 2026", r.nombre());
        assertEquals("RES-161-2026", r.numeroResolucion());
        assertEquals(LocalDate.of(2026, 9, 10), r.fechaResolucion());
        assertEquals(LocalDate.of(2026, 8, 24), r.fechaInicioSemestre());
        assertEquals(LocalDate.of(2026, 12, 18), r.fechaFinSemestre());
        assertEquals(LocalDate.of(2026, 9, 14), r.fechaAdicionInicio());
        assertEquals(LocalDate.of(2026, 9, 15), r.fechaAdicionFin());
        assertEquals(LocalDate.of(2026, 10, 8), r.fechaCancelacionInicio());
        assertEquals(LocalDate.of(2026, 10, 9), r.fechaCancelacionFin());
    }

    @Test
    void rechazaUrlFueraDeUnivalle() {
        assertThrows(BadRequestException.class, () -> service.extraerDesdeUrl("https://evil.example.com/cal.pdf"));
    }
}
