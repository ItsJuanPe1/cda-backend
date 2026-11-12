package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluacionDocenteRepository extends JpaRepository<EvaluacionDocente, Long> {
    List<EvaluacionDocente> findAllByPeriodoIdAndAsignaturaCodeCourse(
            Long periodoId, String codeCourse);

    List<EvaluacionDocente> findAllByDocenteIdAndPeriodoId(Long docenteId, Long periodoId);

    Optional<EvaluacionDocente> findByDocenteIdAndAsignaturaCodeCourseAndPeriodoId(
            Long docenteId, String codeCourse, Long periodoId);
}
