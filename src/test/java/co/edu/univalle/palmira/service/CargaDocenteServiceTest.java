package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CargaProperties;
import co.edu.univalle.palmira.dto.CargaDocenteDto;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CargaDocenteServiceTest {

    static final Long DOCENTE = 12L;
    static final Long PERIODO = 5L;

    @Mock CursoProgramacionRepository cursoRepository;
    @Mock DocenteRepository docenteRepository;
    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock AccesoProgramaService acceso;

    CargaDocenteService service;

    @BeforeEach
    void setUp() {
        service = new CargaDocenteService(cursoRepository, docenteRepository, periodoRepository,
                acceso, new CargaProperties());
        lenient().when(periodoRepository.existsById(PERIODO)).thenReturn(true);
        lenient().when(docenteRepository.existsById(DOCENTE)).thenReturn(true);
    }

    private CursoProgramacion curso(long id, String codeProgram, String codeCourse, Integer horas) {
        Programa programa = mock(Programa.class);
        lenient().when(programa.getCodeProgram()).thenReturn(codeProgram);
        return CursoProgramacion.builder()
                .id(id)
                .programa(programa)
                .asignatura(Asignatura.builder().codeCourse(codeCourse).nameCourse("Cálculo")
                        .horasSemanales(horas).build())
                .nombreAsignatura("Cálculo")
                .grupo(50)
                .build();
    }

    @Test
    void consultar_admin_sumaTresProgramas_15h() {
        List<CursoProgramacion> cursos = List.of(
                curso(1, "P1", "MAT101", 5), curso(2, "P2", "MAT101", 5), curso(3, "P3", "MAT101", 5));
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(cursos);
        when(acceso.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(true, null));

        CargaDocenteDto.Detalle d = service.consultar(DOCENTE, PERIODO);

        assertEquals(15, d.totalHoras());
        assertEquals(20, d.limite());
        assertEquals(5, d.disponibles());
        assertEquals(3, d.cursos().size());
        assertEquals(0, d.otrosProgramasHoras());
    }

    @Test
    void consultar_coordinador_soloDetalleDeSuPrograma() {
        List<CursoProgramacion> cursos = List.of(
                curso(1, "P1", "MAT101", 5), curso(2, "P2", "MAT101", 5), curso(3, "P3", "MAT101", 5));
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(cursos);
        when(acceso.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(false, "P1"));

        CargaDocenteDto.Detalle d = service.consultar(DOCENTE, PERIODO);

        assertEquals(15, d.totalHoras());
        assertEquals(1, d.cursos().size());
        assertEquals("P1", d.cursos().get(0).codeProgram());
        assertEquals(10, d.otrosProgramasHoras());
    }

    @Test
    void consultar_docenteSinCursos_total0() {
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(List.of());
        when(acceso.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(true, null));

        CargaDocenteDto.Detalle d = service.consultar(DOCENTE, PERIODO);

        assertEquals(0, d.totalHoras());
        assertEquals(20, d.disponibles());
        assertTrue(d.cursos().isEmpty());
        assertEquals(0, d.otrosProgramasHoras());
    }

    @Test
    void consultar_cursoSinHoras_cuenta0YMarcaSinHoras() {
        List<CursoProgramacion> cursos = List.of(curso(1, "P1", "MAT101", null), curso(2, "P1", "FIS101", 4));
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(cursos);
        when(acceso.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(true, null));

        CargaDocenteDto.Detalle d = service.consultar(DOCENTE, PERIODO);

        assertEquals(4, d.totalHoras());
        assertTrue(d.cursos().get(0).sinHoras());
        assertEquals(0, d.cursos().get(0).horas());
        assertFalse(d.cursos().get(1).sinHoras());
    }

    @Test
    void consultar_periodoInexistente_lanzaResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.consultar(DOCENTE, 99L));
    }

    @Test
    void consultar_docenteInexistente_lanzaResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.consultar(99L, PERIODO));
    }

    @Test
    void validar_exacto20_pasa() {
        when(cursoRepository.sumarHorasExcluyendo(DOCENTE, PERIODO, 101L)).thenReturn(16L);

        assertDoesNotThrow(() -> service.validar(DOCENTE, PERIODO, 101L, 4));
    }

    @Test
    void validar_21_lanzaConflictConMensajeExacto() {
        when(cursoRepository.sumarHorasExcluyendo(DOCENTE, PERIODO, 101L)).thenReturn(18L);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.validar(DOCENTE, PERIODO, 101L, 4));

        assertEquals("El docente ya tiene 18 h en este período; este curso suma 4 h (límite 20 h)",
                ex.getMessage());
    }

    @Test
    void validar_reasignarMismoCurso_noCuentaDoble() {
        when(cursoRepository.sumarHorasExcluyendo(DOCENTE, PERIODO, 101L)).thenReturn(16L);
        when(cursoRepository.sumarHorasExcluyendo(DOCENTE, PERIODO, -1L)).thenReturn(16L);

        service.validar(DOCENTE, PERIODO, 101L, 4);
        service.validar(DOCENTE, PERIODO, null, 4);

        verify(cursoRepository).sumarHorasExcluyendo(DOCENTE, PERIODO, 101L);
        verify(cursoRepository).sumarHorasExcluyendo(DOCENTE, PERIODO, -1L);
    }

    @Test
    void listarPorPeriodo_devuelveTotalesPorDocente() {
        when(cursoRepository.totalesPorDocente(PERIODO))
                .thenReturn(List.of(new Object[]{12L, 15L, 0L}, new Object[]{13L, 20L, 1L}));

        List<CargaDocenteDto.Resumen> r = service.listarPorPeriodo(PERIODO);

        assertEquals(2, r.size());
        assertEquals(15, r.get(0).totalHoras());
        assertEquals(5, r.get(0).disponibles());
        assertEquals(0, r.get(1).disponibles());
        assertEquals(1, r.get(1).cursosSinHoras());
    }

    @Test
    void listarPorPeriodo_periodoInexistente_lanzaResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.listarPorPeriodo(99L));
    }
}