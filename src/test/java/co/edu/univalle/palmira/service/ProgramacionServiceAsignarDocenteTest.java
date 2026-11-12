package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramacionServiceAsignarDocenteTest {

    @Mock CursoProgramacionRepository cursoRepository;
    @Mock CohortePeriodoRepository cohorteRepository;
    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock ProgramaRepository programaRepository;
    @Mock PensumAcademicoRepository pensumRepository;
    @Mock DocenteAsignaturaService docenteAsignaturaService;
    @Mock BitacoraService bitacoraService;
    @Mock DocenteRepository docenteRepository;
    @Mock AsignaturaRepository asignaturaRepository;
    @Mock AccesoProgramaService accesoProgramaService;
    @Mock CargaDocenteService cargaDocenteService;

    @InjectMocks ProgramacionService service;

    PeriodoAcademico periodo;
    Programa programa;
    Docente ana;

    @BeforeEach
    void setUp() {
        periodo = PeriodoAcademico.builder().id(5L).build();
        programa = mock(Programa.class);
        when(programa.getCodeProgram()).thenReturn("P1");
        when(programa.getNameProgram()).thenReturn("Programa 1");
        ana = Docente.builder().id(12L).nombres("Ana").apellidos("Pérez").build();
        when(cursoRepository.save(any(CursoProgramacion.class))).thenAnswer(i -> i.getArgument(0));
        when(periodoRepository.existsById(5L)).thenReturn(true);
        when(programaRepository.existsById("P1")).thenReturn(true);
        when(asignaturaRepository.existsById("MAT101")).thenReturn(true);
    }

    private CursoProgramacion curso(Integer horas, String estado, Docente docente) {
        return CursoProgramacion.builder()
                .id(101L)
                .periodo(periodo)
                .programa(programa)
                .asignatura(Asignatura.builder().codeCourse("MAT101").nameCourse("Cálculo")
                        .horasSemanales(horas).build())
                .nombreAsignatura("Cálculo")
                .grupo(50)
                .estado(estado)
                .docente(docente)
                .build();
    }

    private void cursoCargado(CursoProgramacion c) {
        when(cursoRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(c));
    }

    private void docenteBloqueado(Docente d) {
        when(docenteRepository.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
    }

    // ------------------------------------------------------------ alcance del coordinador

    @Test
    void asignar_coordinadorEnOtroPrograma_lanzaAccessDenied_yNoGuarda() {
        cursoCargado(curso(4, CursoProgramacion.ESTADO_PROGRAMADO, null));
        doThrow(new AccessDeniedException("x")).when(accesoProgramaService).exigirProgramaPropio("P1");

        assertThrows(AccessDeniedException.class, () -> service.asignarDocente(101L, 12L, false));

        verify(cursoRepository, never()).save(any());
        verifyNoInteractions(cargaDocenteService);
    }

    @Test
    void porMateria_coordinadorEnOtroPrograma_lanzaAccessDenied_yNoTocaNada() {
        doThrow(new AccessDeniedException("x")).when(accesoProgramaService).exigirProgramaPropio("P1");

        assertThrows(AccessDeniedException.class,
                () -> service.asignarDocentePorMateria(5L, "P1", "MAT101", 1, 12L, false));

        verify(cursoRepository, never()).findByPeriodoAndAsignatura(anyLong(), anyString());
        verify(cursoRepository, never()).save(any());
    }

    // ------------------------------------------------------------ solo cursos generados

    @Test
    void porMateria_sinCursoGenerado_lanza404_yNoCreaCurso() {
        when(cursoRepository.findByPeriodoAndAsignatura(5L, "MAT101")).thenReturn(List.of());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.asignarDocentePorMateria(5L, "P1", "MAT101", 1, 12L, false));

        assertEquals("La asignatura no está programada en este período para el programa", ex.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void porMateria_cursoExcluidoNoCuenta_lanza404() {
        CursoProgramacion excluido = curso(4, CursoProgramacion.ESTADO_EXCLUIDO, null);
        when(cursoRepository.findByPeriodoAndAsignatura(5L, "MAT101")).thenReturn(List.of(excluido));

        assertThrows(ResourceNotFoundException.class,
                () -> service.asignarDocentePorMateria(5L, "P1", "MAT101", 1, 12L, false));
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void porMateria_cursoGenerado_asignaSinCrearOtro() {
        CursoProgramacion c = curso(4, CursoProgramacion.ESTADO_PROGRAMADO, null);
        when(cursoRepository.findByPeriodoAndAsignatura(5L, "MAT101")).thenReturn(List.of(c));
        cursoCargado(c);
        docenteBloqueado(ana);

        ProgramacionDto.CursoResponse r = service.asignarDocentePorMateria(5L, "P1", "MAT101", 1, 12L, false);

        assertEquals(12L, r.getIdDocente());
        verify(cursoRepository, times(1)).save(any(CursoProgramacion.class));
    }

    // ------------------------------------------------------------ límite de horas

    @Test
    void asignar_asignaturaSinHoras_lanzaBadRequestConMensajeExacto() {
        cursoCargado(curso(null, CursoProgramacion.ESTADO_PROGRAMADO, null));
        docenteBloqueado(ana);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.asignarDocente(101L, 12L, false));

        assertEquals("La asignatura no tiene horas semanales definidas; defínalas antes de asignar docente",
                ex.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void asignar_excedeLimite_lanzaConflict_yNoGuarda() {
        CursoProgramacion c = curso(4, CursoProgramacion.ESTADO_PROGRAMADO, null);
        cursoCargado(c);
        docenteBloqueado(ana);
        doThrow(new ConflictException("El docente ya tiene 18 h en este período; este curso suma 4 h (límite 20 h)"))
                .when(cargaDocenteService).validar(12L, 5L, 101L, 4);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.asignarDocente(101L, 12L, false));

        assertEquals("El docente ya tiene 18 h en este período; este curso suma 4 h (límite 20 h)", ex.getMessage());
        verify(cursoRepository, never()).save(any());
        assertNull(c.getDocente());
    }

    @Test
    void asignar_dentroDelLimite_guardaYMarcaAsignado() {
        cursoCargado(curso(4, CursoProgramacion.ESTADO_PROGRAMADO, null));
        docenteBloqueado(ana);

        ProgramacionDto.CursoResponse r = service.asignarDocente(101L, 12L, false);

        assertEquals(12L, r.getIdDocente());
        assertEquals(CursoProgramacion.ESTADO_ASIGNADO, r.getEstado());
        verify(cargaDocenteService).validar(12L, 5L, 101L, 4);
    }

    @Test
    void asignar_mismoDocenteMismoCurso_validaExcluyendoElPropioCurso() {
        cursoCargado(curso(4, CursoProgramacion.ESTADO_ASIGNADO, ana));
        docenteBloqueado(ana);

        assertDoesNotThrow(() -> service.asignarDocente(101L, 12L, false));

        verify(cargaDocenteService).validar(12L, 5L, 101L, 4);
    }

    // ------------------------------------------------------------ lo que no debe cambiar

    @Test
    void desasignar_idDocenteNull_noValidaNiExigeHoras() {
        CursoProgramacion c = curso(null, CursoProgramacion.ESTADO_ASIGNADO, ana);
        cursoCargado(c);

        ProgramacionDto.CursoResponse r = service.asignarDocente(101L, null, false);

        assertNull(r.getIdDocente());
        assertEquals(CursoProgramacion.ESTADO_PROGRAMADO, r.getEstado());
        verifyNoInteractions(cargaDocenteService);
        verify(docenteRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void asignar_cursoExcluido_sigueRechazando() {
        cursoCargado(curso(4, CursoProgramacion.ESTADO_EXCLUIDO, null));

        assertThrows(BadRequestException.class, () -> service.asignarDocente(101L, 12L, false));
        verifyNoInteractions(cargaDocenteService);
    }

    @Test
    void asignar_docenteInactivo_sigueRechazando() {
        Docente inactivo = Docente.builder().id(12L).nombres("Ana").apellidos("Pérez").estado(false).build();
        cursoCargado(curso(4, CursoProgramacion.ESTADO_PROGRAMADO, null));
        docenteBloqueado(inactivo);

        assertThrows(BadRequestException.class, () -> service.asignarDocente(101L, 12L, false));
        verifyNoInteractions(cargaDocenteService);
    }

    @Test
    void asignar_reemplazoSinConfirmar_sigueRechazandoConConflict() {
        Docente otro = Docente.builder().id(9L).nombres("Luis").apellidos("Gómez").build();
        cursoCargado(curso(4, CursoProgramacion.ESTADO_ASIGNADO, otro));

        assertThrows(ConflictException.class, () -> service.asignarDocente(101L, 12L, false));
        verifyNoInteractions(cargaDocenteService);
    }

    @Test
    void asignar_cursoInexistente_lanza404() {
        when(cursoRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.asignarDocente(999L, 12L, false));
    }
}