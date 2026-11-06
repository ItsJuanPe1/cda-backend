package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-047 contra PostgreSQL 17: bloqueo, V30 (restricciones) y bitácora en la misma transacción.
 * <p>
 * Solo corre con {@code CDA_IT_LOCAL_DB=true} y {@code DB_URL} apuntando a una base LOCAL desechable
 * (la bitácora no permite borrar). Cada prueba se revierte al terminar. Datos ficticios.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CDA_IT_LOCAL_DB", matches = "true")
class ProgramacionAjustePostgresIT {

    private static final String ADMIN = "admin.prueba@correounivalle.edu.co";

    @Autowired
    private ProgramacionService service;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager em;

    private long periodoId;
    private long cursoA;
    private long cursoB;
    private long cursoC;

    @BeforeEach
    void datos() {
        periodoId = jdbc.queryForObject("""
                INSERT INTO periodos_academicos (nombre, numero_resolucion, fecha_resolucion, fecha_inicio_semestre,
                    fecha_fin_semestre, fecha_adicion_inicio, fecha_adicion_fin, fecha_cancelacion_inicio,
                    fecha_cancelacion_fin, plazo_docente_inicio, plazo_docente_fin, vigente, estado)
                VALUES ('PRB-2099', 'RES-PRB-2099', '2099-01-01', '2099-02-01', '2099-06-30', '2099-02-01',
                    '2099-02-10', '2099-02-01', '2099-04-30', '2099-01-10T00:00:00Z', '2099-01-20T00:00:00Z',
                    false, 'HISTORIAL')
                RETURNING id""", Long.class);
        for (String code : List.of("9901", "9902", "9903")) {
            jdbc.update("INSERT INTO programas (code_program, name_program, jornada, numero_semestre, estado) "
                    + "VALUES (?, ?, 'DIURNA', 6, 'ACTIVO')", code, "Programa Prueba " + code);
        }
        jdbc.update("INSERT INTO asignaturas (code_course, name_course) VALUES ('PRB101', 'Asignatura Prueba')");
        cursoA = curso("9901", 50);
        cursoB = curso("9902", 51);
        cursoC = curso("9903", 52);

        Jwt jwt = Jwt.withTokenValue("token-de-prueba").header("alg", "HS256")
                .claim("email", ADMIN).claim("rol", 1).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private long curso(String programa, int grupo) {
        return jdbc.queryForObject("""
                INSERT INTO cursos_programacion (periodo_id, code_program, code_course, semestre, jornada,
                    numero_resolucion, grupo, nombre_asignatura)
                VALUES (?, ?, 'PRB101', 1, 'DIURNA', 'RES-PRB-2099', ?, 'Asignatura Prueba')
                RETURNING id""", Long.class, periodoId, programa, grupo);
    }

    /** Escribe en la base y verifica ya las restricciones diferidas (si no, se verían solo al confirmar). */
    private void sincronizar() {
        em.flush();
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        em.clear();
    }

    private Map<String, Object> fila(long id) {
        return jdbc.queryForMap("SELECT grupo, estado, justificacion_exclusion, excluido_por, fecha_exclusion "
                + "FROM cursos_programacion WHERE id = ?", id);
    }

    @Test
    @DisplayName("Criterios 2 y 5: excluir el grupo intermedio renumera en la base y deja la bitácora en la misma transacción")
    void excluirIntermedioEnPostgres() {
        service.excluir(cursoB, new ProgramacionDto.JustificacionRequest("Sin estudiantes del programa"));
        sincronizar();

        assertEquals(50, fila(cursoA).get("grupo"));
        Map<String, Object> b = fila(cursoB);
        assertNull(b.get("grupo"));
        assertEquals("EXCLUIDO", b.get("estado"));
        assertEquals("Sin estudiantes del programa", b.get("justificacion_exclusion"));
        assertEquals(ADMIN, b.get("excluido_por"));
        assertNotNull(b.get("fecha_exclusion"));
        assertEquals(51, fila(cursoC).get("grupo"));

        List<Map<String, Object>> bitacora = jdbc.queryForList("""
                SELECT accion, entidad_id, valor_anterior->>'grupo' AS antes, valor_nuevo->>'grupo' AS despues
                FROM bitacora WHERE entidad = 'CURSO' AND periodo_id = ? ORDER BY id""", periodoId);
        assertEquals(2, bitacora.size());
        assertEquals("DESACTIVAR", bitacora.get(0).get("accion"));
        assertEquals(String.valueOf(cursoB), bitacora.get(0).get("entidad_id"));
        assertEquals("EDITAR", bitacora.get(1).get("accion"));
        assertEquals("52", bitacora.get(1).get("antes"));
        assertEquals("51", bitacora.get(1).get("despues"));
    }

    @Test
    @DisplayName("Criterio 2: reactivar después de excluir toma el siguiente número y cumple las restricciones de V30")
    void excluirYReactivarEnPostgres() {
        service.excluir(cursoA, new ProgramacionDto.JustificacionRequest("Sin estudiantes"));
        sincronizar();
        assertEquals(50, fila(cursoB).get("grupo"));
        assertEquals(51, fila(cursoC).get("grupo"));

        service.reactivar(cursoA, new ProgramacionDto.JustificacionRequest("Se confirmaron estudiantes"));
        sincronizar();

        Map<String, Object> a = fila(cursoA);
        assertEquals("PROGRAMADO", a.get("estado"));
        assertEquals(52, a.get("grupo"));
        assertNull(a.get("justificacion_exclusion"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM bitacora WHERE accion = 'REACTIVAR' AND periodo_id = ?", Integer.class, periodoId));
    }

    @Test
    @DisplayName("Criterio 4: la consulta del periodo marca «Requiere revisión» cuando no hay cohortes confirmadas")
    void consultaMarcaRevision() {
        List<ProgramacionDto.CursoResponse> r = service.obtenerProgramacion(periodoId, null, "PRB101", null);
        assertEquals(3, r.size());
        assertTrue(r.stream().allMatch(ProgramacionDto.CursoResponse::isRequiereRevision));
        assertEquals(ProgramacionService.REVISION_SIN_COHORTE, r.get(0).getMotivoRevision());
    }
}
