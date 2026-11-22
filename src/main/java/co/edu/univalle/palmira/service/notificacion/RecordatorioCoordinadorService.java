package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.ACTUALIZACION_PLAZO_COORDINADOR;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.CONFIGURACION_PLAZO_COORDINADOR;
import static co.edu.univalle.palmira.model.enums.TipoEventoNotificacion.RECORDATORIO_CIERRE_COORDINADOR;

/**
 * HU-039: recordatorios del plazo de asignación de docentes para los coordinadores. Solo tres correos:
 * <ul>
 *   <li>a) Configuración: de inmediato, al guardar el plazo de coordinadores (al crear o al definirlo).</li>
 *   <li>b) Actualización: de inmediato, al cambiar el plazo, con las fechas antes y después.</li>
 *   <li>c) Recordatorio: {@code RECORDATORIO_DIAS_ANTES} días antes del cierre; se omite si la asignación
 *       está completa (criterio 3).</li>
 * </ul>
 * Todos incluyen las fechas de adición y cancelación y el aviso de solo consulta (criterio 2).
 * Destinatario: la cuenta de coordinación ({@code coordinadores.correo_institucional}); una cuenta con
 * varios programas recibe un solo correo.
 */
@Service
public class RecordatorioCoordinadorService implements ManejadorNotificacion {

    public static final String MOTIVO_PLAZO_SIN_FECHAS = "PLAZO_SIN_FECHAS";
    public static final String MOTIVO_ASIGNACION_COMPLETA = "Asignación completa";
    static final String AVISO_ADICION_CANCELACION =
            "Durante las fechas de adición y cancelación solo se consulta la programación; no se cambian asignaciones.";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-CO"));
    private static final Set<TipoEventoNotificacion> TIPOS = EnumSet.of(CONFIGURACION_PLAZO_COORDINADOR,
            ACTUALIZACION_PLAZO_COORDINADOR, RECORDATORIO_CIERRE_COORDINADOR);
    private static final String FIRMA = """
            Este es un mensaje automático del sistema de programación académica de la Universidad del Valle, sede Palmira.
            No responda a este correo.""";

