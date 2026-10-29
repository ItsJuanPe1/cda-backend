package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.DisponibilidadDocente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DisponibilidadDocenteRepository extends JpaRepository<DisponibilidadDocente, Long> {
    List<DisponibilidadDocente> findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(
            Long docenteId, Long periodoId);

    void deleteAllByDocenteIdAndPeriodoId(Long docenteId, Long periodoId);

    @Query("""
            select d from DisponibilidadDocente d
            where d.docente.id = :docenteId and d.periodo.id = :periodoId
              and d.dia = :dia and d.horaInicio < :horaFin and d.horaFin > :horaInicio
            order by d.horaInicio
            """)
    List<DisponibilidadDocente> findCruces(
            @Param("docenteId") Long docenteId,
            @Param("periodoId") Long periodoId,
            @Param("dia") Integer dia,
            @Param("horaInicio") java.time.LocalTime horaInicio,
            @Param("horaFin") java.time.LocalTime horaFin);
}
