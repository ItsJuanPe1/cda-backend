package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.EvaluacionDocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluacionDocenteExcelServiceTest {

    @Mock private DocenteRepository docenteRepository;
    @Mock private AsignaturaRepository asignaturaRepository;
    @Mock private PeriodoAcademicoRepository periodoAcademicoRepository;
    @Mock private EvaluacionDocenteRepository evaluacionDocenteRepository;

    @InjectMocks
    private EvaluacionDocenteExcelService service;

    @Test
    @DisplayName("Promedia múltiples grupos de la misma asignatura y marca 0 o vacío como 'No evaluado'")
    void importarDesdeStream_promediaGruposYMarcaNoEvaluado() throws IOException {
        PeriodoAcademico actual = PeriodoAcademico.builder()
                .id(2L).nombre("2026-2").fechaInicioSemestre(LocalDate.of(2026, 8, 1)).build();
        PeriodoAcademico anterior = PeriodoAcademico.builder()
                .id(1L).nombre("2026-1").fechaInicioSemestre(LocalDate.of(2026, 2, 1)).build();

        Docente doc1 = Docente.builder().id(10L).numeroDocumento("1001").nombres("Ana").apellidos("Ruiz").build();
        Docente doc2 = Docente.builder().id(20L).numeroDocumento("1002").nombres("Luis").apellidos("Gómez").build();
        Docente doc3 = Docente.builder().id(30L).numeroDocumento("1003").nombres("Marta").apellidos("Díaz").build();

        Asignatura asig = Asignatura.builder().codeCourse("MAT101").nameCourse("Cálculo I").build();

        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(actual));
        when(periodoAcademicoRepository.findAllByOrderByFechaInicioSemestreDesc()).thenReturn(List.of(actual, anterior));

        when(docenteRepository.findByNumeroDocumento("1001")).thenReturn(Optional.of(doc1));
        when(docenteRepository.findByNumeroDocumento("1002")).thenReturn(Optional.of(doc2));
        when(docenteRepository.findByNumeroDocumento("1003")).thenReturn(Optional.of(doc3));
        when(docenteRepository.findByNumeroDocumento("9999")).thenReturn(Optional.empty());
        when(asignaturaRepository.findById("MAT101")).thenReturn(Optional.of(asig));
        when(evaluacionDocenteRepository.findByDocenteIdAndAsignaturaCodeCourseAndPeriodoId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(evaluacionDocenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] excelBytes = crearExcelPrueba();
        EvaluacionDocenteDto.ImportacionExcelResponse resp =
                service.importarDesdeStream(2L, new ByteArrayInputStream(excelBytes));

        assertEquals(1L, resp.periodoId());
        assertEquals("2026-1", resp.periodoNombre());
        assertEquals(5, resp.totalFilasLeidas());
        assertEquals(3, resp.totalRegistrosGuardados());
        assertEquals(1, resp.totalPromediados());
        assertEquals(2, resp.totalNoEvaluados());
        assertEquals(1, resp.errores().size()); // doc 9999 no existe

        // Verificar promedio de doc1: (4.5 + 3.5) / 2 = 4.00
        EvaluacionDocenteDto.DetalleImportacion det1 = resp.detalles().get(0);
        assertEquals("1001", det1.numeroDocumento());
        assertEquals(2, det1.cantidadGrupos());
        assertEquals(new BigDecimal("4.00"), det1.calificacion());
        assertEquals("4.00", det1.etiqueta());

        // Verificar doc2 (nota 0 -> No evaluado)
        EvaluacionDocenteDto.DetalleImportacion det2 = resp.detalles().get(1);
        assertEquals("1002", det2.numeroDocumento());
        assertEquals(new BigDecimal("0.00"), det2.calificacion());
        assertEquals("No evaluado", det2.etiqueta());

        // Verificar doc3 (celda vacía -> No evaluado)
        EvaluacionDocenteDto.DetalleImportacion det3 = resp.detalles().get(2);
        assertEquals("1003", det3.numeroDocumento());
        assertEquals(new BigDecimal("0.00"), det3.calificacion());
        assertEquals("No evaluado", det3.etiqueta());

        ArgumentCaptor<EvaluacionDocente> captor = ArgumentCaptor.forClass(EvaluacionDocente.class);
        verify(evaluacionDocenteRepository, times(3)).save(captor.capture());
        assertEquals(new BigDecimal("4.00"), captor.getAllValues().get(0).getCalificacion());
        assertEquals(new BigDecimal("0.00"), captor.getAllValues().get(1).getCalificacion());
        assertEquals(new BigDecimal("0.00"), captor.getAllValues().get(2).getCalificacion());
    }

    private byte[] crearExcelPrueba() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Calificaciones");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Número de identificación");
            header.createCell(1).setCellValue("Nombre");
            header.createCell(2).setCellValue("Código");
            header.createCell(3).setCellValue("Grupo");
            header.createCell(4).setCellValue("Nombre de asignatura");
            header.createCell(5).setCellValue("Calificación");

            // Fila 1: doc 1001, MAT101, grupo 50, nota 4.5
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("1001");
            r1.createCell(1).setCellValue("Ana Ruiz");
            r1.createCell(2).setCellValue("MAT101");
            r1.createCell(3).setCellValue(50);
            r1.createCell(4).setCellValue("Cálculo I");
            r1.createCell(5).setCellValue(4.5);

            // Fila 2: doc 1001, MAT101, grupo 51, nota 3.5 -> promedio = 4.00
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("1001");
            r2.createCell(1).setCellValue("Ana Ruiz");
            r2.createCell(2).setCellValue("MAT101");
            r2.createCell(3).setCellValue(51);
            r2.createCell(4).setCellValue("Cálculo I");
            r2.createCell(5).setCellValue(3.5);

            // Fila 3: doc 1002, MAT101, grupo 52, nota 0 -> No evaluado
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("1002");
            r3.createCell(1).setCellValue("Luis Gómez");
            r3.createCell(2).setCellValue("MAT101");
            r3.createCell(3).setCellValue(52);
            r3.createCell(4).setCellValue("Cálculo I");
            r3.createCell(5).setCellValue(0);

            // Fila 4: doc 1003, MAT101, grupo 53, calificación vacía -> No evaluado
            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("1003");
            r4.createCell(1).setCellValue("Marta Díaz");
            r4.createCell(2).setCellValue("MAT101");
            r4.createCell(3).setCellValue(53);
            r4.createCell(4).setCellValue("Cálculo I");

            // Fila 5: doc inexistente
            Row r5 = sheet.createRow(5);
            r5.createCell(0).setCellValue("9999");
            r5.createCell(1).setCellValue("Fantasma");
            r5.createCell(2).setCellValue("MAT101");
            r5.createCell(3).setCellValue(54);
            r5.createCell(4).setCellValue("Cálculo I");
            r5.createCell(5).setCellValue(4.2);

            wb.write(out);
            return out.toByteArray();
        }
    }
}
