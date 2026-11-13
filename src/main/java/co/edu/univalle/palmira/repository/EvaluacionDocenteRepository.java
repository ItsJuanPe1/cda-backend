package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EvaluacionDocenteRepository extends JpaRepository<EvaluacionDocente, Long> {
    List<EvaluacionDocente> findAllByPeriodoIdAndAsignaturaCodeCourse(
            Long periodoId, String codeCourse);

    /** Evaluaciones de una asignatura anteriores al período activo, de la más reciente a la más antigua. */
    @Query("""
            SELECT e FROM EvaluacionDocente e
            JOIN FETCH e.docente d
            JOIN FETCH e.asignatura a
            JOIN FETCH e.periodo p
            WHERE a.codeCourse = :codeCourse
              AND p.fechaInicioSemestre < :fechaInicio
            ORDER BY p.fechaInicioSemestre DESC, e.updatedAt DESC, e.id DESC
            """)
    List<EvaluacionDocente> findAllByAsignaturaBeforePeriodo(
            @Param("codeCourse") String codeCourse,
            @Param("fechaInicio") LocalDate fechaInicio);

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
