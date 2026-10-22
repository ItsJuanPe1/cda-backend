package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Asignatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsignaturaRepository extends JpaRepository<Asignatura, String> {
    List<Asignatura> findAllByOrderByNameCourseAsc();
}
