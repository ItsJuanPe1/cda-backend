package co.edu.univalle.palmira.service.correo;

import co.edu.univalle.palmira.config.GmailProperties;
import co.edu.univalle.palmira.config.NotificacionesProperties;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HU-038: envía correos por Gmail API ({@code users.messages.send}, userId «me») con OAuth 2.0.
 * {@link UserCredentials} obtiene el access token con el refresh token y lo renueva solo; el
 * token nunca se guarda ni se escribe en el log.
 * <p>
 * Si falta la configuración, o Google responde {@code invalid_grant}, la aplicación sigue
 * funcionando: el envío falla con su motivo y se deja un aviso en el log (sin datos personales ni
 * valores de secretos).
 */
@Component
@Slf4j
public class GmailCorreoSender implements CorreoSender {

    private static final String APLICACION = "cda-backend";
    private static final String AVISO_CONFIGURACION = "Envío de correos deshabilitado: falta configurar "
            + "GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN, MAIL_FROM o MAIL_COPIA_NOTIFICACIONES. "
            + "Los correos quedarán FALLIDOS. Ver docs/despliegue.md.";
    private static final String AVISO_REVOCADO = "Gmail respondió invalid_grant: el refresh token de la cuenta "
            + "de notificaciones fue revocado o venció. Los correos quedarán FALLIDOS hasta regenerarlo "
            + "(ver docs/despliegue.md, «Regenerar el refresh token»).";

    private final GmailProperties gmail;
    private final NotificacionesProperties notificaciones;
    private final AtomicBoolean avisoConfiguracion = new AtomicBoolean(false);
    private final AtomicBoolean avisoRevocado = new AtomicBoolean(false);
    private volatile Gmail cliente;

    public GmailCorreoSender(GmailProperties gmail, NotificacionesProperties notificaciones) {
        this.gmail = gmail;
        this.notificaciones = notificaciones;
    }

    /** Aviso al arrancar si falta configuración. La aplicación arranca igual. */
    @EventListener(ApplicationReadyEvent.class)
    public void verificarConfiguracion() {
        if (!configuracionCompleta()) {
            avisoConfiguracion.set(true);
            log.warn(AVISO_CONFIGURACION);
        }
    }

    public boolean configuracionCompleta() {
        return gmail.completa() && tieneValor(notificaciones.getMailFrom()) && tieneValor(notificaciones.getCopia());
    }

    @Override
    public String enviar(CorreoSaliente correo) throws CorreoException {
        if (!gmail.completa() || !tieneValor(correo.de()) || !tieneValor(correo.para())) {
            if (avisoConfiguracion.compareAndSet(false, true)) {
                log.warn(AVISO_CONFIGURACION);
            }
            throw new CorreoException(CorreoException.Motivo.CONFIGURACION_INCOMPLETA);
        }
        try {
            Message mensaje = new Message().setRaw(CorreoMime.construirRaw(correo));
            Message enviado = cliente().users().messages().send("me", mensaje).execute();
            avisoRevocado.set(false);
            return enviado.getId();
        } catch (IOException | RuntimeException e) {
            CorreoException.Motivo motivo = clasificar(e);
            if (motivo == CorreoException.Motivo.AUTORIZACION_REVOCADA && avisoRevocado.compareAndSet(false, true)) {
                log.warn(AVISO_REVOCADO);
            }
            // Solo tipos de excepción, código HTTP y códigos de error de Google: nunca el mensaje,
            // el destinatario ni los tokens.
            log.warn("Fallo al enviar por Gmail API ({}). Detalle técnico: {}", motivo.texto(), detalleSeguro(e));
            throw new CorreoException(motivo);
        }
    }

    /** Códigos OAuth que indican un problema del cliente o del permiso, no del servicio. */
    private static final Set<String> ERRORES_OAUTH_DE_AUTORIZACION = Set.of(
            "invalid_client", "unauthorized_client", "access_denied", "invalid_scope", "invalid_request");
    private static final Pattern CODIGO_OAUTH = Pattern.compile("\"error\"\\s*:\\s*\"([A-Za-z_]+)\"");

