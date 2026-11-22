package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.ACTUALIZACION_PLAZO_COORDINADOR;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.APERTURA_PLAZO_DOCENTE;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.CONFIGURACION_PLAZO_COORDINADOR;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.RECORDATORIO_CIERRE_COORDINADOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** HU-039: recordatorios del plazo de asignación de docentes a los coordinadores. Datos ficticios. */
@ExtendWith(MockitoExtension.class)
class RecordatorioCoordinadorServiceTest {

    /** 21/11/2026 10:00 en Bogotá. */
    private static final Instant AHORA = Instant.parse("2026-11-21T15:00:00Z");
    private static final Long PERIODO_ID = 7L;
    private static final String CUENTA = "coord.prueba@correounivalle.edu.co";

    @Mock
    private NotificacionService notificacionService;
    @Mock
    private PeriodoAcademicoRepository periodoRepository;
    @Mock
    private CoordinadorRepository coordinadorRepository;
    @Mock
    private CursoProgramacionRepository cursoRepository;

    private NotificacionesProperties propiedades;
    private RecordatorioCoordinadorService service;

    @BeforeEach
    void setUp() {
        propiedades = new NotificacionesProperties();
        propiedades.setAppUrl("https://cda.prueba.local");
        service = new RecordatorioCoordinadorService(notificacionService, periodoRepository, coordinadorRepository,
                cursoRepository, propiedades, JsonMapper.builder().build(), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static PeriodoAcademico periodo(Instant inicio, Instant fin) {
        return PeriodoAcademico.builder()
                .id(PERIODO_ID).nombre("2027-1 Prueba")
                .plazoDocenteInicio(AHORA.minus(Duration.ofDays(20))).plazoDocenteFin(AHORA.minus(Duration.ofDays(5)))
                .plazoCoordinadorInicio(inicio).plazoCoordinadorFin(fin)
                .fechaAdicionInicio(LocalDate.of(2027, 2, 1)).fechaAdicionFin(LocalDate.of(2027, 2, 10))
                .fechaCancelacionInicio(LocalDate.of(2027, 3, 1)).fechaCancelacionFin(LocalDate.of(2027, 3, 10))
                .vigente(true).estado("VIGENTE")
                .build();
    }

    private static Notificacion evento(Long id, TipoEventoNotificacion tipo) {
        return Notificacion.builder().id(id).periodoId(PERIODO_ID).tipoEvento(tipo).fechaProgramada(AHORA).build();
    }

    private static Coordinador coordinacion(int id, String correo, String codigo, String nombre, Jornada jornada) {
        Programa programa = new Programa();
        programa.setCodeProgram(codigo);
        programa.setNameProgram(nombre);
        programa.setJornada(jornada);
        Coordinador c = new Coordinador();
        c.setId(id);
        c.setCorreoInstitucional(correo);
        c.setPrograma(programa);
        c.setDocente(Docente.builder().id((long) id).nombres("Prueba").apellidos("Coord").build());
        c.setActivo(true);
        return c;
    }

    // ------------------------------------------------------------------ a) configuración

    @Nested
    @DisplayName("a) Configuración del plazo")
    class Configuracion {

        @Test
        @DisplayName("HU-002 c13: al crear el período con plazo de coordinadores, el aviso sale de inmediato y se programa el recordatorio")
        void alCrearAvisaYProgramaRecordatorio() {
            Instant inicio = Instant.parse("2026-12-01T13:00:00Z");
            Instant fin = Instant.parse("2026-12-15T22:00:00Z");

            service.programarAlCrear(periodo(inicio, fin));

            verify(notificacionService).programar(CONFIGURACION_PLAZO_COORDINADOR, PERIODO_ID, AHORA, null);
            verify(notificacionService).programar(RECORDATORIO_CIERRE_COORDINADOR, PERIODO_ID,
                    Instant.parse("2026-12-12T22:00:00Z"), null);
        }

        @Test
        @DisplayName("HU-002 c3: sin plazo de coordinadores no se programa nada")
        void sinPlazoNoProgramaNada() {
            service.programarAlCrear(periodo(null, null));
            verifyNoInteractions(notificacionService);
        }

        @Test
        @DisplayName("a) (borde): si el plazo ya cerró al guardarlo, no se avisa")
        void plazoCerradoNoAvisa() {
            service.programarAlCrear(periodo(AHORA.minus(Duration.ofDays(10)), AHORA));
            verifyNoInteractions(notificacionService);
        }

        @Test
        @DisplayName("a) al editar: si el plazo pasa de vacío a tener fechas, se avisa igual que al crear y no hay actualización")
        void vacioAConFechas() {
            Instant inicio = Instant.parse("2026-12-01T13:00:00Z");
            Instant fin = Instant.parse("2026-12-15T22:00:00Z");

            service.alCambiarPlazo(periodo(inicio, fin), null, null);

            verify(notificacionService).programar(CONFIGURACION_PLAZO_COORDINADOR, PERIODO_ID, AHORA, null);
            verify(notificacionService, never()).programar(eq(ACTUALIZACION_PLAZO_COORDINADOR), any(), any(), any());
        }

        @Test
        @DisplayName("a) y criterio 2: el correo trae el plazo en hora de Bogotá, las fechas de adición y cancelación y el aviso")
        void contenidoConfiguracion() {
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(
                    periodo(Instant.parse("2026-12-01T02:00:00Z"), Instant.parse("2026-12-15T22:00:00Z"))));

            CorreoContenido c = service.construir(evento(1L, CONFIGURACION_PLAZO_COORDINADOR),
                    new DestinatarioCorreo(1L, "coordinación de Desarrollo de Software (diurna)", CUENTA));

            assertEquals("Fechas para asignar docentes – 2027-1 Prueba", c.asunto());
            assertTrue(c.cuerpo().startsWith("Hola, coordinación de Desarrollo de Software (diurna):"));
            assertTrue(c.cuerpo().contains("Inicio del plazo: 30/11/2026 21:00"));
            assertTrue(c.cuerpo().contains("Cierre del plazo: 15/12/2026 17:00"));
            assertTrue(c.cuerpo().contains("Fechas de adición: 01/02/2027 – 10/02/2027"));
            assertTrue(c.cuerpo().contains("Fechas de cancelación: 01/03/2027 – 10/03/2027"));
            assertTrue(c.cuerpo().contains(RecordatorioCoordinadorService.AVISO_ADICION_CANCELACION));
            assertTrue(c.cuerpo().contains("Ingrese al sistema: https://cda.prueba.local"));
        }
    }

    // ------------------------------------------------------------------ b) actualización

    @Nested
    @DisplayName("b) Actualización de fechas")
    class Actualizacion {

        private final Instant inicioAnterior = Instant.parse("2026-12-01T13:00:00Z");
        private final Instant finAnterior = Instant.parse("2026-12-15T22:00:00Z");

        @Test
        @DisplayName("HU-003 c8: al cambiar el plazo se envía la actualización de inmediato y se reprograma el recordatorio")
        void cambioEnviaActualizacionYReprograma() {
            Instant nuevoFin = Instant.parse("2026-12-20T22:00:00Z");
            Notificacion recordatorio = evento(12L, RECORDATORIO_CIERRE_COORDINADOR);
            when(notificacionService.pendientes(PERIODO_ID, RECORDATORIO_CIERRE_COORDINADOR)).thenReturn(List.of(recordatorio));
            when(notificacionService.pendientes(PERIODO_ID, CONFIGURACION_PLAZO_COORDINADOR)).thenReturn(List.of());

            service.alCambiarPlazo(periodo(inicioAnterior, nuevoFin), inicioAnterior, finAnterior);

            verify(notificacionService).reprogramar(recordatorio, Instant.parse("2026-12-17T22:00:00Z"));
            ArgumentCaptor<String> datos = ArgumentCaptor.forClass(String.class);
            verify(notificacionService).programar(eq(ACTUALIZACION_PLAZO_COORDINADOR), eq(PERIODO_ID), eq(AHORA), datos.capture());
            assertTrue(datos.getValue().contains("2026-12-15T22:00:00Z"));
            assertTrue(datos.getValue().contains("2026-12-20T22:00:00Z"));
        }

        @Test
        @DisplayName("b) si no cambia el plazo de coordinadores, no se envía nada")
        void sinCambioNoHaceNada() {
            service.alCambiarPlazo(periodo(inicioAnterior, finAnterior), inicioAnterior, finAnterior);
            verifyNoInteractions(notificacionService);
        }

        @Test
        @DisplayName("b) si el aviso de configuración aún no salió, no se duplica con una actualización")
        void configuracionPendienteNoDuplica() {
            when(notificacionService.pendientes(PERIODO_ID, RECORDATORIO_CIERRE_COORDINADOR)).thenReturn(List.of());
            when(notificacionService.pendientes(PERIODO_ID, CONFIGURACION_PLAZO_COORDINADOR))
                    .thenReturn(List.of(evento(11L, CONFIGURACION_PLAZO_COORDINADOR)));

            service.alCambiarPlazo(periodo(inicioAnterior, finAnterior.plus(Duration.ofDays(2))), inicioAnterior, finAnterior);

            verify(notificacionService, never()).programar(eq(ACTUALIZACION_PLAZO_COORDINADOR), any(), any(), any());
        }

        @Test
        @DisplayName("Plazo quitado: se cancelan los pendientes y no se envía ningún correo")
        void plazoQuitado() {
            Notificacion recordatorio = evento(12L, RECORDATORIO_CIERRE_COORDINADOR);
            when(notificacionService.pendientes(PERIODO_ID, CONFIGURACION_PLAZO_COORDINADOR)).thenReturn(List.of());
            when(notificacionService.pendientes(PERIODO_ID, RECORDATORIO_CIERRE_COORDINADOR)).thenReturn(List.of(recordatorio));

            service.alCambiarPlazo(periodo(null, null), inicioAnterior, finAnterior);

            verify(notificacionService).cancelar(recordatorio, RecordatorioCoordinadorService.MOTIVO_PLAZO_SIN_FECHAS);
            verify(notificacionService, never()).programar(any(), any(), any(), any());
        }

        @Test
        @DisplayName("b) y criterio 2: el correo muestra las fechas anteriores y las nuevas, con adición y cancelación")
        void contenidoActualizacion() {
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(
                    periodo(inicioAnterior, Instant.parse("2026-12-20T22:00:00Z"))));
            Notificacion n = evento(13L, ACTUALIZACION_PLAZO_COORDINADOR);
            n.setDatos("{\"inicioAnterior\":\"2026-12-01T13:00:00Z\",\"finAnterior\":\"2026-12-15T22:00:00Z\","
                    + "\"inicio\":\"2026-12-01T13:00:00Z\",\"fin\":\"2026-12-20T22:00:00Z\"}");

            CorreoContenido c = service.construir(n, new DestinatarioCorreo(1L, "coordinación de Prueba", CUENTA));

            assertEquals("Cambio en las fechas para asignar docentes – 2027-1 Prueba", c.asunto());
            assertTrue(c.cuerpo().contains("Antes: 01/12/2026 08:00 – 15/12/2026 17:00"));
            assertTrue(c.cuerpo().contains("Ahora: 01/12/2026 08:00 – 20/12/2026 17:00"));
            assertTrue(c.cuerpo().contains("Fechas de adición: 01/02/2027 – 10/02/2027"));
            assertTrue(c.cuerpo().contains(RecordatorioCoordinadorService.AVISO_ADICION_CANCELACION));
        }
    }

