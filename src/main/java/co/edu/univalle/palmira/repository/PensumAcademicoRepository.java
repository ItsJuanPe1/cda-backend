package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PensumId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;

@Repository
public interface PensumAcademicoRepository extends JpaRepository<PensumAcademico, PensumId> {

    @Query("SELECT p FROM PensumAcademico p " +
           "JOIN FETCH p.asignatura " +
           "JOIN FETCH p.programa " +
           "WHERE p.id.codeProgram = :codeProgram AND p.vigente = true " +
           "ORDER BY p.semestre ASC, p.asignatura.nameCourse ASC")
    List<PensumAcademico> findByCodeProgramWithDetails(@Param("codeProgram") String codeProgram);

    List<PensumAcademico> findByIdCodeCourse(String codeCourse);

    @Query("SELECT COUNT(p) FROM PensumAcademico p WHERE p.id.codeProgram = :codeProgram AND p.vigente = true")
    long countByIdCodeProgram(@Param("codeProgram") String codeProgram);

    List<PensumAcademico> findByIdCodeProgramAndVigenteTrue(String codeProgram);

    List<PensumAcademico> findByIdCodeProgramAndVigenteFalseOrderByFechaDesactivacionDesc(String codeProgram);

    List<PensumAcademico> findByIdCodeProgramAndIdVersion(String codeProgram, Integer version);

    @Query("SELECT COALESCE(MAX(p.id.version), 0) FROM PensumAcademico p WHERE p.id.codeProgram = :codeProgram")
    Integer findMaxVersion(@Param("codeProgram") String codeProgram);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PensumAcademico p WHERE p.id.codeProgram = :codeProgram")
    List<PensumAcademico> lockByProgram(@Param("codeProgram") String codeProgram);
}
