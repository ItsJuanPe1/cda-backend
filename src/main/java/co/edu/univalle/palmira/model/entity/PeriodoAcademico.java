package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "periodos_academicos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodoAcademico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String nombre;

    @Column(name = "numero_resolucion", nullable = false, length = 50, unique = true)
    private String numeroResolucion;

    @Column(name = "fecha_resolucion", nullable = false)
    private LocalDate fechaResolucion;
    @Column(name = "fecha_inicio_semestre", nullable = false)
    private LocalDate fechaInicioSemestre;
    @Column(name = "fecha_fin_semestre", nullable = false)
    private LocalDate fechaFinSemestre;
    @Column(name = "fecha_adicion_inicio", nullable = false)
    private LocalDate fechaAdicionInicio;
    @Column(name = "fecha_adicion_fin", nullable = false)
    private LocalDate fechaAdicionFin;
    @Column(name = "fecha_cancelacion_inicio", nullable = false)
    private LocalDate fechaCancelacionInicio;
    @Column(name = "fecha_cancelacion_fin", nullable = false)
    private LocalDate fechaCancelacionFin;
    @Column(name = "plazo_docente_inicio", nullable = false)
    private Instant plazoDocenteInicio;
    @Column(name = "plazo_docente_fin", nullable = false)
    private Instant plazoDocenteFin;
    @Column(name = "plazo_coordinador_inicio")
    private Instant plazoCoordinadorInicio;
    @Column(name = "plazo_coordinador_fin")
    private Instant plazoCoordinadorFin;
    @Column(name = "enlace_resolucion", columnDefinition = "TEXT")
    private String enlaceResolucion;
    @Column(nullable = false)
    private boolean vigente;
    @Column(nullable = false, length = 20)
    private String estado;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
