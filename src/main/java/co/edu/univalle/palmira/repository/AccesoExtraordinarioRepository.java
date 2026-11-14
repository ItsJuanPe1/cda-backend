package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.AccesoExtraordinarioDocente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccesoExtraordinarioRepository extends JpaRepository<AccesoExtraordinarioDocente, Long> {

    /** Accesos del docente que no han sido revocados (algunos pueden haber vencido). */
    List<AccesoExtraordinarioDocente> findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(Long docenteId);
}