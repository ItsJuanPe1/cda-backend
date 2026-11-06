package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.OrigenAsociacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * HU-049: histórico de asignaturas que un docente ha dictado en su facultad.
 * No se elimina físicamente (RN-13); quitar una asociación la desactiva.
 */
@Entity
@Table(name = "docente_asignatura",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_docente_asignatura_facultad",
                columnNames = {"docente_id", "code_course", "id_facultad"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocenteAsignatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "code_course", nullable = false)
    private Asignatura asignatura;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_facultad", nullable = false)
    private Facultad facultad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_id")
    private PeriodoAcademico periodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrigenAsociacion origen = OrigenAsociacion.MANUAL;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "desactivado_at")
    private Instant desactivadoAt;

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
