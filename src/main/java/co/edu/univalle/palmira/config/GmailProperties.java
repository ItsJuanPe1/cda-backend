package co.edu.univalle.palmira.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HU-038: cliente OAuth 2.0 para enviar correos por Gmail API (cda.gmail.*), con el refresh token
 * de la cuenta de notificaciones (scope gmail.send). Es distinto del cliente de inicio de sesión
 * (auth.google.client-id). Los valores son secretos: no tienen valor por defecto, no se imprimen
 * (sin toString) y nunca se escriben en el log.
 */
@Component
@ConfigurationProperties(prefix = "cda.gmail")
@Getter
@Setter
public class GmailProperties {

    /** GMAIL_CLIENT_ID. */
    private String clientId = "";

    /** GMAIL_CLIENT_SECRET. */
    private String clientSecret = "";

    /** GMAIL_REFRESH_TOKEN. */
    private String refreshToken = "";

    /** true si están los tres valores. */
    public boolean completa() {
        return tieneValor(clientId) && tieneValor(clientSecret) && tieneValor(refreshToken);
    }

    private static boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