    private final NotificacionService notificacionService;
    private final PeriodoAcademicoRepository periodoRepository;
    private final CoordinadorRepository coordinadorRepository;
    private final CursoProgramacionRepository cursoRepository;
    private final NotificacionesProperties propiedades;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public RecordatorioCoordinadorService(NotificacionService notificacionService,
            PeriodoAcademicoRepository periodoRepository, CoordinadorRepository coordinadorRepository,
            CursoProgramacionRepository cursoRepository, NotificacionesProperties propiedades,
            JsonMapper jsonMapper, Clock clock) {
        this.notificacionService = notificacionService;
        this.periodoRepository = periodoRepository;
        this.coordinadorRepository = coordinadorRepository;
        this.cursoRepository = cursoRepository;
        this.propiedades = propiedades;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- programación

    /** HU-002 criterio 13: si el período trae plazo de coordinadores, avisa ya (a) y programa el recordatorio (c). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void programarAlCrear(PeriodoAcademico periodo) {
        programarConfiguracion(periodo, clock.instant());
    }

    /**
     * HU-003 criterio 8: vacío → con fechas, igual que al crear; fechas cambiadas, correo de actualización
     * (b) y recordatorio reprogramado; con fechas → vacío, se cancela el recordatorio sin enviar correo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void alCambiarPlazo(PeriodoAcademico periodo, Instant inicioAnterior, Instant finAnterior) {
        Instant inicio = periodo.getPlazoCoordinadorInicio();
        Instant fin = periodo.getPlazoCoordinadorFin();
        if (Objects.equals(inicio, inicioAnterior) && Objects.equals(fin, finAnterior)) {
            return;
        }
        Instant ahora = clock.instant();
        Long periodoId = periodo.getId();
        boolean teniaFechas = inicioAnterior != null && finAnterior != null;
        boolean tieneFechas = inicio != null && fin != null;

        if (!tieneFechas) {
            cancelarPendientes(periodoId, CONFIGURACION_PLAZO_COORDINADOR, MOTIVO_PLAZO_SIN_FECHAS);
            cancelarPendientes(periodoId, RECORDATORIO_CIERRE_COORDINADOR, MOTIVO_PLAZO_SIN_FECHAS);
            return;
        }
        if (!teniaFechas) {
            programarConfiguracion(periodo, ahora);
            return;
        }

        // Recordatorio: se reprograma si sigue pendiente; si ya salió y cambió el cierre, se programa otro.
        Optional<Instant> recordatorio = calcularRecordatorio(inicio, fin, ahora);
        List<Notificacion> recordatorios = notificacionService.pendientes(periodoId, RECORDATORIO_CIERRE_COORDINADOR);
        if (!recordatorios.isEmpty()) {
            for (int i = 0; i < recordatorios.size(); i++) {
                if (i == 0 && recordatorio.isPresent()) {
                    notificacionService.reprogramar(recordatorios.get(i), recordatorio.get());
                } else {
                    notificacionService.cancelar(recordatorios.get(i), RecordatorioDocenteService.MOTIVO_PLAZO_CERRADO);
                }
            }
        } else if (recordatorio.isPresent() && !fin.equals(finAnterior)) {
            notificacionService.programar(RECORDATORIO_CIERRE_COORDINADOR, periodoId, recordatorio.get(), null);
        }

        // b) Si el aviso de configuración aún no salió, ya llevará las fechas nuevas: no se duplica.
        if (!notificacionService.pendientes(periodoId, CONFIGURACION_PLAZO_COORDINADOR).isEmpty()) {
            return;
        }
        RecordatorioDocenteService.DatosActualizacion datos = new RecordatorioDocenteService.DatosActualizacion(
                inicioAnterior.toString(), finAnterior.toString(), inicio.toString(), fin.toString());
        notificacionService.programar(ACTUALIZACION_PLAZO_COORDINADOR, periodoId, ahora, jsonMapper.writeValueAsString(datos));
    }

    private void programarConfiguracion(PeriodoAcademico periodo, Instant ahora) {
        Instant inicio = periodo.getPlazoCoordinadorInicio();
        Instant fin = periodo.getPlazoCoordinadorFin();
        if (inicio == null || fin == null || !fin.isAfter(ahora)) {
            return;
        }
        notificacionService.programar(CONFIGURACION_PLAZO_COORDINADOR, periodo.getId(), ahora, null);
        calcularRecordatorio(inicio, fin, ahora).ifPresent(
                f -> notificacionService.programar(RECORDATORIO_CIERRE_COORDINADOR, periodo.getId(), f, null));
    }

    private void cancelarPendientes(Long periodoId, TipoEventoNotificacion tipo, String motivo) {
        notificacionService.pendientes(periodoId, tipo).forEach(n -> notificacionService.cancelar(n, motivo));
    }

    Optional<Instant> calcularRecordatorio(Instant inicio, Instant fin, Instant ahora) {
        return CalculoAvisos.recordatorio(inicio, fin, ahora, propiedades.getRecordatorioDiasAntes(), propiedades.zoneId());
    }

    // ---------------------------------------------------------------- ManejadorNotificacion

    @Override
    public boolean atiende(TipoEventoNotificacion tipo) {
        return TIPOS.contains(tipo);
    }

    @Override
    public TipoDestinatario tipoDestinatario() {
        return TipoDestinatario.COORDINADOR;
    }

    /** Período vigente con plazo de coordinadores; el recordatorio, además, con el plazo aún abierto. */
    @Override
    public boolean eventoVigente(Notificacion notificacion) {
        Optional<PeriodoAcademico> periodo = periodo(notificacion);
        if (periodo.isEmpty()) {
            return false;
        }
        PeriodoAcademico p = periodo.get();
        if (p.getPlazoCoordinadorInicio() == null || p.getPlazoCoordinadorFin() == null) {
            return false;
        }
        return notificacion.getTipoEvento() != RECORDATORIO_CIERRE_COORDINADOR
                || p.getPlazoCoordinadorFin().isAfter(clock.instant());
    }

    /** Una cuenta de coordinación por destinatario: se usa el id de su primera coordinación activa. */
    @Override
    public List<Long> resolverDestinatarios(Notificacion notificacion) {
        return cuentas(coordinadorRepository.findActivosParaNotificacion()).values().stream()
                .map(grupo -> grupo.get(0).getId().longValue())
                .toList();
    }

    /** Correo y programas vigentes de la cuenta, evaluados al enviar; vacío si la coordinación ya no está activa. */
    @Override
    public Optional<DestinatarioCorreo> resolverDestinatario(Long destinatarioId) {
        return grupoDe(destinatarioId, coordinadorRepository.findActivosParaNotificacion())
                .map(grupo -> aDestinatario(destinatarioId, grupo));
    }

    @Override
    public List<DestinatarioCorreo> describirDestinatarios(List<Long> destinatarioIds) {
        if (destinatarioIds == null || destinatarioIds.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = destinatarioIds.stream().map(Long::intValue).toList();
        List<Coordinador> coordinaciones = coordinadorRepository.findAllById(ids);
        List<Coordinador> activas = coordinadorRepository.findActivosParaNotificacion();
        List<DestinatarioCorreo> resultado = new ArrayList<>();
        for (Coordinador c : coordinaciones) {
            List<Coordinador> grupo = grupoDe(c.getId().longValue(), activas).orElse(List.of(c));
            resultado.add(aDestinatario(c.getId().longValue(), grupo));
        }
        return resultado;
    }

    /** Criterio 3: el recordatorio se omite si ninguna materia no excluida de sus programas queda sin docente. */
    @Override
    public Optional<String> motivoOmision(Notificacion notificacion, Long destinatarioId) {
        if (notificacion.getTipoEvento() != RECORDATORIO_CIERRE_COORDINADOR) {
            return Optional.empty();
        }
        Optional<List<Coordinador>> grupo = grupoDe(destinatarioId, coordinadorRepository.findActivosParaNotificacion());
        if (grupo.isEmpty()) {
            return Optional.empty();
        }
        List<String> codigos = grupo.get().stream().map(c -> c.getPrograma().getCodeProgram()).distinct().toList();
        long activas = cursoRepository.contarActivosPorProgramas(notificacion.getPeriodoId(), codigos);
        if (activas == 0) {
            return Optional.empty();
        }
        long sinDocente = cursoRepository.contarSinDocentePorProgramas(notificacion.getPeriodoId(), codigos);
        return sinDocente == 0 ? Optional.of(MOTIVO_ASIGNACION_COMPLETA) : Optional.empty();
    }

    @Override
    public CorreoContenido construir(Notificacion notificacion, DestinatarioCorreo destinatario) {
        PeriodoAcademico p = periodo(notificacion).orElseThrow();
        return new CorreoContenido(asunto(notificacion, p), "Hola, " + destinatario.nombre() + ":\n\n" + cuerpo(notificacion, p));
    }

    @Override
    public CorreoContenido construirCopia(Notificacion notificacion, List<DestinatarioCorreo> destinatarios, long omitidos) {
        return construirCopia(notificacion, destinatarios, Map.of(NotificacionProcesador.MOTIVO_PERFIL_DESACTIVADO, omitidos));
    }

    @Override
    public CorreoContenido construirCopia(Notificacion notificacion, List<DestinatarioCorreo> destinatarios,
            Map<String, Long> omitidosPorMotivo) {
        PeriodoAcademico p = periodo(notificacion).orElseThrow();
        Map<String, Long> omitidos = omitidosPorMotivo == null ? Map.of() : omitidosPorMotivo;
        long desactivados = omitidos.getOrDefault(NotificacionProcesador.MOTIVO_PERFIL_DESACTIVADO, 0L);
        long completos = omitidos.getOrDefault(MOTIVO_ASIGNACION_COMPLETA, 0L);
        StringBuilder sb = new StringBuilder();
        sb.append("Copia del correo enviado por el sistema de programación académica.\n\n");
        sb.append("Evento: ").append(nombreEvento(notificacion.getTipoEvento())).append('\n');
        sb.append("Período académico: ").append(p.getNombre()).append('\n');
        sb.append("Enviado: ").append(formatear(clock.instant())).append(" (hora de Colombia)\n");
        sb.append("Destinatarios: ").append(destinatarios.size()).append('\n');
        sb.append("Omitidos por perfil desactivado: ").append(desactivados).append('\n');
        if (notificacion.getTipoEvento() == RECORDATORIO_CIERRE_COORDINADOR) {
            sb.append("Omitidos por asignación completa: ").append(completos).append('\n');
        }
        sb.append("\nMensaje enviado:\n----------------------------------------\n");
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
            case CONFIGURACION_PLAZO_COORDINADOR -> "Fechas para asignar docentes – " + p.getNombre();
            case ACTUALIZACION_PLAZO_COORDINADOR -> "Cambio en las fechas para asignar docentes – " + p.getNombre();
            case RECORDATORIO_CIERRE_COORDINADOR -> "Recordatorio: el plazo para asignar docentes cierra el "
                    + formatear(p.getPlazoCoordinadorFin());
            default -> throw new IllegalArgumentException("Evento no atendido por HU-039: " + n.getTipoEvento());
        };
    }

    /** Cuerpo sin saludo; se comparte con la copia. */
    private String cuerpo(Notificacion n, PeriodoAcademico p) {
        StringBuilder sb = new StringBuilder();
        switch (n.getTipoEvento()) {
            case CONFIGURACION_PLAZO_COORDINADOR -> {
                sb.append("El administrador definió el plazo para asignar docentes a las asignaturas de su programa en el período académico ")
                        .append(p.getNombre()).append(".\n\n");
                sb.append("Inicio del plazo: ").append(formatear(p.getPlazoCoordinadorInicio())).append('\n');
                sb.append("Cierre del plazo: ").append(formatear(p.getPlazoCoordinadorFin())).append('\n');
                sb.append("(hora de Colombia)\n");
            }
            case ACTUALIZACION_PLAZO_COORDINADOR -> {
                RecordatorioDocenteService.DatosActualizacion d = datos(n, p);
                sb.append("El administrador actualizó las fechas del plazo para asignar docentes del período académico ")
                        .append(p.getNombre()).append(".\n\n");
                sb.append("Antes: ").append(formatear(d.inicioAnterior())).append(" – ").append(formatear(d.finAnterior())).append('\n');
                sb.append("Ahora: ").append(formatear(d.inicio())).append(" – ").append(formatear(d.fin())).append('\n');
                sb.append("(hora de Colombia)\n");
            }
            case RECORDATORIO_CIERRE_COORDINADOR -> {
                sb.append("El plazo para asignar docentes a las asignaturas de su programa en el período académico ")
                        .append(p.getNombre()).append(" cierra el ").append(formatear(p.getPlazoCoordinadorFin()))
                        .append(" (hora de Colombia). Después de esa fecha solo podrá consultar la programación.\n");
            }
            default -> throw new IllegalArgumentException("Evento no atendido por HU-039: " + n.getTipoEvento());
        }
        // Criterio 2: fechas de adición y cancelación con el aviso de solo consulta.
        sb.append('\n');
        sb.append("Fechas de adición: ").append(fecha(p.getFechaAdicionInicio())).append(" – ")
                .append(fecha(p.getFechaAdicionFin())).append('\n');
        sb.append("Fechas de cancelación: ").append(fecha(p.getFechaCancelacionInicio())).append(" – ")
                .append(fecha(p.getFechaCancelacionFin())).append('\n');
        sb.append(AVISO_ADICION_CANCELACION).append('\n');
        String url = propiedades.getAppUrl();
        if (url != null && !url.isBlank()) {
            sb.append("\nIngrese al sistema: ").append(url.trim()).append('\n');
        }
        sb.append('\n').append(FIRMA).append('\n');
        return sb.toString();
    }

    private RecordatorioDocenteService.DatosActualizacion datos(Notificacion n, PeriodoAcademico p) {
        if (n.getDatos() != null && !n.getDatos().isBlank()) {
            return jsonMapper.readValue(n.getDatos(), RecordatorioDocenteService.DatosActualizacion.class);
        }
        String inicio = texto(p.getPlazoCoordinadorInicio());
        String fin = texto(p.getPlazoCoordinadorFin());
        return new RecordatorioDocenteService.DatosActualizacion(inicio, fin, inicio, fin);
    }

    private static String nombreEvento(TipoEventoNotificacion tipo) {
        return switch (tipo) {
            case CONFIGURACION_PLAZO_COORDINADOR -> "Configuración del plazo de asignación de docentes";
            case ACTUALIZACION_PLAZO_COORDINADOR -> "Actualización de las fechas del plazo de asignación de docentes";
            case RECORDATORIO_CIERRE_COORDINADOR -> "Recordatorio de cierre del plazo de asignación de docentes";
            default -> tipo.name();
        };
    }

    String formatear(Instant instante) {
        return instante == null ? "—" : RecordatorioDocenteService.FORMATO.format(instante.atZone(propiedades.zoneId()));
    }

    private String formatear(String iso) {
        return iso == null ? "—" : formatear(Instant.parse(iso));
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "—" : FECHA.format(fecha);
    }

    // ---------------------------------------------------------------- utilidades

    private Optional<PeriodoAcademico> periodo(Notificacion n) {
        if (n.getPeriodoId() == null) {
            return Optional.empty();
        }
        return periodoRepository.findById(n.getPeriodoId())
                .filter(p -> p.isVigente() && "VIGENTE".equalsIgnoreCase(p.getEstado()));
    }

    /** Coordinaciones activas agrupadas por correo de cuenta (sin mayúsculas ni espacios), en orden de id. */
    static Map<String, List<Coordinador>> cuentas(List<Coordinador> activas) {
        Map<String, List<Coordinador>> grupos = new LinkedHashMap<>();
        for (Coordinador c : activas) {
            grupos.computeIfAbsent(clave(c.getCorreoInstitucional()), k -> new ArrayList<>()).add(c);
        }
        return grupos;
    }

    /** Grupo de la cuenta a la que pertenece la coordinación {@code id}, si sigue activa. */
    private static Optional<List<Coordinador>> grupoDe(Long id, List<Coordinador> activas) {
        if (id == null) {
            return Optional.empty();
        }
        return activas.stream()
                .filter(c -> c.getId() != null && c.getId().longValue() == id)
                .findFirst()
                .map(c -> cuentas(activas).get(clave(c.getCorreoInstitucional())));
    }

    private static DestinatarioCorreo aDestinatario(Long id, List<Coordinador> grupo) {
        List<String> programas = grupo.stream().map(c -> etiqueta(c.getPrograma())).distinct().toList();
        String nombre = "coordinación de " + unir(programas);
        return new DestinatarioCorreo(id, nombre, grupo.get(0).getCorreoInstitucional().trim());
    }

    static String etiqueta(Programa programa) {
        if (programa == null) {
            return "su programa";
        }
        String jornada = programa.getJornada() == null ? "" : switch (programa.getJornada()) {
            case DIURNA -> " (diurna)";
            case NOCTURNA -> " (nocturna)";
            case AMBAS -> " (diurna y nocturna)";
        };
        return programa.getNameProgram() + jornada;
    }

    private static String unir(List<String> partes) {
        if (partes.size() <= 1) {
            return partes.isEmpty() ? "" : partes.get(0);
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    private static String texto(Instant i) {
        return i == null ? null : i.toString();
    }
}
