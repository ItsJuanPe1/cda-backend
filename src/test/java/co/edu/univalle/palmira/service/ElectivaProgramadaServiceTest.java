package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CuposProperties;
import co.edu.univalle.palmira.dto.DocenteAsignaturaDto;
import co.edu.univalle.palmira.dto.ElectivaProgramadaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElectivaProgramadaServiceTest {

    static final String PROGRAMA = "2135-DIU";
    static final String ESPACIO = "ELE-PROF-I";
    static final String IA = "750022C";

    @Mock CursoProgramacionRepository cursoRepository;
    @Mock PeriodoAcademicoRepository periodoRepository;
    @Mock ProgramaRepository programaRepository;
    @Mock PensumAcademicoRepository pensumRepository;
    @Mock CohortePeriodoRepository cohorteRepository;
    @Mock AsignaturaRepository asignaturaRepository;
    @Mock AccesoProgramaService accesoProgramaService;
    @Mock DocenteAsignaturaService docenteAsignaturaService;
    @Mock ProgramacionService programacionService;
    @Mock BitacoraService bitacoraService;
    @Mock CuposProperties cuposProperties;

    ElectivaProgramadaService service;
    Programa programa;
    PeriodoAcademico periodo;
    PensumAcademico espacio;
    CohortePeriodo cohorte;
    Asignatura ia;

    @BeforeEach
    void setUp() {
        service = new ElectivaProgramadaService(cursoRepository, periodoRepository, programaRepository,
                pensumRepository, cohorteRepository, asignaturaRepository, accesoProgramaService,
                docenteAsignaturaService, programacionService, bitacoraService, cuposProperties);

        programa = Programa.builder().codeProgram(PROGRAMA).nameProgram("Contaduría")
                .idFacultadPrograma(1).jornada(Jornada.DIURNA).build();
        periodo = PeriodoAcademico.builder().id(7L).estado("VIGENTE").vigente(true)
                .plazoCoordinadorInicio(Instant.now().minusSeconds(3600))
                .plazoCoordinadorFin(Instant.now().plusSeconds(3600)).build();
        espacio = PensumAcademico.builder().id(new PensumId(PROGRAMA, ESPACIO, 1)).semestre(4)
                .nameCourseSnapshot("Electiva Profesional I").activo(true).vigente(true).build();
        PensumAcademico complementaria = PensumAcademico.builder()
                .id(new PensumId(PROGRAMA, "ELE-PROF-COMPLEMENTARIA", 1)).semestre(4)
                .nameCourseSnapshot("Electiva Complementaria").activo(true).vigente(true).build();
        cohorte = CohortePeriodo.builder().id(1L).periodo(periodo).programa(programa).semestre(4)
                .jornada(Jornada.DIURNA).numeroResolucion("R-1").confirmada(true).build();
        ia = Asignatura.builder().codeCourse(IA).nameCourse("Inteligencia artificial").credits(3)
                .isElective(true).esComplementaria(false).idFacultad(1).activo(true).build();

        lenient().when(accesoProgramaService.alcanceActual())
                .thenReturn(new AccesoProgramaService.Alcance(false, PROGRAMA));
        lenient().when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(periodo));
        lenient().when(programaRepository.findById(PROGRAMA)).thenReturn(Optional.of(programa));
        lenient().when(pensumRepository.findByIdCodeProgramAndVigenteTrue(PROGRAMA))
                .thenReturn(List.of(espacio, complementaria));
        lenient().when(cohorteRepository.findByPeriodoIdAndCodeProgram(7L, PROGRAMA))
                .thenReturn(List.of(cohorte));
        lenient().when(asignaturaRepository.findById(IA)).thenReturn(Optional.of(ia));
        lenient().when(cursoRepository.findElectivasProgramadas(7L, PROGRAMA)).thenReturn(List.of());
        lenient().when(cursoRepository.findByPeriodoAndAsignatura(7L, IA)).thenReturn(List.of());
        lenient().when(cursoRepository.findByPeriodoAndAsignaturaForUpdate(7L, IA)).thenReturn(List.of());
        lenient().when(cursoRepository.save(any(CursoProgramacion.class))).thenAnswer(inv -> {
            CursoProgramacion c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(100L);
            }
            return c;
        });
        lenient().when(docenteAsignaturaService.listar(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(cuposProperties.getMaxPorGrupo()).thenReturn(60);
    }

    private ElectivaProgramadaDto.CrearRequest pedido() {
        return new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ESPACIO, IA, 30);
    }

    private CursoProgramacion cursoElectiva(Long id, Integer grupo) {
        return CursoProgramacion.builder().id(id).periodo(periodo).programa(programa).asignatura(ia)
                .semestre(4).jornada(Jornada.DIURNA).numeroResolucion("R-1").credits(3)
                .nombreAsignatura("Inteligencia artificial").cupos(30).grupo(grupo)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO).codeCourseEspacio(ESPACIO).build();
    }

    // ---------- crear ----------

    @Test
    void crear_guardaCursoVinculadoAlEspacioYRegistraBitacora() {
        ElectivaProgramadaDto.Response r = service.crear(pedido());

        ArgumentCaptor<CursoProgramacion> guardado = ArgumentCaptor.forClass(CursoProgramacion.class);
        verify(cursoRepository).save(guardado.capture());
        CursoProgramacion c = guardado.getValue();
        assertEquals(ESPACIO, c.getCodeCourseEspacio());
        assertEquals(4, c.getSemestre());
        assertEquals(Jornada.DIURNA, c.getJornada());
        assertEquals("R-1", c.getNumeroResolucion());
        assertEquals(50, c.getGrupo());
        assertEquals(30, c.getCupos());
        assertNull(c.getDocente());
        assertEquals(CursoProgramacion.ESTADO_PROGRAMADO, c.getEstado());

        assertEquals("750022C-50", r.codigoCurso());
        assertEquals("Electiva Profesional I", r.nombreEspacio());
        verify(bitacoraService).registrar(eq(AccionBitacora.CREAR), eq("CURSO"), eq("100"), eq(7L),
                isNull(), any());
    }

    @Test
    void crear_sinDocentesAsociados_avisaHU051() {
        ElectivaProgramadaDto.Response r = service.crear(pedido());
        assertTrue(r.sinDocentesAsociados());
        assertEquals(0, r.docentesAsociados());
        assertEquals(ElectivaProgramadaService.ALERTA_SIN_DOCENTES, r.alerta());
    }

    @Test
    void crear_conDocentesAsociados_noHayAlerta() {
        when(docenteAsignaturaService.listar(any(), eq(IA), eq(1), eq(true)))
                .thenReturn(Collections.nCopies(2, (DocenteAsignaturaDto.Response) null));
        ElectivaProgramadaDto.Response r = service.crear(pedido());
        assertFalse(r.sinDocentesAsociados());
        assertEquals(2, r.docentesAsociados());
        assertNull(r.alerta());
    }

    @Test
    void crear_tomaElSiguienteGrupoDeLaAsignatura() {
        when(cursoRepository.findByPeriodoAndAsignaturaForUpdate(7L, IA))
                .thenReturn(List.of(cursoElectiva(5L, 50), cursoElectiva(6L, 51)));
        ElectivaProgramadaDto.Response r = service.crear(pedido());
        assertEquals(52, r.grupo());
    }

    @Test
    void crear_coordinadorDeOtroPrograma_403() {
        when(accesoProgramaService.alcanceActual())
                .thenReturn(new AccesoProgramaService.Alcance(false, "9999-DIU"));
        assertThrows(AccessDeniedException.class, () -> service.crear(pedido()));
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void crear_plazoCerrado_coordinadorRecibe409_peroElAdminPuede() {
        periodo.setPlazoCoordinadorFin(Instant.now().minusSeconds(60));
        ConflictException ex = assertThrows(ConflictException.class, () -> service.crear(pedido()));
        assertEquals(ProgramacionService.MSG_PLAZO_CERRADO, ex.getMessage());

        when(accesoProgramaService.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(true, null));
        assertDoesNotThrow(() -> service.crear(pedido()));
    }

    @Test
    void crear_sinPeriodoVigente_400() {
        when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
    }

    @Test
    void crear_espacioInexistenteOComplementaria_404() {
        assertThrows(ResourceNotFoundException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, "ELE-PROF-IX", IA, 30)));
        assertThrows(ResourceNotFoundException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, "ELE-PROF-COMPLEMENTARIA", IA, 30)));
    }

    @Test
    void crear_sinCohorteConfirmadaEnElSemestre_409() {
        cohorte.setConfirmada(false);
        assertThrows(ConflictException.class, () -> service.crear(pedido()));
        cohorte.setConfirmada(true);
        cohorte.setSemestre(3);
        assertThrows(ConflictException.class, () -> service.crear(pedido()));
    }

    @Test
    void crear_asignaturaQueNoEsDelBancoProfesional_400() {
        ia.setIsElective(false);
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
        ia.setIsElective(true);
        ia.setEsComplementaria(true);
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
        ia.setEsComplementaria(false);
        ia.setIdFacultad(2);   // facultad común
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
        ia.setIdFacultad(4);   // otra facultad
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void crear_asignaturaInactivaOInexistente() {
        ia.setActivo(false);
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
        when(asignaturaRepository.findById("NOEXISTE")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ESPACIO, "NOEXISTE", 30)));
    }

    @Test
    void crear_espacioOcupado_409() {
        when(cursoRepository.findElectivasProgramadas(7L, PROGRAMA)).thenReturn(List.of(cursoElectiva(5L, 50)));
        ConflictException ex = assertThrows(ConflictException.class, () -> service.crear(pedido()));
        assertEquals(ElectivaProgramadaService.MSG_ESPACIO_OCUPADO, ex.getMessage());
    }

    @Test
    void crear_mismaElectivaEnElProgramaYJornada_409_peroEnOtroProgramaPasa() {
        when(cursoRepository.findByPeriodoAndAsignatura(7L, IA)).thenReturn(List.of(cursoElectiva(5L, 50)));
        ConflictException ex = assertThrows(ConflictException.class, () -> service.crear(pedido()));
        assertEquals(ElectivaProgramadaService.MSG_REPETIDA, ex.getMessage());

        // La misma electiva en otro programa (p. ej. la versión NOC) no choca
        Programa otro = Programa.builder().codeProgram("2135-NOC").idFacultadPrograma(1)
                .jornada(Jornada.DIURNA).build();
        CursoProgramacion deOtroPrograma = cursoElectiva(6L, 50);
        deOtroPrograma.setPrograma(otro);
        when(cursoRepository.findByPeriodoAndAsignatura(7L, IA)).thenReturn(List.of(deOtroPrograma));
        assertDoesNotThrow(() -> service.crear(pedido()));
    }

    @Test
    void crear_cuposInvalidos_400() {
        for (Integer cupos : new Integer[]{null, 0, -3, 61}) {
            assertThrows(BadRequestException.class, () -> service.crear(
                    new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ESPACIO, IA, cupos)));
        }
        assertThrows(BadRequestException.class, () -> service.crear(null));
        assertThrows(BadRequestException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, " ", IA, 30)));
        verify(cursoRepository, never()).save(any());
    }

    // ---------- modificar ----------

    @Test
    void modificar_cambiaCuposYRegistraBitacoraEditar() {
        CursoProgramacion c = cursoElectiva(5L, 50);
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));

        ElectivaProgramadaDto.Response r = service.modificar(5L, new ElectivaProgramadaDto.ModificarRequest(null, 40));

        assertEquals(40, r.cupos());
        verify(bitacoraService).registrar(eq(AccionBitacora.EDITAR), eq("CURSO"), eq("5"), eq(7L), any(), any());
        verify(programacionService, never()).renumerarGrupos(any(), any());
    }

    @Test
    void modificar_cambiaLaElectiva_renumeraLaAnterior() {
        CursoProgramacion c = cursoElectiva(5L, 50);
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));
        Asignatura robotica = Asignatura.builder().codeCourse("710239M").nameCourse("Fundamentos de Robótica")
                .credits(3).isElective(true).esComplementaria(false).idFacultad(1).activo(true).build();
        when(asignaturaRepository.findById("710239M")).thenReturn(Optional.of(robotica));
        when(cursoRepository.findByPeriodoAndAsignatura(7L, "710239M")).thenReturn(List.of());
        when(cursoRepository.findByPeriodoAndAsignaturaForUpdate(7L, "710239M")).thenReturn(List.of());

        ElectivaProgramadaDto.Response r = service.modificar(5L,
                new ElectivaProgramadaDto.ModificarRequest("710239M", null));

        assertEquals("710239M", r.codeCourse());
        assertEquals(50, r.grupo());
        verify(programacionService).renumerarGrupos(7L, IA);
    }

    @Test
    void modificar_conDocente_409() {
        CursoProgramacion c = cursoElectiva(5L, 50);
        c.setDocente(Docente.builder().id(3L).nombres("Ana").apellidos("Pérez").build());
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));
        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.modificar(5L, new ElectivaProgramadaDto.ModificarRequest(null, 40)));
        assertEquals(ElectivaProgramadaService.MSG_CON_DOCENTE, ex.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void modificar_sinCambios_400() {
        assertThrows(BadRequestException.class,
                () -> service.modificar(5L, new ElectivaProgramadaDto.ModificarRequest(null, null)));
    }

    @Test
    void modificar_cursoQueNoEsElectivaProgramada_404() {
        CursoProgramacion normal = cursoElectiva(5L, 50);
        normal.setCodeCourseEspacio(null);
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(normal));
        assertThrows(ResourceNotFoundException.class,
                () -> service.modificar(5L, new ElectivaProgramadaDto.ModificarRequest(null, 40)));
    }

    // ---------- quitar ----------

    @Test
    void quitar_borraElCursoRenumeraYRegistraBitacora() {
        CursoProgramacion c = cursoElectiva(5L, 50);
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));

        service.quitar(5L);

        verify(cursoRepository).delete(c);
        verify(programacionService).renumerarGrupos(7L, IA);
        verify(bitacoraService).registrar(eq(AccionBitacora.DESACTIVAR), eq("CURSO"), eq("5"), eq(7L), any(), any());
    }

    @Test
    void quitar_conDocenteOPlazoCerrado_409_yNoBorra() {
        CursoProgramacion c = cursoElectiva(5L, 50);
        c.setDocente(Docente.builder().id(3L).build());
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));
        assertThrows(ConflictException.class, () -> service.quitar(5L));

        c.setDocente(null);
        periodo.setPlazoCoordinadorFin(Instant.now().minusSeconds(60));
        assertThrows(ConflictException.class, () -> service.quitar(5L));
        verify(cursoRepository, never()).delete(any(CursoProgramacion.class));
    }

    @Test
    void quitar_deOtroPrograma_403() {
        when(accesoProgramaService.alcanceActual())
                .thenReturn(new AccesoProgramaService.Alcance(false, "9999-DIU"));
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(cursoElectiva(5L, 50)));
        assertThrows(AccessDeniedException.class, () -> service.quitar(5L));
    }

    // ---------- listar ----------

    @Test
    void listar_devuelveLasElectivasDelPeriodoVigenteYElPlazo() {
        when(cursoRepository.findElectivasProgramadas(7L, PROGRAMA)).thenReturn(List.of(cursoElectiva(5L, 50)));
        ElectivaProgramadaDto.ListaResponse r = service.listar(PROGRAMA);
        assertEquals(7L, r.periodoId());
        assertTrue(r.plazoAbierto());
        assertTrue(r.puedeEditar());
        assertEquals(1, r.electivas().size());
        assertEquals("Electiva Profesional I", r.electivas().get(0).nombreEspacio());
    }

    @Test
    void listar_sinPeriodoVigente_devuelveListaVacia() {
        when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.empty());
        ElectivaProgramadaDto.ListaResponse r = service.listar(PROGRAMA);
        assertNull(r.periodoId());
        assertTrue(r.electivas().isEmpty());
    }

    @Test
    void listar_plazoCerradoCoordinadorNoPuedeEditar() {
        periodo.setPlazoCoordinadorFin(Instant.now().minusSeconds(60));
        ElectivaProgramadaDto.ListaResponse r = service.listar(PROGRAMA);
        assertFalse(r.plazoAbierto());
        assertFalse(r.puedeEditar());
    }

    @Test
    void esEspacio_soloElectivasProfesionales() {
        assertTrue(ElectivaProgramadaService.esEspacio("ELE-PROF-I"));
        assertTrue(ElectivaProgramadaService.esEspacio("ele-prof-iv"));
        assertFalse(ElectivaProgramadaService.esEspacio("ELE-PROF-COMPLEMENTARIA"));
        assertFalse(ElectivaProgramadaService.esEspacio("750022C"));
        assertFalse(ElectivaProgramadaService.esEspacio(null));
    }
}