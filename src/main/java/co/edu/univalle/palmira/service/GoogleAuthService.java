package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Locale;

@Service
public class GoogleAuthService {
    private static final String DOMAIN = "@correounivalle.edu.co";
    private static final int DOCENTE_ROLE = 3;

    private final UsuarioRepository usuarioRepository;
    private final JwtEncoder jwtEncoder;
    private final String googleClientId;
    private final long expirationMinutes;

    public GoogleAuthService(UsuarioRepository usuarioRepository, JwtEncoder jwtEncoder,
                             @Value("${auth.google.client-id:}") String googleClientId,
                             @Value("${auth.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.jwtEncoder = jwtEncoder;
        this.googleClientId = googleClientId;
        this.expirationMinutes = expirationMinutes;
    }

    public LoginResponse login(String credential) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La autenticación de Google no está configurada");
        }

        GoogleIdToken.Payload payload;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No fue posible iniciar sesión. Intente de nuevo");
            }
            payload = idToken.getPayload();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No fue posible iniciar sesión. Intente de nuevo");
        }

        String email = payload.getEmail();
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        if (!emailVerified || email == null || !email.toLowerCase(Locale.ROOT).endsWith(DOMAIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debe usar su correo institucional");
        }

        Usuario usuario = usuarioRepository.buscarPorCorreoInstitucional(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Su cuenta no está registrada. Contacte al administrador"));

        if (usuario.getRol() == null || usuario.getRol() != DOCENTE_ROLE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos de Docente");
        }

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
                .claim("authorities", Collections.singletonList("ROLE_DOCENTE"))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, "Bearer", expirationMinutes * 60, usuario.getId(),
                usuario.getNombre(), usuario.getApellido(), usuario.getEmailInstitucional(),
                usuario.getRol(), "Inicio de sesión exitoso");
    }
}
