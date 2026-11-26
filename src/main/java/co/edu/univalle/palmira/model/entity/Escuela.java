package co.edu.univalle.palmira.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Escuela o departamento: su código de 3 dígitos es su identificador y prefijo de sus asignaturas. */
@Entity
@Table(name = "unidades_academicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "codigo")
public class Escuela {

    @Id
    @Column(name = "codigo", length = 3)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_facultad")
    @JsonIgnore
    private Facultad facultad;

    @JsonProperty("id_facultad")
    public Integer getIdFacultad() {
        return facultad != null ? facultad.getId() : null;
    }
}