    /**
     * Criterio 8: clasifica el error de Google. Solo 429, 5xx y fallas de red se reintentan.
     * No lee ni devuelve el contenido del mensaje.
     */
    static CorreoException.Motivo clasificar(Throwable error) {
        if (esInvalidGrant(error)) {
            return CorreoException.Motivo.AUTORIZACION_REVOCADA;
        }
        Optional<String> codigoOauth = codigoErrorOauth(error);
        if (codigoOauth.isPresent() && ERRORES_OAUTH_DE_AUTORIZACION.contains(codigoOauth.get())) {
            return CorreoException.Motivo.AUTORIZACION_RECHAZADA;
        }
        Throwable http = primerErrorHttp(error);
        if (http instanceof GoogleJsonResponseException g) {
            int status = g.getStatusCode();
            if (status == 429 || status >= 500 || esLimiteDeCuota(g.getDetails())) {
                return CorreoException.Motivo.NO_DISPONIBLE;
            }
            if (status == 401 || status == 403) {
                return CorreoException.Motivo.AUTORIZACION_RECHAZADA;
            }
            return CorreoException.Motivo.MENSAJE_RECHAZADO;
        }
        if (http instanceof HttpResponseException h) {
            // Errores del servidor de tokens (por ejemplo, cliente OAuth inválido).
            int status = h.getStatusCode();
            if (status == 429 || status >= 500) {
                return CorreoException.Motivo.NO_DISPONIBLE;
            }
            if (status == 400 || status == 401 || status == 403) {
                return CorreoException.Motivo.AUTORIZACION_RECHAZADA;
            }
            return CorreoException.Motivo.MENSAJE_RECHAZADO;
        }
        return CorreoException.Motivo.NO_DISPONIBLE;
    }

    /** Código «error» de una respuesta OAuth de Google (por ejemplo, unauthorized_client), si lo hay. */
    static Optional<String> codigoErrorOauth(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            Optional<String> codigo = buscarCodigo(t.getMessage());
            if (codigo.isEmpty() && t instanceof HttpResponseException h) {
                codigo = buscarCodigo(h.getContent());
            }
            if (codigo.isPresent()) {
                return codigo;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> buscarCodigo(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher m = CODIGO_OAUTH.matcher(texto);
        return m.find() ? Optional.of(m.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    /**
     * Descripción para el log sin datos personales ni secretos: tipos de excepción de la cadena,
     * código HTTP, código OAuth y razones de Gmail API (por ejemplo, accessNotConfigured).
     */
    static String detalleSeguro(Throwable error) {
        List<String> tipos = new ArrayList<>();
        Integer status = null;
        List<String> razones = new ArrayList<>();
        for (Throwable t = error; t != null && tipos.size() < 6; t = t.getCause()) {
            tipos.add(t.getClass().getSimpleName());
            if (status == null && t instanceof HttpResponseException h) {
                status = h.getStatusCode();
            }
            if (t instanceof GoogleJsonResponseException g && g.getDetails() != null && g.getDetails().getErrors() != null) {
                g.getDetails().getErrors().stream().map(GoogleJsonError.ErrorInfo::getReason)
                        .filter(r -> r != null && r.matches("[A-Za-z_]{1,60}")).forEach(razones::add);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return "tipos=" + String.join(" <- ", tipos)
                + ", http=" + (status == null ? "—" : status)
                + ", oauth=" + codigoErrorOauth(error).orElse("—")
                + ", razones=" + (razones.isEmpty() ? "—" : String.join(",", razones));
    }

    private static Throwable primerErrorHttp(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpResponseException) {
                return t;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    /** El servidor de tokens de Google responde «invalid_grant» cuando el refresh token no sirve. */
    static boolean esInvalidGrant(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (contieneInvalidGrant(t.getMessage())) {
                return true;
            }
            if (t instanceof HttpResponseException h && contieneInvalidGrant(h.getContent())) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static boolean contieneInvalidGrant(String texto) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains("invalid_grant");
    }

    private static boolean esLimiteDeCuota(GoogleJsonError detalle) {
        if (detalle == null || detalle.getErrors() == null) {
            return false;
        }
        return detalle.getErrors().stream()
                .map(GoogleJsonError.ErrorInfo::getReason)
                .anyMatch(r -> r != null && r.toLowerCase(Locale.ROOT).contains("ratelimit"));
    }

    private Gmail cliente() {
        Gmail actual = cliente;
        if (actual == null) {
            synchronized (this) {
                actual = cliente;
                if (actual == null) {
                    actual = crearCliente();
                    cliente = actual;
                }
            }
        }
        return actual;
    }

    private static final int TIMEOUT_HTTP_MS = 10_000;

    /** Cliente de Gmail API con credenciales de usuario (refresh token) y timeouts explícitos. */
    protected Gmail crearCliente() {
        UserCredentials credenciales = UserCredentials.newBuilder()
                .setClientId(gmail.getClientId().trim())
                .setClientSecret(gmail.getClientSecret().trim())
                .setRefreshToken(gmail.getRefreshToken().trim())
                .build();
        HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credenciales);
        com.google.api.client.http.HttpRequestInitializer initializer = request -> {
            adapter.initialize(request);
            request.setConnectTimeout(TIMEOUT_HTTP_MS);
            request.setReadTimeout(TIMEOUT_HTTP_MS);
        };
        return new Gmail.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), initializer)
                .setApplicationName(APLICACION)
                .build();
    }

    private static boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
