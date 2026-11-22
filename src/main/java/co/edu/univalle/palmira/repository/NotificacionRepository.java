package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** HU-038: eventos de la cola de correos. */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByPeriodoIdAndEstadoOrderByIdAsc(Long periodoId, EstadoNotificacion estado);

    List<Notificacion> findByPeriodoIdAndTipoEventoAndEstadoOrderByIdAsc(Long periodoId,
            TipoEventoNotificacion tipoEvento, EstadoNotificacion estado);

    boolean existsByPeriodoIdAndTipoEventoAndEstado(Long periodoId, TipoEventoNotificacion tipoEvento,
            EstadoNotificacion estado);

    /**
     * Toma el siguiente evento vencido y lo bloquea. SKIP LOCKED evita que dos instancias del
     * backend procesen el mismo evento. Debe llamarse dentro de una transacción.
     */
    @Query(value = """
            SELECT * FROM notificaciones
            WHERE estado = 'PENDIENTE' AND fecha_programada <= :ahora
            ORDER BY fecha_programada, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notificacion> tomarSiguienteVencida(@Param("ahora") Instant ahora);
}
