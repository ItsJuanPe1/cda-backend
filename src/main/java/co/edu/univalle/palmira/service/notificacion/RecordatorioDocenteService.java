package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.ACTUALIZACION_PLAZO_DOCENTE;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.APERTURA_PLAZO_DOCENTE;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.RECORDATORIO_CIERRE_DOCENTE;

/**
 * HU-038: recordatorios del plazo de disponibilidad docente.
 * <ul>
 *   <li>Apertura: en {@code plazo_docente_inicio} (criterio 1).</li>
 *   <li>Recordatorio: {@code RECORDATORIO_DIAS_ANTES} días antes de {@code plazo_docente_fin}, a la misma
 *       hora (criterio 2). Nunca antes de la apertura y, si ese momento ya pasó con el plazo abierto,
 *       de inmediato (supuestos).</li>
 *   <li>Actualización: de inmediato, cuando cambian las fechas del plazo (criterio 3).</li>
 * </ul>
 * Fechas en UTC; en los correos se muestran en {@code APP_TIMEZONE} (America/Bogota).
 */
@Service
public class RecordatorioDocenteService implements ManejadorNotificacion {

    public static final String MOTIVO_PERIODO_HISTORIAL = "PERIODO_HISTORIAL";
    public static final String MOTIVO_PERIODO_DESACTIVADO = "PERIODO_DESACTIVADO";
    public static final String MOTIVO_PLAZO_CERRADO = "PLAZO_CERRADO";
    /** La edición dejó el plazo ya abierto: el correo de actualización reemplaza a la apertura. */
    public static final String MOTIVO_REEMPLAZADA_POR_ACTUALIZACION = "REEMPLAZADA_POR_ACTUALIZACION";

    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO"));
    private static final Set<TipoEventoNotificacion> TIPOS =
            EnumSet.of(APERTURA_PLAZO_DOCENTE, RECORDATORIO_CIERRE_DOCENTE, ACTUALIZACION_PLAZO_DOCENTE);
    private static final String FIRMA = """
            Este es un mensaje automático del sistema de programación académica de la Universidad del Valle, sede Palmira.
            No responda a este correo.""";

