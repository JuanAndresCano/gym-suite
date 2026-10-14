package co.analisys.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el gateway completo (cadena de seguridad + filtro global + enrutamiento) contra un backend
 * falso levantado en un puerto libre. Los tokens son cadenas simples ("admin", "member", "trainer")
 * que un ReactiveJwtDecoder de prueba convierte en JWT con realm_access.roles, asi no hace falta Keycloak.
 */
@Import(TestJwtDecoderConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
class GatewaySecurityAndRoutingTest {

    /** Peticion que llego al backend falso. */
    record Recibida(String metodo, String path, String authorization, String requestId) {}

    private static final List<Recibida> recibidas = new CopyOnWriteArrayList<>();
    private static final HttpServer backend = iniciarBackend();

    private static HttpServer iniciarBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                recibidas.add(new Recibida(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("X-Request-Id")));
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        // La ruta de equipos (Java DSL) apunta al backend falso
        registry.add("gateway.equipos-uri", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void detenerBackend() {
        backend.stop(0);
    }

    @LocalServerPort
    int puerto;

    WebTestClient client;

    @BeforeEach
    void preparar() {
        recibidas.clear();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build();
    }

    @Test
    void sinTokenDevuelve401YNoLlegaAlBackend() {
        client.get().uri("/api/equipos").exchange().expectStatus().isUnauthorized();
        assertThat(recibidas).isEmpty();
    }

    @Test
    void tokenInvalidoDevuelve401() {
        client.get().uri("/api/equipos").header("Authorization", "Bearer basura").exchange()
                .expectStatus().isUnauthorized();
        assertThat(recibidas).isEmpty();
    }

    @Test
    void healthEsPublico() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void miembroNoPuedeCrearMiembrosYElGatewayLoRechazaCon403() {
        client.post().uri("/api/miembros").header("Authorization", "Bearer member")
                .bodyValue("{}").header("Content-Type", "application/json")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void miembroNoPuedeCrearEquiposYElBackendNoRecibeNada() {
        client.post().uri("/api/equipos").header("Authorization", "Bearer member")
                .header("Content-Type", "application/json").bodyValue("{}")
                .exchange().expectStatus().isForbidden();
        assertThat(recibidas).isEmpty();
    }

    @Test
    void entrenadorNoPuedeAjustarInventario() {
        client.patch().uri("/api/equipos/1/inventario").header("Authorization", "Bearer trainer")
                .header("Content-Type", "application/json").bodyValue("{}")
                .exchange().expectStatus().isForbidden();
        assertThat(recibidas).isEmpty();
    }

    @Test
    void adminPuedeCrearEquipos() {
        client.post().uri("/api/equipos").header("Authorization", "Bearer admin")
                .header("Content-Type", "application/json").bodyValue("{}")
                .exchange().expectStatus().isOk();
        assertThat(recibidas).hasSize(1);
    }

    @Test
    void rolSinPrefijoSeConvierteEnROLE() {
        client.post().uri("/api/equipos").header("Authorization", "Bearer sin-prefijo")
                .header("Content-Type", "application/json").bodyValue("{}")
                .exchange().expectStatus().isOk();
    }

    @Test
    void elTokenLlegaIntactoAlBackendYSeAgregaXRequestId() {
        client.get().uri("/api/equipos/7").header("Authorization", "Bearer member").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id");

        assertThat(recibidas).hasSize(1);
        Recibida r = recibidas.get(0);
        assertThat(r.metodo()).isEqualTo("GET");
        assertThat(r.path()).isEqualTo("/api/equipos/7");
        assertThat(r.authorization()).isEqualTo("Bearer member");
        assertThat(r.requestId()).isNotBlank();
    }
}
