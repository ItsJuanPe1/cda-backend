package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@Service
public class GoogleAuthService {
    private static final String DOMAIN = "@correounivalle.edu.co";
    private static final int DOCENTE_ROLE = 3;

    private final UsuarioRepository usuarioRepository;
    private final JwtEncoder jwtEncoder;
    private final String googleClientId;
    private final long expirationMinutes;
    private final RestClient restClient;

    public GoogleAuthService(UsuarioRepository usuarioRepository, JwtEncoder jwtEncoder,
                             @Value("${auth.google.client-id:699612946028-er8cpcrvspuh60933qvur66dqi07jgn4.apps.googleusercontent.com}") String googleClientId,
                             @Value("${auth.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.jwtEncoder = jwtEncoder;
        this.googleClientId = (googleClientId != null && !googleClientId.isBlank())
                ? googleClientId
                : "699612946028-er8cpcrvspuh60933qvur66dqi07jgn4.apps.googleusercontent.com";
        this.expirationMinutes = expirationMinutes;
        this.restClient = RestClient.create();
    }

    public LoginResponse login(String credential) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La autenticación de Google no está configurada");
        }

        Map<String, Object> payload;
        try {
            payload = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={idToken}", credential)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (payload == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No fue posible iniciar sesión. Intente de nuevo");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No fue posible iniciar sesión. Intente de nuevo");
        }

        String aud = (String) payload.get("aud");
        if (aud == null || !aud.equals(googleClientId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google no corresponde a esta aplicación");
        }

        String email = (String) payload.get("email");
        Object emailVerifiedObj = payload.get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(emailVerifiedObj) || "true".equalsIgnoreCase(String.valueOf(emailVerifiedObj));

        if (!emailVerified || email == null || !email.toLowerCase(Locale.ROOT).endsWith(DOMAIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debe usar su correo institucional");
        }

        Usuario usuario = usuarioRepository.buscarPorCorreoInstitucional(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Su cuenta no está registrada. Contacte al administrador"));

        if (usuario.getRol() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Su usuario no tiene un rol asignado en el sistema");
        }

        java.util.List<String> authorities = switch (usuario.getRol()) {
            case 1 -> java.util.List.of("ROLE_ADMIN", "ROLE_COORDINADOR", "ROLE_DOCENTE");
            case 2 -> java.util.List.of("ROLE_COORDINADOR", "ROLE_DOCENTE");
            case 3 -> java.util.List.of("ROLE_DOCENTE");
            default -> java.util.List.of("ROLE_USER");
        };

        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
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
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, "Bearer", expirationMinutes * 60, usuario.getId(),
                usuario.getNombre(), usuario.getApellido(), usuario.getEmailInstitucional(),
                usuario.getRol(), "Inicio de sesión exitoso");
    }
}
