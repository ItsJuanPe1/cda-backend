package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "programas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Programa {

    @Id
    @Column(name = "code_program", length = 50, nullable = false)
    private String codeProgram;

    @Column(name = "name_program", length = 150, nullable = false)
    private String nameProgram;

    @Column(name = "numero_semestre", nullable = false)
    @Builder.Default
    private Integer numeroSemestre = 10;

    @Column(name = "id_facultad_programa")
    private Integer idFacultadPrograma;
}
