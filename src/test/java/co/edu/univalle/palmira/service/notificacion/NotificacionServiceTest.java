package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.repository.NotificacionEnvioRepository;
import co.edu.univalle.palmira.repository.NotificacionRepository;
import co.edu.univalle.palmira.service.BitacoraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.APERTURA_PLAZO_DOCENTE;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.RECORDATORIO_CIERRE_DOCENTE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** HU-038 y HU-058: programar, reprogramar y cancelar avisos deja rastro en la bitácora. */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-21T15:00:00Z");

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private NotificacionEnvioRepository envioRepository;
    @Mock
    private BitacoraService bitacoraService;

    private NotificacionService service;

    @BeforeEach
    void setUp() {
        service = new NotificacionService(notificacionRepository, envioRepository, bitacoraService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Notificacion pendiente(Long id) {
        return Notificacion.builder().id(id).periodoId(7L).tipoEvento(APERTURA_PLAZO_DOCENTE)
                .fechaProgramada(Instant.parse("2026-12-01T13:00:00Z")).estado(EstadoNotificacion.PENDIENTE).build();
    }

    @Test
    @DisplayName("HU-058: programar un aviso queda en la bitácora como CREAR de NOTIFICACION")
    void programarRegistraBitacora() {
        when(notificacionRepository.save(any())).thenAnswer(inv -> {
            Notificacion n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });
        Instant fecha = Instant.parse("2026-12-01T13:00:00Z");

        Notificacion n = service.programar(APERTURA_PLAZO_DOCENTE, 7L, fecha, null);

        assertEquals(EstadoNotificacion.PENDIENTE, n.getEstado());
        assertEquals(fecha, n.getFechaProgramada());
        verify(bitacoraService).registrar(eq(AccionBitacora.CREAR), eq(NotificacionService.ENTIDAD), eq("10"),
                eq(7L), isNull(), any());
    }

    @Test
    @DisplayName("Criterio 3 y HU-058: reprogramar cambia la fecha y queda como EDITAR con la fecha anterior y la nueva")
    void reprogramarRegistraBitacora() {
        Notificacion n = pendiente(10L);
        Instant nueva = Instant.parse("2026-12-03T13:00:00Z");

        service.reprogramar(n, nueva);

        assertEquals(nueva, n.getFechaProgramada());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> antes = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> despues = ArgumentCaptor.forClass(Map.class);
        verify(bitacoraService).registrar(eq(AccionBitacora.EDITAR), eq(NotificacionService.ENTIDAD), eq("10"),
                eq(7L), antes.capture(), despues.capture());
        assertEquals("2026-12-01T13:00:00Z", antes.getValue().get("fechaProgramada"));
        assertEquals("2026-12-03T13:00:00Z", despues.getValue().get("fechaProgramada"));
    }

    @Test
    @DisplayName("Reprogramar a la misma fecha no cambia nada ni escribe en la bitácora")
    void reprogramarMismaFecha() {
        Notificacion n = pendiente(10L);
        service.reprogramar(n, n.getFechaProgramada());
        verifyNoInteractions(bitacoraService, notificacionRepository);
    }

    @Test
    @DisplayName("Criterio 7 y HU-058: cancelar deja el aviso CANCELADO con su motivo y lo registra como DESACTIVAR")
    void cancelarRegistraBitacora() {
        Notificacion n = pendiente(10L);

        service.cancelar(n, "PERIODO_HISTORIAL");

        assertEquals(EstadoNotificacion.CANCELADO, n.getEstado());
        assertEquals("PERIODO_HISTORIAL", n.getMotivoCancelacion());
        assertEquals(AHORA, n.getCanceladaAt());
        verify(bitacoraService).registrar(eq(AccionBitacora.DESACTIVAR), eq(NotificacionService.ENTIDAD), eq("10"),
                eq(7L), any(), any());
    }

    @Test
    @DisplayName("Un aviso ya procesado no se cancela")
    void noCancelaProcesado() {
        Notificacion n = pendiente(10L);
        n.setEstado(EstadoNotificacion.PROCESADO);
        service.cancelar(n, "PERIODO_HISTORIAL");
        assertEquals(EstadoNotificacion.PROCESADO, n.getEstado());
        verifyNoInteractions(bitacoraService);
    }

    @Test
    @DisplayName("Criterio 7: cancelar los pendientes del período cancela sus eventos y los reintentos en espera")
    void criterio7CancelarPendientesDelPeriodo() {
        Notificacion a = pendiente(10L);
        Notificacion b = pendiente(11L);
        b.setTipoEvento(RECORDATORIO_CIERRE_DOCENTE);
        when(notificacionRepository.findByPeriodoIdAndEstadoOrderByIdAsc(7L, EstadoNotificacion.PENDIENTE))
                .thenReturn(List.of(a, b));

        assertEquals(2, service.cancelarPendientesDelPeriodo(7L, "PERIODO_DESACTIVADO"));

        assertEquals(EstadoNotificacion.CANCELADO, a.getEstado());
        assertEquals(EstadoNotificacion.CANCELADO, b.getEstado());
        verify(envioRepository).cancelarPendientesDelPeriodo(7L, AHORA);
        verify(bitacoraService, times(2)).registrar(eq(AccionBitacora.DESACTIVAR), any(), any(), eq(7L), any(), any());
    }
}
