package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.DocenteRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.ACTUALIZACION_PLAZO_DOCENTE;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.APERTURA_PLAZO_DOCENTE;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.RECORDATORIO_CIERRE_DOCENTE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** HU-038: programación y textos de los recordatorios al docente. Datos de prueba ficticios. */
@ExtendWith(MockitoExtension.class)
class RecordatorioDocenteServiceTest {

    /** 21/11/2026 10:00 en Bogotá. */
    private static final Instant AHORA = Instant.parse("2026-11-21T15:00:00Z");
    private static final Long PERIODO_ID = 7L;

    @Mock
    private NotificacionService notificacionService;
    @Mock
    private PeriodoAcademicoRepository periodoRepository;
    @Mock
    private DocenteRepository docenteRepository;

    private NotificacionesProperties propiedades;
    private RecordatorioDocenteService service;

    @BeforeEach
    void setUp() {
        propiedades = new NotificacionesProperties();
        propiedades.setAppUrl("https://cda.prueba.local");
        service = new RecordatorioDocenteService(notificacionService, periodoRepository, docenteRepository,
                propiedades, JsonMapper.builder().build(), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static PeriodoAcademico periodo(Instant inicio, Instant fin) {
        return PeriodoAcademico.builder()
                .id(PERIODO_ID).nombre("2027-1 Prueba")
                .plazoDocenteInicio(inicio).plazoDocenteFin(fin)
                .vigente(true).estado("VIGENTE")
                .build();
    }

    private static Notificacion evento(Long id, Instant fecha, co.edu.univalle.palmira.model.enums.TipoEventoNotificacion tipo) {
        return Notificacion.builder().id(id).periodoId(PERIODO_ID).tipoEvento(tipo).fechaProgramada(fecha).build();
    }

    // ------------------------------------------------------------------ criterio 1

    @Nested
    @DisplayName("Criterio 1: aviso de apertura")
    class Apertura {

        @Test
        @DisplayName("Criterio 1: dado un plazo futuro, la apertura se programa exactamente en plazo_docente_inicio")
        void aperturaEnElInicioDelPlazo() {
            Instant inicio = Instant.parse("2026-12-01T13:00:00Z");
            service.programarAlCrear(periodo(inicio, inicio.plus(Duration.ofDays(14))));
            verify(notificacionService).programar(APERTURA_PLAZO_DOCENTE, PERIODO_ID, inicio, null);
        }

        @Test
        @DisplayName("Criterio 1 (borde): si el plazo abre en este mismo instante, la apertura sale ahora")
        void bordeInicioIgualAhora() {
            service.programarAlCrear(periodo(AHORA, AHORA.plus(Duration.ofDays(14))));
            verify(notificacionService).programar(APERTURA_PLAZO_DOCENTE, PERIODO_ID, AHORA, null);
        }

        @Test
        @DisplayName("Criterio 1 (borde): si el plazo ya abrió y sigue abierto, la apertura sale de inmediato")
        void plazoYaAbierto() {
            service.programarAlCrear(periodo(AHORA.minusSeconds(3600), AHORA.plus(Duration.ofDays(14))));
            verify(notificacionService).programar(APERTURA_PLAZO_DOCENTE, PERIODO_ID, AHORA, null);
        }

        @Test
        @DisplayName("Criterio 1: el correo lleva período, inicio y fin en hora de Bogotá y el enlace al sistema")
        void contenidoApertura() {
            // 01/12/2026 02:00 UTC es 30/11/2026 21:00 en Bogotá.
            PeriodoAcademico p = periodo(Instant.parse("2026-12-01T02:00:00Z"), Instant.parse("2026-12-15T04:59:00Z"));
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(p));

            CorreoContenido c = service.construir(evento(1L, AHORA, APERTURA_PLAZO_DOCENTE),
                    new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co"));

            assertEquals("Plazo abierto para registrar su disponibilidad – 2027-1 Prueba", c.asunto());
            assertTrue(c.cuerpo().startsWith("Hola, Ana Prueba:"));
            assertTrue(c.cuerpo().contains("Inicio del plazo: 30/11/2026 21:00"));
            assertTrue(c.cuerpo().contains("Cierre del plazo: 14/12/2026 23:59"));
            assertTrue(c.cuerpo().contains("(hora de Colombia)"));
            assertTrue(c.cuerpo().contains("Ingrese al sistema: https://cda.prueba.local"));
        }
    }

    // ------------------------------------------------------------------ criterio 2

    @Nested
    @DisplayName("Criterio 2: recordatorio de cierre")
    class Recordatorio {

        @Test
        @DisplayName("Criterio 2: dado un plazo largo, el recordatorio sale 3 días antes del cierre a la misma hora")
        void tresDiasAntesALaMismaHora() {
            Instant fin = Instant.parse("2026-12-10T22:30:00Z");
            service.programarAlCrear(periodo(Instant.parse("2026-12-01T13:00:00Z"), fin));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_DOCENTE, PERIODO_ID,
                    Instant.parse("2026-12-07T22:30:00Z"), null);
        }

        @Test
        @DisplayName("Criterio 2: la antelación es configurable (RECORDATORIO_DIAS_ANTES)")
        void antelacionConfigurable() {
            propiedades.setRecordatorioDiasAntes(5);
            Instant fin = Instant.parse("2026-12-20T22:30:00Z");
            service.programarAlCrear(periodo(Instant.parse("2026-12-01T13:00:00Z"), fin));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_DOCENTE, PERIODO_ID,
                    Instant.parse("2026-12-15T22:30:00Z"), null);
        }

        @Test
        @DisplayName("Criterio 2 (supuesto): si quedan menos de 3 días para el cierre, el recordatorio sale de inmediato")
        void menosDeTresDiasInmediato() {
            service.programarAlCrear(periodo(AHORA.minus(Duration.ofDays(10)), AHORA.plus(Duration.ofDays(2))));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_DOCENTE, PERIODO_ID, AHORA, null);
        }

