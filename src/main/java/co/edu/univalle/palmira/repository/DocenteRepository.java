package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Docente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteRepository extends JpaRepository<Docente, Long> {

    @Query("SELECT d FROM Docente d LEFT JOIN FETCH d.facultad LEFT JOIN FETCH d.programa ORDER BY d.apellidos ASC, d.nombres ASC")
    List<Docente> findAllWithPrograma();

    List<Docente> findAllByOrderByApellidosAscNombresAsc();

    Optional<Docente> findByNumeroDocumento(String numeroDocumento);

    Optional<Docente> findByCorreoInstitucional(String correoInstitucional);

    @Query("SELECT d FROM Docente d LEFT JOIN FETCH d.facultad LEFT JOIN FETCH d.programa WHERE LOWER(d.correoInstitucional) = LOWER(:correoInstitucional) AND d.estado = true")
    Optional<Docente> findByCorreoInstitucionalAndEstadoTrue(@Param("correoInstitucional") String correoInstitucional);

    boolean existsByNumeroDocumento(String numeroDocumento);

    boolean existsByCorreoInstitucional(String correoInstitucional);

    @Query("""
            SELECT d FROM Docente d
            LEFT JOIN FETCH d.facultad
            LEFT JOIN FETCH d.programa
            WHERE d.estado = true
              AND NOT EXISTS (
                  SELECT 1 FROM DisponibilidadDocente dd
                  WHERE dd.docente = d AND dd.periodo.id = :periodoId
              )
            ORDER BY d.apellidos ASC, d.nombres ASC
            """)
    List<Docente> findActivosSinDisponibilidad(@Param("periodoId") Long periodoId);

    @Query("SELECT d FROM Docente d LEFT JOIN FETCH d.facultad LEFT JOIN FETCH d.programa WHERE LOWER(d.correoInstitucional) = LOWER(:correo)")
    Optional<Docente> findByCorreoInstitucionalWithDetails(@Param("correo") String correo);

    @Query("SELECT d FROM Docente d LEFT JOIN FETCH d.facultad LEFT JOIN FETCH d.programa WHERE d.id = :id")
    Optional<Docente> findByIdWithDetails(@Param("id") Long id);

    /**
     * Carga académica: bloquea la fila del docente mientras se valida y se guarda una asignación,
     * para que dos asignaciones simultáneas no superen el límite de horas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Docente d WHERE d.id = :id")
    Optional<Docente> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCodigo(String codigo);

    /**
     * HU-038 criterio 5 y HU-030: docentes que reciben correos. Perfil docente activo, con correo
     * institucional y, si tienen cuenta en usuarios (mismo correo), con la cuenta activa. Incluye
     * administradores y coordinadores con perfil docente.
     */
    @Query("""
            SELECT d.id FROM Docente d
            WHERE d.estado = true
              AND d.correoInstitucional IS NOT NULL AND TRIM(d.correoInstitucional) <> ''
              AND NOT EXISTS (SELECT u FROM Usuario u
                              WHERE LOWER(u.emailInstitucional) = LOWER(d.correoInstitucional)
                                AND u.activo = false)
            ORDER BY d.id
            """)
    List<Long> findIdsDestinatariosNotificacion();

    /** HU-038: el mismo filtro, evaluado al momento del envío para un docente. */
    @Query("""
            SELECT d FROM Docente d
            WHERE d.id = :id
              AND d.estado = true
              AND d.correoInstitucional IS NOT NULL AND TRIM(d.correoInstitucional) <> ''
              AND NOT EXISTS (SELECT u FROM Usuario u
                              WHERE LOWER(u.emailInstitucional) = LOWER(d.correoInstitucional)
                                AND u.activo = false)
            """)
    Optional<Docente> findDestinatarioNotificacion(@Param("id") Long id);
}