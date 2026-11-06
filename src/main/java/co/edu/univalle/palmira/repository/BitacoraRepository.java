package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Bitacora;
import org.springframework.data.repository.Repository;

/**
 * HU-058 criterio 4: solo inserta y consulta. No extiende CrudRepository ni JpaRepository
 * para no exponer métodos de actualización ni de borrado.
 */
public interface BitacoraRepository extends Repository<Bitacora, Long>, BitacoraConsultaRepository {

    Bitacora save(Bitacora registro);
}