    // ------------------------------------------------------------------ c) recordatorio

    @Nested
    @DisplayName("c) Recordatorio de cierre")
    class Recordatorio {

        @Test
        @DisplayName("c) (supuesto): si quedan menos de 3 días para el cierre, el recordatorio sale de inmediato")
        void menosDeTresDias() {
            service.programarAlCrear(periodo(AHORA.minus(Duration.ofDays(5)), AHORA.plus(Duration.ofDays(2))));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_COORDINADOR, PERIODO_ID, AHORA, null);
        }

        @Test
        @DisplayName("c) (borde): con un plazo corto el recordatorio no sale antes del inicio del plazo")
        void noAntesDelInicio() {
            Instant inicio = AHORA.plus(Duration.ofDays(5));
            service.programarAlCrear(periodo(inicio, inicio.plus(Duration.ofDays(2))));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_COORDINADOR, PERIODO_ID, inicio, null);
        }

        @Test
        @DisplayName("c) el recordatorio lleva la fecha de cierre y el criterio 2")
        void contenidoRecordatorio() {
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(
                    periodo(Instant.parse("2026-12-01T13:00:00Z"), Instant.parse("2026-12-11T02:00:00Z"))));

            CorreoContenido c = service.construir(evento(2L, RECORDATORIO_CIERRE_COORDINADOR),
                    new DestinatarioCorreo(1L, "coordinación de Prueba", CUENTA));

            assertEquals("Recordatorio: el plazo para asignar docentes cierra el 10/12/2026 21:00", c.asunto());
            assertTrue(c.cuerpo().contains("cierra el 10/12/2026 21:00 (hora de Colombia)"));
            assertTrue(c.cuerpo().contains("Fechas de cancelación: 01/03/2027 – 10/03/2027"));
            assertTrue(c.cuerpo().contains(RecordatorioCoordinadorService.AVISO_ADICION_CANCELACION));
        }

        @Test
        @DisplayName("c) (borde): un recordatorio atrasado no sale si el plazo ya cerró; la actualización sí")
        void atrasadoConPlazoCerrado() {
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(
                    periodo(AHORA.minus(Duration.ofDays(10)), AHORA.minusSeconds(1))));
            assertFalse(service.eventoVigente(evento(1L, RECORDATORIO_CIERRE_COORDINADOR)));
            assertTrue(service.eventoVigente(evento(2L, ACTUALIZACION_PLAZO_COORDINADOR)));
        }
    }

    // ------------------------------------------------------------------ criterio 3

    @Nested
    @DisplayName("Criterio 3: asignación completa")
    class AsignacionCompleta {

        private final Coordinador diurna = coordinacion(1, CUENTA, "2711", "Desarrollo de Software", Jornada.DIURNA);
        private final Coordinador nocturna = coordinacion(2, CUENTA.toUpperCase(), "2712", "Desarrollo de Software", Jornada.NOCTURNA);

        @Test
        @DisplayName("Criterio 3: si todas las materias de sus programas tienen docente, el recordatorio se omite")
        void todasAsignadas() {
            when(coordinadorRepository.findActivosParaNotificacion()).thenReturn(List.of(diurna, nocturna));
            when(cursoRepository.contarActivosPorProgramas(PERIODO_ID, List.of("2711", "2712"))).thenReturn(10L);
            when(cursoRepository.contarSinDocentePorProgramas(PERIODO_ID, List.of("2711", "2712"))).thenReturn(0L);

            assertEquals(Optional.of(RecordatorioCoordinadorService.MOTIVO_ASIGNACION_COMPLETA),
                    service.motivoOmision(evento(1L, RECORDATORIO_CIERRE_COORDINADOR), 1L));
        }

        @Test
        @DisplayName("Criterio 3: si queda alguna materia sin docente, el recordatorio se envía")
        void quedaPendiente() {
            when(coordinadorRepository.findActivosParaNotificacion()).thenReturn(List.of(diurna));
            when(cursoRepository.contarActivosPorProgramas(PERIODO_ID, List.of("2711"))).thenReturn(10L);
            when(cursoRepository.contarSinDocentePorProgramas(PERIODO_ID, List.of("2711"))).thenReturn(1L);

            assertTrue(service.motivoOmision(evento(1L, RECORDATORIO_CIERRE_COORDINADOR), 1L).isEmpty());
        }

        @Test
        @DisplayName("Criterio 3 (supuesto): si sus programas aún no tienen cursos generados, el recordatorio se envía")
        void sinCursosGenerados() {
            when(coordinadorRepository.findActivosParaNotificacion()).thenReturn(List.of(diurna));
            when(cursoRepository.contarActivosPorProgramas(PERIODO_ID, List.of("2711"))).thenReturn(0L);

            assertTrue(service.motivoOmision(evento(1L, RECORDATORIO_CIERRE_COORDINADOR), 1L).isEmpty());
            verify(cursoRepository, never()).contarSinDocentePorProgramas(any(), any());
        }

        @Test
        @DisplayName("Criterio 3: no aplica a los avisos de configuración ni de actualización")
        void noAplicaAOtrosEventos() {
            assertTrue(service.motivoOmision(evento(1L, CONFIGURACION_PLAZO_COORDINADOR), 1L).isEmpty());
            assertTrue(service.motivoOmision(evento(1L, ACTUALIZACION_PLAZO_COORDINADOR), 1L).isEmpty());
            verifyNoInteractions(cursoRepository);
        }
    }

    // ------------------------------------------------------------------ destinatarios

    @Test
    @DisplayName("Destinatario: una cuenta que coordina dos programas recibe un solo correo que los nombra")
    void unaCuentaVariosProgramas() {
        Coordinador diurna = coordinacion(1, CUENTA, "2711", "Desarrollo de Software", Jornada.DIURNA);
        Coordinador nocturna = coordinacion(2, " " + CUENTA.toUpperCase() + " ", "2712", "Desarrollo de Software", Jornada.NOCTURNA);
        Coordinador otra = coordinacion(3, "otra.prueba@correounivalle.edu.co", "3846", "Administración de Empresas", Jornada.AMBAS);
        when(coordinadorRepository.findActivosParaNotificacion()).thenReturn(List.of(diurna, nocturna, otra));

        assertEquals(List.of(1L, 3L), service.resolverDestinatarios(evento(1L, CONFIGURACION_PLAZO_COORDINADOR)));
        DestinatarioCorreo d = service.resolverDestinatario(1L).orElseThrow();
        assertEquals(CUENTA, d.correo());
        assertEquals("coordinación de Desarrollo de Software (diurna) y Desarrollo de Software (nocturna)", d.nombre());
        assertEquals(TipoDestinatario.COORDINADOR, service.tipoDestinatario());
    }

    @Test
    @DisplayName("HU-038 c5 / HU-030: una coordinación desactivada al enviar no se resuelve como destinatario")
    void coordinacionDesactivada() {
        when(coordinadorRepository.findActivosParaNotificacion()).thenReturn(List.of());
        assertTrue(service.resolverDestinatario(1L).isEmpty());
    }

    @Test
    @DisplayName("HU-038 c6: sin período vigente o sin plazo de coordinadores, el evento no se envía")
    void sinPeriodoOSinPlazo() {
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.empty());
        assertFalse(service.eventoVigente(evento(1L, CONFIGURACION_PLAZO_COORDINADOR)));

        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo(null, null)));
        assertFalse(service.eventoVigente(evento(1L, CONFIGURACION_PLAZO_COORDINADOR)));
    }

    @Test
    @DisplayName("Solo atiende los eventos de coordinador")
    void atiendeSoloCoordinador() {
        assertTrue(service.atiende(CONFIGURACION_PLAZO_COORDINADOR));
        assertTrue(service.atiende(RECORDATORIO_CIERRE_COORDINADOR));
        assertFalse(service.atiende(APERTURA_PLAZO_DOCENTE));
    }

    @Test
    @DisplayName("Copia: separa los omitidos por perfil desactivado y por asignación completa")
    void copiaConOmitidosPorMotivo() {
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(
                periodo(Instant.parse("2026-12-01T13:00:00Z"), Instant.parse("2026-12-15T22:00:00Z"))));

        CorreoContenido c = service.construirCopia(evento(1L, RECORDATORIO_CIERRE_COORDINADOR),
                List.of(new DestinatarioCorreo(1L, "coordinación de Prueba", CUENTA)),
                Map.of(NotificacionProcesador.MOTIVO_PERFIL_DESACTIVADO, 1L,
                        RecordatorioCoordinadorService.MOTIVO_ASIGNACION_COMPLETA, 2L));

        assertTrue(c.asunto().startsWith("[Copia] Recordatorio: el plazo para asignar docentes cierra el"));
        assertTrue(c.cuerpo().contains("Omitidos por perfil desactivado: 1"));
        assertTrue(c.cuerpo().contains("Omitidos por asignación completa: 2"));
        assertTrue(c.cuerpo().contains("- coordinación de Prueba <" + CUENTA + ">"));
        verify(notificacionService, never()).programar(any(), any(), any(), anyString());
    }
}
