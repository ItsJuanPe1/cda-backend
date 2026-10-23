package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.enums.CategoriaDocente;
import co.edu.univalle.palmira.model.enums.TipoDocente;
import co.edu.univalle.palmira.model.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "docentes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Docente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 20, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombres", length = 30, nullable = false)
    private String nombres;

    @Column(name = "apellidos", length = 30, nullable = false)
    private String apellidos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 10, nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", length = 11, nullable = false, unique = true)
    private String numeroDocumento;

    @Column(name = "correo_institucional", length = 50, nullable = false, unique = true)
    private String correoInstitucional;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_docente", length = 20, nullable = false)
    private TipoDocente tipoDocente;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 30, nullable = false)
    private CategoriaDocente categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_program", nullable = false)
    private Programa programa;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(name = "evaluacion_periodo", length = 10)
    private String evaluacionPeriodo;

    @Column(name = "evaluacion_valor", precision = 4, scale = 2)
    private BigDecimal evaluacionValor;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private Boolean estado = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
