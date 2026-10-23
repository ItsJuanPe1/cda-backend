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

    @Query("SELECT c FROM Coordinador c " +
           "LEFT JOIN FETCH c.programa " +
           "LEFT JOIN FETCH c.categoria " +
           "LEFT JOIN FETCH c.tipoContrato " +
           "ORDER BY c.nombreCompleto ASC")
    List<Coordinador> findAllWithDetails();

    @Query("SELECT c FROM Coordinador c " +
           "LEFT JOIN FETCH c.programa " +
           "LEFT JOIN FETCH c.categoria " +
           "LEFT JOIN FETCH c.tipoContrato " +
           "WHERE c.id = :id")
    Optional<Coordinador> findByIdWithDetails(@Param("id") Integer id);

    @Query("SELECT c FROM Coordinador c " +
           "LEFT JOIN FETCH c.programa " +
           "LEFT JOIN FETCH c.categoria " +
           "LEFT JOIN FETCH c.tipoContrato " +
           "WHERE c.programa.codeProgram = :codeProgram " +
           "ORDER BY c.nombreCompleto ASC")
    List<Coordinador> findByProgramaCodeProgram(@Param("codeProgram") String codeProgram);

    @Query("SELECT c FROM Coordinador c " +
           "LEFT JOIN FETCH c.programa " +
           "LEFT JOIN FETCH c.categoria " +
           "LEFT JOIN FETCH c.tipoContrato " +
           "WHERE LOWER(c.correoInstitucional) = LOWER(:correoInstitucional)")
    Optional<Coordinador> findByCorreoInstitucionalWithDetails(@Param("correoInstitucional") String correoInstitucional);

    boolean existsByIdentificacion(String identificacion);

    boolean existsByCorreoInstitucionalIgnoreCase(String correoInstitucional);

    Optional<Coordinador> findByIdentificacion(String identificacion);

    Optional<Coordinador> findByCorreoInstitucionalIgnoreCase(String correoInstitucional);
}
