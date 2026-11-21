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
    @Mock EscuelaRepository escuelaRepository;

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
                docenteAsignaturaService, programacionService, bitacoraService, cuposProperties, escuelaRepository);

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
    void crear_espacioInexistente_404() {
        assertThrows(ResourceNotFoundException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, "ELE-PROF-IX", IA, 30)));
    }

    // ---------- banco por escuela ----------

    private void conEscuelas(String... codigos) {
        List<Escuela> escuelas = java.util.Arrays.stream(codigos)
                .map(c -> Escuela.builder().codigo(c).nombre("ESCUELA " + c).build())
                .toList();
        lenient().when(escuelaRepository.findByPrograma(PROGRAMA)).thenReturn(escuelas);
    }

    private Asignatura electiva(String codigo, Integer facultad) {
        return Asignatura.builder().codeCourse(codigo).nameCourse("Electiva " + codigo).credits(3)
                .isElective(true).esComplementaria(false).idFacultad(facultad).activo(true).build();
    }

    @Test
    void crear_programaConEscuela_rechazaLaElectivaDeOtraEscuelaAunqueSeaDeLaMismaFacultad() {
        conEscuelas("710");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear(pedido()));
        assertEquals(ElectivaProgramadaService.MSG_NO_PROFESIONAL, ex.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void crear_programaConEscuela_aceptaLaDeSuEscuelaAunSinFacultad() {
        conEscuelas("750");
        when(asignaturaRepository.findById(IA)).thenReturn(Optional.of(electiva(IA, null)));
        assertDoesNotThrow(() -> service.crear(pedido()));
    }

    @Test
    void crear_programaConVariasEscuelas_aceptaLasDeCualquieraDeEllas() {
        conEscuelas("801", "802");
        when(asignaturaRepository.findById("802097M")).thenReturn(Optional.of(electiva("802097M", 4)));
        assertDoesNotThrow(() -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ESPACIO, "802097M", 30)));
    }

    @Test
    void crear_programaConEscuela_aceptaElPrefijoTodasLasFacultades() {
        conEscuelas("750");
        when(asignaturaRepository.findById("990111M")).thenReturn(Optional.of(electiva("990111M", 1)));
        assertDoesNotThrow(() -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ESPACIO, "990111M", 30)));
    }

    @Test
    void crear_programaConEscuela_sigueRechazandoLasComunes() {
        conEscuelas("750");
        when(asignaturaRepository.findById(IA)).thenReturn(Optional.of(electiva(IA, 2)));
        assertThrows(BadRequestException.class, () -> service.crear(pedido()));
    }

    // ---------- complementaria del primer semestre ----------

    private Asignatura complementaria() {
        return Asignatura.builder().codeCourse("204024C").nameCourse("Lenguaje y Comunicación").credits(2)
                .isElective(true).esComplementaria(true).idFacultad(2).activo(true).build();
    }

    private void espacioComplementariaEnSemestre(int semestre) {
        PensumAcademico comp = PensumAcademico.builder()
                .id(new PensumId(PROGRAMA, "ELE-PROF-COMPLEMENTARIA", 1)).semestre(semestre)
                .nameCourseSnapshot("Electiva Complementaria").activo(true).vigente(true).build();
        lenient().when(pensumRepository.findByIdCodeProgramAndVigenteTrue(PROGRAMA)).thenReturn(List.of(espacio, comp));
        CohortePeriodo c = CohortePeriodo.builder().id(2L).periodo(periodo).programa(programa)
                .semestre(semestre).jornada(Jornada.DIURNA).numeroResolucion("R-1").confirmada(true).build();
        lenient().when(cohorteRepository.findByPeriodoIdAndCodeProgram(7L, PROGRAMA)).thenReturn(List.of(cohorte, c));
    }

    @Test
    void crear_complementariaEnPrimerSemestre_seProgramaEnSuEspacio() {
        espacioComplementariaEnSemestre(1);
        Asignatura comp = complementaria();
        when(asignaturaRepository.findById("204024C")).thenReturn(Optional.of(comp));

        ElectivaProgramadaDto.Response r = service.crear(new ElectivaProgramadaDto.CrearRequest(
                PROGRAMA, "ELE-PROF-COMPLEMENTARIA", "204024C", 40));

        ArgumentCaptor<CursoProgramacion> guardado = ArgumentCaptor.forClass(CursoProgramacion.class);
        verify(cursoRepository).save(guardado.capture());
        assertEquals("ELE-PROF-COMPLEMENTARIA", guardado.getValue().getCodeCourseEspacio());
        assertEquals(1, guardado.getValue().getSemestre());
        assertEquals("Electiva Complementaria", r.nombreEspacio());
        assertEquals("204024C-50", r.codigoCurso());
    }

    @Test
    void crear_complementariaEnPrimerSemestre_noLaBloqueaUnaGeneralDeOtroPrograma() {
        espacioComplementariaEnSemestre(1);
        Asignatura comp = complementaria();
        when(asignaturaRepository.findById("204024C")).thenReturn(Optional.of(comp));
        Programa otro = Programa.builder().codeProgram("3751-DIU").nameProgram("Industrial").build();
        CursoProgramacion general = CursoProgramacion.builder().id(9L).periodo(periodo).programa(otro)
                .asignatura(comp).semestre(0).jornada(Jornada.NOCTURNA).numeroResolucion("R-9").grupo(50)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO).codeCourseEspacio("COMPLEMENTARIA").build();
        when(cursoRepository.findByPeriodoAndAsignatura(7L, "204024C")).thenReturn(List.of(general));

        assertDoesNotThrow(() -> service.crear(new ElectivaProgramadaDto.CrearRequest(
                PROGRAMA, "ELE-PROF-COMPLEMENTARIA", "204024C", 40)));
    }

    @Test
    void crear_complementariaFueraDelPrimerSemestre_seRechaza() {
        espacioComplementariaEnSemestre(2);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, "ELE-PROF-COMPLEMENTARIA", "204024C", 40)));
        assertEquals(ElectivaProgramadaService.MSG_COMPLEMENTARIA_SEMESTRE, ex.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void crear_espacioComplementaria_noAceptaUnaProfesional() {
        espacioComplementariaEnSemestre(1);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, "ELE-PROF-COMPLEMENTARIA", IA, 40)));
        assertEquals(ElectivaProgramadaService.MSG_NO_COMPLEMENTARIA, ex.getMessage());
    }

    @Test
    void crear_espacioProfesional_noAceptaUnaComplementaria() {
        Asignatura comp = complementaria();
        when(asignaturaRepository.findById("204024C")).thenReturn(Optional.of(comp));
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear(
                new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ESPACIO, "204024C", 40)));
        assertEquals(ElectivaProgramadaService.MSG_NO_PROFESIONAL, ex.getMessage());
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
    void listar_devuelveLasEscuelasDelBancoDelPrograma() {
        conEscuelas("801", "802");
        ElectivaProgramadaDto.ListaResponse r = service.listar(PROGRAMA);
        assertEquals(2, r.escuelasBanco().size());
        assertEquals("801", r.escuelasBanco().get(0).codigo());
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
    void esEspacioComplementaria_soloElEspacioDeLaComplementaria() {
        assertTrue(ElectivaProgramadaService.esEspacioComplementaria("ELE-PROF-COMPLEMENTARIA"));
        assertTrue(ElectivaProgramadaService.esEspacioComplementaria("ele-prof-complementaria"));
        assertFalse(ElectivaProgramadaService.esEspacioComplementaria("ELE-PROF-I"));
        assertFalse(ElectivaProgramadaService.esEspacioComplementaria(null));
    }

    @Test
    void esEspacio_soloElectivasProfesionales() {
        assertTrue(ElectivaProgramadaService.esEspacio("ELE-PROF-I"));
        assertTrue(ElectivaProgramadaService.esEspacio("ele-prof-iv"));
        assertFalse(ElectivaProgramadaService.esEspacio("ELE-PROF-COMPLEMENTARIA"));
        assertFalse(ElectivaProgramadaService.esEspacio("750022C"));
        assertFalse(ElectivaProgramadaService.esEspacio(null));
    }

    // ---------- complementarias propuestas por el coordinador ----------

    static final String COMPLEMENTARIA = "204133C";
    Asignatura comp;

    private void conComplementaria() {
        comp = Asignatura.builder().codeCourse(COMPLEMENTARIA).nameCourse("Cátedra de paz").credits(2)
                .isElective(true).esComplementaria(true).idFacultad(2).activo(true).build();
        lenient().when(asignaturaRepository.findById(COMPLEMENTARIA)).thenReturn(Optional.of(comp));
        lenient().when(cursoRepository.findByPeriodoAndAsignatura(7L, COMPLEMENTARIA)).thenReturn(List.of());
        lenient().when(cursoRepository.findByPeriodoAndAsignaturaForUpdate(7L, COMPLEMENTARIA))
                .thenReturn(List.of());
        lenient().when(asignaturaRepository.findAll()).thenReturn(List.of(comp, ia));
    }

    private ElectivaProgramadaDto.CrearRequest propuesta(String jornada) {
        return new ElectivaProgramadaDto.CrearRequest(PROGRAMA, ElectivaProgramadaService.ESPACIO_PROPUESTA,
                COMPLEMENTARIA, 25, jornada);
    }

    @Test
    void propuesta_seGuardaSinEspacioDeMallaNiSemestre() {
        conComplementaria();
        ElectivaProgramadaDto.Response r = service.crear(propuesta("NOCTURNA"));

        ArgumentCaptor<CursoProgramacion> guardado = ArgumentCaptor.forClass(CursoProgramacion.class);
        verify(cursoRepository).save(guardado.capture());
        CursoProgramacion c = guardado.getValue();
        assertEquals("COMPLEMENTARIA", c.getCodeCourseEspacio());
        assertEquals(ElectivaProgramadaService.SEMESTRE_PROPUESTA, c.getSemestre());
        assertEquals(Jornada.NOCTURNA, c.getJornada());
        assertEquals("R-1", c.getNumeroResolucion());
        assertEquals(50, c.getGrupo());
        assertEquals(25, c.getCupos());
        assertNull(c.getDocente());
        assertEquals("Electiva complementaria", r.nombreEspacio());
        assertEquals(Jornada.NOCTURNA, r.jornada());
        verify(bitacoraService).registrar(eq(AccionBitacora.CREAR), eq("CURSO"), eq("100"), eq(7L),
                isNull(), any());
    }

    @Test
    void propuesta_sinCohorteConfirmada_usaResolucionNoAplica() {
        conComplementaria();
        when(cohorteRepository.findByPeriodoIdAndCodeProgram(7L, PROGRAMA)).thenReturn(List.of());
        service.crear(propuesta("DIURNA"));
        ArgumentCaptor<CursoProgramacion> guardado = ArgumentCaptor.forClass(CursoProgramacion.class);
        verify(cursoRepository).save(guardado.capture());
        assertEquals("N/A", guardado.getValue().getNumeroResolucion());
    }

    @Test
    void propuesta_sinJornadaONoValida_400_yNoUsaLaDelPrograma() {
        conComplementaria();
        for (String jornada : new String[]{null, "", "  ", "AMBAS", "TARDE"}) {
            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear(propuesta(jornada)));
            assertEquals(ElectivaProgramadaService.MSG_JORNADA, ex.getMessage());
        }
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void propuesta_yaProgramadaEnElPeriodo_enCualquierPrograma_409() {
        conComplementaria();
        Programa otro = Programa.builder().codeProgram("3751-DIU").nameProgram("Industrial").build();
        CursoProgramacion existente = CursoProgramacion.builder().id(9L).periodo(periodo).programa(otro)
                .asignatura(comp).semestre(0).jornada(Jornada.NOCTURNA).numeroResolucion("R-9").grupo(50)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO).codeCourseEspacio("COMPLEMENTARIA").build();
        when(cursoRepository.findByPeriodoAndAsignatura(7L, COMPLEMENTARIA)).thenReturn(List.of(existente));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.crear(propuesta("DIURNA")));
        assertEquals(ElectivaProgramadaService.MSG_COMPLEMENTARIA_REPETIDA, ex.getMessage());
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void propuesta_laExcluidaNoImpideProponerlaDeNuevo() {
        conComplementaria();
        CursoProgramacion excluida = CursoProgramacion.builder().id(9L).periodo(periodo).programa(programa)
                .asignatura(comp).semestre(0).jornada(Jornada.DIURNA).numeroResolucion("R-9")
                .estado(CursoProgramacion.ESTADO_EXCLUIDO).codeCourseEspacio("COMPLEMENTARIA").build();
        when(cursoRepository.findByPeriodoAndAsignatura(7L, COMPLEMENTARIA)).thenReturn(List.of(excluida));
        assertDoesNotThrow(() -> service.crear(propuesta("DIURNA")));
    }

    @Test
    void propuesta_unaAsignaturaQueNoEsComplementaria_400() {
        conComplementaria();
        assertThrows(BadRequestException.class, () -> service.crear(new ElectivaProgramadaDto.CrearRequest(
                PROGRAMA, ElectivaProgramadaService.ESPACIO_PROPUESTA, IA, 25, "DIURNA")));
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void propuesta_coordinadorDeOtroPrograma_403() {
        conComplementaria();
        when(accesoProgramaService.alcanceActual())
                .thenReturn(new AccesoProgramaService.Alcance(false, "9999-DIU"));
        assertThrows(AccessDeniedException.class, () -> service.crear(propuesta("DIURNA")));
        verify(cursoRepository, never()).save(any());
    }

    @Test
    void propuesta_plazoCerrado_coordinador409_peroElAdminPuede() {
        conComplementaria();
        periodo.setPlazoCoordinadorFin(Instant.now().minusSeconds(60));
        assertThrows(ConflictException.class, () -> service.crear(propuesta("DIURNA")));
        when(accesoProgramaService.alcanceActual()).thenReturn(new AccesoProgramaService.Alcance(true, null));
        assertDoesNotThrow(() -> service.crear(propuesta("DIURNA")));
    }

    @Test
    void propuesta_variasEnElMismoProgramaYPeriodo_sePermiten() {
        conComplementaria();
        Asignatura otra = Asignatura.builder().codeCourse("204200C").nameCourse("Ética").credits(2)
                .isElective(true).esComplementaria(true).idFacultad(2).activo(true).build();
        when(asignaturaRepository.findById("204200C")).thenReturn(Optional.of(otra));
        when(cursoRepository.findByPeriodoAndAsignatura(7L, "204200C")).thenReturn(List.of());
        when(cursoRepository.findByPeriodoAndAsignaturaForUpdate(7L, "204200C")).thenReturn(List.of());
        service.crear(propuesta("DIURNA"));
        assertDoesNotThrow(() -> service.crear(new ElectivaProgramadaDto.CrearRequest(
                PROGRAMA, "complementaria", "204200C", 20, "NOCTURNA")));
        verify(cursoRepository, times(2)).save(any());
    }

    @Test
    void propuesta_sinDocente_sePuedeModificarYQuitar() {
        conComplementaria();
        CursoProgramacion c = CursoProgramacion.builder().id(5L).periodo(periodo).programa(programa)
                .asignatura(comp).semestre(0).jornada(Jornada.DIURNA).numeroResolucion("R-1")
                .nombreAsignatura("Cátedra de paz").cupos(25).grupo(50).credits(2)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO).codeCourseEspacio("COMPLEMENTARIA").build();
        when(cursoRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));

        ElectivaProgramadaDto.Response r = service.modificar(5L, new ElectivaProgramadaDto.ModificarRequest(null, 40));
        assertEquals(40, r.cupos());
        service.quitar(5L);
        verify(cursoRepository).delete(c);
    }

    @Test
    void listar_separaLasPropuestasDeLasElectivasDeLaMalla_yOfreceElBanco() {
        conComplementaria();
        CursoProgramacion propuesta = CursoProgramacion.builder().id(6L).periodo(periodo).programa(programa)
                .asignatura(comp).semestre(0).jornada(Jornada.NOCTURNA).numeroResolucion("R-1")
                .nombreAsignatura("Cátedra de paz").cupos(25).grupo(50).credits(2)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO).codeCourseEspacio("COMPLEMENTARIA").build();
        when(cursoRepository.findElectivasProgramadas(7L, PROGRAMA))
                .thenReturn(List.of(cursoElectiva(5L, 50), propuesta));
        // la ya programada sigue en el banco, pero marcada con el programa que la tiene
        when(cursoRepository.findByPeriodoAndAsignatura(7L, COMPLEMENTARIA)).thenReturn(List.of(propuesta));

        ElectivaProgramadaDto.ListaResponse r = service.listar(PROGRAMA);
        assertEquals(1, r.electivas().size());
        assertEquals(1, r.complementarias().size());
        assertEquals("COMPLEMENTARIA", r.complementarias().get(0).codeEspacio());
        assertEquals(1, r.bancoComplementarias().size());
        assertNotNull(r.bancoComplementarias().get(0).programadaPor());
    }

    @Test
    void listar_bancoComplementarias_sinProgramarNoTieneProgramaQueLaTenga() {
        conComplementaria();
        ElectivaProgramadaDto.ListaResponse r = service.listar(PROGRAMA);
        assertEquals(1, r.bancoComplementarias().size());
        assertEquals(COMPLEMENTARIA, r.bancoComplementarias().get(0).codeCourse());
        assertNull(r.bancoComplementarias().get(0).programadaPor());
    }

    @Test
    void esPropuesta_soloLaMarcaDeLasPropuestas() {
        assertTrue(ElectivaProgramadaService.esPropuesta("COMPLEMENTARIA"));
        assertTrue(ElectivaProgramadaService.esPropuesta(" complementaria "));
        assertFalse(ElectivaProgramadaService.esPropuesta("ELE-PROF-COMPLEMENTARIA"));
        assertFalse(ElectivaProgramadaService.esPropuesta("ELE-PROF-I"));
        assertFalse(ElectivaProgramadaService.esPropuesta(null));
    }

    @Test
    void listar_pendientesObligatorias_reportaEspaciosSinEscogerYSeReinicianAlCambiarPeriodo() {
        // En el período vigente (7L), el semestre 4 tiene cohorte confirmada y ELE-PROF-I sin escoger.
        ElectivaProgramadaDto.ListaResponse sinEscoger = service.listar(PROGRAMA);
        assertEquals(1, sinEscoger.pendientesObligatorias().size());
        assertEquals(ESPACIO, sinEscoger.pendientesObligatorias().get(0).codeEspacio());
        assertEquals(4, sinEscoger.pendientesObligatorias().get(0).semestre());
        assertFalse(sinEscoger.pendientesObligatorias().get(0).esComplementaria());

        // Una vez el coordinador escoge la electiva en el período 7L, deja de estar pendiente.
        when(cursoRepository.findElectivasProgramadas(7L, PROGRAMA)).thenReturn(List.of(cursoElectiva(5L, 50)));
        ElectivaProgramadaDto.ListaResponse escogida = service.listar(PROGRAMA);
        assertTrue(escogida.pendientesObligatorias().isEmpty());

        // Al pasar a un nuevo período vigente (8L) con cohorte confirmada y sin cursos programados, vuelve a quedar limpia y pendiente.
        PeriodoAcademico nuevoPeriodo = PeriodoAcademico.builder().id(8L).estado("VIGENTE").vigente(true)
                .plazoCoordinadorInicio(Instant.now().minusSeconds(3600))
                .plazoCoordinadorFin(Instant.now().plusSeconds(3600)).build();
        CohortePeriodo nuevaCohorte = CohortePeriodo.builder().id(2L).periodo(nuevoPeriodo).programa(programa)
                .semestre(4).jornada(Jornada.DIURNA).numeroResolucion("R-1").confirmada(true).build();
        when(periodoRepository.findFirstByVigenteTrue()).thenReturn(Optional.of(nuevoPeriodo));
        when(cohorteRepository.findByPeriodoIdAndCodeProgram(8L, PROGRAMA)).thenReturn(List.of(nuevaCohorte));
        when(cursoRepository.findElectivasProgramadas(8L, PROGRAMA)).thenReturn(List.of());

        ElectivaProgramadaDto.ListaResponse nuevoSemestre = service.listar(PROGRAMA);
        assertTrue(nuevoSemestre.electivas().isEmpty());
        assertEquals(1, nuevoSemestre.pendientesObligatorias().size());
        assertEquals(ESPACIO, nuevoSemestre.pendientesObligatorias().get(0).codeEspacio());
    }

    @Test
    void crear_complementaria_cuentaDocentesSinRestringirAFacultadDelPrograma() {
        conComplementaria();
        when(docenteAsignaturaService.listar(isNull(), eq(COMPLEMENTARIA), isNull(), eq(true)))
                .thenReturn(Collections.nCopies(1, (DocenteAsignaturaDto.Response) null));

        ElectivaProgramadaDto.Response r = service.crear(propuesta("DIURNA"));
        assertFalse(r.sinDocentesAsociados());
        assertEquals(1, r.docentesAsociados());
        assertNull(r.alerta());
        verify(docenteAsignaturaService).listar(isNull(), eq(COMPLEMENTARIA), isNull(), eq(true));
    }
}