    private final NotificacionService notificacionService;
    private final PeriodoAcademicoRepository periodoRepository;
    private final DocenteRepository docenteRepository;
    private final NotificacionesProperties propiedades;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public RecordatorioDocenteService(NotificacionService notificacionService,
            PeriodoAcademicoRepository periodoRepository, DocenteRepository docenteRepository,
            NotificacionesProperties propiedades, JsonMapper jsonMapper, Clock clock) {
        this.notificacionService = notificacionService;
        this.periodoRepository = periodoRepository;
        this.docenteRepository = docenteRepository;
        this.propiedades = propiedades;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Fechas del plazo antes y después de un cambio, en ISO-8601 UTC. */
    public record DatosActualizacion(String inicioAnterior, String finAnterior, String inicio, String fin) {
    }

    // ---------------------------------------------------------------- programación

    /** HU-002 criterio 13: programa la apertura y el recordatorio del período recién creado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void programarAlCrear(PeriodoAcademico periodo) {
        Instant ahora = clock.instant();
        calcularApertura(periodo.getPlazoDocenteInicio(), periodo.getPlazoDocenteFin(), ahora)
                .ifPresent(f -> notificacionService.programar(APERTURA_PLAZO_DOCENTE, periodo.getId(), f, null));
        calcularRecordatorio(periodo.getPlazoDocenteInicio(), periodo.getPlazoDocenteFin(), ahora)
                .ifPresent(f -> notificacionService.programar(RECORDATORIO_CIERRE_DOCENTE, periodo.getId(), f, null));
    }

    /**
     * HU-003 criterio 8 y criterio 3: si cambió el plazo docente, programa el correo de
     * actualización y reprograma el recordatorio. La apertura solo se reprograma o se programa si el
     * plazo todavía no ha abierto; si ya abrió, la actualización la reemplaza.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void alCambiarPlazo(PeriodoAcademico periodo, Instant inicioAnterior, Instant finAnterior) {
        Instant inicio = periodo.getPlazoDocenteInicio();
        Instant fin = periodo.getPlazoDocenteFin();
        if (java.util.Objects.equals(inicio, inicioAnterior) && java.util.Objects.equals(fin, finAnterior)) {
            return;
        }
        Long periodoId = periodo.getId();
        if (inicio == null || fin == null) {
            reprogramarOCancelar(notificacionService.pendientes(periodoId, APERTURA_PLAZO_DOCENTE),
                    Optional.empty(), MOTIVO_PLAZO_CERRADO);
            reprogramarOCancelar(notificacionService.pendientes(periodoId, RECORDATORIO_CIERRE_DOCENTE),
                    Optional.empty(), MOTIVO_PLAZO_CERRADO);
            return;
        }
        Instant ahora = clock.instant();

        // Apertura: solo si el plazo aún no ha abierto. Si ya abrió (o ya se envió), basta el correo de
        // actualización: así no llegan dos correos a la vez (decisión del 2026-11-21).
        Optional<Instant> apertura = inicio.isAfter(ahora) ? Optional.of(inicio) : Optional.empty();
        String motivoSinApertura = fin.isAfter(ahora) ? MOTIVO_REEMPLAZADA_POR_ACTUALIZACION : MOTIVO_PLAZO_CERRADO;
        List<Notificacion> aperturas = notificacionService.pendientes(periodoId, APERTURA_PLAZO_DOCENTE);
        if (!aperturas.isEmpty()) {
            reprogramarOCancelar(aperturas, apertura, motivoSinApertura);
        } else if (apertura.isPresent() && !notificacionService.yaProcesado(periodoId, APERTURA_PLAZO_DOCENTE)) {
            notificacionService.programar(APERTURA_PLAZO_DOCENTE, periodoId, apertura.get(), null);
        }

        // Recordatorio: se reprograma si sigue pendiente; si ya se envió y cambió el cierre, se programa otro.
        Optional<Instant> recordatorio = calcularRecordatorio(inicio, fin, ahora);
        List<Notificacion> recordatorios = notificacionService.pendientes(periodoId, RECORDATORIO_CIERRE_DOCENTE);
        if (!recordatorios.isEmpty()) {
            reprogramarOCancelar(recordatorios, recordatorio, MOTIVO_PLAZO_CERRADO);
        } else if (recordatorio.isPresent() && !fin.equals(finAnterior)) {
            notificacionService.programar(RECORDATORIO_CIERRE_DOCENTE, periodoId, recordatorio.get(), null);
        }

        DatosActualizacion datos = new DatosActualizacion(texto(inicioAnterior), texto(finAnterior),
                texto(inicio), texto(fin));
        notificacionService.programar(ACTUALIZACION_PLAZO_DOCENTE, periodoId, ahora, jsonMapper.writeValueAsString(datos));
    }

    /** Criterio 7 y HU-004 criterio 5: cancela los avisos pendientes del período. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelarPendientes(Long periodoId, String motivo) {
        return notificacionService.cancelarPendientesDelPeriodo(periodoId, motivo);
    }

    /** Apertura en el inicio del plazo; de inmediato si ya abrió; nada si ya cerró. */
    static Optional<Instant> calcularApertura(Instant inicio, Instant fin, Instant ahora) {
        if (inicio == null || fin == null || !fin.isAfter(ahora)) {
            return Optional.empty();
        }
        return Optional.of(max(inicio, ahora));
    }

    /** Recordatorio N días antes del cierre, a la misma hora; nunca antes de la apertura ni en el pasado. */
    Optional<Instant> calcularRecordatorio(Instant inicio, Instant fin, Instant ahora) {
        return CalculoAvisos.recordatorio(inicio, fin, ahora, propiedades.getRecordatorioDiasAntes(), propiedades.zoneId());
    }

    private void reprogramarOCancelar(List<Notificacion> pendientes, Optional<Instant> nuevaFecha, String motivo) {
        for (int i = 0; i < pendientes.size(); i++) {
            Notificacion n = pendientes.get(i);
            if (i == 0 && nuevaFecha.isPresent()) {
                notificacionService.reprogramar(n, nuevaFecha.get());
            } else {
                notificacionService.cancelar(n, motivo);
            }
        }
    }

    // ---------------------------------------------------------------- ManejadorNotificacion

    @Override
    public boolean atiende(TipoEventoNotificacion tipo) {
        return TIPOS.contains(tipo);
    }

    @Override
    public TipoDestinatario tipoDestinatario() {
        return TipoDestinatario.DOCENTE;
    }

    /**
     * Criterio 6: solo con el período vigente. La apertura y el recordatorio tampoco salen si el
     * plazo ya cerró (por ejemplo, si el servidor estuvo apagado).
     */
    @Override
    public boolean eventoVigente(Notificacion notificacion) {
        Optional<PeriodoAcademico> periodo = periodo(notificacion);
        if (periodo.isEmpty()) {
            return false;
        }
        PeriodoAcademico p = periodo.get();
        if (notificacion.getTipoEvento() != ACTUALIZACION_PLAZO_DOCENTE
                && (p.getPlazoDocenteFin() == null || !p.getPlazoDocenteFin().isAfter(clock.instant()))) {
            return false;
        }
        return true;
    }

    /** Criterio 4: todos los docentes activos, hayan o no registrado disponibilidad. */
    @Override
    public List<Long> resolverDestinatarios(Notificacion notificacion) {
        return docenteRepository.findIdsDestinatariosNotificacion();
    }

    /** Criterio 5, HU-030 y HU-015 criterio 9: estado y correo evaluados al enviar. */
    @Override
    public Optional<DestinatarioCorreo> resolverDestinatario(Long destinatarioId) {
        if (destinatarioId == null) {
            return Optional.empty();
        }
        return docenteRepository.findDestinatarioNotificacion(destinatarioId).map(RecordatorioDocenteService::aDestinatario);
    }

    @Override
    public List<DestinatarioCorreo> describirDestinatarios(List<Long> destinatarioIds) {
        if (destinatarioIds == null || destinatarioIds.isEmpty()) {
            return List.of();
        }
        return docenteRepository.findAllById(destinatarioIds).stream()
                .sorted(Comparator.comparing((Docente d) -> nulo(d.getApellidos()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(d -> nulo(d.getNombres()), String.CASE_INSENSITIVE_ORDER))
                .map(RecordatorioDocenteService::aDestinatario)
                .toList();
    }

    @Override
    public CorreoContenido construir(Notificacion notificacion, DestinatarioCorreo destinatario) {
        PeriodoAcademico p = periodo(notificacion).orElseThrow();
        String saludo = "Hola, " + destinatario.nombre() + ":\n\n";
        return new CorreoContenido(asunto(notificacion, p), saludo + cuerpo(notificacion, p));
    }

    @Override
    public CorreoContenido construirCopia(Notificacion notificacion, List<DestinatarioCorreo> destinatarios, long omitidos) {
        PeriodoAcademico p = periodo(notificacion).orElseThrow();
        StringBuilder sb = new StringBuilder();
        sb.append("Copia del correo enviado por el sistema de programación académica.\n\n");
        sb.append("Evento: ").append(nombreEvento(notificacion.getTipoEvento())).append('\n');
        sb.append("Período académico: ").append(p.getNombre()).append('\n');
        sb.append("Enviado: ").append(formatear(clock.instant())).append(" (hora de Colombia)\n");
        sb.append("Destinatarios: ").append(destinatarios.size()).append('\n');
        sb.append("Omitidos por perfil desactivado: ").append(omitidos).append("\n\n");
        sb.append("Mensaje enviado:\n----------------------------------------\n");
        sb.append(cuerpo(notificacion, p));
        sb.append("\n----------------------------------------\n\n");
        sb.append("Lista de destinatarios:\n");
        if (destinatarios.isEmpty()) {
            sb.append("(ninguno)\n");
        }
        for (DestinatarioCorreo d : destinatarios) {
            sb.append("- ").append(d.nombre()).append(" <").append(d.correo()).append(">\n");
        }
        return new CorreoContenido("[Copia] " + asunto(notificacion, p), sb.toString());
    }

    // ---------------------------------------------------------------- textos

    private String asunto(Notificacion n, PeriodoAcademico p) {
        return switch (n.getTipoEvento()) {
            case APERTURA_PLAZO_DOCENTE -> "Plazo abierto para registrar su disponibilidad – " + p.getNombre();
            case RECORDATORIO_CIERRE_DOCENTE -> "Recordatorio: el plazo para registrar su disponibilidad cierra el "
                    + formatear(p.getPlazoDocenteFin());
            case ACTUALIZACION_PLAZO_DOCENTE -> "Cambio en las fechas para registrar su disponibilidad – " + p.getNombre();
            default -> throw new IllegalArgumentException("Evento no atendido por HU-038: " + n.getTipoEvento());
        };
    }

    /** Cuerpo sin saludo; se comparte con la copia. */
    private String cuerpo(Notificacion n, PeriodoAcademico p) {
        StringBuilder sb = new StringBuilder();
        switch (n.getTipoEvento()) {
            case APERTURA_PLAZO_DOCENTE -> {
                sb.append("Se abrió el plazo para registrar su disponibilidad horaria del período académico ")
                        .append(p.getNombre()).append(".\n\n");
                sb.append("Inicio del plazo: ").append(formatear(p.getPlazoDocenteInicio())).append('\n');
                sb.append("Cierre del plazo: ").append(formatear(p.getPlazoDocenteFin())).append('\n');
                sb.append("(hora de Colombia)\n");
            }
            case RECORDATORIO_CIERRE_DOCENTE -> {
                sb.append("El plazo para registrar su disponibilidad horaria del período académico ")
                        .append(p.getNombre()).append(" cierra el ").append(formatear(p.getPlazoDocenteFin()))
                        .append(" (hora de Colombia).\n\n");
                sb.append("Si ya la registró, puede revisarla o editarla hasta esa fecha.\n");
            }
            case ACTUALIZACION_PLAZO_DOCENTE -> {
                DatosActualizacion d = datos(n, p);
                sb.append("El administrador actualizó las fechas del plazo para registrar su disponibilidad horaria del período académico ")
                        .append(p.getNombre()).append(".\n\n");
                sb.append("Antes: ").append(formatear(d.inicioAnterior())).append(" – ").append(formatear(d.finAnterior())).append('\n');
                sb.append("Ahora: ").append(formatear(d.inicio())).append(" – ").append(formatear(d.fin())).append('\n');
                sb.append("(hora de Colombia)\n");
            }
            default -> throw new IllegalArgumentException("Evento no atendido por HU-038: " + n.getTipoEvento());
        }
        String url = propiedades.getAppUrl();
        if (url != null && !url.isBlank()) {
            sb.append("\nIngrese al sistema: ").append(url.trim()).append('\n');
        }
        sb.append('\n').append(FIRMA).append('\n');
        return sb.toString();
    }

    private DatosActualizacion datos(Notificacion n, PeriodoAcademico p) {
        if (n.getDatos() != null && !n.getDatos().isBlank()) {
            return jsonMapper.readValue(n.getDatos(), DatosActualizacion.class);
        }
        String inicio = texto(p.getPlazoDocenteInicio());
        String fin = texto(p.getPlazoDocenteFin());
        return new DatosActualizacion(inicio, fin, inicio, fin);
    }

    private static String nombreEvento(TipoEventoNotificacion tipo) {
        return switch (tipo) {
            case APERTURA_PLAZO_DOCENTE -> "Apertura del plazo de disponibilidad docente";
            case RECORDATORIO_CIERRE_DOCENTE -> "Recordatorio de cierre del plazo de disponibilidad docente";
            case ACTUALIZACION_PLAZO_DOCENTE -> "Actualización de las fechas del plazo de disponibilidad docente";
            default -> tipo.name();
        };
    }

    String formatear(Instant instante) {
        return instante == null ? "—" : FORMATO.format(instante.atZone(propiedades.zoneId()));
    }

    private String formatear(String iso) {
        return iso == null ? "—" : formatear(Instant.parse(iso));
    }

    // ---------------------------------------------------------------- utilidades

    /** Período vigente del evento (criterio 6). */
    private Optional<PeriodoAcademico> periodo(Notificacion n) {
        if (n.getPeriodoId() == null) {
            return Optional.empty();
        }
        return periodoRepository.findById(n.getPeriodoId())
                .filter(p -> p.isVigente() && "VIGENTE".equalsIgnoreCase(p.getEstado()));
    }

    private static DestinatarioCorreo aDestinatario(Docente d) {
        String nombre = (nulo(d.getNombres()) + " " + nulo(d.getApellidos())).trim();
        return new DestinatarioCorreo(d.getId(), nombre, d.getCorreoInstitucional());
    }

    private static String nulo(String s) {
        return s == null ? "" : s.trim();
    }

    private static String texto(Instant i) {
        return i == null ? null : i.toString();
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
