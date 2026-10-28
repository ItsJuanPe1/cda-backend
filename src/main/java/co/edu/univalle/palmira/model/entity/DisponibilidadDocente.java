package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "disponibilidades_docentes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadDocente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_docente",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_disponibilidad_docente")
    )
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "periodo_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_disponibilidad_periodo")
    )
    private PeriodoAcademico periodo;

    @Column(name = "dia", nullable = false)
    private Integer dia;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;
}