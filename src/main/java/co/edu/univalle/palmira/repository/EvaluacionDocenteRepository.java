package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EvaluacionDocenteRepository extends JpaRepository<EvaluacionDocente, Long> {
    List<EvaluacionDocente> findAllByPeriodoIdAndAsignaturaCodeCourse(
            Long periodoId, String codeCourse);

    @Query("SELECT e FROM EvaluacionDocente e " +
           "JOIN FETCH e.docente d " +
           "LEFT JOIN FETCH d.facultad " +
           "LEFT JOIN FETCH d.programa " +
           "JOIN FETCH e.asignatura " +
           "JOIN FETCH e.periodo")
    List<EvaluacionDocente> findAllWithDetails();

    List<EvaluacionDocente> findAllByDocenteIdAndPeriodoId(Long docenteId, Long periodoId);

    Optional<EvaluacionDocente> findByDocenteIdAndAsignaturaCodeCourseAndPeriodoId(
            Long docenteId, String codeCourse, Long periodoId);
}
