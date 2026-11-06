package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.LoginResponse;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** HU-058 criterio 2: el login registra en la bitácora los ingresos aceptados y rechazados. Datos ficticios. */
@ExtendWith(MockitoExtension.class)
class GoogleAuthBitacoraTest {

    private static final String TOKEN = "credencial-google-de-prueba";
    private static final String ADMIN = "admin.prueba@correounivalle.edu.co";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CoordinadorRepository coordinadorRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private JwtEncoder jwtEncoder;
    @Mock private GoogleIdTokenVerifier tokenVerifier;
    @Mock private GoogleIdToken googleIdToken;
    @Mock private BitacoraService bitacoraService;

    private GoogleAuthService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthService(usuarioRepository, coordinadorRepository, docenteRepository,
                jwtEncoder, "cliente-prueba.apps.googleusercontent.com", 60, tokenVerifier);
        service.setBitacoraService(bitacoraService);
    }

    private void tokenGoogle(String correo) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(correo);
        payload.setEmailVerified(true);
        when(tokenVerifier.verify(TOKEN)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
    }

    @Test
    @DisplayName("Criterio 2: el ingreso exitoso queda registrado como ACEPTADO, sin token")
    void criterio2_ingresoExitosoQuedaRegistrado() throws Exception {
        tokenGoogle(ADMIN);
        Usuario admin = Usuario.builder().id(1).nombre("Admin").apellido("Prueba")
                .emailInstitucional(ADMIN).rol(AuthConstants.ROL_ADMIN).activo(true).build();
        when(usuarioRepository.buscarPorCorreoInstitucional(ADMIN)).thenReturn(Optional.of(admin));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("jwt-de-prueba");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        LoginResponse r = service.login(TOKEN, AuthConstants.ROL_ADMIN);

        assertEquals(AuthConstants.LOGIN_SUCCESS_MESSAGE, r.mensaje());
        verify(bitacoraService).registrarIngreso(ADMIN, AuthConstants.ROL_ADMIN, ResultadoIngreso.ACEPTADO, null);
        // La firma de registrarIngreso no recibe el token: verificamos que no se pasó en ningún argumento.
        verify(bitacoraService, never()).registrarIngreso(eq(TOKEN), any(), any(), any());
        verify(bitacoraService, never()).registrarIngreso(eq("jwt-de-prueba"), any(), any(), any());
    }

    @Test
    @DisplayName("Criterio 2: si no se puede registrar el ingreso aceptado, no se emite la sesión")
    void criterio2_sinRegistroNoHaySesion() throws Exception {
        tokenGoogle(ADMIN);
        Usuario admin = Usuario.builder().id(1).nombre("Admin").apellido("Prueba")
                .emailInstitucional(ADMIN).rol(AuthConstants.ROL_ADMIN).activo(true).build();
        when(usuarioRepository.buscarPorCorreoInstitucional(ADMIN)).thenReturn(Optional.of(admin));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("jwt-de-prueba");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
        when(bitacoraService.registrarIngreso(anyString(), any(), eq(ResultadoIngreso.ACEPTADO), isNull()))
                .thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class, () -> service.login(TOKEN, AuthConstants.ROL_ADMIN));
    }

    @Test
    @DisplayName("Criterio 2: token inválido queda registrado como RECHAZADO (TOKEN_INVALIDO)")
    void criterio2_tokenInvalidoQuedaRegistrado() throws Exception {
        when(tokenVerifier.verify(TOKEN)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.login(TOKEN, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(bitacoraService).registrarIngreso(null, null, ResultadoIngreso.RECHAZADO,
                MotivoRechazoIngreso.TOKEN_INVALIDO);
    }

    @Test
    @DisplayName("Criterio 2: correo no institucional se registra RECHAZADO sin guardar el correo personal")
    void criterio2_dominioNoInstitucionalNoGuardaCorreo() throws Exception {
        tokenGoogle("persona.ficticia@gmail.com");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.login(TOKEN, AuthConstants.ROL_DOCENTE));

        assertEquals(AuthConstants.ERROR_DOMINIO_INSTITUCIONAL, ex.getReason());
        verify(bitacoraService).registrarIngreso(null, AuthConstants.ROL_DOCENTE, ResultadoIngreso.RECHAZADO,
                MotivoRechazoIngreso.DOMINIO_NO_INSTITUCIONAL);
    }

    @Test
    @DisplayName("Criterio 2: cuenta no registrada queda RECHAZADO con el correo institucional")
    void criterio2_cuentaNoRegistrada() throws Exception {
        String correo = "nadie.prueba@correounivalle.edu.co";
        tokenGoogle(correo);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.login(TOKEN, null));

        assertEquals(AuthConstants.ERROR_CUENTA_NO_REGISTRADA, ex.getReason());
        verify(bitacoraService).registrarIngreso(correo, null, ResultadoIngreso.RECHAZADO,
                MotivoRechazoIngreso.CUENTA_NO_REGISTRADA);
    }

    @Test
    @DisplayName("Criterio 2: perfil desactivado queda RECHAZADO (PERFIL_DESACTIVADO)")
    void criterio2_perfilDesactivado() throws Exception {
        String correo = "docente.prueba@correounivalle.edu.co";
        tokenGoogle(correo);
        Docente docente = new Docente();
        docente.setCorreoInstitucional(correo);
        docente.setEstado(false);
        when(docenteRepository.findByCorreoInstitucionalWithDetails(correo)).thenReturn(Optional.of(docente));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.login(TOKEN, AuthConstants.ROL_DOCENTE));

        assertEquals(AuthConstants.ERROR_PERFIL_DESACTIVADO, ex.getReason());
        verify(bitacoraService).registrarIngreso(correo, AuthConstants.ROL_DOCENTE, ResultadoIngreso.RECHAZADO,
                MotivoRechazoIngreso.PERFIL_DESACTIVADO);
    }

    @Test
    @DisplayName("Criterio 2: si falla el registro de un rechazo, el usuario recibe el mensaje original del rechazo")
    void criterio2_falloAlRegistrarRechazoConservaMensaje() throws Exception {
        when(tokenVerifier.verify(TOKEN)).thenReturn(null);
        when(bitacoraService.registrarIngreso(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("fallo de base de datos"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.login(TOKEN, null));

        assertEquals(AuthConstants.ERROR_LOGIN_FALLIDO, ex.getReason());
    }
}
