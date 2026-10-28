package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PensumId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PensumAcademicoRepository extends JpaRepository<PensumAcademico, PensumId> {

    @Query("SELECT p FROM PensumAcademico p " +
            "JOIN FETCH p.asignatura " +
            "JOIN FETCH p.programa " +
            "WHERE p.id.codeProgram = :codeProgram " +
            "ORDER BY p.semestre ASC, p.asignatura.nameCourse ASC")
    List<PensumAcademico> findByCodeProgramWithDetails(@Param("codeProgram") String codeProgram);

    // Pensum completo en UNA sola consulta (evita el N+1 de findAll() con
    // relaciones LAZY)
    @Query("SELECT p FROM PensumAcademico p " +
            "JOIN FETCH p.asignatura " +
            "ORDER BY p.id.codeProgram ASC, p.semestre ASC, p.asignatura.nameCourse ASC")
    List<PensumAcademico> findAllWithAsignatura();

    // Conteo de materias por programa en UNA sola consulta: filas [code_program,
    // total]
    @Query("SELECT p.id.codeProgram, COUNT(p) FROM PensumAcademico p GROUP BY p.id.codeProgram")
    List<Object[]> contarAsignaturasPorPrograma();

    List<PensumAcademico> findByIdCodeCourse(String codeCourse);

    long countByIdCodeProgram(String codeProgram);
}