        @Test
        @DisplayName("Criterio 2 (borde): con el cierre exactamente en 3 días, el recordatorio sale ahora")
        void bordeCierreEnTresDiasExactos() {
            service.programarAlCrear(periodo(AHORA.minus(Duration.ofDays(10)), AHORA.plus(Duration.ofDays(3))));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_DOCENTE, PERIODO_ID, AHORA, null);
        }

        @Test
        @DisplayName("Criterio 2 (supuesto): con un plazo de menos de 3 días, el recordatorio sale con la apertura y no antes")
        void plazoCortoNoAntesDeLaApertura() {
            Instant inicio = AHORA.plus(Duration.ofDays(5));
            service.programarAlCrear(periodo(inicio, inicio.plus(Duration.ofDays(2))));
            verify(notificacionService).programar(RECORDATORIO_CIERRE_DOCENTE, PERIODO_ID, inicio, null);
        }

        @Test
        @DisplayName("Criterio 2: el recordatorio lleva la fecha de cierre en hora de Bogotá")
        void contenidoRecordatorio() {
            PeriodoAcademico p = periodo(Instant.parse("2026-12-01T13:00:00Z"), Instant.parse("2026-12-11T02:00:00Z"));
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(p));

            CorreoContenido c = service.construir(evento(2L, AHORA, RECORDATORIO_CIERRE_DOCENTE),
                    new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co"));

            assertEquals("Recordatorio: el plazo para registrar su disponibilidad cierra el 10/12/2026 21:00", c.asunto());
            assertTrue(c.cuerpo().contains("cierra el 10/12/2026 21:00 (hora de Colombia)"));
        }
    }

    // ------------------------------------------------------------------ criterio 3

    @Nested
    @DisplayName("Criterio 3: cambio de fechas")
    class CambioDeFechas {

        private final Instant inicioAnterior = Instant.parse("2026-12-01T13:00:00Z");
        private final Instant finAnterior = Instant.parse("2026-12-15T22:00:00Z");

        @Test
        @DisplayName("Criterio 3: dado un cambio de plazo, reprograma apertura y recordatorio y envía la actualización")
        void reprogramaYEnviaActualizacion() {
            Instant nuevoInicio = Instant.parse("2026-12-03T13:00:00Z");
            Instant nuevoFin = Instant.parse("2026-12-20T22:00:00Z");
            Notificacion apertura = evento(11L, inicioAnterior, APERTURA_PLAZO_DOCENTE);
            Notificacion recordatorio = evento(12L, Instant.parse("2026-12-12T22:00:00Z"), RECORDATORIO_CIERRE_DOCENTE);
            when(notificacionService.pendientes(PERIODO_ID, APERTURA_PLAZO_DOCENTE)).thenReturn(List.of(apertura));
            when(notificacionService.pendientes(PERIODO_ID, RECORDATORIO_CIERRE_DOCENTE)).thenReturn(List.of(recordatorio));

            service.alCambiarPlazo(periodo(nuevoInicio, nuevoFin), inicioAnterior, finAnterior);

            verify(notificacionService).reprogramar(apertura, nuevoInicio);
            verify(notificacionService).reprogramar(recordatorio, Instant.parse("2026-12-17T22:00:00Z"));
            ArgumentCaptor<String> datos = ArgumentCaptor.forClass(String.class);
            verify(notificacionService).programar(eq(ACTUALIZACION_PLAZO_DOCENTE), eq(PERIODO_ID), eq(AHORA), datos.capture());
            assertTrue(datos.getValue().contains("2026-12-01T13:00:00Z"));
            assertTrue(datos.getValue().contains("2026-12-20T22:00:00Z"));
        }

        @Test
        @DisplayName("Criterio 3: si no cambian las fechas del plazo docente, no se envía nada")
        void sinCambioNoHaceNada() {
            service.alCambiarPlazo(periodo(inicioAnterior, finAnterior), inicioAnterior, finAnterior);
            verifyNoInteractions(notificacionService);
        }

        @Test
        @DisplayName("Criterio 3: si la apertura ya se envió, no se repite; la actualización la reemplaza")
        void aperturaYaEnviadaNoSeRepite() {
            when(notificacionService.pendientes(eq(PERIODO_ID), any())).thenReturn(List.of());
            when(notificacionService.yaProcesado(PERIODO_ID, APERTURA_PLAZO_DOCENTE)).thenReturn(true);
            Instant nuevoInicioFuturo = Instant.parse("2026-12-05T13:00:00Z");

            service.alCambiarPlazo(periodo(nuevoInicioFuturo, finAnterior), inicioAnterior, finAnterior);

            verify(notificacionService, never()).programar(eq(APERTURA_PLAZO_DOCENTE), any(), any(), any());
            verify(notificacionService).programar(eq(ACTUALIZACION_PLAZO_DOCENTE), eq(PERIODO_ID), eq(AHORA), anyString());
        }

        @Test
        @DisplayName("Criterio 3: si al editar el plazo ya está abierto, no sale la apertura; basta la actualización")
        void plazoYaAbiertoSoloActualizacion() {
            when(notificacionService.pendientes(eq(PERIODO_ID), any())).thenReturn(List.of());

            service.alCambiarPlazo(periodo(AHORA.minus(Duration.ofDays(1)), finAnterior), inicioAnterior, finAnterior);

            verify(notificacionService, never()).programar(eq(APERTURA_PLAZO_DOCENTE), any(), any(), any());
            verify(notificacionService, never()).yaProcesado(any(), any());
            verify(notificacionService).programar(eq(ACTUALIZACION_PLAZO_DOCENTE), eq(PERIODO_ID), eq(AHORA), anyString());
        }

        @Test
        @DisplayName("Criterio 3: una apertura pendiente se cancela si la edición deja el plazo ya abierto")
        void aperturaPendienteReemplazadaPorActualizacion() {
            Notificacion apertura = evento(11L, inicioAnterior, APERTURA_PLAZO_DOCENTE);
            when(notificacionService.pendientes(PERIODO_ID, APERTURA_PLAZO_DOCENTE)).thenReturn(List.of(apertura));
            when(notificacionService.pendientes(PERIODO_ID, RECORDATORIO_CIERRE_DOCENTE)).thenReturn(List.of());

            service.alCambiarPlazo(periodo(AHORA.minusSeconds(60), finAnterior), inicioAnterior, finAnterior);

            verify(notificacionService).cancelar(apertura, RecordatorioDocenteService.MOTIVO_REEMPLAZADA_POR_ACTUALIZACION);
            verify(notificacionService, never()).reprogramar(eq(apertura), any());
            verify(notificacionService).programar(eq(ACTUALIZACION_PLAZO_DOCENTE), eq(PERIODO_ID), eq(AHORA), anyString());
        }

        @Test
        @DisplayName("Criterio 3: si el recordatorio ya salió y el cierre se amplía, se programa uno nuevo")
        void recordatorioYaEnviadoNuevoCierre() {
            Instant nuevoFin = Instant.parse("2026-12-30T22:00:00Z");
            when(notificacionService.pendientes(eq(PERIODO_ID), any())).thenReturn(List.of());
            when(notificacionService.yaProcesado(PERIODO_ID, APERTURA_PLAZO_DOCENTE)).thenReturn(true);

            service.alCambiarPlazo(periodo(inicioAnterior, nuevoFin), inicioAnterior, finAnterior);

            verify(notificacionService).programar(RECORDATORIO_CIERRE_DOCENTE, PERIODO_ID,
                    Instant.parse("2026-12-27T22:00:00Z"), null);
        }

        @Test
        @DisplayName("Criterio 3: si la edición cierra el plazo, se cancelan la apertura y el recordatorio pendientes")
        void edicionCierraElPlazo() {
            Notificacion apertura = evento(11L, inicioAnterior, APERTURA_PLAZO_DOCENTE);
            Notificacion recordatorio = evento(12L, Instant.parse("2026-12-12T22:00:00Z"), RECORDATORIO_CIERRE_DOCENTE);
            when(notificacionService.pendientes(PERIODO_ID, APERTURA_PLAZO_DOCENTE)).thenReturn(List.of(apertura));
            when(notificacionService.pendientes(PERIODO_ID, RECORDATORIO_CIERRE_DOCENTE)).thenReturn(List.of(recordatorio));

            service.alCambiarPlazo(periodo(AHORA.minus(Duration.ofDays(5)), AHORA.minusSeconds(60)),
                    inicioAnterior, finAnterior);

            verify(notificacionService).cancelar(apertura, RecordatorioDocenteService.MOTIVO_PLAZO_CERRADO);
            verify(notificacionService).cancelar(recordatorio, RecordatorioDocenteService.MOTIVO_PLAZO_CERRADO);
        }

        @Test
        @DisplayName("Criterio 3: el correo de actualización muestra las fechas anteriores y las nuevas")
        void contenidoActualizacion() {
            PeriodoAcademico p = periodo(Instant.parse("2026-12-03T13:00:00Z"), Instant.parse("2026-12-20T22:00:00Z"));
            when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(p));
            Notificacion n = evento(13L, AHORA, ACTUALIZACION_PLAZO_DOCENTE);
            n.setDatos("{\"inicioAnterior\":\"2026-12-01T13:00:00Z\",\"finAnterior\":\"2026-12-15T22:00:00Z\","
                    + "\"inicio\":\"2026-12-03T13:00:00Z\",\"fin\":\"2026-12-20T22:00:00Z\"}");

            CorreoContenido c = service.construir(n, new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co"));

            assertEquals("Cambio en las fechas para registrar su disponibilidad – 2027-1 Prueba", c.asunto());
            assertTrue(c.cuerpo().contains("Antes: 01/12/2026 08:00 – 15/12/2026 17:00"));
            assertTrue(c.cuerpo().contains("Ahora: 03/12/2026 08:00 – 20/12/2026 17:00"));
        }
    }

    // ------------------------------------------------------------------ criterios 4 a 7

    @Test
    @DisplayName("Criterio 4: los destinatarios son todos los docentes activos, sin filtrar por disponibilidad registrada")
    void criterio4TodosLosDocentesActivos() {
        when(docenteRepository.findIdsDestinatariosNotificacion()).thenReturn(List.of(1L, 2L, 3L));
        assertEquals(List.of(1L, 2L, 3L), service.resolverDestinatarios(evento(1L, AHORA, APERTURA_PLAZO_DOCENTE)));
        verify(docenteRepository).findIdsDestinatariosNotificacion();
        verifyNoMoreInteractions(docenteRepository);
    }

    @Test
    @DisplayName("Criterio 5: un perfil desactivado al momento de enviar no se resuelve como destinatario")
    void criterio5PerfilDesactivado() {
        when(docenteRepository.findDestinatarioNotificacion(3L)).thenReturn(Optional.empty());
        assertTrue(service.resolverDestinatario(3L).isEmpty());
    }

    @Test
    @DisplayName("HU-015 criterio 9: el correo se toma del perfil al momento de enviar")
    void hu015CorreoVigenteAlEnviar() {
        Docente d = Docente.builder().id(3L).nombres("Ana").apellidos("Prueba")
                .correoInstitucional("ana.nueva@correounivalle.edu.co").build();
        when(docenteRepository.findDestinatarioNotificacion(3L)).thenReturn(Optional.of(d));

        DestinatarioCorreo r = service.resolverDestinatario(3L).orElseThrow();

        assertEquals("ana.nueva@correounivalle.edu.co", r.correo());
        assertEquals("Ana Prueba", r.nombre());
    }

    @Test
    @DisplayName("Criterio 6: si el período ya no está vigente, el evento no se envía")
    void criterio6PeriodoNoVigente() {
        PeriodoAcademico historial = periodo(AHORA, AHORA.plus(Duration.ofDays(10)));
        historial.setVigente(false);
        historial.setEstado("HISTORIAL");
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(historial));
        assertFalse(service.eventoVigente(evento(1L, AHORA, APERTURA_PLAZO_DOCENTE)));
    }

    @Test
    @DisplayName("Criterio 6: si no hay período, el evento no se envía")
    void criterio6SinPeriodo() {
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.empty());
        assertFalse(service.eventoVigente(evento(1L, AHORA, RECORDATORIO_CIERRE_DOCENTE)));
    }

    @Test
    @DisplayName("Criterio 6 (borde): con el plazo ya cerrado no se programa nada al crear")
    void criterio6PlazoCerradoAlCrear() {
        service.programarAlCrear(periodo(AHORA.minus(Duration.ofDays(10)), AHORA));
        verifyNoInteractions(notificacionService);
    }

    @Test
    @DisplayName("Criterio 6 (borde): una apertura atrasada no sale si el plazo ya cerró; la actualización sí")
    void criterio6AperturaAtrasada() {
        PeriodoAcademico cerrado = periodo(AHORA.minus(Duration.ofDays(10)), AHORA.minusSeconds(1));
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(cerrado));
        assertFalse(service.eventoVigente(evento(1L, AHORA, APERTURA_PLAZO_DOCENTE)));
        assertTrue(service.eventoVigente(evento(2L, AHORA, ACTUALIZACION_PLAZO_DOCENTE)));
    }

    @Test
    @DisplayName("Criterio 7: al pasar a historial se cancelan los recordatorios pendientes del período")
    void criterio7CancelaPendientes() {
        when(notificacionService.cancelarPendientesDelPeriodo(PERIODO_ID,
                RecordatorioDocenteService.MOTIVO_PERIODO_HISTORIAL)).thenReturn(2);
        assertEquals(2, service.cancelarPendientes(PERIODO_ID, RecordatorioDocenteService.MOTIVO_PERIODO_HISTORIAL));
    }

    @Test
    @DisplayName("Copia: lleva el resumen del evento y la lista de destinatarios con su correo")
    void copiaConResumenYLista() {
        PeriodoAcademico p = periodo(Instant.parse("2026-12-01T13:00:00Z"), Instant.parse("2026-12-15T22:00:00Z"));
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(p));

        CorreoContenido c = service.construirCopia(evento(1L, AHORA, APERTURA_PLAZO_DOCENTE), List.of(
                new DestinatarioCorreo(1L, "Ana Prueba", "ana.prueba@correounivalle.edu.co"),
                new DestinatarioCorreo(2L, "Luis Ficticio", "luis.ficticio@correounivalle.edu.co")), 1);

        assertEquals("[Copia] Plazo abierto para registrar su disponibilidad – 2027-1 Prueba", c.asunto());
        assertTrue(c.cuerpo().contains("Destinatarios: 2"));
        assertTrue(c.cuerpo().contains("Omitidos por perfil desactivado: 1"));
        assertTrue(c.cuerpo().contains("- Ana Prueba <ana.prueba@correounivalle.edu.co>"));
        assertTrue(c.cuerpo().contains("- Luis Ficticio <luis.ficticio@correounivalle.edu.co>"));
        assertTrue(c.cuerpo().contains("Enviado: 21/11/2026 10:00"));
    }

    @Test
    @DisplayName("Sin APP_URL el correo no muestra la línea del enlace")
    void sinAppUrl() {
        propiedades.setAppUrl("");
        PeriodoAcademico p = periodo(Instant.parse("2026-12-01T13:00:00Z"), Instant.parse("2026-12-15T22:00:00Z"));
        when(periodoRepository.findById(PERIODO_ID)).thenReturn(Optional.of(p));
        CorreoContenido c = service.construir(evento(1L, AHORA, APERTURA_PLAZO_DOCENTE),
                new DestinatarioCorreo(1L, "Ana Prueba", "ana.prueba@correounivalle.edu.co"));
        assertFalse(c.cuerpo().contains("Ingrese al sistema"));
    }
}
