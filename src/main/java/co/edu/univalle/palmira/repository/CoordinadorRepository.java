package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Coordinador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoordinadorRepository extends JpaRepository<Coordinador, Integer> {

    @Query("SELECT c FROM Coordinador c LEFT JOIN FETCH c.programa ORDER BY c.nombreCompleto ASC")
    List<Coordinador> findAllWithPrograma();

    @Query("SELECT c FROM Coordinador c LEFT JOIN FETCH c.programa WHERE c.id = :id")
    Optional<Coordinador> findByIdWithPrograma(@Param("id") Integer id);

    @Query("SELECT c FROM Coordinador c LEFT JOIN FETCH c.programa WHERE c.programa.codeProgram = :codeProgram ORDER BY c.nombreCompleto ASC")
    List<Coordinador> findByProgramaCodeProgram(@Param("codeProgram") String codeProgram);

    boolean existsByIdentificacion(String identificacion);

    boolean existsByCorreoInstitucionalIgnoreCase(String correoInstitucional);

    Optional<Coordinador> findByIdentificacion(String identificacion);
}
