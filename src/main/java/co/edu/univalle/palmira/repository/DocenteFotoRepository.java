package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.DocenteFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface DocenteFotoRepository extends JpaRepository<DocenteFoto, Long> {

    Optional<DocenteFoto> findByDocenteId(Long docenteId);

    Optional<DocenteFoto> findByDocenteIdAndActivaTrue(Long docenteId);

    boolean existsByDocenteIdAndActivaTrue(Long docenteId);

    // Ids de docentes con foto activa, para el listado sin una consulta por docente
    @Query("SELECT f.docente.id FROM DocenteFoto f WHERE f.activa = true")
    Set<Long> findDocenteIdsConFotoActiva();
}
