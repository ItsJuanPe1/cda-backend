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

    @Column(length = 150)
    private String facultad;

    @Column(name = "id_facultad")
    private Integer idFacultad;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
