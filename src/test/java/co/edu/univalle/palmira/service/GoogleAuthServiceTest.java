package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.GoogleLoginRequest;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
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
    private CoordinadorRepository coordinadorRepository;

    @Mock
    private DocenteRepository docenteRepository;

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
                coordinadorRepository,
                docenteRepository,
                jwtEncoder,
                CLIENT_ID,
                EXPIRATION_MINUTES,
                tokenVerifier
        );
    }

    // =========================================================================
    // HU-001: TESTS DE LOGIN ADMINISTRADOR
    // =========================================================================

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

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("admin.sistema@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
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

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(tokenStr)
        );

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

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(tokenStr)
        );

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

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("desconocido@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.buscarPorCorreoInstitucional("desconocido@correounivalle.edu.co"))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(tokenStr)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_CUENTA_NO_REGISTRADA, ex.getReason());
    }

    @Test
    @DisplayName("Criterio 2.3: Cuenta desactivada de administrador")
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

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("inactivo@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.buscarPorCorreoInstitucional("inactivo@correounivalle.edu.co"))
                .thenReturn(Optional.of(usuarioInactivo));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(tokenStr)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_CUENTA_DESACTIVADA, ex.getReason());
    }

<<<<<<< HEAD
    @Test
    @DisplayName("Criterio 2.4 y 2.5: Verificador de Google retorna null o falla")
    void login_Error_TokenInvalido() throws GeneralSecurityException, IOException {
        String tokenStr = "invalid-token";
        when(tokenVerifier.verify(tokenStr)).thenReturn(null);
=======
        @Test
        @DisplayName("Criterio 3.2: Cuenta activa de docente puede iniciar sesión")
        void login_Exitoso_DocentePorRol() throws GeneralSecurityException, IOException {
                String tokenStr = "docente-credential";
                GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
                payload.setEmail("desconocido.rol@correounivalle.edu.co");
                payload.setEmailVerified(true);
>>>>>>> acad0f207f1fcd5f2a84230b3809d1317a59c054


<<<<<<< HEAD
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(tokenStr)
        );
=======
                Usuario usuarioSinRol = Usuario.builder()
                                .id(3)
                                .nombre("Usuario")
                                .apellido("Prueba")
                                .emailInstitucional("docente@correounivalle.edu.co")
                                .rol(AuthConstants.ROL_DOCENTE)
                                .activo(true)
                                .build();
>>>>>>> acad0f207f1fcd5f2a84230b3809d1317a59c054


<<<<<<< HEAD
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_LOGIN_FALLIDO, ex.getReason());
    }

    @Test
    @DisplayName("Criterio 3.2: Cuenta activa pero sin rol de administrador")
    void login_Error_SinRolAdministrador() throws GeneralSecurityException, IOException {
        String tokenStr = "docente-credential";
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("docente@correounivalle.edu.co");
        payload.setEmailVerified(true);
=======
                Jwt mockJwt = mock(Jwt.class);
                when(mockJwt.getTokenValue()).thenReturn("mocked-docente-jwt-token");
                when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

                LoginResponse response = googleAuthService.login(tokenStr);

                assertEquals(AuthConstants.ROL_DOCENTE, response.rol());
                assertEquals("Usuario", response.nombre());
        }
