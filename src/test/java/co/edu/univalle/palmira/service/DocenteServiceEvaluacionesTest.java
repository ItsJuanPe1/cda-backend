package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocenteServiceEvaluacionesTest {

    @Mock DocenteRepository docenteRepository;
    @Mock ProgramaRepository programaRepository;
    @Mock FacultadRepository facultadRepository;
    @Mock EvaluacionDocenteRepository evaluacionDocenteRepository;
    @Mock PeriodoAcademicoRepository periodoAcademicoRepository;
    @Mock CursoProgramacionRepository cursoProgramacionRepository;
    @Mock DocenteAsignaturaService docenteAsignaturaService;

    @InjectMocks DocenteService service;

    @Test
    void listarEvaluacionesAnteriores_devuelveLaUltimaPorDocenteAunqueNoSeaDelPeriodoInmediatamenteAnterior() {
        PeriodoAcademico activo = PeriodoAcademico.builder()
                .id(8L).nombre("2026-2").fechaInicioSemestre(LocalDate.of(2026, 8, 1)).build();
        PeriodoAcademico reciente = PeriodoAcademico.builder()
                .id(7L).nombre("2026-1").fechaInicioSemestre(LocalDate.of(2026, 2, 1)).build();
        PeriodoAcademico antiguo = PeriodoAcademico.builder()
                .id(5L).nombre("2025-1").fechaInicioSemestre(LocalDate.of(2025, 2, 1)).build();
        Docente ana = Docente.builder().id(10L).build();
        Docente luis = Docente.builder().id(20L).build();
        Asignatura asignatura = Asignatura.builder().codeCourse("MAT101").build();

        EvaluacionDocente anaReciente = evaluacion(1L, ana, asignatura, reciente, "4.80");
        EvaluacionDocente anaAntigua = evaluacion(2L, ana, asignatura, antiguo, "3.20");
        EvaluacionDocente luisAntigua = evaluacion(3L, luis, asignatura, antiguo, "4.10");

        when(periodoAcademicoRepository.findById(8L)).thenReturn(Optional.of(activo));
        when(evaluacionDocenteRepository.findAllByAsignaturaBeforePeriodo(
                "MAT101", LocalDate.of(2026, 8, 1)))
                .thenReturn(List.of(anaReciente, anaAntigua, luisAntigua));

        List<EvaluacionDocenteDto> resultado = service.listarEvaluacionesAnteriores(8L, "MAT101");

        assertEquals(2, resultado.size());
        assertEquals(10L, resultado.get(0).docenteId());
        assertEquals("2026-1", resultado.get(0).periodoNombre());
        assertEquals(new BigDecimal("4.80"), resultado.get(0).calificacion());
        assertEquals(20L, resultado.get(1).docenteId());
        assertEquals("2025-1", resultado.get(1).periodoNombre());
        assertEquals(new BigDecimal("4.10"), resultado.get(1).calificacion());
    }

    private EvaluacionDocente evaluacion(Long id, Docente docente, Asignatura asignatura,
                                         PeriodoAcademico periodo, String calificacion) {
        return EvaluacionDocente.builder()
                .id(id)
                .docente(docente)
                .asignatura(asignatura)
                .periodo(periodo)
                .calificacion(new BigDecimal(calificacion))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
