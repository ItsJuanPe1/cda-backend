package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
import java.util.Collections;
import java.util.Locale;

@Service
public class GoogleAuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtEncoder jwtEncoder;
    private final String googleClientId;
    private final long expirationMinutes;
    private GoogleIdTokenVerifier tokenVerifier;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleAuthService(UsuarioRepository usuarioRepository, JwtEncoder jwtEncoder,
                             @Value("${auth.google.client-id:}") String googleClientId,
                             @Value("${auth.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.jwtEncoder = jwtEncoder;
        this.googleClientId = googleClientId;
        this.expirationMinutes = expirationMinutes;
    }

    public GoogleAuthService(UsuarioRepository usuarioRepository, JwtEncoder jwtEncoder,
                             String googleClientId, long expirationMinutes,
                             GoogleIdTokenVerifier tokenVerifier) {
        this.usuarioRepository = usuarioRepository;
        this.jwtEncoder = jwtEncoder;
        this.googleClientId = googleClientId;
        this.expirationMinutes = expirationMinutes;
        this.tokenVerifier = tokenVerifier;
    }

    public void setTokenVerifier(GoogleIdTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    public LoginResponse login(String credential) {
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
                            .setAudience(Collections.singletonList(googleClientId))
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

        // Opcional: si existe el claim hd, verificar que coincida con correounivalle.edu.co
        Object hdClaim = payload.get("hd");
        if (hdClaim != null && !AuthConstants.HD_CLAIM.equalsIgnoreCase(hdClaim.toString().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_DOMINIO_INSTITUCIONAL);
        }

        // 3. Correo no existe en el sistema
        Usuario usuario = usuarioRepository.buscarPorCorreoInstitucional(cleanEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        AuthConstants.ERROR_CUENTA_NO_REGISTRADA));

        // 4. Cuenta desactivada
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_CUENTA_DESACTIVADA);
        }

        // 5. Cuenta sin rol Administrador
        if (usuario.getRol() == null || usuario.getRol() != AuthConstants.ROL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AuthConstants.ERROR_SIN_PERMISOS_ADMIN);
        }

        // 6. Todo correcto: Emisión de sesión para Administrador
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
                .claim("authorities", Collections.singletonList("ROLE_ADMIN"))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, "Bearer", expirationMinutes * 60, usuario.getId(),
                usuario.getNombre(), usuario.getApellido(), usuario.getEmailInstitucional(),
                usuario.getRol(), AuthConstants.LOGIN_SUCCESS_MESSAGE);
    }
}
