package co.edu.univalle.palmira.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PensumId implements Serializable {

    @Column(name = "code_program", length = 50, nullable = false)
    private String codeProgram;

    @Column(name = "code_course", length = 50, nullable = false)
    private String codeCourse;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PensumId pensumId = (PensumId) o;
        return Objects.equals(codeProgram, pensumId.codeProgram) &&
                Objects.equals(codeCourse, pensumId.codeCourse) &&
                Objects.equals(version, pensumId.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeProgram, codeCourse, version);
    }
}
