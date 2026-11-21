package co.edu.univalle.palmira.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * HU-038: reloj de la aplicación (UTC) y tareas programadas de la cola de correos.
 * Las tareas se pueden apagar con cda.notificaciones.programador-habilitado=false.
 */
@Configuration
public class NotificacionesConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "cda.notificaciones", name = "programador-habilitado",
            havingValue = "true", matchIfMissing = true)
    static class Programador {
    }
}
