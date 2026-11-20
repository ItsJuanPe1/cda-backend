package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PensumItemDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * HU-035: exporta a Excel la malla curricular que el usuario tiene en pantalla
 * (vigente o una versión del historial).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallaExcelService {

    public static final String MSG_NO_GENERADO = "No se pudo generar el archivo";
    public static final String MSG_FUERA_DE_PROGRAMA = "Solo puede descargar la malla de su programa";
    public static final String MSG_SIN_MALLA = "El programa no tiene malla registrada";
    public static final String HOJA = "Malla";

    static final String[] COLUMNAS = {
            "Código", "Asignatura", "Créditos", "Horas de clase", "Horas de estudio independiente",
            "Tipo de asignatura", "Electiva", "Facultad", "Estado"
    };

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.forLanguageTag("es-CO"));

    private final PensumService pensumService;
    private final ProgramaRepository programaRepository;
    private final AccesoProgramaService accesoProgramaService;

    /** Archivo listo para enviar: nombre descriptivo y contenido .xlsx. */
    public record Archivo(String nombre, byte[] contenido) {}

    @Transactional(readOnly = true)
    public Archivo exportar(String codeProgram, Integer version) {
        String codigo = codeProgram == null ? "" : codeProgram.trim().toUpperCase();

        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();
        if (!alcance.admin() && !alcance.codeProgram().equalsIgnoreCase(codigo)) {
            throw new AccessDeniedException(MSG_FUERA_DE_PROGRAMA);
        }

        Programa programa = programaRepository.findById(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + codigo));

        List<PensumItemDto.Response> filas;
        if (version == null) {
            filas = pensumService.getByProgram(codigo);
            if (filas.isEmpty()) {
                throw new ConflictException(MSG_SIN_MALLA);
            }
        } else {
            filas = pensumService.getVersion(codigo, version);
            if (filas.isEmpty()) {
                throw new ResourceNotFoundException("La versión " + version + " de la malla no existe");
            }
        }

        boolean historial = !Boolean.TRUE.equals(filas.get(0).vigente());
        byte[] contenido = generar(programa, filas, historial, Instant.now());
        return new Archivo(nombreArchivo(programa, historial), contenido);
    }

    /** Arma el libro. Cualquier falla se informa como «No se pudo generar el archivo» (criterio 3). */
    byte[] generar(Programa programa, List<PensumItemDto.Response> filas, boolean historial, Instant generadoEn) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(workbook);
            Sheet sheet = workbook.createSheet(HOJA);
            PensumItemDto.Response meta = filas.get(0);

            int fila = 0;
            Row titulo = sheet.createRow(fila++);
            celda(titulo, 0, "Malla curricular - " + texto(programa.getNameProgram()), estilos.titulo);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMNAS.length - 1));
            fila++;

            int totalCreditos = filas.stream().mapToInt(f -> entero(f.credits())).sum();
            fila = par(sheet, fila, estilos, "Código del programa", programa.getCodeProgram());
            fila = par(sheet, fila, estilos, "Programa", programa.getNameProgram());
            fila = par(sheet, fila, estilos, "Número de resolución", meta.numeroResolucion());
            fila = par(sheet, fila, estilos, "Descripción", meta.descripcion());
            fila = par(sheet, fila, estilos, "Jornada", etiquetaJornada(meta.jornada(), programa));
            fila = par(sheet, fila, estilos, "Periodo de oferta", capitalizar(meta.periodoOferta()));
            fila = par(sheet, fila, estilos, "Número de semestres", numeroTexto(meta.numeroSemestres()));
            fila = par(sheet, fila, estilos, "Versión", numeroTexto(meta.version()));
            fila = par(sheet, fila, estilos, "Estado", historial ? "Historial" : "Vigente");
            boolean editada = filas.stream().anyMatch(f -> Boolean.TRUE.equals(f.editada()));
            fila = par(sheet, fila, estilos, "Editada", editada ? "Sí" : "No");
            LocalDateTime ultimaEdicion = filas.stream()
                    .map(PensumItemDto.Response::fechaEdicion)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            fila = par(sheet, fila, estilos, "Última edición",
                    ultimaEdicion == null ? "" : FORMATO_FECHA.format(ultimaEdicion));
            fila = par(sheet, fila, estilos, "Total de asignaturas", String.valueOf(filas.size()));
            fila = par(sheet, fila, estilos, "Total de créditos", String.valueOf(totalCreditos));
            fila = par(sheet, fila, estilos, "Generado el", FORMATO_FECHA.format(generadoEn.atZone(ZONA)));
            fila++;

            Map<Integer, List<PensumItemDto.Response>> porSemestre = new TreeMap<>();
            for (PensumItemDto.Response f : filas) {
                porSemestre.computeIfAbsent(entero(f.semestre()), k -> new ArrayList<>()).add(f);
            }
            int ultimoConAsignaturas = porSemestre.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            int semestres = Math.max(entero(meta.numeroSemestres()), ultimoConAsignaturas);

            for (int s = 1; s <= semestres; s++) {
                fila = semestre(sheet, fila, estilos, s, porSemestre.getOrDefault(s, List.of()));
            }
            // Asignaturas sin semestre válido (0 o nulo): no deberían existir, pero no se pierden.
            if (porSemestre.containsKey(0)) {
                fila = semestre(sheet, fila, estilos, 0, porSemestre.get(0));
            }

            int[] anchos = {14, 44, 10, 14, 18, 26, 10, 30, 12};
            for (int i = 0; i < anchos.length; i++) {
                sheet.setColumnWidth(i, anchos[i] * 256);
            }

            workbook.write(salida);
            return salida.toByteArray();
        } catch (Exception ex) {
            log.error("Fallo al generar el Excel de la malla del programa {}", programa.getCodeProgram(), ex);
            throw new IllegalStateException(MSG_NO_GENERADO, ex);
        }
    }

    private int semestre(Sheet sheet, int fila, Estilos estilos, int numero, List<PensumItemDto.Response> asignaturas) {
        Row titulo = sheet.createRow(fila);
        celda(titulo, 0, numero == 0 ? "Sin semestre" : "Semestre " + numero, estilos.semestre);
        sheet.addMergedRegion(new CellRangeAddress(fila, fila, 0, COLUMNAS.length - 1));
        fila++;

        Row cabecera = sheet.createRow(fila++);
        for (int i = 0; i < COLUMNAS.length; i++) {
            celda(cabecera, i, COLUMNAS[i], estilos.cabecera);
        }

        if (asignaturas.isEmpty()) {
            Row vacia = sheet.createRow(fila++);
            celda(vacia, 0, "Sin asignaturas", estilos.borde);
            return fila + 1;
        }

        List<PensumItemDto.Response> ordenadas = asignaturas.stream()
                .sorted(Comparator.comparing((PensumItemDto.Response f) -> texto(f.nameCourse()),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        int creditos = 0;
        int horasClase = 0;
        int horasIndependiente = 0;
        for (PensumItemDto.Response f : ordenadas) {
            Row row = sheet.createRow(fila++);
            celda(row, 0, f.codeCourse(), estilos.borde);
            celda(row, 1, f.nameCourse(), estilos.borde);
            numero(row, 2, f.credits(), estilos.borde);
            numero(row, 3, f.horasClase(), estilos.borde);
            numero(row, 4, f.horasIndependiente(), estilos.borde);
            celda(row, 5, etiquetaTipo(f), estilos.borde);
            celda(row, 6, Boolean.TRUE.equals(f.isElective()) ? "Sí" : "No", estilos.borde);
            celda(row, 7, f.facultad(), estilos.borde);
            celda(row, 8, Boolean.FALSE.equals(f.activo()) ? "Inactiva" : "Activa", estilos.borde);
            creditos += entero(f.credits());
            horasClase += entero(f.horasClase());
            horasIndependiente += entero(f.horasIndependiente());
        }

        Row subtotal = sheet.createRow(fila++);
        celda(subtotal, 0, numero == 0 ? "Subtotal sin semestre" : "Subtotal semestre " + numero, estilos.subtotal);
        celda(subtotal, 1, "", estilos.subtotal);
        numero(subtotal, 2, creditos, estilos.subtotal);
        numero(subtotal, 3, horasClase, estilos.subtotal);
        numero(subtotal, 4, horasIndependiente, estilos.subtotal);
        for (int i = 5; i < COLUMNAS.length; i++) {
            celda(subtotal, i, "", estilos.subtotal);
        }
        return fila + 1;
    }

    /** malla_nombre-programa_jornada[_historial].xlsx, sin tildes ni espacios. */
    static String nombreArchivo(Programa programa, boolean historial) {
        String nombre = slug(programa.getNameProgram() != null ? programa.getNameProgram() : programa.getCodeProgram());
        String jornada = programa.getJornada() != null ? slug(programa.getJornada().name()) : "";
        StringBuilder sb = new StringBuilder("malla_").append(nombre.isEmpty() ? "programa" : nombre);
        if (!jornada.isEmpty()) {
            sb.append('_').append(jornada);
        }
        if (historial) {
            sb.append("_historial");
        }
        return sb.append(".xlsx").toString();
    }

    static String slug(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    static String etiquetaTipo(PensumItemDto.Response f) {
        String tipo = f.tipoAsignatura();
        if (tipo != null && !tipo.isBlank()) {
            return switch (tipo.trim().toUpperCase(Locale.ROOT)) {
                case "BASICA" -> "Asignatura básica";
                case "ELECTIVA_COMPLEMENTARIA" -> "Electiva complementaria";
                case "EXTRACURRICULAR" -> "Act. extracurricular";
                case "PROFESIONAL" -> "Profesional";
                default -> tipo.trim();
            };
        }
        if (Boolean.TRUE.equals(f.esComplementaria())) return "Electiva complementaria";
        if (Boolean.TRUE.equals(f.isElective())) return "Act. extracurricular";
        if (Boolean.TRUE.equals(f.esBasica())) return "Asignatura básica";
        return "Profesional";
    }

    private static String etiquetaJornada(String jornada, Programa programa) {
        String valor = jornada != null ? jornada
                : (programa.getJornada() != null ? programa.getJornada().name() : null);
        return capitalizar(valor);
    }

    private static String capitalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String v = valor.trim();
        if (!v.equals(v.toUpperCase(Locale.ROOT))) {
            return v;
        }
        String minus = v.toLowerCase(Locale.forLanguageTag("es-CO")).replace('_', ' ');
        return Character.toUpperCase(minus.charAt(0)) + minus.substring(1);
    }

    private static int par(Sheet sheet, int fila, Estilos estilos, String etiqueta, String valor) {
        Row row = sheet.createRow(fila);
        celda(row, 0, etiqueta, estilos.etiqueta);
        celda(row, 1, valor, null);
        return fila + 1;
    }

    private static void celda(Row row, int col, String valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor == null ? "" : valor);
        if (estilo != null) {
            cell.setCellStyle(estilo);
        }
    }

    private static void numero(Row row, int col, Integer valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        if (valor != null) {
            cell.setCellValue(valor);
        }
        if (estilo != null) {
            cell.setCellStyle(estilo);
        }
    }

    private static int entero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private static String numeroTexto(Integer valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle etiqueta;
        final CellStyle semestre;
        final CellStyle cabecera;
        final CellStyle borde;
        final CellStyle subtotal;

        Estilos(Workbook wb) {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            Font grande = wb.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            Font blanca = wb.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());

            titulo = wb.createCellStyle();
            titulo.setFont(grande);

            etiqueta = wb.createCellStyle();
            etiqueta.setFont(negrita);

            semestre = wb.createCellStyle();
            semestre.setFont(blanca);
            semestre.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            semestre.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            cabecera = wb.createCellStyle();
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setWrapText(true);
            bordes(cabecera);

            borde = wb.createCellStyle();
            bordes(borde);

            subtotal = wb.createCellStyle();
            subtotal.setFont(negrita);
            subtotal.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            subtotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordes(subtotal);
        }

        private static void bordes(CellStyle estilo) {
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
        }
    }
}
