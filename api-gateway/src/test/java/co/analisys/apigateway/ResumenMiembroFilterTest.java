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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el filtro ResumenMiembro contra un backend falso que hace de miembros-service y de clases-service
 * (se registran como instancias fijas con SimpleDiscoveryClient, sin Eureka).
 */
@Import(TestJwtDecoderConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
class ResumenMiembroFilterTest {

    /** Respuesta programada: status, cuerpo y demora antes de responder. */
    record Programada(int status, String body, long demoraMs) {
        Programada(int status, String body) {
            this(status, body, 0);
        }
    }

    private static final Map<String, Programada> respuestas = new ConcurrentHashMap<>();
    private static final List<String> authorizations = new CopyOnWriteArrayList<>();
    private static final HttpServer backend = iniciarBackend();

    private static HttpServer iniciarBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                Programada p = respuestas.getOrDefault(exchange.getRequestURI().getPath(), new Programada(404, "{}"));
                try {
                    Thread.sleep(p.demoraMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = p.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(p.status(), body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
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
        String uri = "http://127.0.0.1:" + backend.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.miembros-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.clases-service[0].uri", () -> uri);
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
        respuestas.clear();
        authorizations.clear();
        client = WebTestClient.bindToServer()
                .responseTimeout(java.time.Duration.ofSeconds(10))
                .baseUrl("http://localhost:" + puerto).build();
        respuestas.put("/api/miembros/1", new Programada(200, "{\"id\":1,\"nombre\":\"Juan Perez\",\"email\":\"juan@email.com\"}"));
        respuestas.put("/api/miembros/1/pagos", new Programada(200,
                "[{\"id\":\"a\",\"miembroId\":1,\"monto\":50000.0,\"concepto\":\"Mensualidad\"},"
                        + "{\"id\":\"b\",\"miembroId\":1,\"monto\":25000.5,\"concepto\":\"Clase suelta\"}]"));
        respuestas.put("/api/clases", new Programada(200,
                "[{\"id\":1,\"nombre\":\"Yoga\",\"horario\":\"2026-11-01T08:00:00\",\"entrenadorNombre\":\"Carlos\","
                        + "\"entrenadorEspecialidad\":\"Yoga\",\"capacidadMaxima\":20,\"miembrosInscritos\":[1,5]},"
                        + "{\"id\":2,\"nombre\":\"Crossfit\",\"horario\":\"2026-11-02T07:00:00\",\"entrenadorNombre\":\"Ana\","
                        + "\"entrenadorEspecialidad\":\"Crossfit\",\"capacidadMaxima\":10,\"miembrosInscritos\":[7]}]"));
    }

    @Test
    void agregaMiembroClasesYPagosReenviandoElToken() {
        client.get().uri("/api/resumen/miembros/1").header("Authorization", "Bearer member").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.miembro.nombre").isEqualTo("Juan Perez")
                .jsonPath("$.totalClases").isEqualTo(1)
                .jsonPath("$.clases.length()").isEqualTo(1)
                .jsonPath("$.clases[0].id").isEqualTo(1)
                .jsonPath("$.clases[0].nombre").isEqualTo("Yoga")
                .jsonPath("$.clases[0].entrenadorNombre").isEqualTo("Carlos")
                .jsonPath("$.clases[0].entrenadorEspecialidad").isEqualTo("Yoga")
                .jsonPath("$.clases[0].capacidadMaxima").doesNotExist()
                .jsonPath("$.pagos.length()").isEqualTo(2)
                .jsonPath("$.totalPagado").isEqualTo(75000.5)
                .jsonPath("$.advertencias.length()").isEqualTo(0);

        assertThat(authorizations).hasSize(3).containsOnly("Bearer member");
    }

    @Test
    void miembroInexistenteDevuelve404() {
        respuestas.put("/api/miembros/9", new Programada(404, "{\"error\":\"No existe el miembro con id 9\"}"));
        respuestas.put("/api/miembros/9/pagos", new Programada(404, "{}"));

        client.get().uri("/api/resumen/miembros/9").header("Authorization", "Bearer member").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.error").isEqualTo("No existe el miembro con id 9");
    }

    @Test
    void idNoNumericoDevuelve400() {
        client.get().uri("/api/resumen/miembros/abc").header("Authorization", "Bearer member").exchange()
                .expectStatus().isBadRequest();
        assertThat(authorizations).isEmpty();
    }

    @Test
    void sinTokenDevuelve401() {
        client.get().uri("/api/resumen/miembros/1").exchange().expectStatus().isUnauthorized();
        assertThat(authorizations).isEmpty();
    }

    @Test
    void siUnServicioRechazaElTokenSePropagaElStatus() {
        respuestas.put("/api/clases", new Programada(403, ""));

        client.get().uri("/api/resumen/miembros/1").header("Authorization", "Bearer member").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void siFallanLasClasesDevuelve200ConClasesNullYAdvertencia() {
        respuestas.put("/api/clases", new Programada(500, "{}"));

        client.get().uri("/api/resumen/miembros/1").header("Authorization", "Bearer member").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.miembro.nombre").isEqualTo("Juan Perez")
                .jsonPath("$.clases").isEmpty()
                .jsonPath("$.totalClases").isEmpty()
                .jsonPath("$.pagos.length()").isEqualTo(2)
                .jsonPath("$.totalPagado").isEqualTo(75000.5)
                .jsonPath("$.advertencias.length()").isEqualTo(1)
                .jsonPath("$.advertencias[0]").value(a -> assertThat((String) a).contains("clases"));
    }

    @Test
    void siLosPagosSuperanElTimeoutDevuelve200ConPagosNullYAdvertencia() {
        respuestas.put("/api/miembros/1/pagos", new Programada(200, "[]", 4500));

        client.get().uri("/api/resumen/miembros/1").header("Authorization", "Bearer member").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clases.length()").isEqualTo(1)
                .jsonPath("$.pagos").isEmpty()
                .jsonPath("$.totalPagado").isEmpty()
                .jsonPath("$.advertencias.length()").isEqualTo(1)
                .jsonPath("$.advertencias[0]").value(a -> assertThat((String) a).contains("pagos"));
    }
}
