package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PensumItemDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-035: descargar la malla curricular en Excel. */
@ExtendWith(MockitoExtension.class)
class MallaExcelServiceTest {

    static final String CODIGO = "2724-DIU";

    @Mock
    private PensumService pensumService;
    @Mock
    private ProgramaRepository programaRepository;
    @Mock
    private AccesoProgramaService accesoProgramaService;

    @InjectMocks
    private MallaExcelService service;

    private Programa programa;

    @BeforeEach
    void setUp() {
        programa = new Programa();
        programa.setCodeProgram(CODIGO);
        programa.setNameProgram("Tecnología en Desarrollo de Software");
        programa.setJornada(Jornada.DIURNA);
    }

    private static PensumItemDto.Response item(String codigo, String nombre, int semestre, Integer creditos,
                                               Integer hc, Integer hi, boolean vigente, int version) {
        return new PensumItemDto.Response(CODIGO, codigo, nombre, creditos, semestre, false, false, false,
                "Ingeniería", true, version, vigente, hc, hi, "PROFESIONAL", "Resolución de prueba",
                "DIURNA", "SEMESTRAL", "RES-001", 3, true, LocalDateTime.of(2026, 11, 10, 15, 30));
    }

    private List<PensumItemDto.Response> mallaVigente() {
        return List.of(
                item("MAT101", "Cálculo I", 1, 4, 4, 8, true, 2),
                item("ALG101", "Algoritmia", 1, 3, 3, 6, true, 2),
                item("POO201", "Programación orientada a objetos", 2, 3, 4, 5, true, 2));
    }

    private void comoAdmin() {
        when(accesoProgramaService.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(true, null));
    }

    private static String texto(Sheet sheet, int fila, int col) {
        Row row = sheet.getRow(fila);
        return row == null || row.getCell(col) == null ? null : row.getCell(col).getStringCellValue();
    }

    private static int buscarFila(Sheet sheet, String valorColumna0) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            if (valorColumna0.equals(texto(sheet, i, 0))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("Criterio 2: el archivo es .xlsx con nombre malla_nombre-programa_jornada.xlsx")
    void criterio2_nombreDescriptivoDeLaMallaVigente() {
        comoAdmin();
        when(programaRepository.findById(CODIGO)).thenReturn(Optional.of(programa));
        when(pensumService.getByProgram(CODIGO)).thenReturn(mallaVigente());

        MallaExcelService.Archivo archivo = service.exportar(" 2724-diu ", null);

        assertEquals("malla_tecnologia-en-desarrollo-de-software_diurna.xlsx", archivo.nombre());
        assertTrue(archivo.contenido().length > 0);
    }

    @Test
    @DisplayName("Criterio 2: una versión del historial termina en _historial")
    void criterio2_versionDelHistorialLlevaLaPalabraHistorial() {
        comoAdmin();
        when(programaRepository.findById(CODIGO)).thenReturn(Optional.of(programa));
        when(pensumService.getVersion(CODIGO, 1)).thenReturn(List.of(
                item("MAT101", "Cálculo I", 1, 4, 4, 8, false, 1)));

        MallaExcelService.Archivo archivo = service.exportar(CODIGO, 1);

        assertEquals("malla_tecnologia-en-desarrollo-de-software_diurna_historial.xlsx", archivo.nombre());
        verify(pensumService, never()).getByProgram(any());
    }

    @Test
    @DisplayName("Criterio 2: contiene los datos de la resolución y los semestres con todas las asignaturas")
    void criterio2_contieneResolucionSemestresYAsignaturas() throws Exception {
        byte[] contenido = service.generar(programa, mallaVigente(), false, Instant.parse("2026-11-15T17:00:00Z"));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet sheet = wb.getSheet(MallaExcelService.HOJA);
            assertNotNull(sheet);
            assertEquals("Malla curricular - Tecnología en Desarrollo de Software", texto(sheet, 0, 0));

            assertEquals("RES-001", texto(sheet, buscarFila(sheet, "Número de resolución"), 1));
            assertEquals("Resolución de prueba", texto(sheet, buscarFila(sheet, "Descripción"), 1));
            assertEquals("Diurna", texto(sheet, buscarFila(sheet, "Jornada"), 1));
            assertEquals("Semestral", texto(sheet, buscarFila(sheet, "Periodo de oferta"), 1));
            assertEquals("3", texto(sheet, buscarFila(sheet, "Número de semestres"), 1));
            assertEquals("2", texto(sheet, buscarFila(sheet, "Versión"), 1));
            assertEquals("Vigente", texto(sheet, buscarFila(sheet, "Estado"), 1));
            assertEquals("Sí", texto(sheet, buscarFila(sheet, "Editada"), 1));
            assertTrue(texto(sheet, buscarFila(sheet, "Última edición"), 1).startsWith("10/11/2026 03:30"));
            assertEquals("3", texto(sheet, buscarFila(sheet, "Total de asignaturas"), 1));
            assertEquals("10", texto(sheet, buscarFila(sheet, "Total de créditos"), 1));
            // 17:00 UTC = 12:00 en America/Bogota
            assertTrue(texto(sheet, buscarFila(sheet, "Generado el"), 1).startsWith("15/11/2026 12:00"));

            int sem1 = buscarFila(sheet, "Semestre 1");
            assertTrue(sem1 > 0);
            for (int i = 0; i < MallaExcelService.COLUMNAS.length; i++) {
                assertEquals(MallaExcelService.COLUMNAS[i], texto(sheet, sem1 + 1, i));
            }
            // Ordenadas por nombre: Algoritmia antes que Cálculo I
            assertEquals("ALG101", texto(sheet, sem1 + 2, 0));
            assertEquals("Algoritmia", texto(sheet, sem1 + 2, 1));
            assertEquals(3, sheet.getRow(sem1 + 2).getCell(2).getNumericCellValue());
            assertEquals(3, sheet.getRow(sem1 + 2).getCell(3).getNumericCellValue());
            assertEquals(6, sheet.getRow(sem1 + 2).getCell(4).getNumericCellValue());
            assertEquals("Profesional", texto(sheet, sem1 + 2, 5));
            assertEquals("No", texto(sheet, sem1 + 2, 6));
            assertEquals("Ingeniería", texto(sheet, sem1 + 2, 7));
            assertEquals("Activa", texto(sheet, sem1 + 2, 8));
            assertEquals("MAT101", texto(sheet, sem1 + 3, 0));

            assertEquals("Subtotal semestre 1", texto(sheet, sem1 + 4, 0));
            assertEquals(7, sheet.getRow(sem1 + 4).getCell(2).getNumericCellValue());
            assertEquals(7, sheet.getRow(sem1 + 4).getCell(3).getNumericCellValue());
            assertEquals(14, sheet.getRow(sem1 + 4).getCell(4).getNumericCellValue());

            int sem2 = buscarFila(sheet, "Semestre 2");
            assertEquals("POO201", texto(sheet, sem2 + 2, 0));

            // La resolución tiene 3 semestres: el 3 aparece aunque esté vacío
            int sem3 = buscarFila(sheet, "Semestre 3");
            assertEquals("Sin asignaturas", texto(sheet, sem3 + 2, 0));
        }
    }

