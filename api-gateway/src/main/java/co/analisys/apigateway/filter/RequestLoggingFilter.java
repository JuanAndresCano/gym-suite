package co.analisys.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filtro global: registra metodo, path y usuario (preferred_username) de cada peticion y
 * agrega un X-Request-Id a la peticion hacia el backend y a la respuesta.
 * El header Authorization no se toca: el gateway ya lo reenvia por defecto.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> auth instanceof JwtAuthenticationToken jwt
                        ? jwt.getToken().getClaimAsString("preferred_username")
                        : auth.getName())
                .defaultIfEmpty("anonimo")
                .doOnNext(usuario -> log.info("[{}] {} {} usuario={}", requestId,
                        request.getMethod(), request.getPath().value(), usuario))
                .then(Mono.defer(() -> chain.filter(exchange.mutate().request(request).build())));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
