package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Periodo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeriodoRepository extends JpaRepository<Periodo, Long> {
    List<Periodo> findAllByOrderByInicioSemestreDesc();
}