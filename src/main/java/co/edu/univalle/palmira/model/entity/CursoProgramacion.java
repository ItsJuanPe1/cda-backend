package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.Jornada;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Entidad JPA que representa un curso ofertado dentro de la programación académica.
 * <p>
 * Contiene información del período, programa, asignatura, cohorte/semestre, jornada,
 * docente asignado, cupos de estudiantes, horario semanal (soporta 1 sesión o 2 sesiones divididas),
 * estado del curso (PROGRAMADO, ASIGNADO, EXCLUIDO) y campos de auditoría de exclusión.
 * </p>
 */
@Entity
@Table(name = "cursos_programacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursoProgramacion {

    /** Estado inicial del curso cuando se genera en la programación. */
    public static final String ESTADO_PROGRAMADO = "PROGRAMADO";

    /** Estado del curso cuando tiene un docente asignado. */
    public static final String ESTADO_ASIGNADO = "ASIGNADO";

    /** Estado del curso cuando ha sido retirado u omitido con justificación. */
    public static final String ESTADO_EXCLUIDO = "EXCLUIDO";

    /** Grupo inicial para los cursos de una asignatura (inicia en 50 por normativa institucional). */
    public static final int GRUPO_INICIAL = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Período académico al cual pertenece el curso programado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_id", nullable = false)
    private PeriodoAcademico periodo;

    /** Programa académico que oferta el curso. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_program", nullable = false)
    private Programa programa;

    /** Asignatura oficial asociada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_course", nullable = false)
    private Asignatura asignatura;

    /** Semestre o nivel formativo de la malla. */
    @Column(nullable = false)
    private Integer semestre;

    /** Jornada de impartición (DIURNA, NOCTURNA). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Jornada jornada = Jornada.DIURNA;

    /** Número de resolución curricular del programa académico. */
    @Column(name = "numero_resolucion", nullable = false, length = 50)
    private String numeroResolucion;

    /** Número secuencial del grupo (ej. 50, 51, 52). */
    @Column
    private Integer grupo;

    /** Cupos máximos de estudiantes matriculables en el curso. */
    @Column(name = "cupos")
    private Integer cupos;

    /** Código del espacio curricular (ej. ELE-PROF-I) si el curso ocupa una electiva. */
    @Column(name = "code_course_espacio", length = 50)
    private String codeCourseEspacio;

    /** Créditos académicos de la materia. */
    @Column(nullable = false)
    @Builder.Default
    private Integer credits = 0;

    /** Nombre completo de la asignatura. */
    @Column(name = "nombre_asignatura", nullable = false, length = 150)
    private String nombreAsignatura;

    /** Docente asignado a la cátedra del curso. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente")
    private Docente docente;

    /** Día de la semana de la sesión 1 (1 = Lunes, 6 = Sábado). */
    @Column(name = "dia")
    private Integer dia;

    /** Hora de inicio de la sesión 1. */
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    /** Hora de finalización de la sesión 1. */
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    /** Día de la semana de la sesión 2 si el curso divide sus horas en dos días (1-6). */
    @Column(name = "dia2")
    private Integer dia2;

    /** Hora de inicio de la sesión 2. */
    @Column(name = "hora_inicio2")
    private LocalTime horaInicio2;

    /** Hora de finalización de la sesión 2. */
    @Column(name = "hora_fin2")
    private LocalTime horaFin2;

    /** Representación textual formateada del horario (ej. 'LUN 08:00 - 10:00 / MIE 08:00 - 10:00'). */
    @Column(name = "horario", length = 150)
    private String horario;

    /** Estado actual del curso (PROGRAMADO, ASIGNADO, EXCLUIDO). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_PROGRAMADO;

    /** Justificación administrativa registrada cuando el curso es excluido. */
    @Column(name = "justificacion_exclusion", length = 500)
    private String justificacionExclusion;

    /** Correo institucional del usuario que excluyó el curso. */
    @Column(name = "excluido_por", length = 150)
    private String excluidoPor;

    /** Fecha y hora en la que se realizó la exclusión del curso. */
    @Column(name = "fecha_exclusion")
    private Instant fechaExclusion;

    /**
     * Verifica si el curso se encuentra en estado excluido.
     *
     * @return true si el estado es EXCLUIDO
     */
    public boolean isExcluido() {
        return ESTADO_EXCLUIDO.equals(estado);
    }

    /** Fecha y hora de creación del registro. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Fecha y hora de última actualización del registro. */
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
