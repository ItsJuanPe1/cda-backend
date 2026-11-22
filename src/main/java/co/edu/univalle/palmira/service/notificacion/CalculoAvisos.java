package co.edu.univalle.palmira.service.notificacion;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * HU-038 y HU-039: cálculo común del momento de los avisos de un plazo (docente o coordinador).
 * Fechas en UTC; los días se restan en la zona de la aplicación para conservar la hora local.
 */
public final class CalculoAvisos {

    private CalculoAvisos() {
    }

    /**
     * Recordatorio {@code dias} días antes del cierre, a la misma hora; nunca antes del inicio del plazo
     * ni en el pasado (si ya pasó con el plazo abierto, sale ahora). Vacío si el plazo ya cerró.
     */
    public static Optional<Instant> recordatorio(Instant inicio, Instant fin, Instant ahora, int dias, ZoneId zona) {
        if (inicio == null || fin == null || !fin.isAfter(ahora)) {
            return Optional.empty();
        }
        Instant objetivo = fin.atZone(zona).minusDays(Math.max(0, dias)).toInstant();
        return Optional.of(max(max(objetivo, inicio), ahora));
    }

    public static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
