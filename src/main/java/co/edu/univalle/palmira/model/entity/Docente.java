package co.edu.univalle.palmira.model.entity;

import co.edu.univalle.palmira.model.converter.CategoriaDocenteConverter;
import co.edu.univalle.palmira.model.converter.TipoDocenteConverter;
import co.edu.univalle.palmira.model.enums.CategoriaDocente;
import co.edu.univalle.palmira.model.enums.TipoDocente;
import co.edu.univalle.palmira.model.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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

    @Column(name = "codigo", length = 20, unique = true)
    private String codigo;

    @Column(name = "nombres", length = 100, nullable = false)
    private String nombres;

    @Column(name = "apellidos", length = 100, nullable = false)
    private String apellidos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 10, nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", length = 20, nullable = false, unique = true)
    private String numeroDocumento;

    @Column(name = "correo_institucional", length = 150, nullable = false, unique = true)
    private String correoInstitucional;

    // HU-015: dato de contacto opcional que edita el propio docente
    @Column(name = "correo_personal", length = 100)
    private String correoPersonal;

    @Column(name = "telefono", length = 20)
    private String telefono;

    // HU-015: celular opcional, distinto del teléfono, de 7 a 10 dígitos
    @Column(name = "celular", length = 10)
    private String celular;

    // Dirección de vivienda, opcional: la edita el docente en «Mi perfil» y el administrador en Plantilla
    @Column(name = "direccion", length = 150)
    private String direccion;

    @Convert(converter = TipoDocenteConverter.class)
    @Column(name = "tipo_docente", length = 20)
    private TipoDocente tipoDocente;

    @Convert(converter = CategoriaDocenteConverter.class)
    @Column(name = "categoria", length = 30)
    private CategoriaDocente categoria;

    /** Relación N:M: un docente puede pertenecer a varias unidades académicas (gestionadas desde su perfil). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "docente_unidades_academicas",
            joinColumns = @JoinColumn(name = "docente_id"),
            inverseJoinColumns = @JoinColumn(name = "codigo_unidad_academica")
    )
    @Builder.Default
    private Set<Escuela> escuelas = new LinkedHashSet<>();

    /** Derivado de las escuelas del docente (transitorio, sin columna en BD). */
    @Transient
    private Facultad facultad;

    public Facultad getFacultad() {
        if (facultad != null) {
            return facultad;
        }
        if (escuelas != null) {
            for (Escuela e : escuelas) {
                if (e != null && e.getFacultad() != null) {
                    return e.getFacultad();
                }
            }
        }
        return null;
    }

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
