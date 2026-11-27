package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Coordinador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoordinadorRepository extends JpaRepository<Coordinador, Integer> {

    @Query("SELECT c FROM Coordinador c " +
            "JOIN FETCH c.docente d " +
            "LEFT JOIN FETCH c.programa p " +
            "ORDER BY d.apellidos ASC, d.nombres ASC, p.codeProgram ASC")
    List<Coordinador> findAllWithDetails();

    @Query("SELECT c FROM Coordinador c " +
            "JOIN FETCH c.docente " +
            "LEFT JOIN FETCH c.programa " +
            "WHERE c.id = :id")
    Optional<Coordinador> findByIdWithDetails(@Param("id") Integer id);

    @Query("SELECT c FROM Coordinador c " +
            "JOIN FETCH c.docente d " +
            "LEFT JOIN FETCH c.programa " +
            "WHERE c.programa.codeProgram = :codeProgram " +
            "ORDER BY d.apellidos ASC, d.nombres ASC")
    List<Coordinador> findByProgramaCodeProgram(@Param("codeProgram") String codeProgram);

    @Query("SELECT c FROM Coordinador c " +
            "JOIN FETCH c.docente " +
            "LEFT JOIN FETCH c.programa " +
            "WHERE LOWER(c.correoInstitucional) = LOWER(:correoInstitucional) " +
            "ORDER BY c.activo DESC, c.id ASC")
    List<Coordinador> findAllByCorreoInstitucionalWithDetails(
            @Param("correoInstitucional") String correoInstitucional);

    default Optional<Coordinador> findByCorreoInstitucionalWithDetails(String correoInstitucional) {
        return findAllByCorreoInstitucionalWithDetails(correoInstitucional).stream().findFirst();
    }

    @Query("SELECT c FROM Coordinador c " +
            "JOIN FETCH c.docente " +
            "JOIN FETCH c.programa " +
            "WHERE c.activo = true")
    List<Coordinador> findActivosConPrograma();

    /** Bloquea coordinadores participantes en orden de ID para que el intercambio sea atómico. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coordinador c WHERE c.id IN :ids ORDER BY c.id")
    List<Coordinador> findAllByIdsForUpdate(@Param("ids") Collection<Integer> ids);

    boolean existsByCorreoInstitucionalIgnoreCase(String correoInstitucional);

    boolean existsByProgramaCodeProgramAndActivoTrue(String codeProgram);

    boolean existsByProgramaCodeProgramAndActivoTrueAndIdNot(String codeProgram, Integer id);

    Optional<Coordinador> findFirstByCorreoInstitucionalIgnoreCaseOrderByActivoDescIdAsc(String correoInstitucional);

    /**
     * HU-039: coordinaciones que reciben correos. Coordinación activa, programa activo, perfil docente
     * activo y con correo institucional (el estado vive en coordinadores.activo y docentes.estado tras V47).
     */
    @Query("""
            SELECT c FROM Coordinador c
            JOIN FETCH c.docente d
            JOIN FETCH c.programa p
            WHERE c.activo = true
              AND p.estado = co.edu.univalle.palmira.model.enums.EstadoPrograma.ACTIVO
              AND d.estado = true
              AND c.correoInstitucional IS NOT NULL AND TRIM(c.correoInstitucional) <> ''
            ORDER BY c.id
            """)
    List<Coordinador> findActivosParaNotificacion();
}
