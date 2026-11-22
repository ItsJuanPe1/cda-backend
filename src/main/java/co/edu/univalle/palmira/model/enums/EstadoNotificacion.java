package co.edu.univalle.palmira.model.enums;

/** HU-038: estado de un evento de la cola de correos. */
public enum EstadoNotificacion {
    /** Aún no llega su hora. */
    PENDIENTE,
    /** Se resolvieron los destinatarios y se crearon los envíos. */
    PROCESADO,
    /** El período pasó a historial, se desactivó o el aviso quedó sin fecha válida. */
    CANCELADO
}
