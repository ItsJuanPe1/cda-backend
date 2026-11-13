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

    /** HU-059: carga en una sola consulta los cursos que se van a actualizar. */
    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.programa " +
           "JOIN FETCH c.asignatura " +
           "JOIN FETCH c.periodo " +
           "LEFT JOIN FETCH c.docente " +
           "WHERE c.id IN :ids")
    List<CursoProgramacion> findAllByIdWithDetails(@Param("ids") List<Long> ids);

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

    @Query("SELECT c FROM CursoProgramacion c " +
           "JOIN FETCH c.asignatura " +
           "JOIN FETCH c.programa " +
           "WHERE c.docente.id = :docenteId AND c.periodo.id = :periodoId " +
           "ORDER BY c.programa.nameProgram ASC, c.nombreAsignatura ASC, c.grupo ASC")
    List<CursoProgramacion> findByDocenteYPeriodo(@Param("docenteId") Long docenteId,
                                                  @Param("periodoId") Long periodoId);

    @Query("SELECT COALESCE(SUM(a.horasSemanales), 0) FROM CursoProgramacion c " +
           "JOIN c.asignatura a " +
           "WHERE c.docente.id = :docenteId AND c.periodo.id = :periodoId " +
           "AND c.id <> :cursoIdExcluido")
    Long sumarHorasExcluyendo(@Param("docenteId") Long docenteId,
                              @Param("periodoId") Long periodoId,
                              @Param("cursoIdExcluido") Long cursoIdExcluido);

    @Query("SELECT c.docente.id, COALESCE(SUM(a.horasSemanales), 0), " +
           "SUM(CASE WHEN a.horasSemanales IS NULL THEN 1 ELSE 0 END) " +
           "FROM CursoProgramacion c JOIN c.asignatura a " +
           "WHERE c.periodo.id = :periodoId AND c.docente IS NOT NULL " +
           "GROUP BY c.docente.id")
    List<Object[]> totalesPorDocente(@Param("periodoId") Long periodoId);
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

    @Query(value = """
            SELECT p.code_program,
                   p.name_program,
                   COUNT(c.id) FILTER (WHERE c.estado <> 'EXCLUIDO') AS generados,
                   COUNT(c.id) FILTER (WHERE c.estado <> 'EXCLUIDO' AND c.id_docente IS NOT NULL) AS con_docente,
                   COUNT(c.id) FILTER (
                       WHERE c.estado <> 'EXCLUIDO' AND c.id_docente IS NULL AND EXISTS (
                           SELECT 1
                           FROM docente_asignatura da
                           WHERE da.code_course = c.code_course
                             AND da.id_facultad = COALESCE(p.id_facultad_programa, a.id_facultad)
                             AND da.activo = TRUE
                       )
                   ) AS pendientes_asignacion,
                   COUNT(c.id) FILTER (
                       WHERE c.estado <> 'EXCLUIDO' AND c.id_docente IS NULL AND NOT EXISTS (
                           SELECT 1
                           FROM docente_asignatura da
                           WHERE da.code_course = c.code_course
                             AND da.id_facultad = COALESCE(p.id_facultad_programa, a.id_facultad)
                             AND da.activo = TRUE
                       )
                   ) AS pendientes_contratacion,
                   COUNT(c.id) FILTER (WHERE c.estado = 'EXCLUIDO') AS excluidos
            FROM programas p
            LEFT JOIN cursos_programacion c
                   ON c.code_program = p.code_program AND c.periodo_id = :periodoId
            LEFT JOIN asignaturas a ON a.code_course = c.code_course
            WHERE p.estado = 'ACTIVO' OR c.id IS NOT NULL
            GROUP BY p.code_program, p.name_program, p.id_facultad_programa
            ORDER BY p.name_program ASC
            """, nativeQuery = true)
    List<Object[]> contarAvancePorPrograma(@Param("periodoId") Long periodoId);

    @Query(value = """
            SELECT p.code_program,
                   p.name_program,
                   c.code_course,
                   c.nombre_asignatura,
                   c.semestre,
                   c.jornada,
                   c.grupo,
                   c.numero_resolucion,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM docente_asignatura da
                       WHERE da.code_course = c.code_course
                         AND da.id_facultad = COALESCE(p.id_facultad_programa, a.id_facultad)
                         AND da.activo = TRUE
                   ) THEN 'PENDIENTE_ASIGNACION' ELSE 'PENDIENTE_CONTRATACION' END AS tipo_pendiente
            FROM cursos_programacion c
            JOIN programas p ON p.code_program = c.code_program
            JOIN asignaturas a ON a.code_course = c.code_course
            WHERE c.periodo_id = :periodoId
              AND c.estado <> 'EXCLUIDO'
              AND c.id_docente IS NULL
            ORDER BY p.name_program ASC, c.semestre ASC, c.nombre_asignatura ASC, c.grupo ASC
            """, nativeQuery = true)
    List<Object[]> findPendientesParaExcel(@Param("periodoId") Long periodoId);
}
