package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AvanceProgramacionDto;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class AvanceProgramacionExcelService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.forLanguageTag("es-CO"))
                    .withZone(ZONA);

    public byte[] generar(AvanceProgramacionDto.Resumen avance,
                          List<AvanceProgramacionDto.CursoPendiente> pendientes) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle titulo = estiloTitulo(workbook);
            CellStyle cabecera = estiloCabecera(workbook);

            crearResumen(workbook, avance, titulo, cabecera);
            crearPendientes(workbook, pendientes, cabecera);
            crearDocentes(workbook, avance.docentesSinDisponibilidad(), cabecera);

            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo Excel del avance", ex);
        }
    }

    private void crearResumen(Workbook workbook, AvanceProgramacionDto.Resumen avance,
                              CellStyle titulo, CellStyle cabecera) {
        Sheet sheet = workbook.createSheet("Resumen");
        int fila = 0;

        Row rowTitulo = sheet.createRow(fila++);
        rowTitulo.createCell(0).setCellValue("Avance de la programación académica");
        rowTitulo.getCell(0).setCellStyle(titulo);

        fila = escribirPar(sheet, fila, "Período", avance.periodo().nombre());
        fila = escribirPar(sheet, fila, "Estado del período", avance.periodo().estado());
        fila = escribirPar(sheet, fila, "Generado el", fecha(avance.generadoEn()));
        fila = escribirPar(sheet, fila, "Cierre ventana docente", fecha(avance.periodo().ventanas().docenteFin()));
        fila = escribirPar(sheet, fila, "Cierre ventana coordinación", fecha(avance.periodo().ventanas().coordinadorFin()));
        fila++;

        Row rowTotales = sheet.createRow(fila++);
        crearCabecera(rowTotales, cabecera, "Generados", "Con docente", "Pendientes de asignación",
                "Pendientes de contratación", "Excluidos", "Docentes sin disponibilidad");
        Row rowValores = sheet.createRow(fila++);
        escribirNumeros(rowValores, avance.totales().generados(), avance.totales().conDocente(),
                avance.totales().pendientesAsignacion(), avance.totales().pendientesContratacion(),
                avance.totales().excluidos(), avance.totales().docentesSinDisponibilidad());
        fila++;

        Row rowCabecera = sheet.createRow(fila++);
        crearCabecera(rowCabecera, cabecera, "Código", "Programa", "Generados", "Con docente",
                "Pendientes de asignación", "Pendientes de contratación", "Excluidos");

        for (AvanceProgramacionDto.Programa programa : avance.programas()) {
            Row row = sheet.createRow(fila++);
            row.createCell(0).setCellValue(programa.codeProgram());
            row.createCell(1).setCellValue(programa.nombrePrograma());
            escribirNumeros(row, 2, programa.generados(), programa.conDocente(),
                    programa.pendientesAsignacion(), programa.pendientesContratacion(), programa.excluidos());
        }

        if (avance.programas().isEmpty()) {
            sheet.createRow(fila).createCell(0).setCellValue("Sin programas para el período");
        }

        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 38 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setColumnWidth(4, 26 * 256);
        sheet.setColumnWidth(5, 28 * 256);
        sheet.setColumnWidth(6, 12 * 256);
    }

    private void crearPendientes(Workbook workbook, List<AvanceProgramacionDto.CursoPendiente> pendientes,
                                 CellStyle cabecera) {
        Sheet sheet = workbook.createSheet("Cursos pendientes");
        Row rowCabecera = sheet.createRow(0);
        crearCabecera(rowCabecera, cabecera, "Programa", "Código programa", "Asignatura",
                "Código asignatura", "Semestre", "Jornada", "Grupo", "Resolución", "Tipo de pendiente");

        int fila = 1;
        for (AvanceProgramacionDto.CursoPendiente curso : pendientes) {
            Row row = sheet.createRow(fila++);
            row.createCell(0).setCellValue(curso.nombrePrograma());
            row.createCell(1).setCellValue(curso.codeProgram());
            row.createCell(2).setCellValue(curso.nombreAsignatura());
            row.createCell(3).setCellValue(curso.codeCourse());
            if (curso.semestre() != null) {
                row.createCell(4).setCellValue(curso.semestre());
            }
            if (curso.jornada() != null) {
                row.createCell(5).setCellValue(curso.jornada());
            }
            if (curso.grupo() != null) {
                row.createCell(6).setCellValue(curso.grupo());
            }
            if (curso.numeroResolucion() != null) {
                row.createCell(7).setCellValue(curso.numeroResolucion());
            }
            row.createCell(8).setCellValue(tipoPendiente(curso.tipoPendiente()));
        }

        if (pendientes.isEmpty()) {
            sheet.createRow(1).createCell(0).setCellValue("Sin registros");
        } else {
            sheet.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, 8));
        }
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 38 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 38 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 10 * 256);
        sheet.setColumnWidth(5, 12 * 256);
        sheet.setColumnWidth(6, 8 * 256);
        sheet.setColumnWidth(7, 22 * 256);
        sheet.setColumnWidth(8, 26 * 256);
    }

    private void crearDocentes(Workbook workbook,
                               List<AvanceProgramacionDto.DocenteSinDisponibilidad> docentes,
                               CellStyle cabecera) {
        Sheet sheet = workbook.createSheet("Docentes sin disponibilidad");
        Row rowCabecera = sheet.createRow(0);
        crearCabecera(rowCabecera, cabecera, "Código", "Nombre", "Correo institucional",
                "Facultad", "Programa");

        int fila = 1;
        for (AvanceProgramacionDto.DocenteSinDisponibilidad docente : docentes) {
            Row row = sheet.createRow(fila++);
            escribirTexto(row, 0, docente.codigo());
            escribirTexto(row, 1, docente.nombreCompleto());
            escribirTexto(row, 2, docente.correoInstitucional());
            escribirTexto(row, 3, docente.facultad());
            escribirTexto(row, 4, docente.programa());
        }

        if (docentes.isEmpty()) {
            sheet.createRow(1).createCell(0).setCellValue("Sin registros");
        } else {
            sheet.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, 4));
        }
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 36 * 256);
        sheet.setColumnWidth(2, 36 * 256);
        sheet.setColumnWidth(3, 30 * 256);
        sheet.setColumnWidth(4, 38 * 256);
    }

    private static int escribirPar(Sheet sheet, int fila, String etiqueta, String valor) {
        Row row = sheet.createRow(fila);
        row.createCell(0).setCellValue(etiqueta);
        row.createCell(1).setCellValue(valor != null ? valor : "");
        return fila + 1;
    }

    private static void crearCabecera(Row row, CellStyle estilo, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(valores[i]);
            cell.setCellStyle(estilo);
        }
    }

    private static void escribirNumeros(Row row, long... valores) {
        escribirNumeros(row, 0, valores);
    }

    private static void escribirNumeros(Row row, int desde, long... valores) {
        for (int i = 0; i < valores.length; i++) {
            row.createCell(desde + i).setCellValue(valores[i]);
        }
    }

    private static void escribirTexto(Row row, int columna, String valor) {
        row.createCell(columna).setCellValue(valor != null ? valor : "");
    }

    private static String fecha(java.time.Instant valor) {
        return valor != null ? FORMATO_FECHA.format(valor) : "Sin plazo definido";
    }

    private static String tipoPendiente(String valor) {
        return "PENDIENTE_CONTRATACION".equals(valor)
                ? "Pendiente de contratación"
                : "Pendiente de asignación";
    }

    private static CellStyle estiloTitulo(Workbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFont(font);
        return estilo;
    }

    private static CellStyle estiloCabecera(Workbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(font);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }
}
