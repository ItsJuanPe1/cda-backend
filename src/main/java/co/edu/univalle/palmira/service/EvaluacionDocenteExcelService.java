package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.EvaluacionDocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Servicio para la carga masiva de calificaciones docentes desde un archivo Excel (.xlsx / .xls).
 * <p>
 * Formato esperado de columnas:
 * <ol>
 *   <li>Número de identificación (cédula del docente)</li>
 *   <li>Nombre del docente</li>
 *   <li>Código de la asignatura</li>
 *   <li>Grupo</li>
 *   <li>Nombre de la asignatura</li>
 *   <li>Calificación (0 a 5; vacío o 0 se interpreta como "No evaluado")</li>
 * </ol>
 * Si un mismo docente dictó la misma asignatura en varios grupos, se calcula el promedio
 * de las calificaciones válidas (&gt; 0). Si todas las filas están vacías o en 0, se guarda
 * como 0.00 y se etiqueta como "No evaluado".
 * </p>
 */
@Service
public class EvaluacionDocenteExcelService {

    public static final String ETIQUETA_NO_EVALUADO = "No evaluado";
    private static final BigDecimal CINCO = BigDecimal.valueOf(5);

    private final DocenteRepository docenteRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final EvaluacionDocenteRepository evaluacionDocenteRepository;

    @Autowired
    public EvaluacionDocenteExcelService(
            DocenteRepository docenteRepository,
            AsignaturaRepository asignaturaRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            EvaluacionDocenteRepository evaluacionDocenteRepository) {
        this.docenteRepository = docenteRepository;
        this.asignaturaRepository = asignaturaRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.evaluacionDocenteRepository = evaluacionDocenteRepository;
    }

