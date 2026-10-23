package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

        @Mock
        private UsuarioRepository usuarioRepository;

        @Mock
        private JwtEncoder jwtEncoder;

        @Mock
        private GoogleIdTokenVerifier tokenVerifier;

        @Mock
        private GoogleIdToken googleIdToken;

        private GoogleAuthService googleAuthService;

        private static final String CLIENT_ID = "mock-client-id.apps.googleusercontent.com";
        private static final long EXPIRATION_MINUTES = 60;

        @BeforeEach
        void setUp() {
                googleAuthService = new GoogleAuthService(
                                usuarioRepository,
                                jwtEncoder,
                                CLIENT_ID,
                                EXPIRATION_MINUTES,
                                tokenVerifier);
        }

        @Test
        @DisplayName("Criterio 1.3 y 3.1: Admin autenticado con correo institucional activo y rol 1")
        void login_Exitoso_Administrador() throws GeneralSecurityException, IOException {
                String tokenStr = "valid-google-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("admin.sistema@correounivalle.edu.co");
                payload.setEmailVerified(true);
                payload.set("hd", "correounivalle.edu.co");

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                Usuario admin = Usuario.builder()
                                .id(1)
                                .nombre("Admin")
                                .apellido("Principal")
                                .emailInstitucional("admin.sistema@correounivalle.edu.co")
                                .emailPersonal("admin@gmail.com")
                                .rol(AuthConstants.ROL_ADMIN)
                                .activo(true)
                                .build();

                when(usuarioRepository.buscarPorCorreoInstitucional("admin.sistema@correounivalle.edu.co"))
                                .thenReturn(Optional.of(admin));

                Jwt mockJwt = mock(Jwt.class);
                when(mockJwt.getTokenValue()).thenReturn("mocked-jwt-token");
                when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

                LoginResponse response = googleAuthService.login(tokenStr);

                assertNotNull(response);
                assertEquals("mocked-jwt-token", response.token());
                assertEquals("Bearer", response.tokenType());
                assertEquals(AuthConstants.ROL_ADMIN, response.rol());
                assertEquals(AuthConstants.LOGIN_SUCCESS_MESSAGE, response.mensaje());
        }

        @Test
        @DisplayName("Criterio 2.1: Correo fuera del dominio institucional")
        void login_Error_DominioFueraDeInstitucional() throws GeneralSecurityException, IOException {
                String tokenStr = "external-google-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("usuario@gmail.com");
                payload.setEmailVerified(true);

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> googleAuthService.login(tokenStr));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                assertEquals(AuthConstants.ERROR_DOMINIO_INSTITUCIONAL, ex.getReason());
        }

        @Test
        @DisplayName("Criterio 2.1 Extra: Dominio engañoso que no termina en @correounivalle.edu.co")
        void login_Error_DominioEnganoso() throws GeneralSecurityException, IOException {
                String tokenStr = "malicious-google-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("admin@correounivalle.edu.co.evil.com");
                payload.setEmailVerified(true);

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> googleAuthService.login(tokenStr));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                assertEquals(AuthConstants.ERROR_DOMINIO_INSTITUCIONAL, ex.getReason());
        }

        @Test
        @DisplayName("Criterio 2.2: Cuenta institucional no registrada en el sistema")
        void login_Error_CuentaNoRegistrada() throws GeneralSecurityException, IOException {
                String tokenStr = "unregistered-google-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("desconocido@correounivalle.edu.co");
                payload.setEmailVerified(true);

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                when(usuarioRepository.buscarPorCorreoInstitucional("desconocido@correounivalle.edu.co"))
                                .thenReturn(Optional.empty());

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> googleAuthService.login(tokenStr));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                assertEquals(AuthConstants.ERROR_CUENTA_NO_REGISTRADA, ex.getReason());
        }

        @Test
        @DisplayName("Criterio 2.3: Cuenta desactivada")
        void login_Error_CuentaDesactivada() throws GeneralSecurityException, IOException {
                String tokenStr = "deactivated-google-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("inactivo@correounivalle.edu.co");
                payload.setEmailVerified(true);

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                Usuario usuarioInactivo = Usuario.builder()
                                .id(2)
                                .nombre("Inactivo")
                                .apellido("Usuario")
                                .emailInstitucional("inactivo@correounivalle.edu.co")
                                .rol(AuthConstants.ROL_ADMIN)
                                .activo(false)
                                .build();

                when(usuarioRepository.buscarPorCorreoInstitucional("inactivo@correounivalle.edu.co"))
                                .thenReturn(Optional.of(usuarioInactivo));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> googleAuthService.login(tokenStr));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                assertEquals(AuthConstants.ERROR_CUENTA_DESACTIVADA, ex.getReason());
        }

        @Test
        @DisplayName("Criterio 2.4 y 2.5: Verificador de Google retorna null o falla")
        void login_Error_TokenInvalido() throws GeneralSecurityException, IOException {
                String tokenStr = "invalid-token";
                when(tokenVerifier.verify(tokenStr)).thenReturn(null);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> googleAuthService.login(tokenStr));

                assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
                assertEquals(AuthConstants.ERROR_LOGIN_FALLIDO, ex.getReason());
        }

        @Test
        @DisplayName("Criterio 3.2: Cuenta activa de docente puede iniciar sesión")
        void login_Exitoso_Docente() throws GeneralSecurityException, IOException {
                String tokenStr = "docente-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("desconocido.rol@correounivalle.edu.co");
                payload.setEmailVerified(true);

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                Usuario usuarioSinRol = Usuario.builder()
                                .id(3)
                                .nombre("Usuario")
                                .apellido("Prueba")
                                .emailInstitucional("docente@correounivalle.edu.co")
                                .rol(AuthConstants.ROL_DOCENTE)
                                .activo(true)
                                .build();

                when(usuarioRepository.buscarPorCorreoInstitucional("desconocido.rol@correounivalle.edu.co"))
                                .thenReturn(Optional.of(usuarioSinRol));

                Jwt mockJwt = mock(Jwt.class);
                when(mockJwt.getTokenValue()).thenReturn("mocked-docente-jwt-token");
                when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

                LoginResponse response = googleAuthService.login(tokenStr);

                assertEquals(AuthConstants.ROL_DOCENTE, response.rol());
                assertEquals("Docente", response.nombre());
        }

        @Test
        @DisplayName("Login exitoso para Coordinador de programa (Rol 2)")
        void login_Exitoso_Coordinador() throws GeneralSecurityException, IOException {
                String tokenStr = "coord-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("coordinador@correounivalle.edu.co");
                payload.setEmailVerified(true);
                payload.set("hd", "correounivalle.edu.co");

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                Usuario coord = Usuario.builder()
                                .id(20)
                                .nombre("Coordinador")
                                .apellido("Academico")
                                .emailInstitucional("coordinador@correounivalle.edu.co")
                                .rol(AuthConstants.ROL_COORDINADOR)
                                .activo(true)
                                .build();

                when(usuarioRepository.buscarPorCorreoInstitucional("coordinador@correounivalle.edu.co"))
                                .thenReturn(Optional.of(coord));

                Jwt mockJwt = mock(Jwt.class);
                when(mockJwt.getTokenValue()).thenReturn("jwt-coord-token");
                when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

                LoginResponse response = googleAuthService.login(tokenStr);

                assertNotNull(response);
                assertEquals(AuthConstants.ROL_COORDINADOR, response.rol());
                assertEquals("jwt-coord-token", response.token());
        }

        @Test
        @DisplayName("Login exitoso para Docente (Rol 3)")
        void login_Exitoso_Docente() throws GeneralSecurityException, IOException {
                String tokenStr = "docente-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("docente@correounivalle.edu.co");
                payload.setEmailVerified(true);
                payload.set("hd", "correounivalle.edu.co");

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                Usuario doc = Usuario.builder()
                                .id(30)
                                .nombre("Docente")
                                .apellido("Titular")
                                .emailInstitucional("docente@correounivalle.edu.co")
                                .rol(AuthConstants.ROL_DOCENTE)
                                .activo(true)
                                .build();

                when(usuarioRepository.buscarPorCorreoInstitucional("docente@correounivalle.edu.co"))
                                .thenReturn(Optional.of(doc));

                Jwt mockJwt = mock(Jwt.class);
                when(mockJwt.getTokenValue()).thenReturn("jwt-doc-token");
                when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

                LoginResponse response = googleAuthService.login(tokenStr);

                assertNotNull(response);
                assertEquals(AuthConstants.ROL_DOCENTE, response.rol());
                assertEquals("jwt-doc-token", response.token());
        }

        @Test
        @DisplayName("Orden de validaciones: Cuenta desactivada sin rol de admin debe mostrar 'Su cuenta está desactivada'")
        void login_Error_DesactivadaGanaASinRol() throws GeneralSecurityException, IOException {
                String tokenStr = "deactivated-docente-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("inactivo.otro@correounivalle.edu.co");
                payload.setEmailVerified(true);

                when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
                when(googleIdToken.getPayload()).thenReturn(payload);

                Usuario usuario = Usuario.builder()
                                .id(4)
                                .nombre("Otro")
                                .apellido("Inactivo")
                                .emailInstitucional("inactivo.otro@correounivalle.edu.co")
                                .rol(99)
                                .activo(false)
                                .build();

                when(usuarioRepository.buscarPorCorreoInstitucional("inactivo.otro@correounivalle.edu.co"))
                                .thenReturn(Optional.of(usuario));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> googleAuthService.login(tokenStr));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                assertEquals(AuthConstants.ERROR_CUENTA_DESACTIVADA, ex.getReason());
        }
}
