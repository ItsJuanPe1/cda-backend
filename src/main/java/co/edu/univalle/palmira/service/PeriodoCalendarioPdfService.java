package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PeriodoAcademicoDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae datos de un período académico a partir de un PDF oficial de resolución de
 * calendario académico de la Universidad del Valle (archivo subido o enlace oficial).
 */
@Service
public class PeriodoCalendarioPdfService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final int MAX_PDF_BYTES = 10 * 1024 * 1024; // 10 MB

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1),
            Map.entry("febrero", 2),
            Map.entry("marzo", 3),
            Map.entry("abril", 4),
            Map.entry("mayo", 5),
            Map.entry("junio", 6),
            Map.entry("julio", 7),
            Map.entry("agosto", 8),
            Map.entry("septiembre", 9),
            Map.entry("setiembre", 9),
            Map.entry("octubre", 10),
            Map.entry("noviembre", 11),
            Map.entry("diciembre", 12)
    );

    private static final Map<String, String> MESES_ABREV = Map.ofEntries(
            Map.entry("enero", "Ene"),
            Map.entry("febrero", "Feb"),
            Map.entry("marzo", "Mar"),
            Map.entry("abril", "Abr"),
            Map.entry("mayo", "May"),
            Map.entry("junio", "Jun"),
            Map.entry("julio", "Jul"),
            Map.entry("agosto", "Ago"),
            Map.entry("septiembre", "Sep"),
            Map.entry("setiembre", "Sep"),
            Map.entry("octubre", "Oct"),
            Map.entry("noviembre", "Nov"),
            Map.entry("diciembre", "Dic")
    );

    private static final String MES_REGEX =
            "(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)";

    private static final Pattern PAT_RESOLUCION = Pattern.compile(
            "RESOLUCI[OÓ]N\\s+No\\.?\\s*(\\d{1,4})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_FECHA_COMPLETA = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+" + MES_REGEX + "\\s+de\\s+(20\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_NOMBRE_PERIODO = Pattern.compile(
            MES_REGEX + "\\s*[-–—]\\s*" + MES_REGEX + "\\s+(?:de\\s+)?(20\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_INICIO_CLASES = Pattern.compile(
            "Inicio\\s+de\\s+clases\\s+([^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_FIN_PERIODO = Pattern.compile(
            "Finalizaci[oó]n\\s+del\\s+Per[ií]odo\\s+([^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_FIN_CLASES = Pattern.compile(
            "Finalizaci[oó]n\\s+de\\s+clases\\s+([^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_ADICION_CANCELACION = Pattern.compile(
            "Adici[oó]n\\s+y\\s+cancelaci[oó]n\\s+de\\s+asignaturas\\s+([^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_CANCELACION = Pattern.compile(
            "(?m)^\\s*Cancelaci[oó]n\\s+de\\s+asignaturas\\s+([^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_ADICION_MODIFICACION = Pattern.compile(
            "Adiciones\\s+y\\s+Cancelaciones\\s+durante\\s+los\\s+d[ií]as\\s+([^.\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_RANGO_DIAS = Pattern.compile(
            "(?:Del\\s+)?(\\d{1,2})(?:\\s*,\\s*\\d{1,2})*\\s+(?:al|a|y)\\s+(\\d{1,2})\\s+de\\s+" + MES_REGEX + "(?:\\s+de\\s+(20\\d{2}))?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_RANGO_MESES = Pattern.compile(
            "(?:Del\\s+)?(\\d{1,2})\\s+de\\s+" + MES_REGEX + "(?:\\s+de\\s+(20\\d{2}))?\\s+(?:al|hasta\\s+el|a)\\s+(\\d{1,2})\\s+de\\s+" + MES_REGEX + "(?:\\s+de\\s+(20\\d{2}))?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAT_FECHA_SIMPLE = Pattern.compile(
            "(?:Hasta\\s+el\\s+)?(\\d{1,2})\\s+de\\s+" + MES_REGEX + "(?:\\s+de\\s+(20\\d{2}))?",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public PeriodoAcademicoDto.PdfPreviewResponse extraerDesdeArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Debe seleccionar un archivo PDF de resolución");
        }
        String nombreArchivo = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        if (!nombreArchivo.endsWith(".pdf")) {
            throw new BadRequestException("El archivo debe ser un documento PDF (.pdf)");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new BadRequestException("El archivo PDF supera el límite permitido de 10 MB");
        }
        try {
            return extraerDesdeBytes(file.getBytes(), null);
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo leer el archivo PDF: " + ex.getMessage());
        }
    }

    public PeriodoAcademicoDto.PdfPreviewResponse extraerDesdeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BadRequestException("El enlace del PDF es obligatorio");
        }
        String url = rawUrl.trim();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("El enlace proporcionado no es una URL válida");
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new BadRequestException("El enlace debe comenzar por https:// o http://");
        }
        if (!host.equals("univalle.edu.co") && !host.endsWith(".univalle.edu.co")) {
            throw new BadRequestException("Por seguridad, solo se permiten enlaces del dominio univalle.edu.co");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (CDA-Univalle)")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("No se pudo descargar el PDF (HTTP " + response.statusCode() + ")");
            }
            byte[] bytes;
            try (InputStream is = response.body()) {
                bytes = is.readNBytes(MAX_PDF_BYTES + 1);
            }
            if (bytes.length > MAX_PDF_BYTES) {
                throw new BadRequestException("El PDF excede el tamaño máximo permitido (10 MB)");
            }
            return extraerDesdeBytes(bytes, url);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("La descarga del PDF fue interrumpida");
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo descargar el PDF desde el enlace: " + ex.getMessage());
        }
    }

    public PeriodoAcademicoDto.PdfPreviewResponse extraerDesdeBytes(byte[] pdfBytes, String enlaceOrigen) {
        String texto;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            texto = stripper.getText(doc);
        } catch (IOException ex) {
            throw new BadRequestException("El archivo no es un PDF válido o no contiene texto legible");
        }
        return parsearTexto(texto, enlaceOrigen);
    }

    /** Visible para pruebas unitarias. */
    PeriodoAcademicoDto.PdfPreviewResponse parsearTexto(String textoRaw, String enlaceOrigen) {
        if (textoRaw == null || textoRaw.isBlank()) {
            throw new BadRequestException("El PDF no contiene texto extraíble (puede ser una imagen escaneada)");
        }
        String texto = textoRaw.replace("\r\n", "\n").replace('\r', '\n');
        String textoPlano = texto.replaceAll("\\s+", " ");
        List<String> advertencias = new ArrayList<>();

        // 1. Número de resolución y fecha de resolución (primera resolución del documento)
        String numResNum = null;
        LocalDate fechaResolucion = null;
        Matcher mRes = PAT_RESOLUCION.matcher(texto);
        if (mRes.find()) {
            numResNum = mRes.group(1);
            String ventana = texto.substring(mRes.end(), Math.min(texto.length(), mRes.end() + 120));
            Matcher mFechaRes = PAT_FECHA_COMPLETA.matcher(ventana);
            if (mFechaRes.find()) {
                fechaResolucion = construirFecha(
                        Integer.parseInt(mFechaRes.group(1)),
                        mFechaRes.group(2),
                        Integer.parseInt(mFechaRes.group(3)));
            }
        }

        // 2. Nombre del período (ej. "Agosto - Diciembre 2026") y año base
        String nombre = null;
        Integer anioPeriodo = null;
        Matcher mNom = PAT_NOMBRE_PERIODO.matcher(textoPlano);
        if (mNom.find()) {
            String mesIni = capitalizar(mNom.group(1));
            String mesFin = capitalizar(mNom.group(2));
            anioPeriodo = Integer.parseInt(mNom.group(3));
            String completo = mesIni + "-" + mesFin + " " + anioPeriodo;
            if (completo.length() <= 20) {
                nombre = completo;
            } else {
                String abrevIni = MESES_ABREV.getOrDefault(mNom.group(1).toLowerCase(Locale.ROOT), mesIni.substring(0, 3));
                String abrevFin = MESES_ABREV.getOrDefault(mNom.group(2).toLowerCase(Locale.ROOT), mesFin.substring(0, 3));
                nombre = abrevIni + "-" + abrevFin + " " + anioPeriodo;
                advertencias.add("El nombre '" + completo + "' supera 20 caracteres; se abrevió a '" + nombre + "'.");
            }
        }

        int anioBase = anioPeriodo != null ? anioPeriodo
                : (fechaResolucion != null ? fechaResolucion.getYear() : LocalDate.now(ZONA_BOGOTA).getYear());

        String numeroResolucion = numResNum != null ? "RES-" + numResNum + "-" + anioBase : null;

        // 3. Inicio y fin de semestre
        LocalDate fechaInicioSemestre = extraerPrimeraFecha(PAT_INICIO_CLASES, texto, anioBase);
        LocalDate fechaFinSemestre = extraerPrimeraFecha(PAT_FIN_PERIODO, texto, anioBase);
        if (fechaFinSemestre == null) {
            fechaFinSemestre = extraerPrimeraFecha(PAT_FIN_CLASES, texto, anioBase);
        }

        // 4. Adiciones y cancelaciones
        LocalDate[] rangoAdicion = extraerRangoFechas(PAT_ADICION_CANCELACION, texto, anioBase);
        LocalDate fechaAdicionInicio = rangoAdicion != null ? rangoAdicion[0] : null;
        LocalDate fechaAdicionFin = rangoAdicion != null ? rangoAdicion[1] : null;

        // Si el documento es una resolución modificatoria que adiciona fechas de Adiciones y Cancelaciones, avisar
        Matcher mMod = PAT_ADICION_MODIFICACION.matcher(textoPlano);
        if (mMod.find()) {
            String extra = mMod.group(1).trim();
            advertencias.add("Esta resolución modifica el calendario agregando fechas adicionales de Adiciones y Cancelaciones: " + extra + ".");
        }

        // 5. Cancelación de asignaturas
        LocalDate[] rangoCancelacion = extraerRangoFechas(PAT_CANCELACION, texto, anioBase);
        LocalDate fechaCancelacionInicio = rangoCancelacion != null ? rangoCancelacion[0] : null;
        LocalDate fechaCancelacionFin = rangoCancelacion != null ? rangoCancelacion[1] : null;

        // 6. Plazos automáticos: 4 semanas antes del inicio de clases
        //    - Primeras 2 semanas: disponibilidad docentes
        //    - Siguientes 2 semanas: coordinadores
        Instant plazoDocenteInicio = null;
        Instant plazoDocenteFin = null;
        Instant plazoCoordinadorInicio = null;
        Instant plazoCoordinadorFin = null;
        if (fechaInicioSemestre != null) {
            LocalDate docIni = fechaInicioSemestre.minusWeeks(4);
            LocalDate docFin = fechaInicioSemestre.minusWeeks(2).minusDays(1);
            LocalDate coordIni = fechaInicioSemestre.minusWeeks(2);
            LocalDate coordFin = fechaInicioSemestre.minusDays(1);

            plazoDocenteInicio = docIni.atStartOfDay(ZONA_BOGOTA).toInstant();
            plazoDocenteFin = docFin.atTime(LocalTime.of(23, 59)).atZone(ZONA_BOGOTA).toInstant();
            plazoCoordinadorInicio = coordIni.atStartOfDay(ZONA_BOGOTA).toInstant();
            plazoCoordinadorFin = coordFin.atTime(LocalTime.of(23, 59)).atZone(ZONA_BOGOTA).toInstant();
        } else {
            advertencias.add("No se encontró 'Inicio de clases' en el PDF; no se calcularon los plazos de docentes y coordinadores.");
        }

        if (fechaInicioSemestre == null && fechaFinSemestre == null && fechaAdicionInicio == null) {
            advertencias.add("No se encontraron fechas del calendario académico en el formato estándar de Univalle. Revise los campos manualmente.");
        }

        return new PeriodoAcademicoDto.PdfPreviewResponse(
                nombre,
                numeroResolucion,
                fechaResolucion,
                fechaInicioSemestre,
                fechaFinSemestre,
                fechaAdicionInicio,
                fechaAdicionFin,
                fechaCancelacionInicio,
                fechaCancelacionFin,
                plazoDocenteInicio,
                plazoDocenteFin,
                plazoCoordinadorInicio,
                plazoCoordinadorFin,
                enlaceOrigen,
                advertencias
        );
    }

    private static LocalDate extraerPrimeraFecha(Pattern pattern, String texto, int anioBase) {
        Matcher m = pattern.matcher(texto);
        if (!m.find()) {
            return null;
        }
        LocalDate[] rango = parsearRango(m.group(1), anioBase);
        return rango != null ? rango[0] : null;
    }

    private static LocalDate[] extraerRangoFechas(Pattern pattern, String texto, int anioBase) {
        Matcher m = pattern.matcher(texto);
        if (!m.find()) {
            return null;
        }
        return parsearRango(m.group(1), anioBase);
    }

    private static LocalDate[] parsearRango(String fragmento, int anioBase) {
        if (fragmento == null || fragmento.isBlank()) {
            return null;
        }
        String limpio = fragmento.trim();

        Matcher mMeses = PAT_RANGO_MESES.matcher(limpio);
        if (mMeses.find()) {
            int y1 = mMeses.group(3) != null ? Integer.parseInt(mMeses.group(3)) : anioBase;
            int y2 = mMeses.group(6) != null ? Integer.parseInt(mMeses.group(6)) : y1;
            LocalDate d1 = construirFecha(Integer.parseInt(mMeses.group(1)), mMeses.group(2), y1);
            LocalDate d2 = construirFecha(Integer.parseInt(mMeses.group(4)), mMeses.group(5), y2);
            if (d1 != null && d2 != null) {
                return new LocalDate[]{d1, d2};
            }
        }

        Matcher mDias = PAT_RANGO_DIAS.matcher(limpio);
        if (mDias.find()) {
            int y = mDias.group(4) != null ? Integer.parseInt(mDias.group(4)) : anioBase;
            LocalDate d1 = construirFecha(Integer.parseInt(mDias.group(1)), mDias.group(3), y);
            LocalDate d2 = construirFecha(Integer.parseInt(mDias.group(2)), mDias.group(3), y);
            if (d1 != null && d2 != null) {
                return new LocalDate[]{d1, d2};
            }
        }

        Matcher mSimple = PAT_FECHA_SIMPLE.matcher(limpio);
        if (mSimple.find()) {
            int y = mSimple.group(3) != null ? Integer.parseInt(mSimple.group(3)) : anioBase;
            LocalDate d = construirFecha(Integer.parseInt(mSimple.group(1)), mSimple.group(2), y);
            if (d != null) {
                return new LocalDate[]{d, d};
            }
        }
        return null;
    }

    private static LocalDate construirFecha(int dia, String mesTexto, int anio) {
        Integer mes = MESES.get(mesTexto.toLowerCase(Locale.ROOT));
        if (mes == null) {
            return null;
        }
        try {
            return LocalDate.of(anio, mes, dia);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String capitalizar(String s) {
        if (s == null || s.isEmpty()) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
