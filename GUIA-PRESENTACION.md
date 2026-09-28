# Guía de presentación — 30 minutos

Cubre los tres bloques del taller: Bloques 1-3 son la arquitectura DDD original
(15 min); Bloques 4-6 son seguridad + comunicación asincrónica (15 min), que es
lo que pide la consigna actual del taller.

## Antes de entrar al salón (15 min antes)

**Infraestructura compartida** (contenedores ya existentes, solo hay que levantarlos):

```bash
docker start biblioteca-keycloak rabbitmq
docker compose -f docker-compose.kafka.yml up -d
```

Esperar ~15-20s a que Kafka termine de inicializar (`docker logs gym-suite-kafka-1`
debe mostrar `started (kafka.server.KafkaServer)`).

**Los cuatro microservicios:**

```bash
cd ~/microservicios/gym-suite
./scripts/levantar-todo.sh
```

Espera a ver los cuatro `OK`. Esto es solo para confirmar que compila y arranca en la
máquina y la red del salón — no quieres descubrir un puerto ocupado con la profesora
mirando. Déjalos corriendo (a diferencia de la guía original, ahora sí los necesitas
arriba para el bloque 4-6).

**Conseguir los 3 tokens de prueba de antemano** (pégalos en un bloc de notas, los vas
a necesitar varias veces durante la demo):

```bash
get_token () {
  curl -s -X POST http://localhost:8080/realms/gimnasio/protocol/openid-connect/token \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=$1&client_secret=$1-secret&username=$2&password=$3" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])"
}
get_token miembros-service admin.test Admin123!     # token de ADMIN
get_token miembros-service member.test Member123!   # token de MEMBER
get_token clases-service trainer.test Trainer123!   # token de TRAINER
```

Ten abierto de antemano:

