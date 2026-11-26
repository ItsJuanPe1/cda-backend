package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "asignaturas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asignatura {

    @Id
    @Column(name = "code_course", length = 50, nullable = false)
    private String codeCourse;

    @Column(name = "name_course", length = 150, nullable = false)
    private String nameCourse;

    @Column(nullable = false)
    @Builder.Default
    private Integer credits = 0;

    @Column(name = "is_elective")
    @Builder.Default
    private Boolean isElective = false;

    @Column(name = "es_complementaria")
    @Builder.Default
    private Boolean esComplementaria = false;

    @Column(name = "es_basica")
    @Builder.Default
    private Boolean esBasica = false;

    @Column(name = "codigo_unidad_academica", length = 3)
    private String codigoEscuela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codigo_unidad_academica", insertable = false, updatable = false)
    private Escuela escuela;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    /** Carga docente: horas semanales de la asignatura; null hasta que el admin las defina. */
    @Column(name = "horas_semanales")
    private Integer horasSemanales;

    /** Campos transitorios derivados de la escuela para compatibilidad con DTOs y pruebas. */
    @Transient
    private String facultad;

    @Transient
    private Integer idFacultad;

    public Integer getIdFacultad() {
        if (escuela != null && escuela.getFacultad() != null) {
            return escuela.getFacultad().getId();
        }
        return idFacultad;
    }

    public String getFacultad() {
        if (escuela != null && escuela.getFacultad() != null) {
            return escuela.getFacultad().getNombre();
        }
        return facultad;
    }
}
