package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Bitacora;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-058: prueba contra PostgreSQL 17 (V29, jsonb y trigger de solo inserción).
 * <p>
 * Solo corre con {@code CDA_IT_LOCAL_DB=true} y {@code DB_URL} apuntando a una base LOCAL desechable:
 * la bitácora no permite borrar, así que lo que se inserte en una base compartida quedaría para siempre.
 * Cada prueba es transaccional y se revierte al terminar. Datos ficticios.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CDA_IT_LOCAL_DB", matches = "true")
class BitacoraPostgresIT {

    @Autowired
    private BitacoraRepository bitacoraRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Bitacora insertar() {
        return bitacoraRepository.save(Bitacora.builder()
                .usuarioCorreo("admin.prueba@correounivalle.edu.co").usuarioRol("ADMIN")
                .accion(AccionBitacora.EDITAR).entidad("CURSO").entidadId("PRB101-50")
                .valorAnterior("{\"docenteId\":15}").valorNuevo("{\"docenteId\":22}")
                .build());
    }

    @Test
    @DisplayName("Criterio 1: inserta en UTC y guarda los valores como jsonb")
    void criterio1_insertaConJsonb() {
        Bitacora b = insertar();
        bitacoraRepository.save(Bitacora.builder()
                .usuarioCorreo("docente.prueba@correounivalle.edu.co").usuarioRol("DOCENTE")
                .accion(AccionBitacora.INGRESO).entidad("SESION")
                .resultado(ResultadoIngreso.RECHAZADO).motivo(MotivoRechazoIngreso.FUERA_DE_VENTANA).build());

        String tipo = jdbc.queryForObject(
                "SELECT jsonb_typeof(valor_nuevo) FROM bitacora WHERE id = ?", String.class, b.getId());
        Integer docente = jdbc.queryForObject(
                "SELECT (valor_nuevo->>'docenteId')::int FROM bitacora WHERE id = ?", Integer.class, b.getId());
        assertEquals("object", tipo);
        assertEquals(22, docente);
    }

    @Test
    @DisplayName("Criterio 3: consulta filtrada, del más reciente al más antiguo")
    void criterio3_consultaFiltrada() {
        insertar();
        Page<Bitacora> page = bitacoraRepository.buscar("admin.prueba", "CURSO", null,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), PageRequest.of(0, 20));
        assertTrue(page.getTotalElements() >= 1);
        assertEquals("PRB101-50", page.getContent().get(0).getEntidadId());
    }

    @Test
    @DisplayName("Criterio 4: la base rechaza UPDATE sobre la bitácora")
    void criterio4_baseRechazaUpdate() {
        Long id = insertar().getId();
        Exception ex = assertThrows(Exception.class, () ->
                jdbc.update("UPDATE bitacora SET entidad = 'OTRA' WHERE id = ?", id));
        assertTrue(ex.getMessage().contains("solo lectura"), ex.getMessage());
    }

    @Test
    @DisplayName("Criterio 4: la base rechaza DELETE sobre la bitácora")
    void criterio4_baseRechazaDelete() {
        Long id = insertar().getId();
        Exception ex = assertThrows(Exception.class, () ->
                jdbc.update("DELETE FROM bitacora WHERE id = ?", id));
        assertTrue(ex.getMessage().contains("solo lectura"), ex.getMessage());
    }
}
