package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.CategoriaDocente;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoDocente;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgramacionExcelServiceTest {

    private final ProgramacionExcelService excelService = new ProgramacionExcelService();

    @Test
    @DisplayName("Genera libro Excel con las 17 columnas requeridas y datos de sesión 1 y 2")
    void testGenerarExcelProgramacion_Exitoso() throws IOException {
        PeriodoAcademico periodo = PeriodoAcademico.builder()
                .id(1L)
                .nombre("2026-1")
                .build();

        Programa prog = Programa.builder()
                .codeProgram("3743")
                .nameProgram("Tecnología en Desarrollo de Software")
                .build();

        Asignatura asig1 = Asignatura.builder()
                .codeCourse("750001M")
                .nameCourse("Fundamentos de Programación")
                .horasSemanales(4)
                .build();

        Docente doc1 = Docente.builder()
                .id(10L)
                .numeroDocumento("111222333")
                .nombres("Juan Carlos")
                .apellidos("Pérez López")
                .categoria(CategoriaDocente.ASISTENTE)
                .tipoDocente(TipoDocente.NOMBRADO)
                .build();

        CursoProgramacion curso1 = CursoProgramacion.builder()
                .id(101L)
                .periodo(periodo)
                .programa(prog)
                .asignatura(asig1)
                .nombreAsignatura("Fundamentos de Programación")
                .semestre(1)
                .jornada(Jornada.DIURNA)
                .grupo(50)
                .docente(doc1)
                .dia(1) // Lunes
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(10, 0))
                .dia2(3) // Miércoles
                .horaInicio2(LocalTime.of(8, 0))
                .horaFin2(LocalTime.of(10, 0))
                .horario("LUN 08:00 - 10:00 / MIE 08:00 - 10:00")
                .estado(CursoProgramacion.ESTADO_ASIGNADO)
                .build();

        CursoProgramacion curso2 = CursoProgramacion.builder()
                .id(102L)
                .periodo(periodo)
                .programa(prog)
                .asignatura(asig1)
                .nombreAsignatura("Fundamentos de Programación")
                .semestre(1)
                .jornada(Jornada.NOCTURNA)
                .grupo(51)
                .docente(null) // Sin docente
                .dia(null)
                .horaInicio(null)
                .horaFin(null)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO)
                .build();

        byte[] bytes = excelService.generar(periodo, List.of(curso1, curso2));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Programación Académica");
            assertNotNull(sheet);

            // Fila 3 es la cabecera (fila 0: título, fila 1: subtítulo, fila 2: blanco, fila 3: cabecera)
            Row rowCabecera = sheet.getRow(3);
            assertNotNull(rowCabecera);
            assertEquals("Plan (Código)", rowCabecera.getCell(0).getStringCellValue());
            assertEquals("Jornada", rowCabecera.getCell(1).getStringCellValue());
            assertEquals("Semestre", rowCabecera.getCell(2).getStringCellValue());
            assertEquals("Cód. Asignatura", rowCabecera.getCell(3).getStringCellValue());
            assertEquals("Grupo", rowCabecera.getCell(4).getStringCellValue());
            assertEquals("Nombre de la Asignatura", rowCabecera.getCell(5).getStringCellValue());
            assertEquals("Intensidad Horaria (h)", rowCabecera.getCell(6).getStringCellValue());
            assertEquals("N.° Cédula Docente", rowCabecera.getCell(7).getStringCellValue());
            assertEquals("Nombre del Docente", rowCabecera.getCell(8).getStringCellValue());
            assertEquals("Categoría Docente", rowCabecera.getCell(9).getStringCellValue());
            assertEquals("Vinculación Docente", rowCabecera.getCell(10).getStringCellValue());
            assertEquals("Día", rowCabecera.getCell(11).getStringCellValue());
            assertEquals("Hora Inicio", rowCabecera.getCell(12).getStringCellValue());
            assertEquals("Hora Fin", rowCabecera.getCell(13).getStringCellValue());
            assertEquals("Día 2", rowCabecera.getCell(14).getStringCellValue());
            assertEquals("Hora Inicio 2", rowCabecera.getCell(15).getStringCellValue());
            assertEquals("Hora Fin 2", rowCabecera.getCell(16).getStringCellValue());

            // Fila 4: Curso 1 con 2 días y docente
            Row rowCurso1 = sheet.getRow(4);
            assertEquals("3743", rowCurso1.getCell(0).getStringCellValue());
            assertEquals("DIURNA", rowCurso1.getCell(1).getStringCellValue());
            assertEquals("1", rowCurso1.getCell(2).getStringCellValue());
            assertEquals("750001M", rowCurso1.getCell(3).getStringCellValue());
            assertEquals("50", rowCurso1.getCell(4).getStringCellValue());
            assertEquals("Fundamentos de Programación", rowCurso1.getCell(5).getStringCellValue());
            assertEquals("4", rowCurso1.getCell(6).getStringCellValue());
            assertEquals("111222333", rowCurso1.getCell(7).getStringCellValue());
            assertEquals("Juan Carlos Pérez López", rowCurso1.getCell(8).getStringCellValue());
            assertEquals("Asistente", rowCurso1.getCell(9).getStringCellValue());
            assertEquals("Nombrado", rowCurso1.getCell(10).getStringCellValue());
            assertEquals("Lunes", rowCurso1.getCell(11).getStringCellValue());
            assertEquals("08:00", rowCurso1.getCell(12).getStringCellValue());
            assertEquals("10:00", rowCurso1.getCell(13).getStringCellValue());
            assertEquals("Miércoles", rowCurso1.getCell(14).getStringCellValue());
            assertEquals("08:00", rowCurso1.getCell(15).getStringCellValue());
            assertEquals("10:00", rowCurso1.getCell(16).getStringCellValue());

            // Fila 5: Curso 2 sin docente
            Row rowCurso2 = sheet.getRow(5);
            assertEquals("SIN ASIGNAR", rowCurso2.getCell(7).getStringCellValue());
            assertEquals("SIN ASIGNAR", rowCurso2.getCell(8).getStringCellValue());
            assertEquals("", rowCurso2.getCell(9).getStringCellValue());
            assertEquals("", rowCurso2.getCell(10).getStringCellValue());
            assertEquals("", rowCurso2.getCell(11).getStringCellValue());
        }
    }
}
