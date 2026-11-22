package co.edu.univalle.palmira.service.correo;

/**
 * HU-038 criterio 8: fallo de envío clasificado. El mensaje es el motivo que se guarda en
 * notificaciones_envios.ultimo_error: nunca lleva correos, nombres, tokens ni secretos.
 */
public class CorreoException extends Exception {

    public enum Motivo {
        CONFIGURACION_INCOMPLETA("Configuración de Gmail incompleta", false),
        AUTORIZACION_REVOCADA("Autorización de Gmail revocada o vencida", false),
        AUTORIZACION_RECHAZADA("Gmail rechazó la autorización", false),
        MENSAJE_RECHAZADO("Gmail rechazó el mensaje", false),
        NO_DISPONIBLE("Gmail no disponible", true);

        private final String texto;
        private final boolean reintentable;

        Motivo(String texto, boolean reintentable) {
            this.texto = texto;
            this.reintentable = reintentable;
        }

        public String texto() {
            return texto;
        }

        public boolean reintentable() {
            return reintentable;
        }
    }

    private final Motivo motivo;

    public CorreoException(Motivo motivo) {
        // Sin causa: la excepción original puede traer datos del mensaje o del token.
        super(motivo.texto(), null, false, false);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
