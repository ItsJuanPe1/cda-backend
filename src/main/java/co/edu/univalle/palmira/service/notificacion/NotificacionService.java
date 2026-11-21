package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import co.edu.univalle.palmira.repository.NotificacionEnvioRepository;
import co.edu.univalle.palmira.repository.NotificacionRepository;
import co.edu.univalle.palmira.service.BitacoraService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-038: programa, reprograma y cancela eventos de la cola de correos. Se llama dentro de la
 * transacción de la operación del administrador (MANDATORY): si esa operación falla, no queda
 * nada programado. Cada cambio queda en la bitácora (HU-058).
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    public static final String ENTIDAD = "NOTIFICACION";

    private final NotificacionRepository notificacionRepository;
    private final NotificacionEnvioRepository envioRepository;
    private final BitacoraService bitacoraService;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public Notificacion programar(TipoEventoNotificacion tipo, Long periodoId, Instant fechaProgramada, String datos) {
        Notificacion guardada = notificacionRepository.save(Notificacion.builder()
                .tipoEvento(tipo)
                .periodoId(periodoId)
                .fechaProgramada(fechaProgramada)
                .estado(EstadoNotificacion.PENDIENTE)
                .datos(datos)
                .createdAt(clock.instant())
                .build());
        bitacoraService.registrar(AccionBitacora.CREAR, ENTIDAD, String.valueOf(guardada.getId()), periodoId,
                null, resumen(guardada));
        return guardada;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reprogramar(Notificacion notificacion, Instant nuevaFecha) {
        if (notificacion.getEstado() != EstadoNotificacion.PENDIENTE
                || notificacion.getFechaProgramada().equals(nuevaFecha)) {
            return;
        }
        Map<String, Object> antes = resumen(notificacion);
        notificacion.setFechaProgramada(nuevaFecha);
        notificacionRepository.save(notificacion);
        bitacoraService.registrar(AccionBitacora.EDITAR, ENTIDAD, String.valueOf(notificacion.getId()),
                notificacion.getPeriodoId(), antes, resumen(notificacion));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelar(Notificacion notificacion, String motivo) {
        if (notificacion.getEstado() != EstadoNotificacion.PENDIENTE) {
            return;
        }
        Map<String, Object> antes = resumen(notificacion);
        notificacion.setEstado(EstadoNotificacion.CANCELADO);
        notificacion.setMotivoCancelacion(motivo);
        notificacion.setCanceladaAt(clock.instant());
        notificacionRepository.save(notificacion);
        Map<String, Object> despues = resumen(notificacion);
        despues.put("motivo", motivo);
        bitacoraService.registrar(AccionBitacora.DESACTIVAR, ENTIDAD, String.valueOf(notificacion.getId()),
                notificacion.getPeriodoId(), antes, despues);
    }

    /**
     * Criterio 7 y HU-004 criterio 5: cancela los eventos pendientes del período y los envíos que
     * esperaban un reintento.
     *
     * @return número de eventos cancelados
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelarPendientesDelPeriodo(Long periodoId, String motivo) {
        if (periodoId == null) {
            return 0;
        }
        List<Notificacion> pendientes = notificacionRepository
                .findByPeriodoIdAndEstadoOrderByIdAsc(periodoId, EstadoNotificacion.PENDIENTE);
        pendientes.forEach(n -> cancelar(n, motivo));
        envioRepository.cancelarPendientesDelPeriodo(periodoId, clock.instant());
        return pendientes.size();
    }

    @Transactional(readOnly = true)
    public List<Notificacion> pendientes(Long periodoId, TipoEventoNotificacion tipo) {
        return notificacionRepository.findByPeriodoIdAndTipoEventoAndEstadoOrderByIdAsc(periodoId, tipo,
                EstadoNotificacion.PENDIENTE);
    }

    @Transactional(readOnly = true)
    public boolean yaProcesado(Long periodoId, TipoEventoNotificacion tipo) {
        return notificacionRepository.existsByPeriodoIdAndTipoEventoAndEstado(periodoId, tipo,
                EstadoNotificacion.PROCESADO);
    }

    /** Datos del evento para la bitácora; sin datos personales. */
    private static Map<String, Object> resumen(Notificacion n) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipoEvento", n.getTipoEvento() == null ? null : n.getTipoEvento().name());
        datos.put("fechaProgramada", n.getFechaProgramada() == null ? null : n.getFechaProgramada().toString());
        datos.put("estado", n.getEstado() == null ? null : n.getEstado().name());
        return datos;
    }
}
