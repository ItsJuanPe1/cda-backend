package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramacionServiceTest {

    @Mock
    private CursoProgramacionRepository cursoRepository;

    @Mock
    private CohortePeriodoRepository cohorteRepository;

    @Mock
    private PeriodoAcademicoRepository periodoRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private PensumAcademicoRepository pensumRepository;

    @Mock
    private DocenteRepository docenteRepository;

    @Mock
    private DocenteAsignaturaService docenteAsignaturaService;

    @InjectMocks
    private ProgramacionService programacionService;

    private PeriodoAcademico periodo;
    private Programa programa;
    private CohortePeriodo cohorte;
    private Asignatura asignatura1;
    private Asignatura asignaturaElectiva;

    @BeforeEach
    void setUp() {
        periodo = PeriodoAcademico.builder().id(1L).nombre("2026-1").vigente(true).estado("VIGENTE").build();
        programa = Programa.builder().codeProgram("2711").nameProgram("Tecnología en Desarrollo de Software").estado(EstadoPrograma.ACTIVO).build();
        cohorte = CohortePeriodo.builder().id(10L).periodo(periodo).programa(programa).semestre(1).jornada(Jornada.DIURNA).numeroResolucion("Res. 084").confirmada(true).build();

        asignatura1 = Asignatura.builder().codeCourse("MAT101").nameCourse("Cálculo I").credits(4).isElective(false).build();
        asignaturaElectiva = Asignatura.builder().codeCourse("ELE101").nameCourse("Electiva I").credits(3).isElective(true).build();
    }

    @Test
    @DisplayName("Criterio 1, 2 y 3: Generar programación exitosa, excluyendo electivas y asignando grupo 50")
    void testGenerarProgramacion_Exitoso() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.countByPeriodoId(1L)).thenReturn(0L);

        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(List.of(programa));
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(1L)).thenReturn(List.of(cohorte));

        PensumAcademico pensum1 = PensumAcademico.builder().asignatura(asignatura1).semestre(1).isElective(false).numeroResolucion("Res. 084").nameCourseSnapshot("Cálculo I").build();
        PensumAcademico pensumElectiva = PensumAcademico.builder().asignatura(asignaturaElectiva).semestre(1).isElective(true).numeroResolucion("Res. 084").nameCourseSnapshot("Electiva I").build();

        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre("2711", "Res. 084", 1))
                .thenReturn(List.of(pensum1, pensumElectiva));

        when(cursoRepository.contarCursosPorPrograma(1L))
                .thenReturn(Collections.singletonList(new Object[]{"2711", "Tecnología en Desarrollo de Software", 1L}));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CursoProgramacion>> captor = ArgumentCaptor.forClass(List.class);

        ProgramacionDto.ResumenResponse resumen = programacionService.generarProgramacion(1L, false);

        verify(cursoRepository).saveAll(captor.capture());
        List<CursoProgramacion> cursosGuardados = captor.getValue();

        assertEquals(1, cursosGuardados.size()); // La electiva fue excluida (Criterio 3)
        CursoProgramacion curso = cursosGuardados.get(0);
        assertEquals("MAT101", curso.getAsignatura().getCodeCourse());
        assertEquals(50, curso.getGrupo()); // Grupo inicial consecutivo 50 (HU-046)
        assertEquals(4, curso.getCredits());
        assertEquals("Cálculo I", curso.getNombreAsignatura());
        assertEquals(1, resumen.getTotalCursos());
    }

    @Test
    @DisplayName("Generar programación usa la malla legacy cuando la resolución no coincide")
    void testGenerarProgramacion_MallaLegacySinResolucion() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.countByPeriodoId(1L)).thenReturn(0L);
        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(List.of(programa));
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(1L)).thenReturn(List.of(cohorte));

        PensumAcademico pensumLegacy = PensumAcademico.builder()
                .asignatura(asignatura1)
                .semestre(1)
                .numeroResolucion(null)
                .isElective(false)
                .build();
        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre("2711", "Res. 084", 1))
                .thenReturn(List.of());
        when(pensumRepository.findActiveByCodeProgramAndSemestre("2711", 1))
                .thenReturn(List.of(pensumLegacy));
        when(cursoRepository.contarCursosPorPrograma(1L))
                .thenReturn(Collections.singletonList(new Object[]{"2711", "Tecnología en Desarrollo de Software", 1L}));

        programacionService.generarProgramacion(1L, false);

        verify(cursoRepository).saveAll(argThat(cursos -> ((List<?>) cursos).size() == 1));
    }

    @Test
    @DisplayName("Criterio 4: Si falta alguna cohorte o malla, informa y no genera")
    void testGenerarProgramacion_FaltaCohorte() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.countByPeriodoId(1L)).thenReturn(0L);

        Programa prog2 = Programa.builder().codeProgram("3846").nameProgram("Administración").estado(EstadoPrograma.ACTIVO).build();
        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(List.of(programa, prog2));
        // Solo '2711' tiene cohorte confirmada; falta '3846'
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(1L)).thenReturn(List.of(cohorte));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                programacionService.generarProgramacion(1L, false));

        assertTrue(ex.getMessage().contains("faltan cohortes confirmadas para los programas: Administración"));
        verify(cursoRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Criterio 6: Volver a generar para el mismo periodo pide confirmación si no se indica sobrescribir")
    void testGenerarProgramacion_PideConfirmacion() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.countByPeriodoId(1L)).thenReturn(50L); // Ya existen 50 cursos

        ConflictException ex = assertThrows(ConflictException.class, () ->
                programacionService.generarProgramacion(1L, false));

        assertTrue(ex.getMessage().contains("Requiere confirmación para regenerar"));
    }

    @Test
    @DisplayName("Criterio 6: Al regenerar con sobrescribir=true, conserva los docentes ya asignados a cursos que no cambian")
    void testGenerarProgramacion_ConservaDocente() {
        when(periodoRepository.findById(1L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.countByPeriodoId(1L)).thenReturn(1L);

        Docente docente = Docente.builder().id(99L).nombres("Carlos").apellidos("Gómez").build();
        CursoProgramacion cursoExistente = CursoProgramacion.builder()
                .programa(programa)
                .asignatura(asignatura1)
                .jornada(Jornada.DIURNA)
                .docente(docente)
                .build();
        when(cursoRepository.findByPeriodoIdWithDetails(1L)).thenReturn(List.of(cursoExistente));

        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(List.of(programa));
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(1L)).thenReturn(List.of(cohorte));

        PensumAcademico pensum1 = PensumAcademico.builder().asignatura(asignatura1).semestre(1).isElective(false).numeroResolucion("Res. 084").nameCourseSnapshot("Cálculo I").build();
        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre("2711", "Res. 084", 1))
                .thenReturn(List.of(pensum1));

        when(docenteRepository.findById(99L)).thenReturn(Optional.of(docente));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CursoProgramacion>> captor = ArgumentCaptor.forClass(List.class);

        programacionService.generarProgramacion(1L, true);

        verify(cursoRepository).deleteByPeriodoId(1L);
        verify(cursoRepository).saveAll(captor.capture());
        CursoProgramacion guardado = captor.getValue().get(0);
        assertNotNull(guardado.getDocente());
        assertEquals(99L, guardado.getDocente().getId());
        assertEquals("ASIGNADO", guardado.getEstado());
    }
}
