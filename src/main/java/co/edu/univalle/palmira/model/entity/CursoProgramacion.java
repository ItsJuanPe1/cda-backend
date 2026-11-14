package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.Jornada;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cursos_programacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursoProgramacion {

    public static final String ESTADO_PROGRAMADO = "PROGRAMADO";
    public static final String ESTADO_ASIGNADO = "ASIGNADO";
    public static final String ESTADO_EXCLUIDO = "EXCLUIDO";
    /** HU-046: los grupos de cada asignatura empiezan en 50. */
    public static final int GRUPO_INICIAL = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_id", nullable = false)
    private PeriodoAcademico periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_program", nullable = false)
    private Programa programa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_course", nullable = false)
    private Asignatura asignatura;

    @Column(nullable = false)
    private Integer semestre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Jornada jornada = Jornada.DIURNA;

    @Column(name = "numero_resolucion", nullable = false, length = 50)
    private String numeroResolucion;

    @Column
    private Integer grupo;

    /** HU-059: cupos de estudiantes habilitados para el curso/grupo. */
    @Column(name = "cupos")
    private Integer cupos;

    @Column(nullable = false)
    @Builder.Default
    private Integer credits = 0;

    @Column(name = "nombre_asignatura", nullable = false, length = 150)
    private String nombreAsignatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente")
    private Docente docente;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_PROGRAMADO;

    /** HU-047: justificación obligatoria al excluir (V30). */
    @Column(name = "justificacion_exclusion", length = 500)
    private String justificacionExclusion;

    /** HU-047: correo del administrador que excluyó el curso. */
    @Column(name = "excluido_por", length = 150)
    private String excluidoPor;

    @Column(name = "fecha_exclusion")
    private Instant fechaExclusion;

    public boolean isExcluido() {
        return ESTADO_EXCLUIDO.equals(estado);
    }

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
