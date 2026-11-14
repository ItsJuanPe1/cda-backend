package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.GoogleLoginRequest;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.exception.IngresoRechazadoException;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class GoogleAuthService {

    private final UsuarioRepository usuarioRepository;
    private final CoordinadorRepository coordinadorRepository;
    private final DocenteRepository docenteRepository;
    private final JwtEncoder jwtEncoder;
    private final String googleClientId;
    private final long expirationMinutes;
    private GoogleIdTokenVerifier tokenVerifier;
    /** HU-058. Se inyecta en producción; los constructores de prueba antiguos lo dejan en null. */
    private BitacoraService bitacoraService;

    @Autowired
    public GoogleAuthService(UsuarioRepository usuarioRepository,
                             CoordinadorRepository coordinadorRepository,
                             DocenteRepository docenteRepository,
                             JwtEncoder jwtEncoder,
                             BitacoraService bitacoraService,
                             @Value("${auth.google.client-id:699612946028-er8cpcrvspuh60933qvur66dqi07jgn4.apps.googleusercontent.com}") String googleClientId,
                             @Value("${auth.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.coordinadorRepository = coordinadorRepository;
        this.docenteRepository = docenteRepository;
        this.jwtEncoder = jwtEncoder;
        this.googleClientId = (googleClientId != null && !googleClientId.isBlank())
                ? googleClientId
                : "699612946028-er8cpcrvspuh60933qvur66dqi07jgn4.apps.googleusercontent.com";
        this.expirationMinutes = expirationMinutes;
        this.bitacoraService = bitacoraService;
    }

    public GoogleAuthService(UsuarioRepository usuarioRepository,
                             CoordinadorRepository coordinadorRepository,
                             DocenteRepository docenteRepository,
                             JwtEncoder jwtEncoder,
                             String googleClientId,
                             long expirationMinutes,
                             GoogleIdTokenVerifier tokenVerifier) {
        this.usuarioRepository = usuarioRepository;
        this.coordinadorRepository = coordinadorRepository;
        this.docenteRepository = docenteRepository;
        this.jwtEncoder = jwtEncoder;
        this.googleClientId = (googleClientId != null && !googleClientId.isBlank())
                ? googleClientId
                : "699612946028-er8cpcrvspuh60933qvur66dqi07jgn4.apps.googleusercontent.com";
        this.expirationMinutes = expirationMinutes;
        this.tokenVerifier = tokenVerifier;
    }

    // Sobrecarga para mantener retrocompatibilidad con tests existentes
    public GoogleAuthService(UsuarioRepository usuarioRepository,
                             JwtEncoder jwtEncoder,
                             String googleClientId,
                             long expirationMinutes,
                             GoogleIdTokenVerifier tokenVerifier) {
        this(usuarioRepository, null, null, jwtEncoder, googleClientId, expirationMinutes, tokenVerifier);
    }

    public void setTokenVerifier(GoogleIdTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    public void setBitacoraService(BitacoraService bitacoraService) {
        this.bitacoraService = bitacoraService;
    }

    public LoginResponse login(GoogleLoginRequest request) {
        if (request == null) {
            return login(null, null);
        }
        return login(request.credential(), request.rolEsperado());
    }

    public LoginResponse login(String credential) {
        return login(credential, null);
    }

    /**
     * HU-058 criterio 2: todo intento de ingreso queda en la bitácora, aceptado o rechazado.
     * No se registra el token. Si no se puede registrar un ingreso aceptado, no se emite la sesión.
     */
    public LoginResponse login(String credential, Integer rolEsperado) {
        LoginResponse respuesta;
        try {
            respuesta = autenticar(credential, rolEsperado);
        } catch (IngresoRechazadoException ex) {
            registrarRechazo(ex, rolEsperado);
            throw ex;
        }
        if (bitacoraService != null) {
            bitacoraService.registrarIngreso(respuesta.email(), respuesta.rol(), ResultadoIngreso.ACEPTADO, null);
        }
        return respuesta;
    }

    private void registrarRechazo(IngresoRechazadoException ex, Integer rolEsperado) {
        if (bitacoraService == null) {
            return;
        }
        try {
            bitacoraService.registrarIngreso(ex.getCorreo(), rolEsperado, ResultadoIngreso.RECHAZADO, ex.getMotivo());
        } catch (RuntimeException fallo) {
            // El ingreso ya está rechazado; se conserva el mensaje original para el usuario.
            // No se registran datos personales en el log.
            log.error("No se pudo registrar en la bitácora un ingreso rechazado (motivo {})", ex.getMotivo(), fallo);
        }
    }

    private static IngresoRechazadoException rechazo(HttpStatus status, String mensaje,
                                                     MotivoRechazoIngreso motivo, String correo) {
        return new IngresoRechazadoException(status, mensaje, motivo, correo);
    }

    private LoginResponse autenticar(String credential, Integer rolEsperado) {
        if (credential == null || credential.isBlank()) {
            throw rechazo(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO,
                    MotivoRechazoIngreso.TOKEN_INVALIDO, null);
        }

        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "La autenticación de Google no está configurada");
        }

        // 1. Verificación del token de Google
        GoogleIdToken.Payload payload;
        try {
            GoogleIdTokenVerifier verifier = this.tokenVerifier != null ? this.tokenVerifier
                    : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                      .setAudience(List.of(googleClientId))
                      .build();

            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw rechazo(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO,
                        MotivoRechazoIngreso.TOKEN_INVALIDO, null);
            }
            payload = idToken.getPayload();
            if (payload == null) {
                throw rechazo(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO,
                        MotivoRechazoIngreso.TOKEN_INVALIDO, null);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw rechazo(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO,
                    MotivoRechazoIngreso.TOKEN_INVALIDO, null);
        }

        // 2. Correo institucional: validar dominio exacto y sufijo institucional.
        // HU-058: si se rechaza aquí no se guarda el correo (puede ser personal).
        String email = payload.getEmail();
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        if (!emailVerified || email == null) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL,
                    MotivoRechazoIngreso.DOMINIO_NO_INSTITUCIONAL, null);
        }

        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!cleanEmail.endsWith(AuthConstants.DOMAIN)) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL,
                    MotivoRechazoIngreso.DOMINIO_NO_INSTITUCIONAL, null);
        }

        Object hdClaim = payload.get("hd");
        if (hdClaim != null && !AuthConstants.HD_CLAIM.equalsIgnoreCase(hdClaim.toString().trim())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL,
                    MotivoRechazoIngreso.DOMINIO_NO_INSTITUCIONAL, null);
        }

        // 3. Resolución por rol esperado
        if (rolEsperado != null) {
            if (rolEsperado == AuthConstants.ROL_COORDINADOR) {
                return resolverLoginCoordinador(cleanEmail);
            } else if (rolEsperado == AuthConstants.ROL_ADMIN) {
                return resolverLoginAdmin(cleanEmail);
            } else if (rolEsperado == AuthConstants.ROL_DOCENTE) {
                return resolverLoginDocente(cleanEmail);
            }
        }

        // 4. Sin rol explícito, usuarios.rol es la fuente de verdad. Los perfiles
        // independientes se conservan como fallback para cuentas legadas sin fila en usuarios.
        Optional<Usuario> usuarioOpt = usuarioRepository != null
                ? usuarioRepository.buscarPorCorreoInstitucional(cleanEmail)
                : Optional.empty();
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (Integer.valueOf(AuthConstants.ROL_ADMIN).equals(usuario.getRol())) {
                return emitirSesionAdmin(usuario);
            }
            if (Integer.valueOf(AuthConstants.ROL_COORDINADOR).equals(usuario.getRol())) {
                return resolverLoginCoordinador(cleanEmail);
            }
            if (Integer.valueOf(AuthConstants.ROL_DOCENTE).equals(usuario.getRol())) {
                return resolverLoginDocente(cleanEmail);
            }
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, cleanEmail);
        }

        if (coordinadorRepository != null) {
            Optional<Coordinador> coordinadorOpt = coordinadorRepository
                    .findByCorreoInstitucionalWithDetails(cleanEmail);
            if (coordinadorOpt.isPresent()) {
                return emitirSesionCoordinador(coordinadorOpt.get());
            }
        }

        if (docenteRepository != null) {
            Optional<Docente> docenteOpt = docenteRepository.findByCorreoInstitucionalWithDetails(cleanEmail);
            if (docenteOpt.isPresent()) {
                return emitirSesionDocente(docenteOpt.get());
            }
        }

        throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA,
                MotivoRechazoIngreso.CUENTA_NO_REGISTRADA, cleanEmail);
    }

    private LoginResponse resolverLoginCoordinador(String email) {
        if (coordinadorRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Repositorio de coordinadores no disponible");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository != null
                ? usuarioRepository.buscarPorCorreoInstitucional(email)
                : Optional.empty();
        if (usuarioOpt.isPresent()
                && !Integer.valueOf(AuthConstants.ROL_COORDINADOR).equals(usuarioOpt.get().getRol())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_COORDINADOR,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, email);
        }
        if (usuarioOpt.isPresent() && Boolean.FALSE.equals(usuarioOpt.get().getActivo())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_DESACTIVADA,
                    MotivoRechazoIngreso.PERFIL_DESACTIVADO, email);
        }

        Optional<Coordinador> coordinadorOpt = coordinadorRepository.findByCorreoInstitucionalWithDetails(email);
        if (coordinadorOpt.isPresent()) {
            return emitirSesionCoordinador(coordinadorOpt.get());
        }

        if (usuarioOpt.isPresent()) {
            return emitirSesionUsuarioRol(usuarioOpt.get(), AuthConstants.ROL_COORDINADOR, "ROLE_COORDINADOR", "COORDINADOR");
        }

        boolean existeEnOtroRol = usuarioOpt.isPresent()
                || (docenteRepository != null && docenteRepository.existsByCorreoInstitucional(email));

        if (existeEnOtroRol) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_COORDINADOR,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, email);
        }

        throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA,
                MotivoRechazoIngreso.CUENTA_NO_REGISTRADA, email);
    }

    private LoginResponse resolverLoginDocente(String email) {
        if (docenteRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Repositorio de docentes no disponible");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository != null
                ? usuarioRepository.buscarPorCorreoInstitucional(email)
                : Optional.empty();

        // Un perfil académico en docentes no permite al usuario con rol 2 iniciar
        // sesión como docente ni obtener los permisos de autoservicio del rol 3.
        if (usuarioOpt.isPresent()
                && Integer.valueOf(AuthConstants.ROL_COORDINADOR).equals(usuarioOpt.get().getRol())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_DOCENTE,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, email);
        }
        // Para cuentas legadas sin fila en usuarios, el perfil de coordinador tampoco
        // se convierte en una sesión docente por tener además docentes.id.
        if (usuarioOpt.isEmpty() && coordinadorRepository != null
                && coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(email)) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_DOCENTE,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, email);
        }

        Optional<Docente> docenteOpt = docenteRepository.findByCorreoInstitucionalWithDetails(email);
        if (docenteOpt.isPresent()) {
            return emitirSesionDocente(docenteOpt.get());
        }

        if (usuarioOpt.isPresent() && Integer.valueOf(AuthConstants.ROL_DOCENTE).equals(usuarioOpt.get().getRol())) {
            return emitirSesionUsuarioRol(usuarioOpt.get(), AuthConstants.ROL_DOCENTE, "ROLE_DOCENTE", "DOCENTE");
        }

        boolean existeEnOtroRol = usuarioOpt.isPresent()
                || (coordinadorRepository != null
                && coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(email));

        if (existeEnOtroRol) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_DOCENTE,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, email);
        }

        throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA,
                MotivoRechazoIngreso.CUENTA_NO_REGISTRADA, email);
    }

    private LoginResponse emitirSesionUsuarioRol(Usuario usuario, int rol, String roleAuthority, String shortAuthority) {
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_PERFIL_DESACTIVADO,
                    MotivoRechazoIngreso.PERFIL_DESACTIVADO, usuario.getEmailInstitucional());
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        // No se agregan autoridades de otros perfiles por la mera existencia de filas
        // relacionadas. El rol 2 sigue siendo coordinador aunque también tenga docentes.id.
        List<String> authorities = new ArrayList<>(List.of(roleAuthority, shortAuthority));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cda-backend")
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", usuario.getEmailInstitucional())
                .claim("nombre", usuario.getNombre())
                .claim("apellido", usuario.getApellido())
                .claim("rol", rol)
                .claim("authorities", authorities)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String mensaje = rol == AuthConstants.ROL_COORDINADOR
                ? AuthConstants.WARN_SIN_PROGRAMA_ASIGNADO
                : AuthConstants.LOGIN_SUCCESS_MESSAGE;

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes * 60,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmailInstitucional(),
                rol,
                null,
                null,
                mensaje);
    }

    private LoginResponse emitirSesionCoordinador(Coordinador coordinador) {
        if (Boolean.FALSE.equals(coordinador.getActivo())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_PERFIL_DESACTIVADO,
                    MotivoRechazoIngreso.PERFIL_DESACTIVADO, coordinador.getCorreoInstitucional());
        }

        String codeProgram = coordinador.getPrograma() != null ? coordinador.getPrograma().getCodeProgram() : null;
        String nombrePrograma = coordinador.getPrograma() != null ? coordinador.getPrograma().getNameProgram() : null;

        String mensaje = (codeProgram == null || codeProgram.isBlank())
                ? AuthConstants.WARN_SIN_PROGRAMA_ASIGNADO
                : AuthConstants.LOGIN_SUCCESS_MESSAGE;

        String fullName = coordinador.getNombreCompleto() != null ? coordinador.getNombreCompleto().trim() : "";
        String nombre = fullName;
        String apellido = "";
        int lastSpace = fullName.lastIndexOf(' ');
        if (lastSpace > 0) {
            nombre = fullName.substring(0, lastSpace);
            apellido = fullName.substring(lastSpace + 1);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        // El rol funcional lo determina la sesión de usuarios (rol 2).
        // Tener además una fila en docentes permite asignar ese perfil académico a cursos,
        // pero no concede permisos ni pantallas de autoservicio exclusivos del rol docente.
        List<String> authorities = new ArrayList<>(List.of("ROLE_COORDINADOR", "COORDINADOR"));

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("cda-backend")
                .subject(String.valueOf(coordinador.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", coordinador.getCorreoInstitucional())
                .claim("nombre", nombre)
                .claim("apellido", apellido)
                .claim("rol", AuthConstants.ROL_COORDINADOR)
                .claim("authorities", authorities);

        if (codeProgram != null) {
            claimsBuilder.claim("codeProgram", codeProgram);
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claimsBuilder.build())).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes * 60,
                coordinador.getId(),
                nombre,
                apellido,
                coordinador.getCorreoInstitucional(),
                AuthConstants.ROL_COORDINADOR,
                codeProgram,
                nombrePrograma,
                mensaje);
    }

    private LoginResponse emitirSesionDocente(Docente docente) {
        if (Boolean.FALSE.equals(docente.getEstado())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_PERFIL_DESACTIVADO,
                    MotivoRechazoIngreso.PERFIL_DESACTIVADO, docente.getCorreoInstitucional());
        }

        String codeProgram = docente.getPrograma() != null ? docente.getPrograma().getCodeProgram() : null;
        String nombrePrograma = docente.getPrograma() != null ? docente.getPrograma().getNameProgram() : null;

        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        List<String> authorities = List.of("ROLE_DOCENTE", "DOCENTE");

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("cda-backend")
                .subject(String.valueOf(docente.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", docente.getCorreoInstitucional())
                .claim("nombre", docente.getNombres())
                .claim("apellido", docente.getApellidos())
                .claim("rol", AuthConstants.ROL_DOCENTE)
                .claim("authorities", authorities);

        if (codeProgram != null) {
            claimsBuilder.claim("codeProgram", codeProgram);
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claimsBuilder.build())).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes * 60,
                docente.getId().intValue(),
                docente.getNombres(),
                docente.getApellidos(),
                docente.getCorreoInstitucional(),
                AuthConstants.ROL_DOCENTE,
                codeProgram,
                nombrePrograma,
                AuthConstants.LOGIN_SUCCESS_MESSAGE);
    }

    private LoginResponse resolverLoginAdmin(String email) {
        if (usuarioRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Repositorio de usuarios no disponible");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorCorreoInstitucional(email);
        if (usuarioOpt.isPresent()) {
            return emitirSesionAdmin(usuarioOpt.get());
        }

        boolean existeEnOtroRol = (coordinadorRepository != null
                && coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(email))
                || (docenteRepository != null && docenteRepository.existsByCorreoInstitucional(email));

        if (existeEnOtroRol) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERMISOS_ADMIN,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, email);
        }

        throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA,
                MotivoRechazoIngreso.CUENTA_NO_REGISTRADA, email);
    }

    private LoginResponse emitirSesionAdmin(Usuario usuario) {
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_DESACTIVADA,
                    MotivoRechazoIngreso.PERFIL_DESACTIVADO, usuario.getEmailInstitucional());
        }

        if (usuario.getRol() == null || usuario.getRol() != AuthConstants.ROL_ADMIN) {
            throw rechazo(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERMISOS_ADMIN,
                    MotivoRechazoIngreso.SIN_PERFIL_ROL, usuario.getEmailInstitucional());
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        List<String> authorities = new ArrayList<>(List.of("ROLE_ADMIN", "ADMIN"));
        // RN-07: Si el administrador también dicta clase, tiene perfil docente
        if (docenteRepository != null
                && docenteRepository.existsByCorreoInstitucional(usuario.getEmailInstitucional())) {
            authorities.add("ROLE_DOCENTE");
            authorities.add("DOCENTE");
        }

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cda-backend")
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", usuario.getEmailInstitucional())
                .claim("nombre", usuario.getNombre())
                .claim("apellido", usuario.getApellido())
                .claim("rol", usuario.getRol())
                .claim("authorities", authorities)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes * 60,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmailInstitucional(),
                usuario.getRol(),
                null,
                null,
                AuthConstants.LOGIN_SUCCESS_MESSAGE);
    }
}
