package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HU-038: lo que cada HU de notificaciones aporta a la cola genérica: quién recibe, si el evento
 * sigue vigente y el texto de los correos. HU-038 lo implementa en {@code RecordatorioDocenteService};
 * HU-039, HU-055 y HU-057 agregarán los suyos sin cambiar la cola.
 * <p>
 * Todos los métodos se llaman al momento de procesar o enviar, no al programar.
 */
public interface ManejadorNotificacion {

    boolean atiende(TipoEventoNotificacion tipo);

    TipoDestinatario tipoDestinatario();

    /** false si el evento ya no debe enviarse (por ejemplo, el período dejó de estar vigente). */
    boolean eventoVigente(Notificacion notificacion);

    /** Ids de los destinatarios activos en este momento. */
    List<Long> resolverDestinatarios(Notificacion notificacion);

    /** Destinatario con su correo vigente, o vacío si su perfil está desactivado. */
    Optional<DestinatarioCorreo> resolverDestinatario(Long destinatarioId);

    /** Datos vigentes de los destinatarios para la copia, aunque ahora estén desactivados. */
    List<DestinatarioCorreo> describirDestinatarios(List<Long> destinatarioIds);

    CorreoContenido construir(Notificacion notificacion, DestinatarioCorreo destinatario);

    /** Copia por evento: resumen y lista de destinatarios. */
    CorreoContenido construirCopia(Notificacion notificacion, List<DestinatarioCorreo> destinatarios, long omitidos);

    /**
     * Copia con los omitidos separados por motivo (por ejemplo, «Perfil desactivado» o «Asignación
     * completa»). Por defecto suma todos y usa {@link #construirCopia(Notificacion, List, long)}.
     */
    default CorreoContenido construirCopia(Notificacion notificacion, List<DestinatarioCorreo> destinatarios,
            Map<String, Long> omitidosPorMotivo) {
        long total = omitidosPorMotivo == null ? 0 : omitidosPorMotivo.values().stream().mapToLong(Long::longValue).sum();
        return construirCopia(notificacion, destinatarios, total);
    }

    /**
     * HU-039 criterio 3: motivo para no enviar a un destinatario activo (por ejemplo, «Asignación
     * completa»). Vacío si se debe enviar. Se evalúa al momento del envío.
     */
    default Optional<String> motivoOmision(Notificacion notificacion, Long destinatarioId) {
        return Optional.empty();
    }
}
