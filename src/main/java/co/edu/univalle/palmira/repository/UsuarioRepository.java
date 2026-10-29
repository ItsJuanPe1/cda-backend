package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    @Query("select u from Usuario u where lower(u.emailInstitucional) = lower(:email)")
    Optional<Usuario> buscarPorCorreoInstitucional(@Param("email") String email);
}
