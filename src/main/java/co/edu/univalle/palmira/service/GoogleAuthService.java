package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.GoogleLoginRequest;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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

@Service
public class GoogleAuthService {

    private final UsuarioRepository usuarioRepository;
    private final CoordinadorRepository coordinadorRepository;
    private final DocenteRepository docenteRepository;
    private final JwtEncoder jwtEncoder;
    private final String googleClientId;
    private final long expirationMinutes;
    private GoogleIdTokenVerifier tokenVerifier;

    @Autowired
    public GoogleAuthService(UsuarioRepository usuarioRepository,
                             CoordinadorRepository coordinadorRepository,
                             DocenteRepository docenteRepository,
                             JwtEncoder jwtEncoder,
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

    public LoginResponse login(GoogleLoginRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO);
        }
        return login(request.credential(), request.rolEsperado());
    }

    public LoginResponse login(String credential) {
        return login(credential, null);
    }

    public LoginResponse login(String credential, Integer rolEsperado) {
        if (credential == null || credential.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO);
        }

        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La autenticación de Google no está configurada");
        }

        // 1. Verificación del token de Google
        GoogleIdToken.Payload payload;
        try {
            GoogleIdTokenVerifier verifier = this.tokenVerifier != null ? this.tokenVerifier :
                    new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                            .setAudience(List.of(googleClientId))
                            .build();

            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO);
            }
            payload = idToken.getPayload();
            if (payload == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthConstants.ERROR_LOGIN_FALLIDO);
        }

        // 2. Correo institucional: validar dominio exacto y sufijo institucional
        String email = payload.getEmail();
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        if (!emailVerified || email == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL);
        }

        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!cleanEmail.endsWith(AuthConstants.DOMAIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL);
        }

        Object hdClaim = payload.get("hd");
        if (hdClaim != null && !AuthConstants.HD_CLAIM.equalsIgnoreCase(hdClaim.toString().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL);
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

        // 4. Si no se especificó rol, auto-detectar: Coordinador -> Administrador -> Docente
        if (coordinadorRepository != null) {
            Optional<Coordinador> coordinadorOpt = coordinadorRepository.findByCorreoInstitucionalWithDetails(cleanEmail);
            if (coordinadorOpt.isPresent()) {
                return emitirSesionCoordinador(coordinadorOpt.get());
            }
        }

        if (usuarioRepository != null) {
            Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorCorreoInstitucional(cleanEmail);
            if (usuarioOpt.isPresent()) {
                return emitirSesionAdmin(usuarioOpt.get());
            }
        }

        if (docenteRepository != null) {
            Optional<Docente> docenteOpt = docenteRepository.findByCorreoInstitucional(cleanEmail);
            if (docenteOpt.isPresent()) {
                return emitirSesionDocente(docenteOpt.get());
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA);
    }

    private LoginResponse resolverLoginCoordinador(String email) {
        if (coordinadorRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Repositorio de coordinadores no disponible");
        }

        Optional<Coordinador> coordinadorOpt = coordinadorRepository.findByCorreoInstitucionalWithDetails(email);
        if (coordinadorOpt.isPresent()) {
            return emitirSesionCoordinador(coordinadorOpt.get());
        }

        boolean existeEnOtroRol = (usuarioRepository != null && usuarioRepository.buscarPorCorreoInstitucional(email).isPresent())
                || (docenteRepository != null && docenteRepository.existsByCorreoInstitucional(email));

        if (existeEnOtroRol) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_COORDINADOR);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA);
    }

    private LoginResponse resolverLoginDocente(String email) {
        if (docenteRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Repositorio de docentes no disponible");
        }

        Optional<Docente> docenteOpt = docenteRepository.findByCorreoInstitucional(email);
        if (docenteOpt.isPresent()) {
            return emitirSesionDocente(docenteOpt.get());
        }

        boolean existeEnOtroRol = (usuarioRepository != null && usuarioRepository.buscarPorCorreoInstitucional(email).isPresent())
                || (coordinadorRepository != null && coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(email));

        if (existeEnOtroRol) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERFIL_DOCENTE);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA);
    }

    private LoginResponse emitirSesionCoordinador(Coordinador coordinador) {
        if (Boolean.FALSE.equals(coordinador.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_PERFIL_DESACTIVADO);
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

        List<String> authorities = new ArrayList<>(List.of("ROLE_COORDINADOR", "COORDINADOR"));
        // RN-07: Si el coordinador también está registrado como docente, hereda perfil docente
        if (docenteRepository != null && docenteRepository.existsByCorreoInstitucional(coordinador.getCorreoInstitucional())) {
            authorities.add("ROLE_DOCENTE");
            authorities.add("DOCENTE");
        }

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
                mensaje
        );
    }

    private LoginResponse emitirSesionDocente(Docente docente) {
        if (Boolean.FALSE.equals(docente.getEstado())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_PERFIL_DESACTIVADO);
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
                AuthConstants.LOGIN_SUCCESS_MESSAGE
        );
    }

    private LoginResponse resolverLoginAdmin(String email) {
        if (usuarioRepository == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Repositorio de usuarios no disponible");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorCorreoInstitucional(email);
        if (usuarioOpt.isPresent()) {
            return emitirSesionAdmin(usuarioOpt.get());
        }

        boolean existeEnOtroRol = (coordinadorRepository != null && coordinadorRepository.existsByCorreoInstitucionalIgnoreCase(email))
                || (docenteRepository != null && docenteRepository.existsByCorreoInstitucional(email));

        if (existeEnOtroRol) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERMISOS_ADMIN);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_NO_REGISTRADA);
    }

    private LoginResponse emitirSesionAdmin(Usuario usuario) {
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_DESACTIVADA);
        }

        if (usuario.getRol() == null || usuario.getRol() != AuthConstants.ROL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERMISOS_ADMIN);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        List<String> authorities = new ArrayList<>(List.of("ROLE_ADMIN", "ADMIN"));
        // RN-07: Si el administrador también dicta clase, tiene perfil docente
        if (docenteRepository != null && docenteRepository.existsByCorreoInstitucional(usuario.getEmailInstitucional())) {
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
                AuthConstants.LOGIN_SUCCESS_MESSAGE
        );
    }
}
