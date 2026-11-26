package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entidad JPA que vincula un {@link Docente} existente como coordinador de un
 * {@link Programa},
 * junto con el correo institucional de coordinación del programa.
 * <p>
 * Los datos personales (identificación, nombre, teléfono, categoría,
 * vinculación) se derivan
 * directamente del docente asociado para evitar duplicidad de personas.
 * </p>
 */
@Entity
@Table(name = "coordinadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coordinador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "code_program", nullable = false)
    private Programa programa;

    @Column(name = "correo_institucional", nullable = false, length = 150)
    private String correoInstitucional;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Campos transitorios de respaldo solo para compatibilidad con constructores de
     * pruebas unitarias.
     */
    @Transient
    private String nombreCompleto;

    @Transient
    private String identificacion;

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Nombre completo derivado del docente asociado. */
    public String getNombreCompleto() {
        if (docente != null) {
            String n = docente.getNombres() != null ? docente.getNombres().trim() : "";
            String a = docente.getApellidos() != null ? docente.getApellidos().trim() : "";
            return (n + " " + a).trim();
        }
        return nombreCompleto;
    }

    /** Número de identificación derivado del docente asociado. */
    public String getIdentificacion() {
        return docente != null ? docente.getNumeroDocumento() : identificacion;
    }

    /** Correo personal derivado del docente asociado. */
    public String getCorreo() {
        return docente != null ? docente.getCorreoPersonal() : null;
    }

    /** Teléfono o celular derivado del docente asociado. */
    public String getTelefono() {
        if (docente == null) {
            return null;
        }
        return docente.getCelular() != null && !docente.getCelular().isBlank()
                ? docente.getCelular()
                : docente.getTelefono();
    }

    /** Categoría docente derivada del docente asociado. */
    public String getCategoriaNombre() {
        if (docente == null || docente.getCategoria() == null) {
            return null;
        }
        return switch (docente.getCategoria()) {
            case AUXILIAR -> "Auxiliar";
            case ASISTENTE -> "Asistente";
            case ASOCIADO -> "Asociado";
            case TITULAR -> "Titular";
        };
    }

    /** Tipo de vinculación derivada del docente asociado. */
    public String getTipoContratoNombre() {
        if (docente == null || docente.getTipoDocente() == null) {
            return null;
        }
        return switch (docente.getTipoDocente()) {
            case NOMBRADO -> "Nombrado";
            case OCASIONAL -> "Ocasional";
            case CATEDRA -> "Cátedra";
            case HORA_CATEDRA -> "Hora Cátedra";
        };
    }
}
