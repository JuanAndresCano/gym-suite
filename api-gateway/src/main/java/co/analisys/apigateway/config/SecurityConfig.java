package co.analisys.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Seguridad reactiva del gateway. Replica las reglas de autorizacion de los servicios
 * (que siguen validando el JWT por su cuenta) para rechazar con 401/403 antes de reenviar.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/miembros").hasAuthority("ROLE_ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/pagos").hasAnyAuthority("ROLE_ADMIN", "ROLE_MEMBER")
                        .pathMatchers(HttpMethod.POST, "/api/clases").hasAnyAuthority("ROLE_ADMIN", "ROLE_TRAINER")
                        .pathMatchers(HttpMethod.PATCH, "/api/clases/*/horario").hasAnyAuthority("ROLE_ADMIN", "ROLE_TRAINER")
                        .pathMatchers(HttpMethod.POST, "/api/clases/*/inscripciones/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MEMBER")
                        .pathMatchers(HttpMethod.DELETE, "/api/clases/*/inscripciones/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MEMBER")
                        .pathMatchers(HttpMethod.POST, "/api/entrenadores").hasAuthority("ROLE_ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/equipos/**").hasAuthority("ROLE_ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/equipos/**").hasAuthority("ROLE_ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Convierte realm_access.roles en authorities. En el realm "gimnasio" los roles ya se llaman
     * ROLE_ADMIN, ROLE_MEMBER y ROLE_TRAINER (igual que en los servicios); si llegara un rol sin
     * prefijo (ADMIN) se le antepone ROLE_.
     */
    ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private static Flux<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            return Flux.fromIterable(roles)
                    .map(String::valueOf)
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new);
        }
        return Flux.empty();
    }
}
