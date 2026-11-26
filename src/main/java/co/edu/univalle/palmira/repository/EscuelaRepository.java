package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Escuela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EscuelaRepository extends JpaRepository<Escuela, String> {

    List<Escuela> findAllByOrderByCodigoAsc();

    /**
     * Unidades académicas cuyas electivas profesionales puede programar el programa
     * (la propia primero).
     */
    @Query(value = "SELECT ua.* FROM unidades_academicas ua JOIN programa_unidades_academicas pua ON pua.codigo_unidad_academica = ua.codigo "
            + "WHERE pua.code_program = :codeProgram ORDER BY pua.propia DESC, ua.codigo", nativeQuery = true)
    List<Escuela> findByPrograma(@Param("codeProgram") String codeProgram);
}
