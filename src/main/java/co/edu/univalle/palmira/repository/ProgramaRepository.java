package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Programa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramaRepository extends JpaRepository<Programa, String> {
    List<Programa> findAllByOrderByNameProgramAsc();

    /** Bloquea los programas implicados en orden estable para reducir interbloqueos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Programa p WHERE p.codeProgram IN :codes ORDER BY p.codeProgram")
    List<Programa> findAllByCodeProgramInForUpdate(@Param("codes") Collection<String> codes);
}
