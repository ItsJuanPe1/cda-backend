package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.BitacoraDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.model.entity.Bitacora;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
import co.edu.univalle.palmira.repository.BitacoraRepository;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * HU-058: bitácora de cambios e ingresos.
 * <p>
 * {@link #registrar} se llama desde otros servicios dentro de su misma transacción: si el registro
 * falla, la operación completa se revierte (no hay cambios sin rastro). {@link #registrarIngreso}
 * usa una transacción propia para que los ingresos rechazados queden registrados aunque el login
 * termine en excepción.
 */
@Service
public class BitacoraService {

    public static final String ENTIDAD_SESION = "SESION";
    public static final String ERROR_RANGO_FECHAS = "La fecha inicial no puede ser posterior a la fecha final";
    public static final String ERROR_PARAMETROS = "Parámetros de consulta inválidos";
    static final int TAMANO_MAXIMO = 100;

    private final BitacoraRepository bitacoraRepository;
    private final CoordinadorRepository coordinadorRepository;
    private final JsonMapper jsonMapper;
    private final ZoneId zona;

    public BitacoraService(BitacoraRepository bitacoraRepository,
            CoordinadorRepository coordinadorRepository,
            JsonMapper jsonMapper,
            @Value("${APP_TIMEZONE:America/Bogota}") String zona) {
        this.bitacoraRepository = bitacoraRepository;
        this.coordinadorRepository = coordinadorRepository;
        this.jsonMapper = jsonMapper;
        this.zona = ZoneId.of(zona);
    }

    /**
     * Criterio 1: registra un cambio hecho por el usuario de la sesión actual.
     * Exige una transacción activa del llamador (MANDATORY).
     *
     * @param valorAnterior objeto que se serializa a JSON (solo campos de negocio; nunca fotos ni tokens)
     * @param valorNuevo    objeto que se serializa a JSON
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Bitacora registrar(AccionBitacora accion, String entidad, String entidadId, Long periodoId,
            Object valorAnterior, Object valorNuevo) {
        if (accion == null || accion == AccionBitacora.INGRESO) {
            throw new IllegalArgumentException("Use registrarIngreso para los ingresos");
        }
        if (entidad == null || entidad.isBlank()) {
            throw new IllegalArgumentException("La entidad de la bitácora es obligatoria");
        }

        String correo = null;
        String rol = null;
        String nombreSesion = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            correo = jwt.getClaimAsString("email");
            rol = nombreRol(jwt.getClaim("rol"));
            nombreSesion = unir(jwt.getClaimAsString("nombre"), jwt.getClaimAsString("apellido"));
        }

        Bitacora registro = Bitacora.builder()
                .usuarioCorreo(correo)
                .usuarioRol(rol)
                .operadorNombre(operador(correo, rol, nombreSesion))
                .accion(accion)
                .entidad(entidad.trim().toUpperCase(Locale.ROOT))
                .entidadId(entidadId)
                .periodoId(periodoId)
                .valorAnterior(aJson(valorAnterior))
                .valorNuevo(aJson(valorNuevo))
                .build();
        return bitacoraRepository.save(registro);
    }

    /**
     * Criterio 2: registra un ingreso aceptado o rechazado. Nunca recibe el token.
     *
     * @param correo correo institucional verificado, o null si el rechazo ocurrió antes de verificarlo
     * @param rol    1 administrador, 2 coordinador, 3 docente, o null si no se conoce
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Bitacora registrarIngreso(String correo, Integer rol, ResultadoIngreso resultado,
            MotivoRechazoIngreso motivo) {
        if (resultado == null) {
            throw new IllegalArgumentException("El resultado del ingreso es obligatorio");
        }
        String nombreRol = nombreRol(rol);
        Bitacora registro = Bitacora.builder()
                .usuarioCorreo(correo)
                .usuarioRol(nombreRol)
                .operadorNombre(operador(correo, nombreRol, null))
                .accion(AccionBitacora.INGRESO)
                .entidad(ENTIDAD_SESION)
                .resultado(resultado)
                .motivo(resultado == ResultadoIngreso.RECHAZADO ? motivo : null)
                .build();
        return bitacoraRepository.save(registro);
    }

    /** Criterio 3: consulta con filtros, paginada, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public BitacoraDto.Pagina consultar(String usuario, String entidad, Long periodoId,
            String desde, String hasta, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new BadRequestException(ERROR_PARAMETROS);
        }
        LocalDate fechaDesde = aFecha(desde);
        LocalDate fechaHasta = aFecha(hasta);
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new BadRequestException(ERROR_RANGO_FECHAS);
        }
        // Los días se interpretan en la zona de la aplicación; en la base las fechas están en UTC.
        Instant inicio = fechaDesde != null ? fechaDesde.atStartOfDay(zona).toInstant() : null;
        Instant fin = fechaHasta != null ? fechaHasta.plusDays(1).atStartOfDay(zona).toInstant() : null;

        Page<Bitacora> resultado = bitacoraRepository.buscar(
                normalizar(usuario, false), normalizar(entidad, true), periodoId, inicio, fin,
                PageRequest.of(pagina, tamano));

        return new BitacoraDto.Pagina(
                resultado.getContent().stream().map(this::aResponse).toList(),
                pagina,
                tamano,
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    private BitacoraDto.Response aResponse(Bitacora b) {
        return new BitacoraDto.Response(
                b.getId(),
                b.getFecha(),
                b.getUsuarioCorreo(),
                b.getUsuarioRol(),
                b.getOperadorNombre(),
                b.getAccion(),
                b.getEntidad(),
                b.getEntidadId(),
                b.getPeriodoId(),
                aNodo(b.getValorAnterior()),
                aNodo(b.getValorNuevo()),
                b.getResultado(),
                b.getMotivo());
    }

    /** Decisión 2026-11-06: el operador de una cuenta de coordinación es el coordinador del programa. */
    private String operador(String correo, String rol, String nombreSesion) {
        if (!"COORDINADOR".equals(rol) || correo == null) {
            return null;
        }
        return coordinadorRepository.findByCorreoInstitucionalWithDetails(correo)
                .map(Coordinador::getNombreCompleto)
                .orElse(nombreSesion);
    }

    private static String nombreRol(Object rol) {
        if (!(rol instanceof Number n)) {
            return null;
        }
        return switch (n.intValue()) {
            case AuthConstants.ROL_ADMIN -> "ADMIN";
            case AuthConstants.ROL_COORDINADOR -> "COORDINADOR";
            case AuthConstants.ROL_DOCENTE -> "DOCENTE";
            default -> null;
        };
    }

    private String aJson(Object valor) {
        // Si falla la serialización se lanza la excepción y la operación del llamador se revierte.
        return valor == null ? null : jsonMapper.writeValueAsString(valor);
    }

    private JsonNode aNodo(String json) {
        return json == null ? null : jsonMapper.readTree(json);
    }

    private static LocalDate aFecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(ERROR_PARAMETROS);
        }
    }

    private static String normalizar(String valor, boolean mayusculas) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return mayusculas ? limpio.toUpperCase(Locale.ROOT) : limpio.toLowerCase(Locale.ROOT);
    }

    private static String unir(String nombre, String apellido) {
        String completo = ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
        return completo.isEmpty() ? null : completo;
    }
}
