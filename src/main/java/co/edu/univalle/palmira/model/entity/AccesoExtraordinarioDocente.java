package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Plazo extraordinario para que un docente edite su disponibilidad fuera de la ventana general. */
@Entity
@Table(name = "accesos_extraordinarios_docente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccesoExtraordinarioDocente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente", nullable = false)
    private Docente docente;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private Instant fechaFin;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "otorgado_por", nullable = false, length = 150)
    private String otorgadoPor;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "revocado_por", length = 150)
    private String revocadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Vigente en t: no revocado y t entre el inicio y el fin (ambos incluidos). */
    public boolean estaVigenteEn(Instant t) {
        return revocadoEn == null && !t.isBefore(fechaInicio) && !t.isAfter(fechaFin);
    }
}