package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Bitacora;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/** HU-058 criterio 3: consulta con filtros opcionales, del más reciente al más antiguo. */
public interface BitacoraConsultaRepository {

    /**
     * @param usuario   texto en minúsculas; coincide parcialmente con el correo o el operador
     * @param entidad   entidad en mayúsculas; coincidencia exacta
     * @param periodoId periodo académico
     * @param desde     instante inicial inclusivo
     * @param hasta     instante final exclusivo
     */
    Page<Bitacora> buscar(String usuario, String entidad, Long periodoId,
                          Instant desde, Instant hasta, Pageable pageable);
}
