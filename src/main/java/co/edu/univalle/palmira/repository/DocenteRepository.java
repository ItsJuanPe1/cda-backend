package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteRepository extends JpaRepository<Docente, Long> {

    @Query("SELECT d FROM Docente d LEFT JOIN FETCH d.programa ORDER BY d.apellidos ASC, d.nombres ASC")
    List<Docente> findAllWithPrograma();

    List<Docente> findAllByOrderByApellidosAscNombresAsc();

    Optional<Docente> findByNumeroDocumento(String numeroDocumento);

    Optional<Docente> findByCorreoInstitucional(String correoInstitucional);

    Optional<Docente> findByCorreoInstitucionalAndEstadoTrue(String correoInstitucional);

    boolean existsByNumeroDocumento(String numeroDocumento);

    boolean existsByCorreoInstitucional(String correoInstitucional);

    @Query("SELECT d FROM Docente d JOIN FETCH d.programa WHERE LOWER(d.correoInstitucional) = LOWER(:correo)")
    Optional<Docente> findByCorreoInstitucionalWithDetails(@Param("correo") String correo);

    boolean existsByCodigo(String codigo);
}
