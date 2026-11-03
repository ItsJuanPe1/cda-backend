package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.CohorteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.repository.CohortePeriodoRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CohorteServiceTest {

    @Mock
    private CohortePeriodoRepository cohorteRepository;

    @Mock
    private PeriodoAcademicoRepository periodoRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private PensumAcademicoRepository pensumRepository;

    @InjectMocks
    private CohorteService cohorteService;

    private PeriodoAcademico periodo;
    private Programa programa;

    @BeforeEach
    void setUp() {
        periodo = PeriodoAcademico.builder()
                .id(1L)
                .nombre("2026-1")
                .estado("VIGENTE")
                .vigente(true)
                .build();

        programa = Programa.builder()
                .codeProgram("2711")
                .nameProgram("Tecnología en Desarrollo de Software")
                .numeroSemestre(6)
                .jornada(Jornada.DIURNA)
                .ingreso(TipoIngreso.SEMESTRAL)
                .estado(EstadoPrograma.ACTIVO)
                .numeroResolucion("Res. 084")
                .build();
    }

    @Test
    @DisplayName("Guardar cohortes correctamente")
    void testGuardarCohortes_Exitoso() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(programaRepository.findById("2711")).thenReturn(Optional.of(programa));

        PensumAcademico mockMateria = PensumAcademico.builder()
                .asignatura(Asignatura.builder().codeCourse("MAT101").nameCourse("Cálculo").credits(4).build())
                .semestre(1)
                .numeroResolucion("Res. 084")
                .activo(true)
                .build();
        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre(eq("2711"), eq("Res. 084"), anyInt()))
                .thenReturn(List.of(mockMateria));

        CohorteDto.Request req1 = CohorteDto.Request.builder()
                .semestre(1)
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .confirmada(true)
                .build();
        CohorteDto.Request req2 = CohorteDto.Request.builder()
                .semestre(3)
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .confirmada(true)
                .build();

        when(cohorteRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<CohorteDto.Response> res = cohorteService.guardarCohortes(1L, "2711", List.of(req1, req2));

        assertEquals(2, res.size());
        verify(cohorteRepository).deleteByPeriodoIdAndCodeProgram(1L, "2711");
        verify(cohorteRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Criterio 6: Falla si cohorte supera duración del programa")
    void testGuardarCohortes_SuperaDuracion() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(programaRepository.findById("2711")).thenReturn(Optional.of(programa));

        CohorteDto.Request req = CohorteDto.Request.builder()
                .semestre(7) // Máximo es 6
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .confirmada(true)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                cohorteService.guardarCohortes(1L, "2711", List.of(req)));

        assertTrue(ex.getMessage().contains("supera la duración máxima permitida"));
    }

    @Test
    @DisplayName("Falla si la resolución no tiene materias activas para ese semestre")
    void testGuardarCohortes_SinMaterias() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(programaRepository.findById("2711")).thenReturn(Optional.of(programa));

        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre("2711", "Res. 084", 2))
                .thenReturn(List.of());

        CohorteDto.Request req = CohorteDto.Request.builder()
                .semestre(2)
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .confirmada(true)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                cohorteService.guardarCohortes(1L, "2711", List.of(req)));

        assertTrue(ex.getMessage().contains("no tiene asignaturas activas"));
    }

    @Test
    @DisplayName("Criterio 3: Proponer siguiente periodo rota c -> c + 1 y propone 1er semestre")
    void testProponerSiguientePeriodo_Rotacion() {
        when(periodoRepository.findById(2L)).thenReturn(Optional.of(periodo));
        PeriodoAcademico periodoAnterior = PeriodoAcademico.builder().id(1L).estado("HISTORIAL").build();
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodoAnterior));

        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(List.of(programa));

        CohortePeriodo cohorte1 = CohortePeriodo.builder()
                .programa(programa)
                .semestre(1)
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .build();
        CohortePeriodo cohorte3 = CohortePeriodo.builder()
                .programa(programa)
                .semestre(3)
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .build();
        CohortePeriodo cohorte5 = CohortePeriodo.builder()
                .programa(programa)
                .semestre(5)
                .jornada(Jornada.DIURNA)
                .numeroResolucion("Res. 084")
                .build();

        when(cohorteRepository.findByPeriodoIdAndCodeProgram(1L, "2711"))
                .thenReturn(List.of(cohorte1, cohorte3, cohorte5));

        List<CohorteDto.PropuestaResponse> propuestas = cohorteService.proponerSiguientePeriodo(2L, 1L, false);

        assertEquals(1, propuestas.size());
        List<CohorteDto.PropuestaCohorteItem> items = propuestas.get(0).getCohortesPropuestas();
        // Debe proponer 1 (ingreso semestral), y 2, 4, 6 (rotación de 1, 3, 5)
        List<Integer> semestres = items.stream().map(CohorteDto.PropuestaCohorteItem::getSemestre).toList();
        assertTrue(semestres.contains(1));
        assertTrue(semestres.contains(2));
        assertTrue(semestres.contains(4));
        assertTrue(semestres.contains(6));
    }

    @Test
    @DisplayName("Criterio 5: Si el periodo anterior se canceló, no propone avance automático sin confirmación")
    void testProponerSiguientePeriodo_PeriodoCancelado() {
        when(periodoRepository.findById(2L)).thenReturn(Optional.of(periodo));
        PeriodoAcademico anteriorCancelado = PeriodoAcademico.builder().id(1L).estado("DESACTIVADO").build();
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(anteriorCancelado));
        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(List.of(programa));

        List<CohorteDto.PropuestaResponse> propuestas = cohorteService.proponerSiguientePeriodo(2L, 1L, false);

        assertNotNull(propuestas.get(0).getAdvertenciaPeriodoAnterior());
        assertTrue(propuestas.get(0).getCohortesPropuestas().isEmpty());
    }
}
