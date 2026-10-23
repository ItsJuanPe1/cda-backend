package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.TipoContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TipoContratoRepository extends JpaRepository<TipoContrato, Integer> {
    List<TipoContrato> findAllByOrderByNombreAsc();
    Optional<TipoContrato> findByNombreIgnoreCase(String nombre);
}
