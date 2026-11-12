package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.controller.ProgramacionController;
import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HU-047: revisar y ajustar la programación. Datos de prueba ficticios. */
@ExtendWith(MockitoExtension.class)
class ProgramacionAjusteServiceTest {

    private static final String ADMIN = "admin.prueba@correounivalle.edu.co";

    @Mock private CursoProgramacionRepository cursoRepository;
    @Mock private CohortePeriodoRepository cohorteRepository;
    @Mock private PeriodoAcademicoRepository periodoRepository;
    @Mock private ProgramaRepository programaRepository;
    @Mock private PensumAcademicoRepository pensumRepository;
    @Mock private DocenteAsignaturaService docenteAsignaturaService;
    @Mock private BitacoraService bitacoraService;

    @InjectMocks
    private ProgramacionService service;

    private PeriodoAcademico periodo;
    private Programa progA;
    private Programa progB;
    private Programa progC;
    private Asignatura calculo;

    @BeforeEach
    void setUp() {
        periodo = PeriodoAcademico.builder().id(7L).nombre("2026-2").vigente(true).estado("VIGENTE").build();
        progA = Programa.builder().codeProgram("9001").nameProgram("Programa Prueba A").estado(EstadoPrograma.ACTIVO).build();
        progB = Programa.builder().codeProgram("9002").nameProgram("Programa Prueba B").estado(EstadoPrograma.ACTIVO).build();
        progC = Programa.builder().codeProgram("9003").nameProgram("Programa Prueba C").estado(EstadoPrograma.ACTIVO).build();
        calculo = Asignatura.builder().codeCourse("MAT101").nameCourse("Cálculo I").credits(4).isElective(false).activo(true).build();

        Jwt jwt = Jwt.withTokenValue("token-de-prueba").header("alg", "HS256")
                .claim("email", ADMIN).claim("rol", 1).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private CursoProgramacion curso(long id, Programa programa, int semestre, Integer grupo) {
        return CursoProgramacion.builder()
                .id(id).periodo(periodo).programa(programa).asignatura(calculo)
                .semestre(semestre).jornada(Jornada.DIURNA).numeroResolucion("RES-PRUEBA")
                .nombreAsignatura("Cálculo I").grupo(grupo)
                .estado(CursoProgramacion.ESTADO_PROGRAMADO)
                .build();
    }

    private CursoProgramacion excluido(long id, Programa programa) {
        CursoProgramacion c = curso(id, programa, 2, null);
        c.setEstado(CursoProgramacion.ESTADO_EXCLUIDO);
        c.setJustificacionExclusion("Sin estudiantes");
        c.setExcluidoPor(ADMIN);
        return c;
    }

    /** Cálculo en 3 programas: A → 50, B → 51, C → 52 (ejemplo de la decisión 2026-11-06). */
    private List<CursoProgramacion> tresGrupos() {
        return new ArrayList<>(List.of(curso(1, progA, 1, 50), curso(2, progB, 2, 51), curso(3, progC, 2, 52)));
    }

    private void bloqueo(List<CursoProgramacion> cursos, long idObjetivo) {
        CursoProgramacion objetivo = cursos.stream().filter(c -> c.getId() == idObjetivo).findFirst().orElseThrow();
        when(cursoRepository.findByIdWithDetails(idObjetivo)).thenReturn(Optional.of(objetivo));
        when(cursoRepository.findByPeriodoAndAsignaturaForUpdate(7L, "MAT101")).thenReturn(cursos);
    }

    private static Integer grupoDespues(ProgramacionDto.AjusteGruposResponse r, long id) {
        return r.getCursos().stream().filter(g -> g.getId() == id).findFirst().orElseThrow().getGrupoDespues();
    }

    private static ProgramacionDto.JustificacionRequest justificacion(String texto) {
        return new ProgramacionDto.JustificacionRequest(texto);
    }

    // ------------------------------------------------------------------ Criterio 1

    @Test
    @DisplayName("Criterio 1: la consulta consolida los cursos con estado, grupo, código de curso y justificación")
    void criterio1_consultaConsolidada() {
        CursoProgramacion activo = curso(1, progA, 1, 50);
        CursoProgramacion fuera = excluido(3, progC);
        when(periodoRepository.findById(7L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.findByPeriodoIdWithDetails(7L)).thenReturn(List.of(activo, fuera));
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(7L)).thenReturn(List.of(
                CohortePeriodo.builder().periodo(periodo).programa(progA).semestre(1).jornada(Jornada.DIURNA)
                        .numeroResolucion("RES-PRUEBA").confirmada(true).build()));
        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre("9001", "RES-PRUEBA", 1))
                .thenReturn(List.of(PensumAcademico.builder().asignatura(calculo).semestre(1).isElective(false).build()));

        List<ProgramacionDto.CursoResponse> r = service.obtenerProgramacion(7L, null, "mat101", Jornada.DIURNA);

        assertEquals(2, r.size());
        assertEquals("MAT101-50", r.get(0).getCodigoCurso());
        assertEquals("PROGRAMADO", r.get(0).getEstado());
        assertFalse(r.get(0).isRequiereRevision());
        assertEquals("EXCLUIDO", r.get(1).getEstado());
        assertNull(r.get(1).getGrupo());
        assertNull(r.get(1).getCodigoCurso());
        assertEquals("Sin estudiantes", r.get(1).getJustificacionExclusion());
        assertEquals(ADMIN, r.get(1).getExcluidoPor());
    }

