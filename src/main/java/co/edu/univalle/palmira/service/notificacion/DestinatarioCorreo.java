package co.edu.univalle.palmira.service.notificacion;

/** HU-038: destinatario resuelto al momento del envío. No se guarda ni se escribe en el log. */
public record DestinatarioCorreo(Long id, String nombre, String correo) {
}
