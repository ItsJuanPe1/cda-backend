package co.edu.univalle.palmira.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Programación Académica - Universidad del Valle")
                        .version("1.0.0")
                        .description("API REST para la gestión de programas académicos, asignaturas y pensum curricular.")
                        .contact(new Contact()
                                .name("Universidad del Valle - Palmira")
                                .url("https://www.univalle.edu.co")));
    }
}
