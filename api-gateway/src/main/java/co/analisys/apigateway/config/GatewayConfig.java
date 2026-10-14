package co.analisys.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ruta de equipos con el Java DSL; las demas rutas viven en application.yml (mismo resultado, otro estilo). */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator equiposRoute(RouteLocatorBuilder builder,
                                     @Value("${gateway.equipos-uri:http://localhost:8084}") String equiposUri) {
        return builder.routes()
                .route("equipos", r -> r.path("/api/equipos/**").uri(equiposUri))
                .build();
    }
}
