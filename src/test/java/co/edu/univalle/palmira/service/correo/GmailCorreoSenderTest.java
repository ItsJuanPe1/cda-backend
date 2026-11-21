package co.edu.univalle.palmira.service.correo;

import co.edu.univalle.palmira.config.GmailProperties;
import co.edu.univalle.palmira.config.NotificacionesProperties;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** HU-038 criterio 8: envío por Gmail API. No llama a Google: el cliente se simula. */
class GmailCorreoSenderTest {

    private static final CorreoSaliente CORREO = new CorreoSaliente("notificaciones.prueba@correounivalle.edu.co",
            "ana.prueba@correounivalle.edu.co", "Asunto", "Cuerpo");

    private GmailProperties gmail;
    private NotificacionesProperties notificaciones;

    @BeforeEach
    void setUp() {
        gmail = new GmailProperties();
        notificaciones = new NotificacionesProperties();
        notificaciones.setMailFrom("notificaciones.prueba@correounivalle.edu.co");
        notificaciones.setCopia("notificaciones.prueba@correounivalle.edu.co");
    }

    private void configurar() {
        gmail.setClientId("client-id-de-prueba");
        gmail.setClientSecret("secreto-de-prueba");
        gmail.setRefreshToken("refresh-de-prueba");
    }

    /** Sender que falla si intenta crear un cliente real de Google. */
    private GmailCorreoSender senderSinGoogle() {
        return new GmailCorreoSender(gmail, notificaciones) {
            @Override
            protected Gmail crearCliente() {
                throw new AssertionError("No debe llamar a Google");
            }
        };
    }

    private static GoogleJsonResponseException errorGmail(int status, String razon) {
        GoogleJsonError detalle = new GoogleJsonError();
        if (razon != null) {
            GoogleJsonError.ErrorInfo info = new GoogleJsonError.ErrorInfo();
            info.setReason(razon);
            detalle.setErrors(List.of(info));
        }
        return new GoogleJsonResponseException(new HttpResponseException.Builder(status, "error", new HttpHeaders()), detalle);
    }

    @Test
    @DisplayName("Criterio 8: sin los secretos de Gmail no se llama a Google y el motivo es configuración incompleta")
    void sinSecretos() {
        CorreoException e = assertThrows(CorreoException.class, () -> senderSinGoogle().enviar(CORREO));
        assertEquals(CorreoException.Motivo.CONFIGURACION_INCOMPLETA, e.getMotivo());
        assertFalse(e.getMotivo().reintentable());
    }

    @Test
    @DisplayName("Criterio 8: sin remitente (MAIL_FROM) tampoco se envía")
    void sinRemitente() {
        configurar();
        CorreoSaliente sinDe = new CorreoSaliente("", CORREO.para(), "Asunto", "Cuerpo");
        CorreoException e = assertThrows(CorreoException.class, () -> senderSinGoogle().enviar(sinDe));
        assertEquals(CorreoException.Motivo.CONFIGURACION_INCOMPLETA, e.getMotivo());
    }

    @Test
    @DisplayName("La configuración completa exige los tres secretos, el remitente y la copia")
    void configuracionCompleta() {
        GmailCorreoSender sender = senderSinGoogle();
        assertFalse(sender.configuracionCompleta());
        configurar();
        assertTrue(sender.configuracionCompleta());
        notificaciones.setCopia(" ");
        assertFalse(sender.configuracionCompleta());
        assertDoesNotThrow(sender::verificarConfiguracion);
    }

    @Test
    @DisplayName("Criterio 1: con la configuración completa se envía por users.messages.send y se devuelve el id")
    void envioExitoso() throws Exception {
        configurar();
        Gmail cliente = mock(Gmail.class);
        Gmail.Users usuarios = mock(Gmail.Users.class);
        Gmail.Users.Messages mensajes = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.Send enviar = mock(Gmail.Users.Messages.Send.class);
        when(cliente.users()).thenReturn(usuarios);
        when(usuarios.messages()).thenReturn(mensajes);
        when(mensajes.send(eq("me"), any(Message.class))).thenReturn(enviar);
        when(enviar.execute()).thenReturn(new Message().setId("msg-123"));
        GmailCorreoSender sender = new GmailCorreoSender(gmail, notificaciones) {
            @Override
            protected Gmail crearCliente() {
                return cliente;
            }
        };

        assertEquals("msg-123", sender.enviar(CORREO));
        verify(mensajes).send(eq("me"), argThat(m -> m.getRaw() != null && !m.getRaw().isBlank()));
    }