    @Test
    @DisplayName("Criterio 2: el estado de la versión del historial se muestra como «Historial»")
    void criterio2_estadoHistorial() throws Exception {
        byte[] contenido = service.generar(programa,
                List.of(item("MAT101", "Cálculo I", 1, 4, null, null, false, 1)), true, Instant.now());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet sheet = wb.getSheet(MallaExcelService.HOJA);
            assertEquals("Historial", texto(sheet, buscarFila(sheet, "Estado"), 1));
            int sem1 = buscarFila(sheet, "Semestre 1");
            // Horas sin registrar quedan en blanco, no en cero
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(sem1 + 2).getCell(3).getCellType());
        }
    }

    @Test
    @DisplayName("Criterio 3: si falla la generación responde «No se pudo generar el archivo»")
    void criterio3_fallaLaGeneracion() {
        List<PensumItemDto.Response> conNulo = new ArrayList<>(mallaVigente());
        conNulo.add(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generar(programa, conNulo, false, Instant.now()));

        assertEquals("No se pudo generar el archivo", ex.getMessage());
    }

    @Test
    @DisplayName("Coordinador: descarga la malla de su programa")
    void coordinadorDescargaSuPrograma() {
        when(accesoProgramaService.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(false, CODIGO));
        when(programaRepository.findById(CODIGO)).thenReturn(Optional.of(programa));
        when(pensumService.getByProgram(CODIGO)).thenReturn(mallaVigente());

        assertNotNull(service.exportar(CODIGO, null));
    }

    @Test
    @DisplayName("Coordinador: no descarga la malla de otro programa")
    void coordinadorNoDescargaOtroPrograma() {
        when(accesoProgramaService.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(false, "9999-DIU"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.exportar(CODIGO, null));

        assertEquals("Solo puede descargar la malla de su programa", ex.getMessage());
        verifyNoInteractions(pensumService, programaRepository);
    }

    @Test
    @DisplayName("Programa inexistente responde 404")
    void programaInexistente() {
        comoAdmin();
        when(programaRepository.findById(CODIGO)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.exportar(CODIGO, null));
        assertEquals("Programa no encontrado con código: 2724-DIU", ex.getMessage());
    }

    @Test
    @DisplayName("Programa sin malla vigente responde «El programa no tiene malla registrada»")
    void programaSinMalla() {
        comoAdmin();
        when(programaRepository.findById(CODIGO)).thenReturn(Optional.of(programa));
        when(pensumService.getByProgram(CODIGO)).thenReturn(List.of());

        ConflictException ex = assertThrows(ConflictException.class, () -> service.exportar(CODIGO, null));
        assertEquals("El programa no tiene malla registrada", ex.getMessage());
    }

    @Test
    @DisplayName("Versión inexistente responde 404")
    void versionInexistente() {
        comoAdmin();
        when(programaRepository.findById(CODIGO)).thenReturn(Optional.of(programa));
        when(pensumService.getVersion(CODIGO, 7)).thenReturn(List.of());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.exportar(CODIGO, 7));
        assertEquals("La versión 7 de la malla no existe", ex.getMessage());
    }

    @Test
    @DisplayName("El nombre del archivo no lleva tildes, ñ ni espacios")
    void slugSinTildes() {
        programa.setNameProgram("  Ingeniería de Diseño Ñandú  ");
        programa.setJornada(Jornada.NOCTURNA);

        assertEquals("malla_ingenieria-de-diseno-nandu_nocturna.xlsx",
                MallaExcelService.nombreArchivo(programa, false));
    }
}
