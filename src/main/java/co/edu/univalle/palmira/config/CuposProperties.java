package co.edu.univalle.palmira.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Parámetros institucionales de los cupos de estudiantes (cda.cupos.*). */
@Component
@ConfigurationProperties(prefix = "cda.cupos")
@Getter
@Setter
public class CuposProperties {

    /** Máximo de cupos permitido por curso/grupo. */
    private int maxPorGrupo = 60;
}
