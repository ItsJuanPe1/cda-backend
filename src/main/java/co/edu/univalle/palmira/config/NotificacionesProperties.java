package co.edu.univalle.palmira.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * HU-038: parámetros de la cola de correos (cda.notificaciones.*). Sin secretos: los del
 * cliente OAuth de Gmail están en {@link GmailProperties}.
 */
@Component
@ConfigurationProperties(prefix = "cda.notificaciones")
@Getter
@Setter
public class NotificacionesProperties {

    /** Remitente (MAIL_FROM). */
    private String mailFrom = "";

    /** Cuenta que recibe una copia por evento (MAIL_COPIA_NOTIFICACIONES). */
    private String copia = "";

    /** Enlace al sistema en los correos (APP_URL). */
    private String appUrl = "";

    /** Días antes del cierre del plazo docente para el recordatorio (RECORDATORIO_DIAS_ANTES). */
    private int recordatorioDiasAntes = 3;

    /** Zona para mostrar las fechas en los correos (APP_TIMEZONE). */
    private String zona = "America/Bogota";

    /** Cada cuánto corre la tarea programada, en milisegundos. */
    private long intervaloMs = 60000;

    /** Máximo de eventos y de envíos que procesa cada corrida. */
    private int lote = 100;

    /** Intentos máximos de un envío antes de quedar FALLIDO. */
    private int maxIntentos = 5;

    /** Espera antes de cada reintento, en minutos (después del 1.º, 2.º, … intento fallido). */
    private List<Integer> reintentosMinutos = new ArrayList<>(List.of(5, 15, 60, 240));

    public ZoneId zoneId() {
        return ZoneId.of(zona);
    }
}
