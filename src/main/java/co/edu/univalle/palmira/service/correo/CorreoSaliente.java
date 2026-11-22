package co.edu.univalle.palmira.service.correo;

/** HU-038: correo de texto plano listo para enviar. No se escribe en el log. */
public record CorreoSaliente(String de, String para, String asunto, String cuerpo) {
}
