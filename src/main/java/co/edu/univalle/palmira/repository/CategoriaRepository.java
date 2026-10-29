package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
    List<Categoria> findAllByOrderByNombreAsc();
    Optional<Categoria> findByNombreIgnoreCase(String nombre);
}
