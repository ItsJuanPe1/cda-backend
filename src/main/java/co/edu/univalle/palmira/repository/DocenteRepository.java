package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteRepository extends JpaRepository<Docente, Long> {

    List<Docente> findAllByOrderByApellidosAscNombresAsc();

    Optional<Docente> findByNumeroDocumento(String numeroDocumento);

    Optional<Docente> findByCorreoInstitucional(String correoInstitucional);

    Optional<Docente> findByCorreoInstitucionalAndEstadoTrue(String correoInstitucional);

    boolean existsByNumeroDocumento(String numeroDocumento);

    boolean existsByCorreoInstitucional(String correoInstitucional);

    boolean existsByCodigo(String codigo);
}
