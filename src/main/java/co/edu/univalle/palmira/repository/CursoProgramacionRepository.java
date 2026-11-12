package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CursoProgramacionRepository extends JpaRepository<CursoProgramacion, Long> {

    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.programa " +
           "JOIN FETCH c.asignatura " +
           "LEFT JOIN FETCH c.docente " +
           "WHERE c.periodo.id = :periodoId " +
           "ORDER BY c.programa.nameProgram ASC, c.semestre ASC, c.nombreAsignatura ASC")
    List<CursoProgramacion> findByPeriodoIdWithDetails(@Param("periodoId") Long periodoId);

    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.programa " +
           "JOIN FETCH c.asignatura " +
           "LEFT JOIN FETCH c.docente " +
           "WHERE c.periodo.id = :periodoId AND c.programa.codeProgram = :codeProgram " +
           "ORDER BY c.semestre ASC, c.nombreAsignatura ASC")
    List<CursoProgramacion> findByPeriodoIdAndCodeProgramWithDetails(@Param("periodoId") Long periodoId, @Param("codeProgram") String codeProgram);

    long countByPeriodoId(Long periodoId);

    @Query("SELECT DISTINCT c FROM CursoProgramacion c " +
           "JOIN FETCH c.asignatura " +
           "WHERE c.periodo.id = :periodoId AND c.docente.id = :docenteId " +
           "AND c.estado <> 'EXCLUIDO'")
    List<CursoProgramacion> findByPeriodoIdAndDocenteId(
            @Param("periodoId") Long periodoId, @Param("docenteId") Long docenteId);

    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.docente d " +
           "LEFT JOIN FETCH d.facultad " +
           "LEFT JOIN FETCH d.programa " +
           "JOIN FETCH c.asignatura " +
           "JOIN FETCH c.programa " +
           "JOIN FETCH c.periodo " +
           "WHERE c.estado <> 'EXCLUIDO'")
    List<CursoProgramacion> findHistorialDocenteAsignatura();

    // HU-047: ya no se borran los cursos al regenerar (RN-13); se concilian por programa, asignatura y jornada.

    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.programa " +
           "JOIN FETCH c.asignatura " +
           "LEFT JOIN FETCH c.docente " +
           "WHERE c.id = :id")
    Optional<CursoProgramacion> findByIdWithDetails(@Param("id") Long id);

    /**
     * HU-047: cursos de una asignatura en el periodo, bloqueados mientras se renumeran sus grupos
     * para que dos ajustes simultáneos no choquen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.programa " +
           "WHERE c.periodo.id = :periodoId AND c.asignatura.codeCourse = :codeCourse")
    List<CursoProgramacion> findByPeriodoAndAsignaturaForUpdate(@Param("periodoId") Long periodoId,
                                                                @Param("codeCourse") String codeCourse);

    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.programa " +
           "WHERE c.periodo.id = :periodoId AND c.asignatura.codeCourse = :codeCourse")
    List<CursoProgramacion> findByPeriodoAndAsignatura(@Param("periodoId") Long periodoId,
                                                       @Param("codeCourse") String codeCourse);

    @Query("SELECT COUNT(c) FROM CursoProgramacion c " +
           "WHERE c.periodo.id = :periodoId AND c.asignatura.codeCourse = :codeCourse " +
           "AND c.docente.id = :docenteId AND c.estado <> 'EXCLUIDO' AND c.id <> :cursoId")
    long countActivosByPeriodoAsignaturaDocenteExcludingCurso(
            @Param("periodoId") Long periodoId,
            @Param("codeCourse") String codeCourse,
            @Param("docenteId") Long docenteId,
            @Param("cursoId") Long cursoId);

    @Query("SELECT c.programa.codeProgram, c.programa.nameProgram, COUNT(c) " +
           "FROM CursoProgramacion c " +
           "WHERE c.periodo.id = :periodoId " +
           "GROUP BY c.programa.codeProgram, c.programa.nameProgram " +
           "ORDER BY c.programa.nameProgram ASC")
    List<Object[]> contarCursosPorPrograma(@Param("periodoId") Long periodoId);
}
