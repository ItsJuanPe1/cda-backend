
package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String apellido;

    @Column(nullable = true)
    private Integer telefono;

    @Column(name = "email_institucional", columnDefinition = "TEXT",
            nullable = false)
    private String emailInstitucional;

    @Column(name = "email_personal", columnDefinition = "TEXT",
            nullable = false)
    private String emailPersonal;

    @Column(nullable = false)
    private Integer rol;

    @Column(name = "detalle_docente", columnDefinition = "TEXT",
            nullable = true)
    private String detalleDocente;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
