package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * HU-058: registro de la bitácora de cambios e ingresos.
 * Es inmutable: la aplicación solo inserta y consulta (criterio 4, RN-13). La base de datos
 * además rechaza UPDATE, DELETE y TRUNCATE con un trigger (V29).
 */
@Entity
@Immutable
@Table(name = "bitacora")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fecha en UTC. */
    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Column(name = "usuario_correo", length = 150, updatable = false)
    private String usuarioCorreo;

    @Column(name = "usuario_rol", length = 20, updatable = false)
    private String usuarioRol;

    /** Persona que operó la cuenta compartida de coordinación (nombre del coordinador del programa). */
    @Column(name = "operador_nombre", length = 150, updatable = false)
    private String operadorNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AccionBitacora accion;

    @Column(nullable = false, length = 50, updatable = false)
    private String entidad;

    @Column(name = "entidad_id", length = 100, updatable = false)
    private String entidadId;

    @Column(name = "periodo_id", updatable = false)
    private Long periodoId;

    /** JSON del estado anterior; se guarda como jsonb. */
    @ColumnTransformer(write = "cast(? as jsonb)")
    @Column(name = "valor_anterior", columnDefinition = "jsonb", updatable = false)
    private String valorAnterior;

    /** JSON del estado nuevo; se guarda como jsonb. */
    @ColumnTransformer(write = "cast(? as jsonb)")
    @Column(name = "valor_nuevo", columnDefinition = "jsonb", updatable = false)
    private String valorNuevo;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, updatable = false)
    private ResultadoIngreso resultado;

    @Enumerated(EnumType.STRING)
    @Column(length = 60, updatable = false)
    private MotivoRechazoIngreso motivo;

    @PrePersist
    void antesDeInsertar() {
        if (fecha == null) {
            fecha = Instant.now();
        }
    }
}
