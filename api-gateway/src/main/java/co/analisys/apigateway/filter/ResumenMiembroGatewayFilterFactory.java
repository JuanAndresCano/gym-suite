package co.analisys.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Map;

/**
 * Filtro "ResumenMiembro": no reenvia a ningun backend, responde el mismo con un JSON que agrega
 * el miembro, sus clases y sus pagos. Llama en paralelo (timeout de 3 s cada una) a miembros-service
 * y clases-service por nombre de servicio (WebClient balanceado) reenviando el Authorization original.
 *
 * Uso en application.yml:  filters: - ResumenMiembro
 */
@Component
public class ResumenMiembroGatewayFilterFactory
        extends AbstractGatewayFilterFactory<ResumenMiembroGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(ResumenMiembroGatewayFilterFactory.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final JsonMapper mapper;

    public ResumenMiembroGatewayFilterFactory(@LoadBalanced WebClient.Builder loadBalancedBuilder, JsonMapper mapper) {
        super(Config.class);
        this.webClient = loadBalancedBuilder.build();
        this.mapper = mapper;
    }

    /** El filtro no tiene parametros. */
    public static class Config {
    }

    /** Resultado de una llamada: status HTTP (0 si no hubo respuesta) y cuerpo JSON si fue 2xx. */
    private record Respuesta(int status, JsonNode body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }

        boolean sinRespuesta() {
            return status == 0;
        }

        String motivo() {
            return sinRespuesta() ? "sin respuesta (servicio caido o timeout)" : "HTTP " + status;
        }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            Map<String, String> variables = ServerWebExchangeUtils.getUriTemplateVariables(exchange);
            String id = variables.get("id");
            if (id == null || !id.matches("\\d{1,18}")) {
                return responder(exchange, 400, error("El id del miembro debe ser numerico"));
            }
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            Mono<Respuesta> miembro = llamar("http://miembros-service/api/miembros/{id}", authorization, id);
            Mono<Respuesta> clases = llamar("http://clases-service/api/clases", authorization);
            Mono<Respuesta> pagos = llamar("http://miembros-service/api/miembros/{id}/pagos", authorization, id);

            return Mono.zip(miembro, clases, pagos)
                    .flatMap(t -> construirRespuesta(exchange, id, t.getT1(), t.getT2(), t.getT3()));
        };
    }

    private Mono<Respuesta> llamar(String url, String authorization, Object... variables) {
        WebClient.RequestHeadersSpec<?> request = webClient.get().uri(url, variables);
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(JsonNode.class)
                                .defaultIfEmpty(MissingNode.getInstance())
                                .map(body -> new Respuesta(status, body));
                    }
                    return response.releaseBody().thenReturn(new Respuesta(status, MissingNode.getInstance()));
                })
                .timeout(TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Fallo la llamada a {}: {}", url, e.toString());
                    return Mono.just(new Respuesta(0, MissingNode.getInstance()));
                });
    }

    private Mono<Void> construirRespuesta(ServerWebExchange exchange, String id,
                                          Respuesta miembro, Respuesta clases, Respuesta pagos) {
        // Si alguien rechaza el token, se propaga tal cual
        for (Respuesta r : new Respuesta[]{miembro, clases, pagos}) {
            if (r.status() == 401 || r.status() == 403) {
                return responder(exchange, r.status(), error(r.status() == 401 ? "No autenticado" : "Acceso denegado"));
            }
        }
        if (miembro.status() == 404) {
            return responder(exchange, 404, error("No existe el miembro con id " + id));
        }
        if (!miembro.ok()) {
            return responder(exchange, miembro.sinRespuesta() ? 503 : 502,
                    error("No se pudo obtener el miembro (miembros-service: " + miembro.motivo() + ")"));
        }

        ArrayNode advertencias = mapper.createArrayNode();
        ObjectNode resumen = mapper.createObjectNode();
        resumen.set("miembro", miembro.body());

        if (clases.ok()) {
            ArrayNode propias = mapper.createArrayNode();
            long idMiembro = Long.parseLong(id);
            for (JsonNode clase : clases.body()) {
                if (estaInscrito(clase.path("miembrosInscritos"), idMiembro)) {
                    ObjectNode c = propias.addObject();
                    c.set("id", clase.path("id"));
                    c.set("nombre", clase.path("nombre"));
                    c.set("horario", clase.path("horario"));
                    c.set("entrenadorNombre", clase.path("entrenadorNombre"));
                    c.set("entrenadorEspecialidad", clase.path("entrenadorEspecialidad"));
                }
            }
            resumen.set("clases", propias);
            resumen.put("totalClases", propias.size());
        } else {
            resumen.putNull("clases");
            resumen.putNull("totalClases");
            advertencias.add("No se pudieron obtener las clases (clases-service: " + clases.motivo() + ")");
        }

        if (pagos.ok()) {
            double total = 0;
            for (JsonNode pago : pagos.body()) {
                total += pago.path("monto").asDouble(0);
            }
            resumen.set("pagos", pagos.body());
            resumen.put("totalPagado", total);
        } else {
            resumen.putNull("pagos");
            resumen.putNull("totalPagado");
            advertencias.add("No se pudieron obtener los pagos (miembros-service: " + pagos.motivo() + ")");
        }

        resumen.set("advertencias", advertencias);
        return responder(exchange, 200, resumen);
    }

    private static boolean estaInscrito(JsonNode inscritos, long idMiembro) {
        for (JsonNode inscrito : inscritos) {
            if (inscrito.asLong(-1) == idMiembro) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode error(String mensaje) {
        return mapper.createObjectNode().put("error", mensaje);
    }

    private Mono<Void> responder(ServerWebExchange exchange, int status, JsonNode cuerpo) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = mapper.writeValueAsBytes(cuerpo);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