1. El diagrama `architecture-diagram.drawio` en [app.diagrams.net](https://app.diagrams.net)
2. Postman con las peticiones ya guardadas (o la terminal con los scripts), incluyendo
   los 3 tokens como variables de entorno (`token_admin`, `token_member`, `token_trainer`)
3. Swagger UI de `miembros-service` abierto en una pestaña: `http://localhost:8081/swagger-ui/index.html`
4. La consola de administración de Keycloak: `http://localhost:8080/admin` (realm `gimnasio`)
5. La UI de management de RabbitMQ: `http://localhost:15672` (user `gimnasio`/`gimnasio123`)
6. El editor con `Clase.java`, `EntrenadorClient.java`, `SecurityConfig.java` (miembros-service),
   `RabbitMQConfig.java` (miembros-service) y `KafkaStreamsConfig.java` en pestañas
7. Una terminal en `gym-suite`

**Plan B si Postman falla:** los scripts de `scripts/` hacen exactamente lo mismo con `curl`.

---

## Bloque 1 — Arquitectura (3 min)

> Pantalla: el diagrama en draw.io

**Qué decir:**

"Partimos de un monolito con cuatro capacidades: miembros, clases, entrenadores y equipos.
Todas vivían en la misma aplicación y la misma base de datos.

Aplicando DDD identificamos **cuatro contextos acotados**, uno por capacidad. El criterio no
fue 'son cuatro entidades, entonces cuatro servicios' — fue que cada una tiene su propio
lenguaje ubicuo y su propia razón de cambio. *Inscribir* un miembro, *programar* una clase y
*dar de alta* un equipo son verbos de negocios distintos, que evolucionan a ritmos distintos.

Cada contexto es un microservicio con **su propia base de datos**. Ese es el punto que hace
la separación real: si compartieran esquema, un cambio en uno rompería a los otros y
tendríamos un monolito distribuido, que es peor que el monolito original."

**Los cuatro servicios** (señalar en el diagrama):

| Servicio | Puerto | Base de datos |
|---|---|---|
| Miembros | 8081 | miembrosdb |
| Clases | 8082 | clasesdb |
| Entrenadores | 8083 | entrenadoresdb |
| Equipos | 8084 | equiposdb |

### El punto fuerte: la relación Clase → Entrenador

> Pantalla: cambiar a `Clase.java`

"Aquí está la única decisión de diseño real del taller. En el monolito, `Clase` tenía:

```java
@ManyToOne
private Entrenador entrenador;
```

Esa relación **no puede sobrevivir a la separación**. `clasesdb` y `entrenadoresdb` son bases
de datos distintas: no hay JOIN posible entre ellas. Mantenerla obligaría a compartir base
de datos, que es justo lo que rompe la independencia.

La solución que da DDD para referenciar entre agregados —y con más razón cruzando contextos
acotados— es **referenciar por identidad**:

```java
private Long entrenadorId;
```

`Clase` no importa la clase `Entrenador`, no conoce su esquema y no depende de él. Solo guarda
el identificador con el que puede preguntar por él cuando lo necesite."

---

## Bloque 2 — Demo en vivo (9 min)

### 2.1 Levantar los cuatro (1 min)

```bash
./scripts/levantar-todo.sh
```

"Cuatro procesos Java independientes, cada uno en su puerto, cada uno con su base de datos en
memoria y **sus propios datos de ejemplo cargados al arrancar** — igual que el DataLoader del
monolito original, pero repartido: cada contexto carga lo suyo. Ninguno sabe que los otros
existen, salvo una excepción que veremos ahora."

### 2.2 Los cuatro responden simultáneamente (2 min)

> Pantalla: Postman

`GET` a los cuatro, que ya traen datos sin haber creado nada:

1. `GET http://localhost:8081/api/miembros` → 2 miembros
2. `GET http://localhost:8083/api/entrenadores` → 2 entrenadores
3. `GET http://localhost:8084/api/equipos` → 2 equipos
4. `GET http://localhost:8082/api/clases` → 2 clases

Luego un `POST` a cualquiera para mostrar el `201 Created`.

**Qué decir:** "Los cuatro corriendo al mismo tiempo, en cuatro puertos, contra cuatro bases de
datos, sin pisarse. Cada uno funciona de forma completamente independiente."

### 2.3 La integración REST (2.5 min)

> Pantalla: `GET http://localhost:8082/api/clases`

"Fíjense en lo que guarda la clase: solo `entrenadorId: 1`. Pero el GET devuelve
`entrenadorNombre: "Carlos Rodriguez"` y `entrenadorEspecialidad: "Yoga"`.

Ese dato **no está en clasesdb**. Clases-service lo pidió por HTTP a entrenadores-service en el
momento de responder: `GET /api/entrenadores/1` al puerto 8083.

Esa es la forma correcta de resolver la referencia entre contextos: no leyendo la base de datos
ajena, sino preguntándole al servicio que es dueño de ese dato."

> Pantalla: `EntrenadorClient.java`

"La llamada está encapsulada en una sola clase. Es el único punto de todo el servicio de Clases
que sabe que entrenadores-service existe."

### 2.4 Los agregados protegen sus invariantes (2 min) — el momento DDD

> Pantalla: Postman

Crea una clase con **capacidad 2** y trata de inscribir tres miembros:

```
POST /api/clases                          → { "capacidadMaxima": 2, ... }
POST /api/clases/{id}/inscripciones       → { "miembroId": 1 }   201, cupos: 1
POST /api/clases/{id}/inscripciones       → { "miembroId": 2 }   201, cupos: 0
POST /api/clases/{id}/inscripciones       → { "miembroId": 3 }   400 ✕
```

Respuesta del tercero:

```json
{ "error": "La clase Crossfit Matutino ya alcanzo su capacidad maxima de 2" }
```

**Qué decir:** "Este es el corazón del diseño. `Clase` no es un contenedor de datos: es un
Aggregate Root que **protege un invariante**. La regla «los inscritos nunca superan la
capacidad» no está en el servicio ni en el controlador — está dentro del agregado, en
`inscribirMiembro()`. No hay forma de crear una clase sobrecupada, ni siquiera por error.

Y por eso la lista de inscritos vive dentro del agregado: la frontera de un agregado se dibuja
alrededor de lo que tiene que ser consistente a la vez. Si las inscripciones fueran un agregado
aparte, dos inscripciones simultáneas podrían pasarse del cupo sin que nadie lo detecte."

Si sobra medio minuto, muestra uno más:

```
PATCH /api/equipos/1/inventario  → { "ajuste": -9999 }
{ "error": "No hay suficientes unidades de Mancuernas: disponibles 20, solicitadas 9999" }
```

"Mismo principio en otro contexto: el inventario nunca queda negativo porque `Equipo` no deja."

### 2.5 Tolerancia a fallos (1.5 min) — el momento que nadie más va a mostrar

```bash
./scripts/probar-resiliencia.sh
```

El script apaga entrenadores-service en vivo y vuelve a hacer `GET /api/clases`.

**Qué decir:** "Acabamos de matar el servicio de entrenadores. Y clases-service **sigue
respondiendo** — en 40 milisegundos, sin errores. Simplemente devuelve 'No disponible' en los
campos del entrenador.

Esto es lo que separa microservicios de verdad de un monolito distribuido: si la caída de un
servicio tumbara a otro, no serían independientes, solo estarían repartidos en varios procesos.
La información del entrenador es un enriquecimiento, no una dependencia dura."

---

## Bloque 3 — Cierre DDD (3 min)

"Para cerrar, los criterios de DDD que aplicamos:

**Aggregate Roots que protegen invariantes.** Cada contexto tiene exactamente uno: `Miembro`,
`Clase`, `Entrenador`, `Equipo`. Ninguno expone setters públicos — el estado solo cambia por
operaciones de negocio que validan antes de mutar. Eso evita el modelo de dominio anémico:
las reglas viven en el dominio, no repartidas por los servicios.

**Referencias entre agregados por identidad**, nunca por objeto — lo que explicamos con
`entrenadorId`.

**Un Value Object, y solo uno: `Email`.** Se justifica porque cumple los tres criterios: no
tiene identidad propia, es inmutable, y tiene una regla de validación que le pertenece a él y
no al agregado que lo contiene. Los demás campos son datos simples sin reglas asociadas, y
envolverlos sería aplicar DDD como ritual en lugar de como diseño. Un Value Object se justifica
cuando encapsula una regla, no cuando encapsula un String.

**Servicios de dominio que delegan.** `ClaseService.inscribirMiembro()` no comprueba el cupo:
se lo pide a `Clase`, que es quien conoce esa regla. El servicio orquesta, el agregado decide.

**DTOs en la frontera.** La entrada llega como `ClaseRequest`, no como la entidad, para que
Jackson nunca pueda construir un agregado saltándose sus validaciones."

---

## Bloque 4 — Seguridad con Keycloak (5 min)

> Pantalla: consola de Keycloak, realm `gimnasio` → Users / Roles / Clients

**Qué decir:** "Cada microservicio es ahora un OAuth2 Resource Server: valida el JWT
contra el realm `gimnasio` de Keycloak y autoriza según el rol que venga en
`realm_access.roles` del token — no según quién dice ser el llamador, sino según lo
que el token firmado dice que puede hacer.

Definimos tres roles (`ROLE_ADMIN`, `ROLE_TRAINER`, `ROLE_MEMBER`), un cliente por
microservicio, y tres usuarios de prueba, uno por rol."

### 4.1 Sin token → 401 (30s)

```bash
curl -i http://localhost:8081/api/miembros
```

"Ni siquiera puede leer sin autenticarse."

### 4.2 Token de rol insuficiente → 403 (1 min)

> Pantalla: Postman, header `Authorization: Bearer {{token_member}}`

```
POST http://localhost:8081/api/miembros
{ "nombre": "Intento no autorizado", "email": "x@x.com" }
```

**Qué decir:** "`member.test` tiene un token válido y vigente — la autenticación pasa.
Pero registrar un miembro nuevo es una operación de `ROLE_ADMIN`. La autorización lo
rechaza con 403, no 401: sabemos quién es, simplemente no puede hacer esto."

### 4.3 Token correcto → éxito (1 min)

Mismo POST con `{{token_admin}}` → `201 Created`.

### 4.4 El caso cruzado: seguridad entre servicios (1.5 min)

> Pantalla: `RestTemplateConfig.java` en clases-service

"Este es el detalle que casi se nos escapa: `clases-service` llama internamente a
`entrenadores-service` para traer el nombre del entrenador. Si protegemos ese endpoint
con JWT, esa llamada interna se rompe — el `RestTemplate` no tiene ningún token propio.

La solución: un interceptor que **propaga** el header `Authorization` de la petición
entrante hacia la llamada saliente. El token del usuario original sigue siendo válido
para reenviar, porque la llamada corre en el mismo hilo de la petición HTTP."

`GET /api/clases/{id}` con `{{token_admin}}` → sigue trayendo `entrenadorNombre` y
`entrenadorEspecialidad`, a pesar de que `entrenadores-service` ahora exige JWT.

### 4.5 Swagger documentado (1 min)

> Pantalla: `http://localhost:8081/swagger-ui/index.html`

"Cada endpoint documenta sus respuestas posibles — 200/201/400/401/403/404 — y sus
parámetros. Esto es lo que pide la Parte 1 del taller además de la seguridad."

---

## Bloque 5 — RabbitMQ: comunicación asincrónica (5 min)

> Pantalla: `http://localhost:15672` (RabbitMQ management), pestaña Queues

**Qué decir:** "Hasta acá toda la comunicación fue síncrona: HTTP request/response.
RabbitMQ nos deja desacoplar en el tiempo: el que publica no espera a que el que
consume termine, ni siquiera necesita que esté vivo en ese momento."

### 5.1 Notificación de inscripción (1.5 min)

> Pantalla: terminal con logs de `miembros-service`

```
POST http://localhost:8082/api/clases/{id}/inscripciones   (token admin o trainer)
{ "miembroId": 1 }
```

Mostrar en el log de miembros-service: `Notificación: el miembro 1 fue inscrito en la
clase '...'`. "Clases-service publicó a una cola (`notificaciones.inscripciones.queue`)
y siguió respondiendo al cliente sin esperar. Miembros-service la consumió en paralelo."

### 5.2 Pub/sub de cambio de horario — el momento fanout (2 min)

> Pantalla: RabbitMQ management → Exchanges → `horarios.exchange`

"Acá está el patrón publish/subscribe real: un exchange **fanout** con dos colas
independientes suscritas — una de miembros-service, otra de entrenadores-service.
Cuando publico un mensaje, **ambas** reciben una copia, sin que el publicador sepa
cuántos suscriptores hay ni quiénes son."

```
PATCH http://localhost:8082/api/clases/{id}/horario   (token admin o trainer)
{ "nuevoHorario": "2026-12-01T09:00:00" }
```

Mostrar en los logs de **ambos** servicios (miembros y entrenadores) el mismo evento
de cambio de horario llegando de forma independiente.

### 5.3 Dead Letter Queue de pagos (1.5 min)

> Pantalla: RabbitMQ management → Queues → `pagos-queue` / `pagos-dlq`

```
POST http://localhost:8081/api/pagos   (token admin o member)
{ "miembroId": 1, "monto": 50000, "concepto": "Mensualidad" }
```
→ log: "Pago ... procesado exitosamente"

```
POST http://localhost:8081/api/pagos
{ "miembroId": 1, "monto": -100, "concepto": "Pago corrupto" }
```
→ log: "Error procesando el pago ... Se envía a la DLQ" seguido de
"Pago ... requiere atención manual"

**Qué decir:** "Un monto inválido lanza una excepción que el listener convierte en
`AmqpRejectAndDontRequeueException`. RabbitMQ, siguiendo la configuración
`x-dead-letter-exchange`/`x-dead-letter-routing-key` de `pagos-queue`, reenvía
automáticamente el mensaje a `pagos-dlq` en vez de perderlo o reintentarlo
infinitamente. Un segundo listener en la DLQ lo deja registrado para atención
manual — nada se pierde silenciosamente."

---

## Bloque 6 — Kafka: streaming y recuperación (5 min)

### 6.1 Ocupación en tiempo real (1.5 min)

> Pantalla: logs de `clases-service`

Inscribir 2-3 miembros seguidos en una clase y mostrar en el log:

```
[Dashboard] Clase 'Spinning PM' (id=3): 1/3 cupos ocupados a las ...
[Dashboard] Clase 'Spinning PM' (id=3): 2/3 cupos ocupados a las ...
```

**Qué decir:** "Cada inscripción publica al topic `ocupacion-clases`. Un consumidor
simula la actualización de un dashboard de monitoreo en tiempo real — este es el caso
de uso típico de Kafka: eventos de alto volumen consumidos por streaming, no por
request/response."

### 6.2 Kafka Streams: análisis de entrenamiento (2 min)

> Pantalla: `KafkaStreamsConfig.java`, y una terminal con
> `docker exec gym-suite-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic resumen-entrenamiento --from-beginning`

```
POST http://localhost:8081/api/miembros/1/entrenamientos   (cualquier token)
{ "tipoActividad": "Cardio", "duracionMinutos": 30, "calorias": 250 }

POST http://localhost:8081/api/miembros/1/entrenamientos
{ "tipoActividad": "Pesas", "duracionMinutos": 45, "calorias": 300 }
```

Mostrar en el consumer de consola el resumen agregado:
`{"miembroId":1,"totalSesiones":2,"totalMinutos":75,"totalCalorias":550}`

**Qué decir:** "No estamos consumiendo evento por evento: un stream processor de Kafka
Streams agrupa por miembro y agrega en una ventana de tiempo de 7 días. Es
procesamiento continuo sobre el log, no una consulta puntual a una base de datos."

### 6.3 Recuperación ante fallos (1.5 min) — el momento más técnico

**Qué decir:** "El consumidor de ocupación usa *ack manual*: solo confirma el mensaje
a Kafka después de procesarlo, no automáticamente al recibirlo. Eso es lo que permite
recuperarse de una caída sin perder ni duplicar eventos."

Matar el proceso de `clases-service` en vivo (`kill -9 <pid>` o Ctrl+C) después de una
inscripción, y volver a levantarlo:

```bash
./mvnw spring-boot:run
```

Mostrar en el log de arranque **dos** líneas (en este orden):

```
Setting offset for partition ocupacion-clases-2 to the committed offset ... offset=2
Recuperación: reanudando 'ocupacion-clases-2' desde el offset 2 (último checkpoint conocido)
```

**Qué decir:** "La primera línea es Kafka retomando por su propio commit de offset. La
segunda es *nuestro* código: `clases-service` guarda el último offset procesado en una
tabla `KafkaCheckpoint` en su base de datos (H2 en archivo, no en memoria — sobrevive el
reinicio), y al reconectar, `ConsumerSeekAware` lee esa tabla y hace un seek explícito.
Las dos coinciden, porque son dos formas independientes de resolver el mismo problema:
no perder ni reprocesar mensajes tras una caída — una la da Kafka gratis, la otra la
implementamos nosotros para que quede auditable en nuestra propia base de datos
transaccional, como pide literalmente la Parte 3 del taller."

> **Ensayado en vivo, timing real:** tras un `kill -9`, Kafka tarda **20-40 segundos**
> en detectar que el consumidor murió antes de reasignar la partición al que reinicia
> (`session.timeout.ms`). No es una falla — es el protocolo de consumer groups
> confirmando que la caída es real y no un corte momentáneo — pero avisale a tu
> compañero de antemano para no quedarse en silencio incómodo esperando el log. Es
> buen momento para explicarlo en voz alta mientras se espera: demuestra que entendés
> el mecanismo, no solo que "funciona".

Cierra con: **"¿Preguntas?"**

---

## Preguntas probables y cómo responderlas

**"¿Qué invariante protege tu Aggregate Root?"** — *la pregunta que separa el 4.5 del 5.0*
`Clase` protege dos: los inscritos nunca superan `capacidadMaxima`, y un miembro no puede
inscribirse dos veces. `Equipo` protege que el inventario nunca quede negativo. `Miembro` que
el email tenga formato válido y que la fecha de inscripción no esté en el futuro. Lo demostramos
en vivo con la clase de capacidad 2.

**"¿Por qué cuatro servicios y no dos, o seis?"**
El taller pedía máximo cuatro, y coincide con los cuatro contextos que identificamos: cada uno
tiene su propio lenguaje ubicuo y su propia razón de cambio. Agrupar Clases con Entrenadores
habría sido posible, pero mezclaría la programación de horarios con la gestión de personal, que
son responsabilidades que cambian por motivos distintos.

**"¿Por qué la lista de inscritos está dentro de Clase y no es su propio agregado?"**
Porque la frontera de un agregado se dibuja alrededor de lo que debe ser consistente a la vez.
La regla de capacidad solo puede garantizarse si un único objeto controla las dos cosas — el
límite y los inscritos. Si fueran agregados separados, dos inscripciones concurrentes podrían
pasarse del cupo.

**"¿Qué pasa si borran un entrenador que tiene clases asignadas?"**
La clase queda con un `entrenadorId` que ya no resuelve, y el GET responde "No disponible" —
lo mismo que pasa con un id inexistente. En microservicios no existe la integridad referencial
entre bases de datos; se acepta **consistencia eventual**. Una versión de producción publicaría
un evento `EntrenadorEliminado` al que Clases se suscribiría.

**"¿Solo tienen un Value Object?"**
Sí, `Email`, y es deliberado. Un Value Object se justifica cuando encapsula una regla propia:
`Email` valida su formato, es inmutable y no tiene identidad. `nombre` o `descripcion` son
Strings sin reglas asociadas — envolverlos sería ceremonia. Preferimos justificar por qué los
demás no están, en vez de crear clases vacías para cumplir una lista.

**"¿No es ineficiente llamar por REST en cada GET?"**
Sí, es el costo de la independencia. En producción se mitiga con caché en el cliente, o
guardando una copia del nombre en clasesdb sincronizada por eventos. Para el alcance del taller
preferimos la llamada directa, que hace visible la comunicación entre contextos.

**"¿Por qué H2 en memoria y no una base real?"**
Para que los cuatro servicios se levanten sin instalar nada. Lo importante del diseño es que son
**cuatro bases separadas**; que sean H2 o PostgreSQL no cambia la arquitectura, solo la cadena
de conexión.

**"¿Y si se cae clases-service?"**
Los otros tres siguen funcionando — lo mostramos: cuando apagamos entrenadores, los otros tres
respondieron 200. Ninguno depende de otro para arrancar.

**"¿Dónde está el API Gateway / service discovery?"**
No lo incluimos porque el taller pedía máximo cuatro microservicios y son componentes de
infraestructura, no contextos de dominio. En producción irían delante: el gateway como único
punto de entrada, y discovery para no tener el `localhost:8083` en configuración.

**"¿Cómo saben qué rol tiene el usuario si Spring Security no conoce Keycloak?"**
El JWT firmado por Keycloak trae el claim `realm_access.roles`. Configuramos un
`JwtAuthenticationConverter` que lee ese claim y lo convierte en `GrantedAuthority` de
Spring Security — no hay llamada adicional a Keycloak en cada request, todo lo necesario
ya viene firmado en el token.

**"¿Por qué RabbitMQ para notificaciones/horarios y Kafka para ocupación/entrenamiento,
y no todo con una sola tecnología?"**
Son patrones distintos. RabbitMQ brilla en mensajería con enrutamiento flexible y
garantías por mensaje (colas, DLQ, fanout) — encaja con "notificar a alguien" o
"garantizar que un pago se procese o quede registrado como fallido". Kafka brilla en
streams de eventos de alto volumen que se pueden re-leer y agregar con el tiempo — encaja
con "monitorear ocupación en tiempo real" o "analizar historial de entrenamiento". Usar
ambos demuestra que entendemos cuándo aplica cada una, no que eran intercambiables.

**"¿Qué pasa si RabbitMQ o Kafka se caen?"**
Con RabbitMQ, el publicador (`RabbitTemplate.convertAndSend`) lanzaría una excepción si
no puede conectar — habría que decidir si la petición HTTP falla o se degrada
(como con el cliente HTTP a entrenadores-service). Con Kafka, gracias al ack manual y al
offset commit del broker, un consumidor que se cae no pierde ni duplica mensajes: al
volver a levantarse, retoma exactamente donde se quedó.

**"El mecanismo de checkpoint en base de datos que mencionan, ¿sobrevive un reinicio?"**
Sí. `clases-service` usa H2 en **archivo** (no en memoria), así que la tabla
`KafkaCheckpoint` persiste entre reinicios del proceso. Lo verificamos en vivo: matamos el
proceso, lo reiniciamos, y el log mostró `Recuperación: reanudando 'ocupacion-clases-2'
desde el offset 2 (último checkpoint conocido)` — nuestro propio `ConsumerSeekAware`
leyendo la tabla, no solo el offset nativo de Kafka. Ambos mecanismos coinciden, lo cual
es la prueba de que el checkpoint propio está funcionando correctamente en paralelo al
commit de Kafka.

---

## Peticiones para Postman

Todas con header `Content-Type: application/json`. **Los cuatro servicios ya arrancan con datos**
(2 registros cada uno), así que puedes hacer los GET de una vez sin crear nada.

**POST** `http://localhost:8083/api/entrenadores`
```json
{ "nombre": "Laura Gomez", "especialidad": "Crossfit" }
```

**POST** `http://localhost:8081/api/miembros`
```json
{ "nombre": "Juan Cano", "email": "juan@gym.com" }
```

**POST** `http://localhost:8082/api/clases` — usa una **fecha futura**, el agregado rechaza el pasado
```json
{ "nombre": "Crossfit Matutino", "horario": "2026-12-01T07:00:00", "capacidadMaxima": 2, "entrenadorId": 1 }
```

**POST** `http://localhost:8082/api/clases/{id}/inscripciones` — repite tres veces para ver el rechazo
```json
{ "miembroId": 1 }
```

**PATCH** `http://localhost:8084/api/equipos/1/inventario`
```json
{ "ajuste": -9999 }
```

**GET** — uno por servicio:
```
http://localhost:8081/api/miembros
http://localhost:8082/api/clases          <- este trae los datos del entrenador
http://localhost:8083/api/entrenadores
http://localhost:8084/api/equipos
```

### Peticiones que deben fallar (para demostrar los invariantes)

| Petición | Respuesta esperada |
|---|---|
| Tercera inscripción en clase de capacidad 2 | 400 · "ya alcanzo su capacidad maxima de 2" |
| Inscribir dos veces al mismo miembro | 400 · "ya esta inscrito en la clase" |
| `{"ajuste": -9999}` en inventario | 400 · "No hay suficientes unidades" |
| Clase con `horario` en el pasado | 400 · "No se puede programar una clase en el pasado" |
| Miembro con `"email": "no-es-un-email"` | 400 · "no tiene un formato valido" |

---

## Reparto sugerido (30 min)

| Bloque | Minutos | Quién |
|---|---|---|
| 1. Arquitectura y decisión `entrenadorId` | 3 | Quien mejor domine DDD |
| 2. Demo en vivo (DDD) | 9 | Quien tenga el proyecto en su máquina |
| 3. Cierre DDD | 3 | Ambos |
| 4. Seguridad con Keycloak | 5 | Quien implementó Security |
| 5. RabbitMQ | 5 | Quien implementó la mensajería |
| 6. Kafka | 5 | Quien implementó streaming/Kafka |

**Los dos deben poder responder sobre cualquier servicio**, no solo el que programaron. La
pregunta cruzada es el riesgo más probable — y con seis bloques distintos, es todavía
más probable que te pregunten por un bloque que no presentaste vos.
