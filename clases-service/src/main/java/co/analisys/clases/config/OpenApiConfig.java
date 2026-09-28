package co.analisys.clases.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /** Operaciones que SecurityConfig restringe por rol (método HTTP + ruta). */
    private static final List<RestrictedRoute> RESTRICTED_ROUTES = List.of(
            new RestrictedRoute(PathItem.HttpMethod.POST, "/api/clases"),
            new RestrictedRoute(PathItem.HttpMethod.PATCH, "/api/clases/{id}/horario"),
            new RestrictedRoute(PathItem.HttpMethod.POST, "/api/clases/{id}/inscripciones"),
            new RestrictedRoute(PathItem.HttpMethod.DELETE, "/api/clases/{id}/inscripciones/{miembroId}")
    );

    private record RestrictedRoute(PathItem.HttpMethod method, String pattern) {}

    @Bean
    public OpenAPI clasesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clases Service API")
                        .description("Programación de clases, horarios e inscripción de miembros.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer securityResponsesCustomizer() {
        AntPathMatcher matcher = new AntPathMatcher();
        return openApi -> openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> {
                    addIfAbsent(operation, "401", "No autenticado: falta el token JWT o no es válido");
                    boolean restricted = RESTRICTED_ROUTES.stream()
                            .anyMatch(r -> r.method() == method && matcher.match(r.pattern(), path));
                    if (restricted) {
                        addIfAbsent(operation, "403", "Prohibido: el rol del usuario no tiene permiso");
                    }
                }));
    }

    private static void addIfAbsent(Operation operation, String code, String description) {
        if (operation.getResponses() == null || !operation.getResponses().containsKey(code)) {
            operation.getResponses().addApiResponse(code, new ApiResponse().description(description));
        }
    }
}