    /**
     * Importa las calificaciones docentes desde un archivo Excel subido vía HTTP.
     *
     * @param periodoId identificador del período activo en Asignación Docente
     * @param file      archivo Excel (.xlsx o .xls)
     * @return resumen detallado de la importación
     */
    @Transactional
    public EvaluacionDocenteDto.ImportacionExcelResponse importar(Long periodoId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Debe seleccionar un archivo Excel con las calificaciones docentes");
        }
        String nombreArchivo = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!nombreArchivo.endsWith(".xlsx") && !nombreArchivo.endsWith(".xls")) {
            throw new BadRequestException("El archivo debe ser un libro de Excel válido (.xlsx o .xls)");
        }
        try (InputStream is = file.getInputStream()) {
            return importarDesdeStream(periodoId, is);
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo leer el archivo Excel: " + ex.getMessage());
        }
    }

    /**
     * Procesa el flujo de entrada de un libro Excel, agrupa por docente y asignatura,
     * promedia múltiples grupos y persiste las evaluaciones en la base de datos.
     */
    @Transactional
    public EvaluacionDocenteDto.ImportacionExcelResponse importarDesdeStream(Long periodoId, InputStream inputStream) {
        if (periodoId == null) {
            throw new BadRequestException("El período académico es obligatorio");
        }
        PeriodoAcademico actual = periodoAcademicoRepository.findById(periodoId)
                .orElseThrow(() -> new BadRequestException("El período académico no existe"));

        // Las evaluaciones previas se guardan en el período inmediatamente anterior al activo
        // para que la consulta de historial en Asignación Docente las muestre automáticamente.
        PeriodoAcademico periodoDestino = periodoAcademicoRepository.findAllByOrderByFechaInicioSemestreDesc()
                .stream()
                .filter(p -> p.getFechaInicioSemestre() != null
                        && actual.getFechaInicioSemestre() != null
                        && p.getFechaInicioSemestre().isBefore(actual.getFechaInicioSemestre()))
                .findFirst()
                .orElse(actual);

        List<String> errores = new ArrayList<>();
        Map<ClaveGrupo, Acumulador> acumulados = new LinkedHashMap<>();
        int totalFilasLeidas = 0;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException("El archivo Excel no contiene hojas");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.US);

            for (Row row : sheet) {
                if (row == null || esFilaVacia(row, formatter)) {
                    continue;
                }
                String col0 = leerTextoIdentificador(row.getCell(0), formatter);
                String col2 = leerTextoCodigo(row.getCell(2), formatter);
                String col5Texto = formatter.formatCellValue(row.getCell(5)).trim();

                if (esFilaCabecera(col0, col2, col5Texto)) {
                    continue;
                }

                totalFilasLeidas++;
                int numFila = row.getRowNum() + 1;

                if (col0.isBlank()) {
                    errores.add("Fila " + numFila + ": falta el número de identificación del docente.");
                    continue;
                }
                if (col2.isBlank()) {
                    errores.add("Fila " + numFila + ": falta el código de la asignatura.");
                    continue;
                }

                String nombreExcel = formatter.formatCellValue(row.getCell(1)).trim();
                String asignaturaExcel = formatter.formatCellValue(row.getCell(4)).trim();

                Optional<BigDecimal> califOpt;
                try {
                    califOpt = leerCalificacion(row.getCell(5), formatter);
                } catch (IllegalArgumentException ex) {
                    errores.add("Fila " + numFila + ": " + ex.getMessage());
                    continue;
                }

                ClaveGrupo clave = new ClaveGrupo(col0, col2);
                Acumulador acc = acumulados.computeIfAbsent(
                        clave, k -> new Acumulador(col0, nombreExcel, col2, asignaturaExcel));
                acc.totalGrupos++;
                califOpt.ifPresent(acc.calificacionesPositivas::add);
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Error al procesar el archivo Excel: " + ex.getMessage());
        }

        if (totalFilasLeidas == 0) {
            throw new BadRequestException("El archivo Excel no contiene filas con datos para importar");
        }

        List<EvaluacionDocenteDto.DetalleImportacion> detalles = new ArrayList<>();
        int totalGuardados = 0;
        int totalPromediados = 0;
        int totalNoEvaluados = 0;

        for (Acumulador acc : acumulados.values()) {
            Optional<Docente> docenteOpt = buscarDocentePorDocumento(acc.numeroDocumento);
            if (docenteOpt.isEmpty()) {
                errores.add("Docente con identificación '" + acc.numeroDocumento + "' (" + acc.nombreExcel + ") no encontrado en el sistema.");
                continue;
            }
            Optional<Asignatura> asignaturaOpt = asignaturaRepository.findById(acc.codeCourse);
            if (asignaturaOpt.isEmpty()) {
                errores.add("Asignatura con código '" + acc.codeCourse + "' (" + acc.asignaturaExcel + ") no encontrada en el catálogo.");
                continue;
            }

            Docente docente = docenteOpt.get();
            Asignatura asignatura = asignaturaOpt.get();

            BigDecimal calificacionFinal;
            String etiqueta;
            if (acc.calificacionesPositivas.isEmpty()) {
                calificacionFinal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                etiqueta = ETIQUETA_NO_EVALUADO;
                totalNoEvaluados++;
            } else {
                BigDecimal suma = BigDecimal.ZERO;
                for (BigDecimal nota : acc.calificacionesPositivas) {
                    suma = suma.add(nota);
                }
                calificacionFinal = suma.divide(
                        BigDecimal.valueOf(acc.calificacionesPositivas.size()), 2, RoundingMode.HALF_UP);
                etiqueta = calificacionFinal.toPlainString();
                if (acc.totalGrupos > 1) {
                    totalPromediados++;
                }
            }

            EvaluacionDocente evaluacion = evaluacionDocenteRepository
                    .findByDocenteIdAndAsignaturaCodeCourseAndPeriodoId(
                            docente.getId(), asignatura.getCodeCourse(), periodoDestino.getId())
                    .orElseGet(() -> EvaluacionDocente.builder()
                            .docente(docente)
                            .asignatura(asignatura)
                            .periodo(periodoDestino)
                            .build());
            evaluacion.setCalificacion(calificacionFinal);
            evaluacionDocenteRepository.save(evaluacion);
            totalGuardados++;

            String nombreDocente = (docente.getNombres() + " " + docente.getApellidos()).trim();
            detalles.add(new EvaluacionDocenteDto.DetalleImportacion(
                    docente.getNumeroDocumento(),
                    nombreDocente,
                    asignatura.getCodeCourse(),
                    asignatura.getNameCourse(),
                    acc.totalGrupos,
                    calificacionFinal,
                    etiqueta));
        }

        return new EvaluacionDocenteDto.ImportacionExcelResponse(
                periodoDestino.getId(),
                periodoDestino.getNombre(),
                totalFilasLeidas,
                totalGuardados,
                totalPromediados,
                totalNoEvaluados,
                errores,
                detalles);
    }

    private Optional<Docente> buscarDocentePorDocumento(String numeroDocumento) {
        Optional<Docente> exacto = docenteRepository.findByNumeroDocumento(numeroDocumento);
        if (exacto.isPresent()) {
            return exacto;
        }
        String soloDigitos = numeroDocumento.replaceAll("[.\\s,-]", "");
        if (!soloDigitos.equals(numeroDocumento) && !soloDigitos.isBlank()) {
            return docenteRepository.findByNumeroDocumento(soloDigitos);
        }
        return Optional.empty();
    }

    private static boolean esFilaVacia(Row row, DataFormatter formatter) {
        for (int c = 0; c <= 5; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean esFilaCabecera(String col0, String col2, String col5) {
        String c0 = col0.toLowerCase(Locale.ROOT);
        String c2 = col2.toLowerCase(Locale.ROOT);
        String c5 = col5.toLowerCase(Locale.ROOT);
        return c0.contains("identificaci") || c0.contains("cedula") || c0.contains("cédula")
                || c0.contains("documento") || c0.contains("numero") || c0.contains("número")
                || c2.contains("codigo") || c2.contains("código")
                || c5.contains("calificaci") || c5.contains("nota");
    }

    private static String leerTextoIdentificador(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros()
                    .toPlainString()
                    .trim();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static String leerTextoCodigo(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        String valor;
        if (cell.getCellType() == CellType.NUMERIC) {
            valor = BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros()
                    .toPlainString();
        } else {
            valor = formatter.formatCellValue(cell);
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Lee la calificación de una celda.
     * Devuelve {@link Optional#empty()} si está vacía, es 0, o indica "No evaluado".
     */
    private static Optional<BigDecimal> leerCalificacion(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return Optional.empty();
        }
        BigDecimal valor;
        if (cell.getCellType() == CellType.NUMERIC) {
            valor = BigDecimal.valueOf(cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
            valor = BigDecimal.valueOf(cell.getNumericCellValue());
        } else {
            String texto = formatter.formatCellValue(cell).trim();
            if (texto.isEmpty()
                    || texto.equalsIgnoreCase("no evaluado")
                    || texto.equalsIgnoreCase("sin evaluar")
                    || texto.equalsIgnoreCase("n/a")
                    || texto.equals("-")) {
                return Optional.empty();
            }
            String normalizado = texto.replace(',', '.');
            try {
                valor = new BigDecimal(normalizado);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("calificación inválida ('" + texto + "').");
            }
        }

        if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(CINCO) > 0) {
            throw new IllegalArgumentException("la calificación (" + valor.toPlainString() + ") debe estar entre 0 y 5.");
        }
        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }
        return Optional.of(valor);
    }

    private record ClaveGrupo(String numeroDocumento, String codeCourse) {
    }

    private static class Acumulador {
        final String numeroDocumento;
        final String nombreExcel;
        final String codeCourse;
        final String asignaturaExcel;
        final List<BigDecimal> calificacionesPositivas = new ArrayList<>();
        int totalGrupos = 0;

        Acumulador(String numeroDocumento, String nombreExcel, String codeCourse, String asignaturaExcel) {
            this.numeroDocumento = numeroDocumento;
            this.nombreExcel = nombreExcel;
            this.codeCourse = codeCourse;
            this.asignaturaExcel = asignaturaExcel;
        }
    }
}
