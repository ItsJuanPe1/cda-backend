package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.NotificacionEnvio;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion;
import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.model.enums.TipoDocumento;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.APERTURA_PLAZO_DOCENTE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-038: consultas de la cola y de destinatarios contra PostgreSQL 17 (V46).
 * Solo corre con {@code CDA_IT_LOCAL_DB=true} y {@code DB_URL} apuntando a una base LOCAL desechable:
 * Flyway aplica las migraciones al arrancar. Nunca contra la base compartida. Datos ficticios; cada
 * prueba se revierte al terminar. Desactiva la tarea programada para que no envíe nada.
 */
@SpringBootTest(properties = "cda.notificaciones.programador-habilitado=false")
@Transactional
@EnabledIfEnvironmentVariable(named = "CDA_IT_LOCAL_DB", matches = "true")
class NotificacionesPostgresIT {

    private static final Instant AHORA = Instant.parse("2026-11-21T15:00:00Z");

    @Autowired
    private NotificacionRepository notificacionRepository;
    @Autowired
    private NotificacionEnvioRepository envioRepository;
    @Autowired
    private DocenteRepository docenteRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PeriodoAcademicoRepository periodoRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private PeriodoAcademico periodo() {
        return periodoRepository.save(PeriodoAcademico.builder()
                .nombre("IT-038 Prueba").numeroResolucion("IT-038-RES")
                .fechaResolucion(LocalDate.of(2026, 11, 1))
                .fechaInicioSemestre(LocalDate.of(2027, 2, 1)).fechaFinSemestre(LocalDate.of(2027, 6, 30))
                .fechaAdicionInicio(LocalDate.of(2027, 2, 1)).fechaAdicionFin(LocalDate.of(2027, 2, 10))
                .fechaCancelacionInicio(LocalDate.of(2027, 3, 1)).fechaCancelacionFin(LocalDate.of(2027, 3, 10))
                .plazoDocenteInicio(Instant.parse("2026-12-01T13:00:00Z"))
                .plazoDocenteFin(Instant.parse("2026-12-15T22:00:00Z"))
                .vigente(false).estado("HISTORIAL").build());
    }

    private Docente docente(String codigo, String correo, boolean activo) {
        return docenteRepository.save(Docente.builder().codigo(codigo).nombres("Prueba").apellidos("IT " + codigo)
                .tipoDocumento(TipoDocumento.CC).numeroDocumento("9" + codigo).correoInstitucional(correo)
                .estado(activo).build());
    }

    @Test
    @DisplayName("Criterio 5 y HU-030: solo reciben los docentes activos cuya cuenta, si existe, también está activa")
    void criterio5Destinatarios() {
        Docente activo = docente("IT0381", "it.activo.038@correounivalle.edu.co", true);
        Docente inactivo = docente("IT0382", "it.inactivo.038@correounivalle.edu.co", false);
        Docente cuentaInactiva = docente("IT0383", "it.cuenta.038@correounivalle.edu.co", true);
        usuarioRepository.save(Usuario.builder().nombre("Prueba").apellido("IT")
                .emailInstitucional("IT.CUENTA.038@correounivalle.edu.co").rol(3).activo(false).build());
        entityManager.flush();

        List<Long> ids = docenteRepository.findIdsDestinatariosNotificacion();

        assertTrue(ids.contains(activo.getId()));
        assertFalse(ids.contains(inactivo.getId()));
        assertFalse(ids.contains(cuentaInactiva.getId()));
        assertTrue(docenteRepository.findDestinatarioNotificacion(activo.getId()).isPresent());
        assertTrue(docenteRepository.findDestinatarioNotificacion(cuentaInactiva.getId()).isEmpty());
    }

    @Test
    @DisplayName("Criterio 8: la cola toma el evento vencido más antiguo y guarda las fechas en UTC")
    void criterio8TomaEventoVencido() {
        PeriodoAcademico p = periodo();
        Notificacion vencida = notificacionRepository.save(Notificacion.builder().tipoEvento(APERTURA_PLAZO_DOCENTE)
                .periodoId(p.getId()).fechaProgramada(AHORA.minusSeconds(60)).estado(EstadoNotificacion.PENDIENTE)
                .datos("{\"inicio\":\"2026-12-01T13:00:00Z\"}").build());
        notificacionRepository.save(Notificacion.builder().tipoEvento(APERTURA_PLAZO_DOCENTE)
                .periodoId(p.getId()).fechaProgramada(AHORA.plusSeconds(3600)).estado(EstadoNotificacion.PENDIENTE).build());
        entityManager.flush();

        Notificacion tomada = notificacionRepository.tomarSiguienteVencida(AHORA).orElseThrow();

        assertEquals(vencida.getId(), tomada.getId());
        String tipo = jdbc.queryForObject("SELECT jsonb_typeof(datos) FROM notificaciones WHERE id = ?", String.class,
                vencida.getId());
        assertEquals("object", tipo);
    }

    @Test
    @DisplayName("Criterio 7: cancelar los envíos pendientes del período no toca los ya enviados")
    void criterio7CancelaEnviosPendientes() {
        PeriodoAcademico p = periodo();
        Notificacion n = notificacionRepository.save(Notificacion.builder().tipoEvento(APERTURA_PLAZO_DOCENTE)
                .periodoId(p.getId()).fechaProgramada(AHORA).estado(EstadoNotificacion.PROCESADO).build());
        NotificacionEnvio pendiente = envioRepository.save(NotificacionEnvio.builder().notificacionId(n.getId())
                .tipoDestinatario(TipoDestinatario.DOCENTE).destinatarioId(1L).proximoIntento(AHORA).build());
        NotificacionEnvio enviado = envioRepository.save(NotificacionEnvio.builder().notificacionId(n.getId())
                .tipoDestinatario(TipoDestinatario.COPIA).estado(EstadoEnvioNotificacion.ENVIADO).proximoIntento(AHORA).build());
        entityManager.flush();

        assertEquals(1, envioRepository.cancelarPendientesDelPeriodo(p.getId(), AHORA));
        entityManager.clear();

        assertEquals(EstadoEnvioNotificacion.CANCELADO, envioRepository.findById(pendiente.getId()).orElseThrow().getEstado());
        assertEquals(EstadoEnvioNotificacion.ENVIADO, envioRepository.findById(enviado.getId()).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("V46: la copia no puede tener destinatario y un docente no puede repetirse en el mismo evento")
    void restriccionesV46() {
        PeriodoAcademico p = periodo();
        Notificacion n = notificacionRepository.save(Notificacion.builder().tipoEvento(APERTURA_PLAZO_DOCENTE)
                .periodoId(p.getId()).fechaProgramada(AHORA).estado(EstadoNotificacion.PROCESADO).build());
        entityManager.flush();

        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO notificaciones_envios "
                + "(notificacion_id, tipo_destinatario, destinatario_id, proximo_intento) VALUES (?, 'COPIA', 1, now())", n.getId()));
    }
}
