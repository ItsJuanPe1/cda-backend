package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * HU-038: un envío de la cola (V46, tabla notificaciones_envios): un destinatario o la copia.
 * No guarda el correo ni el nombre: se resuelven al enviar (HU-015 criterio 9).
 */
@Entity
@Table(name = "notificaciones_envios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacionEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notificacion_id", nullable = false)
    private Long notificacionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destinatario", nullable = false, length = 20)
    private TipoDestinatario tipoDestinatario;

    /** docentes.id; null en la copia. */
    @Column(name = "destinatario_id")
    private Long destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoEnvioNotificacion estado = EstadoEnvioNotificacion.PENDIENTE;

    @Column(nullable = false)
    @Builder.Default
    private int intentos = 0;

    @Column(name = "proximo_intento", nullable = false)
    private Instant proximoIntento;

    /** Motivo del fallo, sin correos, nombres ni tokens. */
    @Column(name = "ultimo_error", length = 300)
    private String ultimoError;

    @Column(name = "proveedor_mensaje_id", length = 100)
    private String proveedorMensajeId;

    @Column(name = "enviado_at")
    private Instant enviadoAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant ahora = Instant.now();
        if (createdAt == null) {
            createdAt = ahora;
        }
        updatedAt = ahora;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
