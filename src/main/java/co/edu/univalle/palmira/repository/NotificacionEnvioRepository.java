package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.NotificacionEnvio;
import co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** HU-038: envíos de la cola de correos. */
public interface NotificacionEnvioRepository extends JpaRepository<NotificacionEnvio, Long> {

    List<NotificacionEnvio> findByNotificacionIdAndTipoDestinatarioNotOrderByIdAsc(Long notificacionId,
            TipoDestinatario tipoDestinatario);

    long countByNotificacionIdAndEstado(Long notificacionId, EstadoEnvioNotificacion estado);

    /**
     * Toma el siguiente envío pendiente cuyo intento ya venció y lo bloquea (SKIP LOCKED).
     * Debe llamarse dentro de una transacción.
     */
    @Query(value = """
            SELECT * FROM notificaciones_envios
            WHERE estado = 'PENDIENTE' AND proximo_intento <= :ahora
            ORDER BY proximo_intento, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificacionEnvio> tomarSiguientePendiente(@Param("ahora") Instant ahora);

    /** Criterio 7: cancela los envíos pendientes (por ejemplo, reintentos) de los eventos de un período. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update NotificacionEnvio e
               set e.estado = co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion.CANCELADO,
                   e.updatedAt = :ahora
             where e.estado = co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion.PENDIENTE
               and e.notificacionId in (select n.id from Notificacion n where n.periodoId = :periodoId)
            """)
    int cancelarPendientesDelPeriodo(@Param("periodoId") Long periodoId, @Param("ahora") Instant ahora);
}
