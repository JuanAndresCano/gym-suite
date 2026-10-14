# API Gateway

Punto de entrada único del gimnasio (Spring Cloud Gateway, WebFlux). Valida el JWT de Keycloak,
aplica la autorización por rol y enruta hacia los microservicios descubiertos con Eureka.

**Stack:** Java 21 · Spring Boot 4.1.1 · Spring Cloud 2025.1.2 (Oakwood) · `spring-cloud-starter-gateway-server-webflux`

## Puertos

| Componente | Puerto |
|---|---|
| Keycloak (realm `gimnasio`) | 8080 |
| RabbitMQ (AMQP / consola) | 5672 / 15672 |
| Kafka (externo) / Zookeeper | 29092 / 22181 |
| eureka-server | 8761 |
| miembros-service | 8081 |
| clases-service | 8082 |
| entrenadores-service | 8083 |
| equipos-service | 8084 |
| **api-gateway** | **8085** |

## Orden de arranque

1. **Keycloak, RabbitMQ y Kafka** (Docker): `keycloak-gimnasio`, `rabbitmq`, `gym-suite-zookeeper-1` y `gym-suite-kafka-1`.
   Si Kafka cae porque Zookeeper aún no estaba listo, basta con volver a arrancarlo.
2. **eureka-server**: `cd eureka-server && ./mvnw spring-boot:run`
3. **Los 4 servicios** (cada uno en su terminal): `miembros-service`, `clases-service`, `entrenadores-service`, `equipos-service`
   con `./mvnw spring-boot:run` (en Windows `mvnw.cmd spring-boot:run`).
4. **api-gateway**: `cd api-gateway && ./mvnw spring-boot:run`

> Tras arrancar, espere unos 30-60 s antes de probar: el gateway descarga el registro de Eureka
> de forma periódica y hasta entonces puede responder `503` para un servicio recién registrado.
> Dashboard de Eureka: <http://localhost:8761>.

## Rutas

| Ruta | Destino | Definida en |
|---|---|---|
| `/api/miembros/**`, `/api/pagos/**` | `lb://miembros-service` | `application.yml` |
| `/api/clases/**` | `lb://clases-service` | `application.yml` |
| `/api/entrenadores/**` | `lb://entrenadores-service` | `application.yml` |
| `/api/equipos/**` | `lb://equipos-service` | `GatewayConfig` (Java DSL) |

`lb://` indica que el nombre lo resuelve Eureka y que el balanceador (round-robin) elige la instancia.
Las rutas de YAML y la de Java DSL son equivalentes; se usan los dos estilos a propósito.

## Seguridad

- El gateway valida el JWT (`issuer-uri` = `http://localhost:8080/realms/gimnasio`, autoconfiguración: Keycloak no
  tiene que estar arriba al arrancar) y convierte `realm_access.roles` en authorities. Los roles del realm ya se
  llaman `ROLE_ADMIN`, `ROLE_MEMBER` y `ROLE_TRAINER`; si llegara uno sin prefijo se le antepone `ROLE_`.
- Sin token solo son públicos `/actuator/health` y `/actuator/info`. Sin token → **401**; rol insuficiente → **403**,
  ambos respondidos por el gateway sin llegar al servicio.
- Las reglas replican las de los servicios (que siguen validando el JWT por su cuenta):

| Operación | Roles |
|---|---|
| `POST /api/miembros` | ADMIN |
| `POST /api/pagos` | ADMIN, MEMBER |
| `POST /api/clases`, `PATCH /api/clases/*/horario` | ADMIN, TRAINER |
| `POST` y `DELETE /api/clases/*/inscripciones/**` | ADMIN, MEMBER |
| `POST /api/entrenadores` | ADMIN |
| `POST` y `PATCH /api/equipos/**` | ADMIN |
| cualquier otra | autenticado |

- El header `Authorization` se reenvía intacto al servicio (comportamiento por defecto del gateway).
- Un filtro global (`RequestLoggingFilter`) registra método, path y `preferred_username`, y añade `X-Request-Id`
  a la petición hacia el servicio y a la respuesta.

## Pruebas

**Automáticas** (no necesitan Keycloak ni los servicios; usan un backend falso y tokens simulados):

```
cd api-gateway && ./mvnw test
```

**De extremo a extremo** (con todo arriba, desde la raíz del repo, en PowerShell):

```
powershell -File scripts\test-gateway.ps1
```

El script pide un token a Keycloak justo antes de cada llamada (duran 300 s) y verifica 401/403 desde el gateway
y el POST/GET de los cuatro recursos. Termina con código de salida 1 si algún caso falla.

## Demostrar el balanceo

Segunda instancia de entrenadores con su propia H2 en memoria:

```
cd entrenadores-service
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8093"
```

Con las dos instancias registradas en Eureka, varias llamadas `GET http://localhost:8085/api/entrenadores`
(con token) alternan entre 8083 y 8093. Para verlo en los logs de cada instancia, arránquelas con
`logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG`.
