package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Escuela o departamento: su código de 3 dígitos es el prefijo del código de sus asignaturas. */
@Entity
@Table(name = "escuelas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Escuela {

    @Id
    @Column(name = "codigo", length = 3)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;
}
