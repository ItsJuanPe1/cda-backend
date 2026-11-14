package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CargaProperties;
import co.edu.univalle.palmira.dto.MisCursosDto;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MisCursosServiceTest {

    static final String CORREO = "ana@correounivalle.edu.co";
    static final Long DOCENTE = 7L;
    static final Long PERIODO = 9L;

    @Mock DocenteRepository docenteRepository;
    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock CursoProgramacionRepository cursoRepository;

    MisCursosService service;

    @BeforeEach
    void setUp() {
        service = new MisCursosService(docenteRepository, periodoRepository, cursoRepository,
                new CargaProperties());
        Docente docente = Docente.builder().id(DOCENTE).build();
        lenient().when(docenteRepository.findByCorreoInstitucionalAndEstadoTrue(CORREO))
                .thenReturn(Optional.of(docente));
        periodoVigente("VIGENTE");
    }

    private void periodoVigente(String estado) {
        PeriodoAcademico periodo = mock(PeriodoAcademico.class);
        lenient().when(periodo.getId()).thenReturn(PERIODO);
        lenient().when(periodo.getNombre()).thenReturn("2026-2");
        lenient().when(periodo.isVigente()).thenReturn(true);
        lenient().when(periodo.getEstado()).thenReturn(estado);
        lenient().when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(periodo));
    }

    private CursoProgramacion curso(long id, String programa, String codeCourse, int grupo,
                                    Integer horas, Jornada jornada) {
        return CursoProgramacion.builder()
                .id(id)
                .programa(Programa.builder().codeProgram("0000").nameProgram(programa).build())
                .asignatura(Asignatura.builder().codeCourse(codeCourse).nameCourse("X")
                        .horasSemanales(horas).build())
                .nombreAsignatura("Asignatura " + codeCourse)
                .grupo(grupo)
                .jornada(jornada)
                .build();
    }

    @Test
    void devuelveSusCursosConCodigoGrupoYHoras() {
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(List.of(
                curso(1, "Ingeniería de Software", "710022C", 50, 4, Jornada.DIURNA),
                curso(2, "Contaduría", "710030C", 51, 6, Jornada.NOCTURNA)));

        MisCursosDto.Respuesta r = service.consultar(CORREO);

        assertEquals(2, r.cursos().size());
        assertEquals("710022C-50", r.cursos().get(0).codigoGrupo());
        assertEquals("Ingeniería de Software", r.cursos().get(0).nombrePrograma());
        assertEquals("DIURNA", r.cursos().get(0).jornada());
        assertEquals("710030C-51", r.cursos().get(1).codigoGrupo());
        assertEquals("NOCTURNA", r.cursos().get(1).jornada());
        assertEquals(10, r.totalHoras());
        assertEquals(20, r.limite());
        assertEquals(10, r.disponibles());
        assertEquals(PERIODO, r.periodo().id());
        assertEquals("2026-2", r.periodo().nombre());
    }

    @Test
    void consultaSoloConElIdDelDocenteDelToken() {
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(List.of());

        service.consultar(CORREO);

        verify(cursoRepository).findByDocenteYPeriodo(DOCENTE, PERIODO);
        verifyNoMoreInteractions(cursoRepository);
    }

    @Test
    void cursoSinHorasCuentaCeroYSeMarca() {
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(List.of(
                curso(1, "P", "A100", 50, null, Jornada.DIURNA),
                curso(2, "P", "B200", 50, 5, Jornada.DIURNA)));

        MisCursosDto.Respuesta r = service.consultar(CORREO);

        assertEquals(0, r.cursos().get(0).horas());
        assertTrue(r.cursos().get(0).sinHoras());
        assertFalse(r.cursos().get(1).sinHoras());
        assertEquals(5, r.totalHoras());
    }

    @Test
    void sinCursos_devuelveListaVaciaYTotalCero() {
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(List.of());

        MisCursosDto.Respuesta r = service.consultar(CORREO);

        assertTrue(r.cursos().isEmpty());
        assertEquals(0, r.totalHoras());
        assertEquals(20, r.disponibles());
    }

    @Test
    void sinPeriodoVigente_devuelvePeriodoNuloYVacio() {
        when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.empty());

        MisCursosDto.Respuesta r = service.consultar(CORREO);

        assertNull(r.periodo());
        assertTrue(r.cursos().isEmpty());
        assertEquals(0, r.totalHoras());
        verifyNoInteractions(cursoRepository);
    }

    @Test
    void periodoVigenteConEstadoHistorial_seTrataComoSinPeriodo() {
        periodoVigente("HISTORIAL");

        MisCursosDto.Respuesta r = service.consultar(CORREO);

        assertNull(r.periodo());
        verifyNoInteractions(cursoRepository);
    }

    @Test
    void docenteInexistenteOInactivo_lanzaAccessDenied() {
        when(docenteRepository.findByCorreoInstitucionalAndEstadoTrue("otro@correounivalle.edu.co"))
                .thenReturn(Optional.empty());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.consultar("otro@correounivalle.edu.co"));

        assertEquals(AccesoProgramaService.MSG_SIN_PERMISO, ex.getMessage());
        verifyNoInteractions(cursoRepository);
    }

    @Test
    void correoNuloOVacio_lanzaAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> service.consultar(null));
        assertThrows(AccessDeniedException.class, () -> service.consultar("   "));
        verifyNoInteractions(docenteRepository);
    }

    @Test
    void cursosSeOrdenanPorCodigoYGrupo() {
        when(cursoRepository.findByDocenteYPeriodo(DOCENTE, PERIODO)).thenReturn(List.of(
                curso(1, "P", "B200", 50, 2, Jornada.DIURNA),
                curso(2, "P", "A100", 51, 2, Jornada.DIURNA),
                curso(3, "P", "A100", 50, 2, Jornada.DIURNA)));

        List<String> codigos = service.consultar(CORREO).cursos().stream()
                .map(MisCursosDto.Curso::codigoGrupo).toList();

        assertEquals(List.of("A100-50", "A100-51", "B200-50"), codigos);
    }
}