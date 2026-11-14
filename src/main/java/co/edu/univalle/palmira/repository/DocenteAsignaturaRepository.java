package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.DocenteAsignatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteAsignaturaRepository extends JpaRepository<DocenteAsignatura, Long> {

    Optional<DocenteAsignatura> findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(
            Long docenteId, String codeCourse, Integer idFacultad);

    boolean existsByDocenteIdAndAsignaturaCodeCourseAndFacultadId(
            Long docenteId, String codeCourse, Integer idFacultad);

    @Query("SELECT da FROM DocenteAsignatura da " +
            "JOIN FETCH da.docente " +
            "JOIN FETCH da.asignatura " +
            "JOIN FETCH da.facultad")
    List<DocenteAsignatura> findAllWithDetails();

    @Query("SELECT da FROM DocenteAsignatura da " +
            "JOIN FETCH da.docente d " +
            "JOIN FETCH da.asignatura a " +
            "JOIN FETCH da.facultad f " +
            "LEFT JOIN FETCH da.periodo " +
            "WHERE (:docenteId IS NULL OR d.id = :docenteId) " +
            "AND (:codeCourse IS NULL OR a.codeCourse = :codeCourse) " +
            "AND (:idFacultad IS NULL OR f.id = :idFacultad) " +
            "AND (:activo IS NULL OR da.activo = :activo) " +
            "ORDER BY d.apellidos ASC, d.nombres ASC, a.nameCourse ASC")
    List<DocenteAsignatura> buscar(
            @Param("docenteId") Long docenteId,
            @Param("codeCourse") String codeCourse,
            @Param("idFacultad") Integer idFacultad,
            @Param("activo") Boolean activo);
}
