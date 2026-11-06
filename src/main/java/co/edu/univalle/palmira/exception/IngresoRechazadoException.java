package co.edu.univalle.palmira.exception;

import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-058 criterio 2: rechazo de un ingreso. Conserva el estado y el mensaje de las HU de login
 * (sigue siendo una ResponseStatusException) y agrega el motivo y el correo para la bitácora.
 */
@Getter
public class IngresoRechazadoException extends ResponseStatusException {

    private final MotivoRechazoIngreso motivo;
    /** Correo institucional verificado, o null si el rechazo ocurrió antes de verificarlo. */
    private final String correo;

    public IngresoRechazadoException(HttpStatus status, String mensaje, MotivoRechazoIngreso motivo, String correo) {
        super(status, mensaje);
        this.motivo = motivo;
        this.correo = correo;
    }
}
