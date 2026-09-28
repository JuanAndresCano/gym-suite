package co.analisys.entrenadores.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI entrenadoresOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Entrenadores Service API")
                .description("Gestión del personal entrenador y sus especialidades.")
                .version("v1"));
    }
}