>>>>>>> acad0f207f1fcd5f2a84230b3809d1317a59c054

        when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        Usuario docente = Usuario.builder()
                .id(3)
                .nombre("Docente")
                .apellido("Prueba")
                .emailInstitucional("docente@correounivalle.edu.co")
                .rol(AuthConstants.ROL_DOCENTE) // Rol 3 no es admin
                .activo(true)
                .build();

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("docente@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.buscarPorCorreoInstitucional("docente@correounivalle.edu.co"))
                .thenReturn(Optional.of(docente));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(tokenStr)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_SIN_PERMISOS_ADMIN, ex.getReason());
    }

    // =========================================================================
    // HU-010: TESTS DE LOGIN COORDINADOR
    // =========================================================================

    @Test
    @DisplayName("HU-010 Criterio 1.3 y 3.1: Login exitoso de Coordinador con programa asignado")
    void login_Exitoso_Coordinador_ConPrograma() throws GeneralSecurityException, IOException {
        String tokenStr = "valid-coord-token";
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("coordinador.sistemas@correounivalle.edu.co");
        payload.setEmailVerified(true);
        payload.set("hd", "correounivalle.edu.co");

        when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        Programa programa = Programa.builder()
                .codeProgram("3743")
                .nameProgram("Tecnología en Desarrollo de Software")
                .build();

        Coordinador coordinador = Coordinador.builder()
                .id(10)
                .identificacion("111222333")
                .nombreCompleto("Juan Pérez Gómez")
                .correoInstitucional("coordinador.sistemas@correounivalle.edu.co")
                .programa(programa)
                .activo(true)
                .build();

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coordinador.sistemas@correounivalle.edu.co"))
                .thenReturn(Optional.of(coordinador));

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("coord-jwt-token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        GoogleLoginRequest request = new GoogleLoginRequest(tokenStr, AuthConstants.ROL_COORDINADOR);
        LoginResponse response = googleAuthService.login(request);

        assertNotNull(response);
        assertEquals("coord-jwt-token", response.token());
        assertEquals(AuthConstants.ROL_COORDINADOR, response.rol());
        assertEquals("3743", response.codeProgram());
        assertEquals("Tecnología en Desarrollo de Software", response.nombrePrograma());
        assertEquals("Juan Pérez", response.nombre());
        assertEquals("Gómez", response.apellido());
        assertEquals(AuthConstants.LOGIN_SUCCESS_MESSAGE, response.mensaje());
    }

    @Test
    @DisplayName("HU-010 Criterio 3.4: Coordinador activo sin programa asignado debe permitir acceso con mensaje de aviso")
    void login_Exitoso_Coordinador_SinProgramaAsignado() throws GeneralSecurityException, IOException {
        String tokenStr = "valid-coord-token-sin-prog";
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("coord.nuevo@correounivalle.edu.co");
        payload.setEmailVerified(true);
        payload.set("hd", "correounivalle.edu.co");

        when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        Coordinador coordinador = Coordinador.builder()
                .id(12)
                .identificacion("999888777")
                .nombreCompleto("Ana López")
                .correoInstitucional("coord.nuevo@correounivalle.edu.co")
                .programa(null)
                .activo(true)
                .build();

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coord.nuevo@correounivalle.edu.co"))
                .thenReturn(Optional.of(coordinador));

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("coord-jwt-token-2");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        GoogleLoginRequest request = new GoogleLoginRequest(tokenStr, AuthConstants.ROL_COORDINADOR);
        LoginResponse response = googleAuthService.login(request);

        assertNotNull(response);
        assertEquals(AuthConstants.ROL_COORDINADOR, response.rol());
        assertNull(response.codeProgram());
        assertNull(response.nombrePrograma());
        assertEquals(AuthConstants.WARN_SIN_PROGRAMA_ASIGNADO, response.mensaje());
    }

    @Test
    @DisplayName("HU-010 Criterio 2.3: Perfil de coordinador desactivado")
    void login_Error_Coordinador_Desactivado() throws GeneralSecurityException, IOException {
        String tokenStr = "coord-desactivado-token";
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("coord.inactivo@correounivalle.edu.co");
        payload.setEmailVerified(true);

        when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        Coordinador coordinador = Coordinador.builder()
                .id(15)
                .identificacion("555444333")
                .nombreCompleto("Inactivo Coord")
                .correoInstitucional("coord.inactivo@correounivalle.edu.co")
                .activo(false)
                .build();

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("coord.inactivo@correounivalle.edu.co"))
                .thenReturn(Optional.of(coordinador));

        GoogleLoginRequest request = new GoogleLoginRequest(tokenStr, AuthConstants.ROL_COORDINADOR);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_PERFIL_DESACTIVADO, ex.getReason());
    }

    @Test
    @DisplayName("HU-010 Criterio 3.3: Cuenta registrada en otro perfil pero sin perfil de coordinador")
    void login_Error_Coordinador_SinPerfilCoordinador() throws GeneralSecurityException, IOException {
        String tokenStr = "docente-intentando-coord";
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("docente.registrado@correounivalle.edu.co");
        payload.setEmailVerified(true);

        when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("docente.registrado@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
        when(docenteRepository.existsByCorreoInstitucional("docente.registrado@correounivalle.edu.co"))
                .thenReturn(true);

        GoogleLoginRequest request = new GoogleLoginRequest(tokenStr, AuthConstants.ROL_COORDINADOR);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_SIN_PERFIL_COORDINADOR, ex.getReason());
    }

    @Test
    @DisplayName("HU-010 Criterio 2.2: Coordinador con cuenta institucional no registrada")
    void login_Error_Coordinador_CuentaNoRegistrada() throws GeneralSecurityException, IOException {
        String tokenStr = "no-registrado-coord";
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("no.registrado@correounivalle.edu.co");
        payload.setEmailVerified(true);

        when(tokenVerifier.verify(tokenStr)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        when(coordinadorRepository.findByCorreoInstitucionalWithDetails("no.registrado@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.buscarPorCorreoInstitucional("no.registrado@correounivalle.edu.co"))
                .thenReturn(Optional.empty());
        when(docenteRepository.existsByCorreoInstitucional("no.registrado@correounivalle.edu.co"))
                .thenReturn(false);

        GoogleLoginRequest request = new GoogleLoginRequest(tokenStr, AuthConstants.ROL_COORDINADOR);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleAuthService.login(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(AuthConstants.ERROR_CUENTA_NO_REGISTRADA, ex.getReason());
    }
}
