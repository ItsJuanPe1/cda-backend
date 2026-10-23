package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "periodos")
@Getter
@Setter
@NoArgsConstructor
public class Periodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPeriodo estado;

    @Column(name = "numero_resolucion", length = 100)
    private String numeroResolucion;

    @Column(name = "enlace_resolucion", length = 500)
    private String enlaceResolucion;

    @Column(name = "inicio_semestre", nullable = false)
    private LocalDate inicioSemestre;

    @Column(name = "fin_semestre", nullable = false)
    private LocalDate finSemestre;

    @Column(name = "fecha_adicion")
    private LocalDate fechaAdicion;

    @Column(name = "fecha_cancelacion")
    private LocalDate fechaCancelacion;

    // DEPENDE-HU:plazos
    @Column(name = "plazo_docentes_inicio")
    private OffsetDateTime plazoDocentesInicio;

    @Column(name = "plazo_docentes_fin")
    private OffsetDateTime plazoDocentesFin;

    @Column(name = "plazo_coordinadores_inicio")
    private OffsetDateTime plazoCoordinadoresInicio;

    @Column(name = "plazo_coordinadores_fin")
    private OffsetDateTime plazoCoordinadoresFin;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime fechaDesactivacion;
}