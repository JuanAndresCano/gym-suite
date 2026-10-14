package co.analisys.apigateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Decoder de prueba: los tokens son cadenas simples ("admin", "member", "trainer", "sin-prefijo")
 * que se convierten en un JWT con realm_access.roles, asi no hace falta Keycloak.
 */
@TestConfiguration
class TestJwtDecoderConfig {

    @Bean
    @Primary
    ReactiveJwtDecoder jwtDecoder() {
        return token -> switch (token) {
            case "admin" -> Mono.just(jwt(token, "admin.test", "ROLE_ADMIN"));
            case "member" -> Mono.just(jwt(token, "member.test", "ROLE_MEMBER"));
            case "trainer" -> Mono.just(jwt(token, "trainer.test", "ROLE_TRAINER"));
            case "sin-prefijo" -> Mono.just(jwt(token, "otro", "ADMIN"));
            default -> Mono.error(new BadJwtException("token invalido"));
        };
    }

    private static Jwt jwt(String token, String usuario, String rol) {
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("preferred_username", usuario)
                .claim("realm_access", Map.of("roles", List.of(rol)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
