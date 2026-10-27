package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * HU-015: foto del docente. Una sola fila por docente: al cambiar la foto se reemplaza
 * el contenido; al eliminarla se desactiva (RN-13).
 */
@Entity
@Table(name = "docente_fotos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocenteFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false, unique = true)
    private Docente docente;

    @Column(name = "contenido", nullable = false)
    private byte[] contenido;

    @Column(name = "tipo_mime", length = 20, nullable = false)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Integer tamanoBytes;

    @Column(name = "activa", nullable = false)
    @Builder.Default
    private Boolean activa = true;

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
