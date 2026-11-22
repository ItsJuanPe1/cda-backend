package co.edu.univalle.palmira.service.correo;

/** HU-038: envía un correo. La implementación real usa Gmail API; las pruebas la simulan. */
public interface CorreoSender {

    /**
     * Envía el correo.
     *
     * @return id del mensaje que devuelve el proveedor
     * @throws CorreoException con el motivo del fallo (sin datos personales ni secretos)
     */
    String enviar(CorreoSaliente correo) throws CorreoException;
}
