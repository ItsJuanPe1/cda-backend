package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Escuela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EscuelaRepository extends JpaRepository<Escuela, String> {

    List<Escuela> findAllByOrderByCodigoAsc();

    /** Escuelas cuyas electivas profesionales puede programar el programa (la propia primero). */
    @Query(value = "SELECT e.* FROM escuelas e JOIN programa_escuelas pe ON pe.codigo_escuela = e.codigo "
            + "WHERE pe.code_program = :codeProgram ORDER BY pe.propia DESC, e.codigo", nativeQuery = true)
    List<Escuela> findByPrograma(@Param("codeProgram") String codeProgram);
}
