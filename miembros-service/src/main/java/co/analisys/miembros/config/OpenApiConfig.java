package co.analisys.miembros.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI miembrosOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Miembros Service API")
                .description("Gestión de inscripciones y datos de los miembros del gimnasio.")
                .version("v1"));
    }
}
