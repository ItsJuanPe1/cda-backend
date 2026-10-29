package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
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

    // HU-040: código de 4 dígitos (restricción chk_programas_codigo en V15)
    @Id
    @Column(name = "code_program", length = 50, nullable = false)
    private String codeProgram;

    @Column(name = "name_program", length = 100, nullable = false)
    private String nameProgram;

    // Duración en jornada diurna. Vacía si el programa solo se oferta en nocturna.
    @Column(name = "numero_semestre")
    private Integer numeroSemestre;

    @Column(name = "id_facultad_programa")
    private Integer idFacultadPrograma;

    @Enumerated(EnumType.STRING)
    @Column(name = "jornada", length = 10, nullable = false)
    @Builder.Default
    private Jornada jornada = Jornada.DIURNA;

    // Duración en jornada nocturna. Vacía si el programa solo se oferta en diurna.
    @Column(name = "semestres_nocturna")
    private Integer semestresNocturna;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingreso", length = 10, nullable = false)
    @Builder.Default
    private TipoIngreso ingreso = TipoIngreso.SEMESTRAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 10, nullable = false)
    @Builder.Default
    private EstadoPrograma estado = EstadoPrograma.ACTIVO;

    @Column(name = "apertura", length = 10)
    private String apertura;

    @Column(name = "numero_resolucion", length = 50)
    private String numeroResolucion;
}
