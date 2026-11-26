package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HU-038: tarea programada de la cola de correos. Cada corrida resuelve los eventos vencidos y
 * después envía los pendientes, hasta {@code cda.notificaciones.lote} de cada uno. Un error en un
 * elemento no detiene los demás (criterio 8).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacionScheduler {

    private static final int MAX_ERRORES_CONSECUTIVOS = 3;

    private final NotificacionProcesador procesador;
    private final NotificacionesProperties propiedades;

    @Scheduled(fixedDelayString = "${cda.notificaciones.intervalo-ms:60000}",
            initialDelayString = "${cda.notificaciones.retraso-inicial-ms:30000}")
    public void ejecutar() {
        procesarEventos();
        enviarPendientes();
    }

    int procesarEventos() {
        int procesados = 0;
        int erroresConsecutivos = 0;
        int lote = Math.max(1, propiedades.getLote());
        for (int i = 0; i < lote; i++) {
            try {
                if (!procesador.procesarSiguienteEvento()) {
                    break;
                }
                procesados++;
                erroresConsecutivos = 0;
            } catch (RuntimeException e) {
                erroresConsecutivos++;
                log.error("No se pudo procesar un evento de notificación: {}", e.getClass().getSimpleName());
                if (erroresConsecutivos >= MAX_ERRORES_CONSECUTIVOS) {
                    log.warn("Se detiene el lote de eventos tras {} errores consecutivos", erroresConsecutivos);
                    break;
                }
            }
        }
        return procesados;
    }

    int enviarPendientes() {
        int enviados = 0;
        int erroresConsecutivos = 0;
        int lote = Math.max(1, propiedades.getLote());
        for (int i = 0; i < lote; i++) {
            try {
                if (!procesador.enviarSiguiente()) {
                    break;
                }
                enviados++;
                erroresConsecutivos = 0;
            } catch (RuntimeException e) {
                erroresConsecutivos++;
                log.error("No se pudo procesar un envío de notificación: {}", e.getClass().getSimpleName());
                if (erroresConsecutivos >= MAX_ERRORES_CONSECUTIVOS) {
                    log.warn("Se detiene el lote de envíos tras {} errores consecutivos", erroresConsecutivos);
                    break;
                }
            }
        }
        return enviados;
    }
}
