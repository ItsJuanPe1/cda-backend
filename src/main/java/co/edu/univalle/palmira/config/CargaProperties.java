package co.edu.univalle.palmira.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Parámetros de la carga académica docente (cda.carga.*). */
@Component
@ConfigurationProperties(prefix = "cda.carga")
@Getter
@Setter
public class CargaProperties {

    /** Límite de horas semanales de un docente en un período. */
    private int maxHorasDocente = 20;
}