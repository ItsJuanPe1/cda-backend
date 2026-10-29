package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Asignatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface AsignaturaRepository extends JpaRepository<Asignatura, String> {
    List<Asignatura> findAllByOrderByNameCourseAsc();

    List<Asignatura> findByActivoTrueOrderByNameCourseAsc();

    @Query("SELECT COUNT(a) FROM Asignatura a WHERE LOWER(TRIM(a.nameCourse)) = LOWER(TRIM(:nameCourse))")
    long countByNormalizedName(@Param("nameCourse") String nameCourse);

    @Query("SELECT COUNT(a) FROM Asignatura a WHERE LOWER(TRIM(a.nameCourse)) = LOWER(TRIM(:nameCourse)) AND UPPER(a.codeCourse) <> UPPER(:codeCourse)")
    long countByNormalizedNameExcludingCode(@Param("nameCourse") String nameCourse,
                                            @Param("codeCourse") String codeCourse);
}
