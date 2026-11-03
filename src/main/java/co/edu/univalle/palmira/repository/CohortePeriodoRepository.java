package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.CohortePeriodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CohortePeriodoRepository extends JpaRepository<CohortePeriodo, Long> {

    @Query("SELECT c FROM CohortePeriodo c JOIN FETCH c.programa WHERE c.periodo.id = :periodoId ORDER BY c.programa.codeProgram ASC, c.semestre ASC")
    List<CohortePeriodo> findByPeriodoIdWithPrograma(@Param("periodoId") Long periodoId);

    @Query("SELECT c FROM CohortePeriodo c JOIN FETCH c.programa WHERE c.periodo.id = :periodoId AND c.programa.codeProgram = :codeProgram ORDER BY c.semestre ASC")
    List<CohortePeriodo> findByPeriodoIdAndCodeProgram(@Param("periodoId") Long periodoId, @Param("codeProgram") String codeProgram);

    @Modifying
    @Query("DELETE FROM CohortePeriodo c WHERE c.periodo.id = :periodoId AND c.programa.codeProgram = :codeProgram")
    void deleteByPeriodoIdAndCodeProgram(@Param("periodoId") Long periodoId, @Param("codeProgram") String codeProgram);

    @Query("SELECT c FROM CohortePeriodo c JOIN FETCH c.programa WHERE c.periodo.id = :periodoId AND c.confirmada = true ORDER BY c.programa.codeProgram ASC, c.semestre ASC")
    List<CohortePeriodo> findByPeriodoIdAndConfirmadaTrue(@Param("periodoId") Long periodoId);

    long countByPeriodoId(Long periodoId);
}
