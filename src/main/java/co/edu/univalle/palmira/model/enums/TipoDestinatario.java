package co.edu.univalle.palmira.model.enums;

/** HU-038: a quién va un envío. COORDINADOR queda para HU-039. */
public enum TipoDestinatario {
    DOCENTE,
    COORDINADOR,
    /** Copia por evento a la cuenta de notificaciones; no tiene destinatario_id. */
    COPIA
}
