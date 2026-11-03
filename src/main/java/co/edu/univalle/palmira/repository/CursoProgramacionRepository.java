package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    @Modifying
    @Query("DELETE FROM CursoProgramacion c WHERE c.periodo.id = :periodoId")
    void deleteByPeriodoId(@Param("periodoId") Long periodoId);

    @Query("SELECT c.programa.codeProgram, c.programa.nameProgram, COUNT(c) " +
           "FROM CursoProgramacion c " +
           "WHERE c.periodo.id = :periodoId " +
           "GROUP BY c.programa.codeProgram, c.programa.nameProgram " +
           "ORDER BY c.programa.nameProgram ASC")
    List<Object[]> contarCursosPorPrograma(@Param("periodoId") Long periodoId);
}
