package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PeriodoAcademicoRepository extends JpaRepository<PeriodoAcademico, Long> {
    Optional<PeriodoAcademico> findFirstByVigenteTrue();
    List<PeriodoAcademico> findAllByOrderByFechaInicioSemestreDesc();
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNumeroResolucionIgnoreCase(String numeroResolucion);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
    boolean existsByNumeroResolucionIgnoreCaseAndIdNot(String numeroResolucion, Long id);

    @Modifying
    @Transactional
    @Query("update PeriodoAcademico p set p.vigente = false, p.estado = 'HISTORIAL' where p.vigente = true")
    int marcarVigenteComoHistorial();
}
