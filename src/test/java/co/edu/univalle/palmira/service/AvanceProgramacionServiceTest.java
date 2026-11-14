package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AvanceProgramacionDto;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Facultad;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvanceProgramacionServiceTest {

    static final Long PERIODO = 7L;

    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock CursoProgramacionRepository cursoRepository;
    @Mock DocenteRepository docenteRepository;
    @Mock AvanceProgramacionExcelService excelService;

    AvanceProgramacionService service;

    @BeforeEach
    void setUp() {
        service = new AvanceProgramacionService(periodoRepository, cursoRepository,
                docenteRepository, excelService);
    }

    private PeriodoAcademico periodo(Instant inicio, Instant fin, Instant coordInicio, Instant coordFin) {
        return PeriodoAcademico.builder()
                .id(PERIODO)
                .nombre("2026-2")
                .estado("ACTIVO")
                .plazoDocenteInicio(inicio)
                .plazoDocenteFin(fin)
                .plazoCoordinadorInicio(coordInicio)
                .plazoCoordinadorFin(coordFin)
                .build();
    }

    private Docente docente() {
        return Docente.builder()
                .id(3L)
                .codigo("D003")
                .nombres("Ana")
                .apellidos("Pérez")
                .correoInstitucional("ana@univalle.edu.co")
                .facultad(Facultad.builder().id(1).nombre("Ingeniería").build())
                .programa(Programa.builder().codeProgram("4014-DIU")
                        .nameProgram("Ingeniería de Sistemas").build())
                .build();
    }

    @Test
    void obtenerAvance_mapeaProgramasTotalesDocentesYVentanas() {
        Instant ahora = Instant.now();
        when(periodoRepository.findById(PERIODO))
                .thenReturn(Optional.of(periodo(ahora.minusSeconds(60), ahora.plusSeconds(60),
                        ahora.minusSeconds(120), ahora.minusSeconds(60))));
        when(cursoRepository.contarAvancePorPrograma(PERIODO)).thenReturn(List.of(
                new Object[]{"4014-DIU", "Ingeniería de Sistemas", 10L, 6L, 3L, 1L, 2L},
                new Object[]{"4015-DIU", "Ingeniería Industrial", 5L, 5L, 0L, 0L, 0L}));
        when(docenteRepository.findActivosSinDisponibilidad(PERIODO)).thenReturn(List.of(docente()));

        AvanceProgramacionDto.Resumen r = service.obtenerAvance(PERIODO);

        assertEquals("2026-2", r.periodo().nombre());
        assertEquals(2, r.programas().size());
        assertEquals(15, r.totales().generados());
        assertEquals(11, r.totales().conDocente());
        assertEquals(3, r.totales().pendientesAsignacion());
        assertEquals(1, r.totales().pendientesContratacion());
        assertEquals(2, r.totales().excluidos());
        assertEquals(1, r.totales().docentesSinDisponibilidad());
        assertTrue(r.periodo().ventanas().docenteAbierta());
        assertFalse(r.periodo().ventanas().coordinadorAbierta());
        assertEquals("Ana Pérez", r.docentesSinDisponibilidad().get(0).nombreCompleto());
        assertEquals("Ingeniería", r.docentesSinDisponibilidad().get(0).facultad());
    }

    @Test
    void obtenerAvance_sinProgramasNiDocentes_devuelveTotalesEnCero() {
        Instant ahora = Instant.now();
        when(periodoRepository.findById(PERIODO))
                .thenReturn(Optional.of(periodo(ahora.minusSeconds(60), ahora.plusSeconds(60), null, null)));
        when(cursoRepository.contarAvancePorPrograma(PERIODO)).thenReturn(List.of());
        when(docenteRepository.findActivosSinDisponibilidad(PERIODO)).thenReturn(List.of());

        AvanceProgramacionDto.Resumen r = service.obtenerAvance(PERIODO);

        assertTrue(r.programas().isEmpty());
        assertTrue(r.docentesSinDisponibilidad().isEmpty());
        assertEquals(0, r.totales().generados());
        assertTrue(r.periodo().ventanas().docenteAbierta());
        assertFalse(r.periodo().ventanas().coordinadorAbierta());
    }

    @Test
    void obtenerAvance_periodoInexistente_lanzaResourceNotFound() {
        when(periodoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.obtenerAvance(99L));
    }

    @Test
    void generarExcel_delegaEnServicioExcelConPendientesMapeados() {
        when(periodoRepository.findById(PERIODO))
                .thenReturn(Optional.of(periodo(Instant.now(), Instant.now(), null, null)));
        when(cursoRepository.contarAvancePorPrograma(PERIODO)).thenReturn(List.of());
        when(docenteRepository.findActivosSinDisponibilidad(PERIODO)).thenReturn(List.of());
        when(cursoRepository.findPendientesParaExcel(PERIODO)).thenReturn(List.<Object[]>of(
                new Object[]{"4014-DIU", "Ingeniería de Sistemas", "MAT101", "Cálculo",
                        1, "DIURNA", 50, "Res. 001", "PENDIENTE_CONTRATACION"}));
        when(excelService.generar(any(AvanceProgramacionDto.Resumen.class), any()))
                .thenReturn(new byte[]{1, 2, 3});

        byte[] resultado = service.generarExcel(PERIODO);

        assertArrayEquals(new byte[]{1, 2, 3}, resultado);
        verify(excelService).generar(any(AvanceProgramacionDto.Resumen.class), eq(List.of(
                new AvanceProgramacionDto.CursoPendiente("4014-DIU", "Ingeniería de Sistemas",
                        "MAT101", "Cálculo", 1, "DIURNA", 50, "Res. 001", "PENDIENTE_CONTRATACION"))));
    }
}
