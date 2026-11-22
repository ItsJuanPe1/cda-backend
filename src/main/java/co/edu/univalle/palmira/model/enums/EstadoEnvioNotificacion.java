package co.edu.univalle.palmira.model.enums;

/** HU-038: estado de un envío (un destinatario o la copia) de la cola de correos. */
public enum EstadoEnvioNotificacion {
    PENDIENTE,
    ENVIADO,
    FALLIDO,
    CANCELADO,
    /** El perfil estaba desactivado al momento de enviar (criterio 5). */
    OMITIDO
}