    @Test
    @DisplayName("Criterio 8: invalid_grant (refresh token revocado o vencido) no se reintenta")
    void invalidGrant() {
        IOException e = new IOException("Error getting access token: {\"error\":\"invalid_grant\"}");
        assertEquals(CorreoException.Motivo.AUTORIZACION_REVOCADA, GmailCorreoSender.clasificar(e));
        assertFalse(CorreoException.Motivo.AUTORIZACION_REVOCADA.reintentable());
    }

    @Test
    @DisplayName("Criterio 8: invalid_grant dentro de la causa también se detecta")
    void invalidGrantEnCausa() {
        HttpResponseException token = new HttpResponseException.Builder(400, "Bad Request", new HttpHeaders())
                .setContent("{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}")
                .build();
        assertEquals(CorreoException.Motivo.AUTORIZACION_REVOCADA,
                GmailCorreoSender.clasificar(new IOException("fallo", token)));
    }

    @Test
    @DisplayName("Criterio 8: 429, 5xx, límite de cuota y fallas de red se reintentan")
    void reintentables() {
        assertEquals(CorreoException.Motivo.NO_DISPONIBLE, GmailCorreoSender.clasificar(errorGmail(429, null)));
        assertEquals(CorreoException.Motivo.NO_DISPONIBLE, GmailCorreoSender.clasificar(errorGmail(503, null)));
        assertEquals(CorreoException.Motivo.NO_DISPONIBLE, GmailCorreoSender.clasificar(errorGmail(403, "userRateLimitExceeded")));
        assertEquals(CorreoException.Motivo.NO_DISPONIBLE, GmailCorreoSender.clasificar(new SocketTimeoutException()));
        assertTrue(CorreoException.Motivo.NO_DISPONIBLE.reintentable());
    }

    @Test
    @DisplayName("Criterio 8: 400, 401 y 403 de Gmail no se reintentan")
    void noReintentables() {
        assertEquals(CorreoException.Motivo.MENSAJE_RECHAZADO, GmailCorreoSender.clasificar(errorGmail(400, null)));
        assertEquals(CorreoException.Motivo.AUTORIZACION_RECHAZADA, GmailCorreoSender.clasificar(errorGmail(401, null)));
        assertEquals(CorreoException.Motivo.AUTORIZACION_RECHAZADA, GmailCorreoSender.clasificar(errorGmail(403, "insufficientPermissions")));
    }

    @Test
    @DisplayName("Criterio 8: unauthorized_client o invalid_client del servidor de tokens no se reintentan")
    void clienteOauthInvalido() {
        IOException e = new IOException("Error getting access token for service account: 401 Unauthorized\n"
                + "POST https://oauth2.googleapis.com/token\n{\"error\":\"unauthorized_client\",\"error_description\":\"Unauthorized\"}");
        assertEquals(CorreoException.Motivo.AUTORIZACION_RECHAZADA, GmailCorreoSender.clasificar(e));
        assertEquals("unauthorized_client", GmailCorreoSender.codigoErrorOauth(e).orElseThrow());
        assertEquals(CorreoException.Motivo.AUTORIZACION_RECHAZADA,
                GmailCorreoSender.clasificar(new IOException("{\"error\": \"invalid_client\"}")));
    }

    @Test
    @DisplayName("Criterio 8: el detalle técnico del log no lleva correos, tokens ni el texto del error")
    void detalleSeguroSinDatos() {
        IOException e = new IOException("token refresh-de-prueba para ana.prueba@correounivalle.edu.co "
                + "{\"error\":\"unauthorized_client\"}", errorGmail(403, "accessNotConfigured"));
        String detalle = GmailCorreoSender.detalleSeguro(e);
        assertEquals("tipos=IOException <- GoogleJsonResponseException, http=403, oauth=unauthorized_client, "
                + "razones=accessNotConfigured", detalle);
        assertFalse(detalle.contains("@"));
        assertFalse(detalle.contains("refresh-de-prueba"));
    }

    @Test
    @DisplayName("Criterio 8: el motivo guardado no lleva datos del error original")
    void motivoSinDatos() {
        CorreoException e = new CorreoException(CorreoException.Motivo.MENSAJE_RECHAZADO);
        assertEquals("Gmail rechazó el mensaje", e.getMessage());
        assertNull(e.getCause());
    }
}