    // ------------------------------------------------------------------ Criterio 2

    @Test
    @DisplayName("Criterio 2: excluir el último grupo (52) no renumera a los demás")
    void criterio2_excluirUltimoGrupoSinRenumerar() {
        List<CursoProgramacion> cursos = tresGrupos();
        bloqueo(cursos, 3);

        ProgramacionDto.AjusteGruposResponse r = service.excluir(3L, justificacion("  Sin estudiantes de C  "));

        assertEquals(50, cursos.get(0).getGrupo());
        assertEquals(51, cursos.get(1).getGrupo());
        CursoProgramacion c = cursos.get(2);
        assertEquals("EXCLUIDO", c.getEstado());
        assertNull(c.getGrupo());
        assertEquals("Sin estudiantes de C", c.getJustificacionExclusion());
        assertEquals(ADMIN, c.getExcluidoPor());
        assertNotNull(c.getFechaExclusion());
        assertNull(grupoDespues(r, 3));
        verify(bitacoraService, times(1)).registrar(any(), any(), any(), any(), any(), any());
        verify(bitacoraService, never()).registrar(eq(AccionBitacora.EDITAR), any(), any(), any(), any(), any());
        verify(cursoRepository).saveAll(cursos);
    }

    @Test
    @DisplayName("Criterio 2: excluir un grupo intermedio (51) renumera el 52 como 51 conservando el orden")
    void criterio2_excluirGrupoIntermedioRenumera() {
        List<CursoProgramacion> cursos = tresGrupos();
        bloqueo(cursos, 2);

        ProgramacionDto.AjusteGruposResponse r = service.excluir(2L, justificacion("Sin estudiantes de B"));

        assertEquals(50, cursos.get(0).getGrupo());
        assertNull(cursos.get(1).getGrupo());
        assertEquals(51, cursos.get(2).getGrupo());
        assertEquals(52, r.getCursos().stream().filter(g -> g.getId() == 3).findFirst().orElseThrow().getGrupoAntes());
        assertEquals(51, grupoDespues(r, 3));
        assertTrue(r.getCursos().stream().filter(g -> g.getId() == 2).findFirst().orElseThrow().isExcluido());
    }

