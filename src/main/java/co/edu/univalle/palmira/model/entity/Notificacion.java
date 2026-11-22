package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

/** HU-038: evento de la cola de correos (V46, tabla notificaciones). Nunca se borra (RN-13). */
@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private TipoEventoNotificacion tipoEvento;

    @Column(name = "periodo_id")
    private Long periodoId;

    /** Momento de envío, en UTC. */
    @Column(name = "fecha_programada", nullable = false)
    private Instant fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    /** JSON con las fechas del plazo (antes y después del cambio); sin datos personales. */
    @ColumnTransformer(write = "cast(? as jsonb)")
    @Column(name = "datos", columnDefinition = "jsonb")
    private String datos;

    @Column(name = "motivo_cancelacion", length = 100)
    private String motivoCancelacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "procesada_at")
    private Instant procesadaAt;

    @Column(name = "cancelada_at")
    private Instant canceladaAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
