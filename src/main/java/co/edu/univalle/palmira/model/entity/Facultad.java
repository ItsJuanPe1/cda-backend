package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "\"Facultad\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facultad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;
}
