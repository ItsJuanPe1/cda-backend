package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pensum_academico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PensumAcademico {

    @EmbeddedId
    private PensumId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("codeProgram")
    @JoinColumn(name = "code_program", nullable = false)
    private Programa programa;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("codeCourse")
    @JoinColumn(name = "code_course", nullable = false)
    private Asignatura asignatura;

    @Column(nullable = false)
    private Integer semestre;

    @Column(name = "is_elective")
    @Builder.Default
    private Boolean isElective = false;

    @Column(length = 150)
    private String facultad;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean vigente = true;

    @Column(name = "fecha_desactivacion")
    private java.time.LocalDateTime fechaDesactivacion;

    @Column(name = "fecha_reactivacion")
    private java.time.LocalDateTime fechaReactivacion;

    @Column(name = "numero_resolucion", length = 50)
    private String numeroResolucion;

    @Column(name = "numero_semestres")
    private Integer numeroSemestres;

    @Column(name = "editada", nullable = false)
    @Builder.Default
    private Boolean editada = false;

    @Column(name = "fecha_edicion")
    private java.time.LocalDateTime fechaEdicion;
}
