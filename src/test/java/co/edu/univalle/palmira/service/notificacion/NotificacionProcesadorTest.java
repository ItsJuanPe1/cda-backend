package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.NotificacionEnvio;
import co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion;
import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.repository.NotificacionEnvioRepository;
import co.edu.univalle.palmira.repository.NotificacionRepository;
import co.edu.univalle.palmira.service.correo.CorreoException;
import co.edu.univalle.palmira.service.correo.CorreoSaliente;
import co.edu.univalle.palmira.service.correo.CorreoSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.APERTURA_PLAZO_DOCENTE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** HU-038: cola de correos (procesar eventos y enviar). Cliente de correo simulado; datos ficticios. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionProcesadorTest {

    private static final Instant AHORA = Instant.parse("2026-11-21T15:00:00Z");
    private static final String COPIA = "notificaciones.prueba@correounivalle.edu.co";

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private NotificacionEnvioRepository envioRepository;
    @Mock
    private ManejadorNotificacion manejador;
    @Mock
    private CorreoSender correoSender;

    private NotificacionesProperties propiedades;
    private NotificacionProcesador procesador;
    private Notificacion evento;

    @BeforeEach
    void setUp() {
        propiedades = new NotificacionesProperties();
        propiedades.setMailFrom(COPIA);
        propiedades.setCopia(COPIA);
        procesador = new NotificacionProcesador(notificacionRepository, envioRepository, List.of(manejador),
                correoSender, propiedades, Clock.fixed(AHORA, ZoneOffset.UTC));
        evento = Notificacion.builder().id(5L).periodoId(7L).tipoEvento(APERTURA_PLAZO_DOCENTE)
                .fechaProgramada(AHORA).estado(EstadoNotificacion.PENDIENTE).build();
        when(manejador.atiende(APERTURA_PLAZO_DOCENTE)).thenReturn(true);
        when(manejador.tipoDestinatario()).thenReturn(TipoDestinatario.DOCENTE);
        when(manejador.eventoVigente(evento)).thenReturn(true);
        when(notificacionRepository.findById(5L)).thenReturn(Optional.of(evento));
        when(manejador.construir(any(), any())).thenReturn(new CorreoContenido("Asunto", "Cuerpo"));
        when(manejador.motivoOmision(any(), any())).thenReturn(Optional.empty());
    }

    private NotificacionEnvio envioDocente(Long destinatarioId, int intentos) {
        NotificacionEnvio e = NotificacionEnvio.builder().id(50L).notificacionId(5L)
                .tipoDestinatario(TipoDestinatario.DOCENTE).destinatarioId(destinatarioId)
                .estado(EstadoEnvioNotificacion.PENDIENTE).intentos(intentos).proximoIntento(AHORA).build();
        when(envioRepository.tomarSiguientePendiente(AHORA)).thenReturn(Optional.of(e));
        return e;
    }

    // ------------------------------------------------------------------ procesar eventos

    @Test
    @DisplayName("Criterio 1: al llegar la hora se crea un envío por destinatario activo y una sola copia")
    void procesaEventoVencido() {
        when(notificacionRepository.tomarSiguienteVencida(AHORA)).thenReturn(Optional.of(evento));
        when(manejador.resolverDestinatarios(evento)).thenReturn(List.of(1L, 2L, 2L));

        assertTrue(procesador.procesarSiguienteEvento());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificacionEnvio>> envios = ArgumentCaptor.forClass(List.class);
        verify(envioRepository).saveAll(envios.capture());
        List<NotificacionEnvio> creados = envios.getValue();
        assertEquals(3, creados.size());
        assertEquals(List.of(1L, 2L), creados.stream().filter(e -> e.getTipoDestinatario() == TipoDestinatario.DOCENTE)
                .map(NotificacionEnvio::getDestinatarioId).toList());
        assertEquals(1, creados.stream().filter(e -> e.getTipoDestinatario() == TipoDestinatario.COPIA).count());
        assertEquals(EstadoNotificacion.PROCESADO, evento.getEstado());
        assertEquals(AHORA, evento.getProcesadaAt());
    }

    @Test
    @DisplayName("Sin eventos vencidos no se hace nada")
    void sinEventos() {
        when(notificacionRepository.tomarSiguienteVencida(AHORA)).thenReturn(Optional.empty());
        assertFalse(procesador.procesarSiguienteEvento());
        verifyNoInteractions(envioRepository);
    }

    @Test
    @DisplayName("Criterio 6: si al llegar la hora el período no está vigente, el evento se cancela sin envíos")
    void criterio6EventoNoVigente() {
        when(notificacionRepository.tomarSiguienteVencida(AHORA)).thenReturn(Optional.of(evento));
        when(manejador.eventoVigente(evento)).thenReturn(false);

        assertTrue(procesador.procesarSiguienteEvento());

        assertEquals(EstadoNotificacion.CANCELADO, evento.getEstado());
        assertEquals(NotificacionProcesador.MOTIVO_EVENTO_NO_VIGENTE, evento.getMotivoCancelacion());
        verify(envioRepository, never()).saveAll(anyList());
    }

    // ------------------------------------------------------------------ enviar

    @Test
    @DisplayName("Criterio 1: el envío exitoso queda ENVIADO con el id del mensaje de Gmail")
    void envioExitoso() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co")));
        when(correoSender.enviar(any())).thenReturn("msg-123");

        assertTrue(procesador.enviarSiguiente());

        assertEquals(EstadoEnvioNotificacion.ENVIADO, e.getEstado());
        assertEquals("msg-123", e.getProveedorMensajeId());
        assertEquals(1, e.getIntentos());
        assertEquals(AHORA, e.getEnviadoAt());
        verify(envioRepository).save(e);
    }

    @Test
    @DisplayName("Criterio 5: un perfil desactivado al momento de enviar queda OMITIDO y no se llama a Gmail")
    void criterio5Omitido() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L)).thenReturn(Optional.empty());

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.OMITIDO, e.getEstado());
        assertEquals(NotificacionProcesador.MOTIVO_PERFIL_DESACTIVADO, e.getUltimoError());
        verify(correoSender, never()).enviar(any());
    }

    @Test
    @DisplayName("HU-039 criterio 3: si el manejador da un motivo de omisión, el envío queda OMITIDO con ese motivo")
    void hu039MotivoDeOmision() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Coordinación de prueba", "coord.prueba@correounivalle.edu.co")));
        when(manejador.motivoOmision(evento, 3L)).thenReturn(Optional.of("Asignación completa"));

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.OMITIDO, e.getEstado());
        assertEquals("Asignación completa", e.getUltimoError());
        verify(correoSender, never()).enviar(any());
    }

    @Test
    @DisplayName("HU-015 criterio 9: el correo se resuelve al enviar y llega a la dirección vigente")
    void hu015CorreoAlEnviar() throws Exception {
        envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Ana Prueba", "ana.nueva@correounivalle.edu.co")));
        when(correoSender.enviar(any())).thenReturn("msg-1");

        procesador.enviarSiguiente();

        ArgumentCaptor<CorreoSaliente> correo = ArgumentCaptor.forClass(CorreoSaliente.class);
        verify(correoSender).enviar(correo.capture());
        assertEquals("ana.nueva@correounivalle.edu.co", correo.getValue().para());
        assertEquals(COPIA, correo.getValue().de());
    }

    @Test
    @DisplayName("Criterio 8: un fallo temporal se reintenta a los 5 minutos y registra el motivo")
    void criterio8Reintenta() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co")));
        when(correoSender.enviar(any())).thenThrow(new CorreoException(CorreoException.Motivo.NO_DISPONIBLE));

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.PENDIENTE, e.getEstado());
        assertEquals(1, e.getIntentos());
        assertEquals(AHORA.plus(Duration.ofMinutes(5)), e.getProximoIntento());
        assertEquals("Gmail no disponible", e.getUltimoError());
    }

    @Test
    @DisplayName("Criterio 8: al quinto intento fallido el envío queda FALLIDO")
    void criterio8AgotaReintentos() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 4);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co")));
        when(correoSender.enviar(any())).thenThrow(new CorreoException(CorreoException.Motivo.NO_DISPONIBLE));

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.FALLIDO, e.getEstado());
        assertEquals(5, e.getIntentos());
    }

    @Test
    @DisplayName("Criterio 8: las esperas entre reintentos son 5, 15, 60 y 240 minutos")
    void criterio8Esperas() {
        assertEquals(Duration.ofMinutes(5), procesador.espera(1));
        assertEquals(Duration.ofMinutes(15), procesador.espera(2));
        assertEquals(Duration.ofMinutes(60), procesador.espera(3));
        assertEquals(Duration.ofMinutes(240), procesador.espera(4));
        assertEquals(Duration.ofMinutes(240), procesador.espera(9));
    }

    @Test
    @DisplayName("Criterio 8: sin configuración de Gmail el envío queda FALLIDO sin reintentos")
    void criterio8SinConfiguracion() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co")));
        when(correoSender.enviar(any())).thenThrow(new CorreoException(CorreoException.Motivo.CONFIGURACION_INCOMPLETA));

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.FALLIDO, e.getEstado());
        assertEquals(1, e.getIntentos());
        assertEquals("Configuración de Gmail incompleta", e.getUltimoError());
    }

    @Test
    @DisplayName("Criterio 8: con el refresh token revocado (invalid_grant) el envío queda FALLIDO sin reintentos")
    void criterio8InvalidGrant() throws Exception {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L))
                .thenReturn(Optional.of(new DestinatarioCorreo(3L, "Ana Prueba", "ana.prueba@correounivalle.edu.co")));
        when(correoSender.enviar(any())).thenThrow(new CorreoException(CorreoException.Motivo.AUTORIZACION_REVOCADA));

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.FALLIDO, e.getEstado());
        assertEquals("Autorización de Gmail revocada o vencida", e.getUltimoError());
    }

    @Test
    @DisplayName("Criterio 8: un error interno no se propaga; el envío se reintenta y se guarda el motivo")
    void criterio8ErrorInterno() {
        NotificacionEnvio e = envioDocente(3L, 0);
        when(manejador.resolverDestinatario(3L)).thenThrow(new IllegalStateException("datos de prueba"));

        assertDoesNotThrow(() -> procesador.enviarSiguiente());

        assertEquals(EstadoEnvioNotificacion.PENDIENTE, e.getEstado());
        assertEquals(NotificacionProcesador.ERROR_INTERNO, e.getUltimoError());
        verify(envioRepository).save(e);
    }

    @Test
    @DisplayName("Copia: va a la cuenta de notificaciones con la lista de destinatarios y los omitidos")
    void copia() throws Exception {
        NotificacionEnvio copia = NotificacionEnvio.builder().id(60L).notificacionId(5L)
                .tipoDestinatario(TipoDestinatario.COPIA).estado(EstadoEnvioNotificacion.PENDIENTE).proximoIntento(AHORA).build();
        when(envioRepository.tomarSiguientePendiente(AHORA)).thenReturn(Optional.of(copia));
        NotificacionEnvio d1 = NotificacionEnvio.builder().destinatarioId(1L).build();
        NotificacionEnvio d2 = NotificacionEnvio.builder().destinatarioId(2L).build();
        when(envioRepository.findByNotificacionIdAndTipoDestinatarioNotOrderByIdAsc(5L, TipoDestinatario.COPIA))
                .thenReturn(List.of(d1, d2));
        when(envioRepository.contarOmitidosPorMotivo(5L)).thenReturn(List.<Object[]>of(
                new Object[]{"Perfil desactivado", 1L}, new Object[]{null, 2L}, new Object[]{"Asignación completa", 3L}));
        List<DestinatarioCorreo> lista = List.of(new DestinatarioCorreo(1L, "Ana Prueba", "ana.prueba@correounivalle.edu.co"));
        when(manejador.describirDestinatarios(List.of(1L, 2L))).thenReturn(lista);
        when(manejador.construirCopia(evento, lista, Map.of("Perfil desactivado", 3L, "Asignación completa", 3L)))
                .thenReturn(new CorreoContenido("[Copia] Asunto", "Resumen"));
        when(correoSender.enviar(any())).thenReturn("msg-copia");

        procesador.enviarSiguiente();

        ArgumentCaptor<CorreoSaliente> correo = ArgumentCaptor.forClass(CorreoSaliente.class);
        verify(correoSender).enviar(correo.capture());
        assertEquals(COPIA, correo.getValue().para());
        assertEquals("[Copia] Asunto", correo.getValue().asunto());
        assertEquals(EstadoEnvioNotificacion.ENVIADO, copia.getEstado());
    }

    @Test
    @DisplayName("Criterio 7: un envío de un evento cancelado queda CANCELADO sin llamar a Gmail")
    void criterio7EventoCancelado() throws Exception {
        evento.setEstado(EstadoNotificacion.CANCELADO);
        NotificacionEnvio e = envioDocente(3L, 1);

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.CANCELADO, e.getEstado());
        verify(correoSender, never()).enviar(any());
    }

    @Test
    @DisplayName("Criterio 6: si al enviar el período ya no está vigente, el envío queda CANCELADO")
    void criterio6PeriodoNoVigenteAlEnviar() throws Exception {
        when(manejador.eventoVigente(evento)).thenReturn(false);
        NotificacionEnvio e = envioDocente(3L, 1);

        procesador.enviarSiguiente();

        assertEquals(EstadoEnvioNotificacion.CANCELADO, e.getEstado());
        verify(correoSender, never()).enviar(any());
        verify(manejador, never()).resolverDestinatario(eq(3L));
    }
}
