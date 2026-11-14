package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AvanceProgramacionDto;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.CursoPendiente;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.DocenteSinDisponibilidad;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Periodo;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Programa;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Resumen;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Totales;
import co.edu.univalle.palmira.dto.AvanceProgramacionDto.Ventanas;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AvanceProgramacionExcelServiceTest {

    private final AvanceProgramacionExcelService service = new AvanceProgramacionExcelService();

    private Resumen resumen() {
        return new Resumen(
                new Periodo(1L, "2026-2", "ACTIVO",
                        new Ventanas(Instant.now(), Instant.now(), null, null, true, false)),
                new Totales(2, 1, 1, 0, 0, 1),
                List.of(new Programa("4014-DIU", "Ingeniería de Sistemas", 2, 1, 1, 0, 0)),
                List.of(new DocenteSinDisponibilidad(3L, "D003", "Ana Pérez",
                        "ana@univalle.edu.co", "Ingeniería", "Ingeniería de Sistemas")),
                Instant.now());
    }

    @Test
    void generaTresHojasConLosPendientesYDocentes() throws Exception {
        List<CursoPendiente> pendientes = List.of(
                new CursoPendiente("4014-DIU", "Ingeniería de Sistemas", "MAT101", "Cálculo",
                        1, "DIURNA", 50, "Res. 001", "PENDIENTE_ASIGNACION"),
                new CursoPendiente("4014-DIU", "Ingeniería de Sistemas", "FIS101", "Física",
                        2, "DIURNA", 50, "Res. 001", "PENDIENTE_CONTRATACION"));

        byte[] archivo = service.generar(resumen(), pendientes);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            assertEquals(3, workbook.getNumberOfSheets());

            Sheet resumen = workbook.getSheet("Resumen");
            assertEquals("2026-2", resumen.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Generados", resumen.getRow(7).getCell(0).getStringCellValue());

            Sheet cursos = workbook.getSheet("Cursos pendientes");
            assertEquals("Cálculo", cursos.getRow(1).getCell(2).getStringCellValue());
            assertEquals("Pendiente de asignación", cursos.getRow(1).getCell(8).getStringCellValue());
            assertEquals("Pendiente de contratación", cursos.getRow(2).getCell(8).getStringCellValue());

            Sheet docentes = workbook.getSheet("Docentes sin disponibilidad");
            assertEquals("D003", docentes.getRow(1).getCell(0).getStringCellValue());
            assertEquals("ana@univalle.edu.co", docentes.getRow(1).getCell(2).getStringCellValue());
        }
    }

    @Test
    void sinPendientesGeneraEncabezadosYFilaSinRegistros() throws Exception {
        byte[] archivo = service.generar(resumen(), List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            Sheet cursos = workbook.getSheet("Cursos pendientes");
            assertEquals("Tipo de pendiente", cursos.getRow(0).getCell(8).getStringCellValue());
            assertEquals("Sin registros", cursos.getRow(1).getCell(0).getStringCellValue());
        }
    }
}
