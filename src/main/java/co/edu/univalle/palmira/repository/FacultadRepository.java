package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Facultad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultadRepository extends JpaRepository<Facultad, Integer> {
    List<Facultad> findAllByOrderByNombreAsc();
}
