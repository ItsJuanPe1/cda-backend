package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.enums.CategoriaDocente;
import co.edu.univalle.palmira.model.enums.TipoDocente;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio encargado de generar el archivo Excel oficial (.xlsx) de la programación académica.
 * <p>
 * Incluye código de programa, jornada, semestre, código de asignatura, grupo, nombre de asignatura,
 * intensidad horaria, cédula, nombre, categoría y vinculación del docente, y detalle de horario
 * en 1 o 2 días.
 * </p>
 */
@Service
public class ProgramacionExcelService {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] CABECERAS = {
            "Plan (Código)",
            "Jornada",
            "Semestre",
            "Cód. Asignatura",
            "Grupo",
            "Nombre de la Asignatura",
            "Intensidad Horaria (h)",
            "N.° Cédula Docente",
            "Nombre del Docente",
            "Categoría Docente",
            "Vinculación Docente",
            "Día",
            "Hora Inicio",
            "Hora Fin",
            "Día 2",
            "Hora Inicio 2",
            "Hora Fin 2"
    };

    /**
     * Genera el libro Excel de programación académica.
     *
     * @param periodo Período académico correspondiente
     * @param cursos  Lista de cursos activos a exportar
     * @return Arreglo de bytes del archivo .xlsx
     */
    public byte[] generar(PeriodoAcademico periodo, List<CursoProgramacion> cursos) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Programación Académica");
            sheet.setDisplayGridlines(true);

            CellStyle estiloTitulo = crearEstiloTitulo(workbook);
            CellStyle estiloSubtitulo = crearEstiloSubtitulo(workbook);
            CellStyle estiloCabecera = crearEstiloCabecera(workbook);
            CellStyle estiloDatos = crearEstiloDatos(workbook);
            CellStyle estiloCentro = crearEstiloCentro(workbook);

            int filaNum = 0;

            // Fila de Título Principal
            Row rowTitulo = sheet.createRow(filaNum++);
            Cell cellTitulo = rowTitulo.createCell(0);
            cellTitulo.setCellValue("UNIVERSIDAD DEL VALLE - SEDE PALMIRA");
            cellTitulo.setCellStyle(estiloTitulo);

            // Fila de Subtítulo con Período
            Row rowSubtitulo = sheet.createRow(filaNum++);
            Cell cellSub = rowSubtitulo.createCell(0);
            String nombrePeriodo = periodo != null && periodo.getNombre() != null ? periodo.getNombre() : "N/A";
            cellSub.setCellValue("PROGRAMACIÓN ACADÉMICA - PERÍODO: " + nombrePeriodo);
            cellSub.setCellStyle(estiloSubtitulo);

            filaNum++; // Espacio en blanco

            // Fila de Cabecera
            Row rowCabecera = sheet.createRow(filaNum++);
            for (int col = 0; col < CABECERAS.length; col++) {
                Cell cell = rowCabecera.createCell(col);
                cell.setCellValue(CABECERAS[col]);
                cell.setCellStyle(estiloCabecera);
            }

            int primeraFilaDatos = filaNum;

            // Filas de Datos
            for (CursoProgramacion curso : cursos) {
                Row row = sheet.createRow(filaNum++);
                Docente d = curso.getDocente();

                // 0: Plan (Código Programa)
                String plan = curso.getPrograma() != null ? curso.getPrograma().getCodeProgram() : "";
                crearCelda(row, 0, plan, estiloCentro);

                // 1: Jornada
                String jornada = curso.getJornada() != null ? curso.getJornada().name() : "";
                crearCelda(row, 1, jornada, estiloCentro);

                // 2: Semestre
                String sem = curso.getSemestre() != null ? String.valueOf(curso.getSemestre()) : "";
                crearCelda(row, 2, sem, estiloCentro);

                // 3: Código Asignatura
                String codAsig = curso.getAsignatura() != null ? curso.getAsignatura().getCodeCourse() : "";
                crearCelda(row, 3, codAsig, estiloCentro);

                // 4: Grupo
                String grupo = curso.getGrupo() != null ? String.valueOf(curso.getGrupo()) : "";
                crearCelda(row, 4, grupo, estiloCentro);

                // 5: Nombre Asignatura
                String nombreAsig = curso.getNombreAsignatura() != null ? curso.getNombreAsignatura() : "";
                crearCelda(row, 5, nombreAsig, estiloDatos);

                // 6: Intensidad Horaria
                String horas = "";
                if (curso.getAsignatura() != null && curso.getAsignatura().getHorasSemanales() != null) {
                    horas = String.valueOf(curso.getAsignatura().getHorasSemanales());
                }
                crearCelda(row, 6, horas, estiloCentro);

                // 7: Cédula Docente
                String cedula = d != null && d.getNumeroDocumento() != null ? d.getNumeroDocumento() : "SIN ASIGNAR";
                crearCelda(row, 7, cedula, estiloCentro);

                // 8: Nombre Docente
                String nombreDocente = d != null ? (d.getNombres() + " " + d.getApellidos()).trim() : "SIN ASIGNAR";
                crearCelda(row, 8, nombreDocente, estiloDatos);

                // 9: Categoría Docente
                String cat = d != null && d.getCategoria() != null ? formatearCategoria(d.getCategoria()) : "";
                crearCelda(row, 9, cat, estiloCentro);

                // 10: Vinculación Docente
                String vinc = d != null && d.getTipoDocente() != null ? formatearVinculacion(d.getTipoDocente()) : "";
                crearCelda(row, 10, vinc, estiloCentro);

                // 11: Día
                crearCelda(row, 11, formatearDia(curso.getDia()), estiloCentro);

                // 12: Hora Inicio
                crearCelda(row, 12, formatearHora(curso.getHoraInicio()), estiloCentro);

                // 13: Hora Fin
                crearCelda(row, 13, formatearHora(curso.getHoraFin()), estiloCentro);

                // 14: Día 2
                crearCelda(row, 14, formatearDia(curso.getDia2()), estiloCentro);

                // 15: Hora Inicio 2
                crearCelda(row, 15, formatearHora(curso.getHoraInicio2()), estiloCentro);

                // 16: Hora Fin 2
                crearCelda(row, 16, formatearHora(curso.getHoraFin2()), estiloCentro);
            }

            // Ancho de columnas preconfigurado para legibilidad óptima
            int[] anchos = { 14, 12, 11, 16, 10, 38, 20, 20, 34, 18, 20, 14, 14, 14, 14, 14, 14 };
            for (int col = 0; col < anchos.length; col++) {
                sheet.setColumnWidth(col, anchos[col] * 256);
            }

            // Filtro automático y panel fijo en la cabecera
            if (filaNum > primeraFilaDatos) {
                sheet.setAutoFilter(new CellRangeAddress(primeraFilaDatos - 1, filaNum - 1, 0, CABECERAS.length - 1));
            }
            sheet.createFreezePane(0, primeraFilaDatos);

            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar el archivo Excel de la programación académica", e);
        }
    }

    private static void crearCelda(Row row, int col, String valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor != null ? valor : "");
        cell.setCellStyle(estilo);
    }

    private static String formatearDia(Integer dia) {
        if (dia == null) return "";
        return switch (dia) {
            case 1 -> "Lunes";
            case 2 -> "Martes";
            case 3 -> "Miércoles";
            case 4 -> "Jueves";
            case 5 -> "Viernes";
            case 6 -> "Sábado";
            case 7 -> "Domingo";
            default -> "Día " + dia;
        };
    }

    private static String formatearHora(LocalTime hora) {
        if (hora == null) return "";
        return hora.format(FORMATO_HORA);
    }

    private static String formatearCategoria(CategoriaDocente cat) {
        if (cat == null) return "";
        return switch (cat) {
            case AUXILIAR -> "Auxiliar";
            case ASISTENTE -> "Asistente";
            case ASOCIADO -> "Asociado";
            case TITULAR -> "Titular";
        };
    }

    private static String formatearVinculacion(TipoDocente tipo) {
        if (tipo == null) return "";
        return switch (tipo) {
            case NOMBRADO -> "Nombrado";
            case HORA_CATEDRA -> "Hora Cátedra";
        };
    }

    private static CellStyle crearEstiloTitulo(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        Font fuente = wb.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 13);
        fuente.setColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFont(fuente);
        return estilo;
    }

    private static CellStyle crearEstiloSubtitulo(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        Font fuente = wb.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 11);
        fuente.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        estilo.setFont(fuente);
        return estilo;
    }

    private static CellStyle crearEstiloCabecera(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        Font fuente = wb.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        aplicarBordes(estilo);
        return estilo;
    }

    private static CellStyle crearEstiloDatos(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        aplicarBordes(estilo);
        return estilo;
    }

    private static CellStyle crearEstiloCentro(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        estilo.setAlignment(HorizontalAlignment.CENTER);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        aplicarBordes(estilo);
        return estilo;
    }

    private static void aplicarBordes(CellStyle estilo) {
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
    }
}
