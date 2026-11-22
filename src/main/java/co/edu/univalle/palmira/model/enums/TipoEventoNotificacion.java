package co.edu.univalle.palmira.model.enums;

/** HU-038: tipo de evento de la cola de correos. HU-039, HU-055 y HU-057 agregarán los suyos. */
public enum TipoEventoNotificacion {
    // HU-038: plazo de disponibilidad docente
    APERTURA_PLAZO_DOCENTE,
    RECORDATORIO_CIERRE_DOCENTE,
    ACTUALIZACION_PLAZO_DOCENTE,
    // HU-039: plazo de asignación de docentes (coordinadores)
    CONFIGURACION_PLAZO_COORDINADOR,
    ACTUALIZACION_PLAZO_COORDINADOR,
    RECORDATORIO_CIERRE_COORDINADOR
}