    @Test
    @DisplayName("Criterio 2: asignatura de un solo programa: se excluye sin afectar otros cursos")
    void criterio2_asignaturaDeUnSoloPrograma() {
        List<CursoProgramacion> cursos = new ArrayList<>(List.of(curso(1, progA, 1, 50)));
        bloqueo(cursos, 1);

        ProgramacionDto.AjusteGruposResponse r = service.excluir(1L, justificacion("Sin estudiantes"));

        assertEquals("EXCLUIDO", cursos.get(0).getEstado());
        assertNull(cursos.get(0).getGrupo());
        assertEquals(1, r.getCursos().size());
        verify(bitacoraService, times(1)).registrar(eq(AccionBitacora.DESACTIVAR), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Criterio 2: justificación vacía, en blanco o ausente se rechaza sin cambios")
    void criterio2_justificacionVaciaRechazada() {
        for (ProgramacionDto.JustificacionRequest req : Arrays.asList(
                justificacion(""), justificacion("   "), justificacion(null), null)) {
            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.excluir(2L, req));
            assertEquals(ProgramacionService.MSG_JUSTIFICACION_OBLIGATORIA, ex.getMessage());
        }
        verifyNoInteractions(cursoRepository, bitacoraService);
    }

    @Test
    @DisplayName("Criterio 2: justificación de más de 500 caracteres se rechaza")
    void criterio2_justificacionLargaRechazada() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.excluir(2L, justificacion("x".repeat(501))));
        assertEquals(ProgramacionService.MSG_JUSTIFICACION_LARGA, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 2: un curso ya excluido no se vuelve a excluir")
    void criterio2_yaExcluido() {
        List<CursoProgramacion> cursos = new ArrayList<>(List.of(curso(1, progA, 1, 50), excluido(2, progB)));
        bloqueo(cursos, 2);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.excluir(2L, justificacion("x")));
        assertEquals(ProgramacionService.MSG_YA_EXCLUIDO, ex.getMessage());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    @DisplayName("Criterio 2: curso inexistente responde «Curso no encontrado»")
    void criterio2_cursoNoEncontrado() {
        when(cursoRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.excluir(99L, justificacion("x")));
        assertEquals(ProgramacionService.MSG_CURSO_NO_ENCONTRADO, ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 2: la vista previa muestra antes → después y avisa si hay docente, sin guardar nada")
    void criterio2_vistaPreviaNoGuarda() {
        List<CursoProgramacion> cursos = tresGrupos();
        cursos.get(1).setDocente(Docente.builder().id(15L).nombres("Docente").apellidos("Prueba").build());
        when(cursoRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(cursos.get(1)));
        when(cursoRepository.findByPeriodoAndAsignatura(7L, "MAT101")).thenReturn(cursos);

        ProgramacionDto.AjusteGruposResponse r = service.vistaPreviaExclusion(2L);

        assertTrue(r.isTieneDocente());
        assertEquals(50, grupoDespues(r, 1));
        assertNull(grupoDespues(r, 2));
        assertEquals(51, grupoDespues(r, 3));
        // Nada cambió en las entidades ni se guardó
        assertEquals(51, cursos.get(1).getGrupo());
        assertEquals(52, cursos.get(2).getGrupo());
        verify(cursoRepository, never()).saveAll(any());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    @DisplayName("Criterio 2: al confirmar la exclusión de un curso con docente, se quita la asignación")
    void criterio2_excluirQuitaDocente() {
        List<CursoProgramacion> cursos = tresGrupos();
        cursos.get(2).setDocente(Docente.builder().id(15L).build());
        cursos.get(2).setEstado(CursoProgramacion.ESTADO_ASIGNADO);
        bloqueo(cursos, 3);

        service.excluir(3L, justificacion("Sin estudiantes"));

        assertNull(cursos.get(2).getDocente());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> anterior = ArgumentCaptor.forClass(Map.class);
        verify(bitacoraService).registrar(eq(AccionBitacora.DESACTIVAR), eq("CURSO"), eq("3"), eq(7L),
                anterior.capture(), any());
        assertEquals(15L, anterior.getValue().get("docenteId"));
        assertEquals("ASIGNADO", anterior.getValue().get("estado"));
    }

    @Test
    @DisplayName("Criterio 2: reactivar un curso excluido le da el siguiente número de la serie sin mover a los demás")
    void criterio2_reactivarTomaSiguienteGrupo() {
        List<CursoProgramacion> cursos = new ArrayList<>(List.of(
                curso(1, progA, 1, 50), excluido(2, progB), curso(3, progC, 2, 51)));
        bloqueo(cursos, 2);

        ProgramacionDto.AjusteGruposResponse r = service.reactivar(2L, justificacion("Se confirmaron estudiantes"));

        CursoProgramacion c = cursos.get(1);
        assertEquals("PROGRAMADO", c.getEstado());
        assertEquals(52, c.getGrupo());
        assertNull(c.getJustificacionExclusion());
        assertNull(c.getExcluidoPor());
        assertNull(c.getFechaExclusion());
        assertEquals(50, cursos.get(0).getGrupo());
        assertEquals(51, cursos.get(2).getGrupo());
        assertEquals(52, grupoDespues(r, 2));
        verify(bitacoraService).registrar(eq(AccionBitacora.REACTIVAR), eq("CURSO"), eq("2"), eq(7L), any(), any());
    }

    @Test
    @DisplayName("Criterio 2: reactivar cuando no quedan cursos activos toma el grupo 50")
    void criterio2_reactivarSinActivos() {
        List<CursoProgramacion> cursos = new ArrayList<>(List.of(excluido(1, progA)));
        bloqueo(cursos, 1);

        service.reactivar(1L, justificacion("Se confirmaron estudiantes"));

        assertEquals(50, cursos.get(0).getGrupo());
    }

    @Test
    @DisplayName("Criterio 2: no se puede reactivar un curso que no está excluido")
    void criterio2_reactivarNoExcluido() {
        List<CursoProgramacion> cursos = tresGrupos();
        bloqueo(cursos, 1);
        ConflictException ex = assertThrows(ConflictException.class, () -> service.reactivar(1L, justificacion("x")));
        assertEquals(ProgramacionService.MSG_NO_EXCLUIDO, ex.getMessage());
    }

    // ------------------------------------------------------------------ Criterio 3

    @Test
    @DisplayName("Criterio 3: la API no tiene forma de agregar cursos ni asignaturas; solo se generan desde la malla")
    void criterio3_noSePuedenAgregarCursos() {
        List<String> posts = Arrays.stream(ProgramacionController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class))
                .map(Method::getName)
                .toList();
        // asignarDocentePorMateria (HU de asignación) crea el curso al designar un docente
        assertEquals(java.util.Set.of("generarProgramacion", "asignarDocentePorMateria"),
                new java.util.HashSet<>(posts));
        // El único cuerpo que reciben los ajustes es la justificación
        assertEquals(1, ProgramacionDto.JustificacionRequest.class.getRecordComponents().length);
    }

    // ------------------------------------------------------------------ Criterio 4

    @Test
    @DisplayName("Criterio 4: se marcan «Requiere revisión» los cursos fuera de malla, sin cohorte o con asignatura inactiva")
    void criterio4_requiereRevision() {
        Asignatura fisica = Asignatura.builder().codeCourse("FIS101").nameCourse("Física").activo(true).build();
        Asignatura quimica = Asignatura.builder().codeCourse("QUI101").nameCourse("Química").activo(false).build();

        CursoProgramacion enMalla = curso(1, progA, 1, 50);
        CursoProgramacion fueraDeMalla = curso(2, progA, 1, 50);
        fueraDeMalla.setAsignatura(fisica);
        CursoProgramacion sinCohorte = curso(3, progB, 3, 51);
        CursoProgramacion inactiva = curso(4, progA, 1, 50);
        inactiva.setAsignatura(quimica);
        CursoProgramacion yaExcluido = excluido(5, progC);

        when(periodoRepository.findById(7L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.findByPeriodoIdWithDetails(7L))
                .thenReturn(List.of(enMalla, fueraDeMalla, sinCohorte, inactiva, yaExcluido));
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(7L)).thenReturn(List.of(
                CohortePeriodo.builder().periodo(periodo).programa(progA).semestre(1).jornada(Jornada.DIURNA)
                        .numeroResolucion("RES-PRUEBA").confirmada(true).build()));
        when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre("9001", "RES-PRUEBA", 1))
                .thenReturn(List.of(
                        PensumAcademico.builder().asignatura(calculo).semestre(1).isElective(false).build(),
                        PensumAcademico.builder().asignatura(quimica).semestre(1).isElective(false).build()));

        Map<Long, ProgramacionDto.CursoResponse> r = new HashMap<>();
        service.obtenerProgramacion(7L, null, null, null).forEach(c -> r.put(c.getId(), c));

        assertFalse(r.get(1L).isRequiereRevision());
        assertEquals(ProgramacionService.REVISION_FUERA_DE_MALLA, r.get(2L).getMotivoRevision());
        assertEquals(ProgramacionService.REVISION_SIN_COHORTE, r.get(3L).getMotivoRevision());
        assertEquals(ProgramacionService.REVISION_ASIGNATURA_INACTIVA, r.get(4L).getMotivoRevision());
        assertTrue(r.get(4L).isRequiereRevision());
        assertFalse(r.get(5L).isRequiereRevision());
    }

    // ------------------------------------------------------------------ Criterio 5

    @Test
    @DisplayName("Criterio 5: la exclusión y la renumeración quedan en la bitácora con valor anterior y nuevo")
    void criterio5_bitacoraConValores() {
        List<CursoProgramacion> cursos = tresGrupos();
        bloqueo(cursos, 1);

        service.excluir(1L, justificacion("Sin estudiantes de A"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> anterior = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> nuevo = ArgumentCaptor.forClass(Map.class);

        verify(bitacoraService).registrar(eq(AccionBitacora.DESACTIVAR), eq("CURSO"), eq("1"), eq(7L),
                anterior.capture(), nuevo.capture());
        assertEquals("MAT101-50", anterior.getValue().get("codigoCurso"));
        assertEquals("EXCLUIDO", nuevo.getValue().get("estado"));
        assertEquals("Sin estudiantes de A", nuevo.getValue().get("justificacion"));

        verify(bitacoraService).registrar(eq(AccionBitacora.EDITAR), eq("CURSO"), eq("2"), eq(7L),
                anterior.capture(), nuevo.capture());
        assertEquals(51, anterior.getValue().get("grupo"));
        assertEquals(50, nuevo.getValue().get("grupo"));
        assertEquals("MAT101-50", nuevo.getValue().get("codigoCurso"));

        verify(bitacoraService).registrar(eq(AccionBitacora.EDITAR), eq("CURSO"), eq("3"), eq(7L),
                anterior.capture(), nuevo.capture());
        assertEquals(52, anterior.getValue().get("grupo"));
        assertEquals(51, nuevo.getValue().get("grupo"));
    }

    @Test
    @DisplayName("Criterio 5: si la bitácora falla, la exclusión no se guarda (misma transacción)")
    void criterio5_falloBitacoraNoGuarda() throws NoSuchMethodException {
        assertNotNull(ProgramacionService.class.getMethod("excluir", Long.class,
                ProgramacionDto.JustificacionRequest.class).getAnnotation(Transactional.class));
        List<CursoProgramacion> cursos = tresGrupos();
        bloqueo(cursos, 2);
        when(bitacoraService.registrar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class, () -> service.excluir(2L, justificacion("Sin estudiantes")));
        verify(cursoRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------------ Regeneración (HU-045 c6, HU-046 c4)

    private void prepararRegeneracion(List<CursoProgramacion> existentes, List<Programa> programas) {
        when(periodoRepository.findById(7L)).thenReturn(Optional.of(periodo));
        when(cursoRepository.countByPeriodoId(7L)).thenReturn((long) existentes.size());
        when(cursoRepository.findByPeriodoIdWithDetails(7L)).thenReturn(existentes);
        when(programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)).thenReturn(programas);
        List<CohortePeriodo> cohortes = new ArrayList<>();
        for (Programa p : programas) {
            cohortes.add(CohortePeriodo.builder().periodo(periodo).programa(p).semestre(1).jornada(Jornada.DIURNA)
                    .numeroResolucion("RES-PRUEBA").confirmada(true).build());
            when(pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre(p.getCodeProgram(), "RES-PRUEBA", 1))
                    .thenReturn(List.of(PensumAcademico.builder().asignatura(calculo).semestre(1)
                            .isElective(false).nameCourseSnapshot("Cálculo I").build()));
        }
        when(cohorteRepository.findByPeriodoIdAndConfirmadaTrue(7L)).thenReturn(cohortes);
        when(cursoRepository.contarCursosPorPrograma(7L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Regeneración: los cursos excluidos siguen excluidos con su justificación y la numeración los salta")
    void regeneracion_conservaExclusiones() {
        CursoProgramacion a = curso(1, progA, 1, 50);
        CursoProgramacion b = excluido(2, progB);
        CursoProgramacion c = curso(3, progC, 1, 51);
        prepararRegeneracion(new ArrayList<>(List.of(a, b, c)), List.of(progA, progB, progC));

        service.generarProgramacion(7L, true);

        assertEquals("EXCLUIDO", b.getEstado());
        assertEquals("Sin estudiantes", b.getJustificacionExclusion());
        assertNull(b.getGrupo());
        assertEquals(50, a.getGrupo());
        assertEquals(51, c.getGrupo());
        verify(cursoRepository, never()).deleteAll(any());
        verify(cursoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Regeneración: un programa nuevo se agrega al final de la serie sin mover los grupos existentes")
    void regeneracion_nuevoAlFinal() {
        Programa progD = Programa.builder().codeProgram("9000").nameProgram("Programa Prueba D").estado(EstadoPrograma.ACTIVO).build();
        CursoProgramacion a = curso(1, progA, 1, 50);
        CursoProgramacion b = curso(2, progB, 1, 51);
        prepararRegeneracion(new ArrayList<>(List.of(a, b)), List.of(progD, progA, progB));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CursoProgramacion>> captor = ArgumentCaptor.forClass(List.class);
        service.generarProgramacion(7L, true);
        verify(cursoRepository).saveAll(captor.capture());

        assertEquals(50, a.getGrupo());
        assertEquals(51, b.getGrupo());
        CursoProgramacion nuevo = captor.getValue().stream()
                .filter(x -> x.getPrograma().getCodeProgram().equals("9000")).findFirst().orElseThrow();
        assertNull(nuevo.getId());
        assertEquals(52, nuevo.getGrupo()); // aunque su código (9000) sea menor, va al final
    }

    @Test
    @DisplayName("Regeneración: un curso que ya no está en la oferta no se borra y conserva su grupo")
    void regeneracion_cursoFueraDeOfertaNoSeBorra() {
        CursoProgramacion a = curso(1, progA, 1, 50);
        CursoProgramacion c = curso(3, progC, 1, 51); // C ya no tiene cohorte con Cálculo
        prepararRegeneracion(new ArrayList<>(List.of(a, c)), List.of(progA));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CursoProgramacion>> captor = ArgumentCaptor.forClass(List.class);
        service.generarProgramacion(7L, true);
        verify(cursoRepository).saveAll(captor.capture());

        assertTrue(captor.getValue().contains(c));
        assertEquals("PROGRAMADO", c.getEstado());
        assertEquals(51, c.getGrupo());
    }
}
