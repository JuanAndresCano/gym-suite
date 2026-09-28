# Sistema de Gestión de un Gimnasio — Arquitectura de Microservicios

Refactorización del monolito de gestión de gimnasio hacia una arquitectura de
microservicios aplicando Domain-Driven Design.

**Stack:** Java 21 · Spring Boot 4.1.1 · Spring Data JPA · H2 (en memoria) · Lombok · Maven

---

## 1. Análisis del dominio

El monolito original agrupaba en una sola aplicación, una sola base de datos y un
único servicio cuatro capacidades de negocio distintas:

| Capacidad | Entidad | Pregunta de negocio que responde |
|---|---|---|
| Gestión de Miembros | `Miembro` | ¿Quién está inscrito en el gimnasio? |
| Gestión de Clases | `Clase` | ¿Qué actividades se dictan y cuándo? |
| Gestión de Entrenadores | `Entrenador` | ¿Quién dicta y con qué especialidad? |
| Gestión de Equipos | `Equipo` | ¿Qué inventario tiene el gimnasio? |

Estas cuatro capacidades cambian por razones diferentes y a ritmos diferentes: el
inventario de equipos no tiene nada que ver con el ciclo de vida de una inscripción,
ni la programación de horarios con la contratación de personal.

## 2. Contextos acotados (Bounded Contexts)

Se identificaron **cuatro contextos acotados**, uno por capacidad de negocio. Cada uno
se materializa como un microservicio independiente con su propia base de datos:

| Contexto acotado | Microservicio | Puerto | Base de datos | Endpoint base |
|---|---|---|---|---|
| Miembros | `miembros-service` | 8081 | `miembrosdb` | `/api/miembros` |
| Clases | `clases-service` | 8082 | `clasesdb` | `/api/clases` |
| Entrenadores | `entrenadores-service` | 8083 | `entrenadoresdb` | `/api/entrenadores` |
| Equipos | `equipos-service` | 8084 | `equiposdb` | `/api/equipos` |

**Criterio de la separación:** cada contexto tiene su propio lenguaje ubicuo y su propia
razón de cambio. "Programar" pertenece al contexto de Clases; "inscribir" al de Miembros;
"dar de alta en inventario" al de Equipos. Ninguna palabra significa lo mismo cruzando
la frontera, y por eso la frontera existe.

**Base de datos por servicio:** ninguno de los cuatro comparte esquema con otro. Es el
requisito que hace que la separación sea real y no cosmética — sin él, un cambio de
esquema en un servicio rompería a los demás y volveríamos al monolito distribuido.

## 3. Entidades, agregados y servicios

Cada contexto contiene **un único Aggregate Root**, que coincide con su entidad principal.
Ninguno es un contenedor de datos pasivo: cada uno **protege sus propios invariantes** y no
expone setters públicos, de modo que es imposible dejarlo en un estado inválido.

| Aggregate Root | Campos | Invariantes que protege |
|---|---|---|
| `Miembro` | id, nombre, `Email`, fechaInscripcion | Nombre obligatorio; email con formato válido; la fecha de inscripción no puede estar en el futuro |
| `Clase` | id, nombre, horario, capacidadMaxima, entrenadorId, miembrosInscritos | No se programa en el pasado; capacidad > 0; entrenador obligatorio; **los inscritos nunca superan la capacidad**; un miembro no se inscribe dos veces |
| `Entrenador` | id, nombre, especialidad | Nombre y especialidad obligatorios |
| `Equipo` | id, nombre, descripcion, cantidad | Nombre obligatorio; **el inventario nunca queda en negativo** |

El estado solo cambia a través de operaciones de negocio que validan antes de mutar:
`Clase.inscribirMiembro()`, `Clase.cancelarInscripcion()`, `Clase.asignarEntrenador()`,
`Equipo.retirarUnidades()`, `Equipo.agregarUnidades()`, `Miembro.cambiarEmail()`.

Los *Domain Services* (`MiembroService`, `ClaseService`, `EntrenadorService`, `EquipoService`)
orquestan casos de uso y hablan con los repositorios, pero **delegan las reglas al agregado**:
por ejemplo, `ClaseService.inscribirMiembro()` no comprueba el cupo, se lo pide a `Clase`, que
es quien conoce esa regla. Los controladores REST solo traducen HTTP a llamadas de dominio.

Los datos de entrada llegan como DTOs (`ClaseRequest`, `MiembroRequest`, …), no como entidades.
Eso permite que los agregados no necesiten setters públicos y que Jackson nunca pueda construir
un agregado saltándose sus validaciones.

### El agregado Clase y su frontera

`Clase` es el agregado más interesante del sistema. La colección de miembros inscritos vive
**dentro** del agregado, no como una entidad aparte, y esa decisión es deliberada: la regla
"los inscritos nunca superan la capacidad" solo puede garantizarse si un único objeto controla
ambos lados. Si las inscripciones fueran su propio agregado, dos inscripciones concurrentes
podrían pasarse del cupo sin que nadie lo detectara. **La frontera del agregado se dibuja
alrededor de lo que debe ser consistente a la vez.**

### Sobre los Value Objects

Hay **exactamente uno**: `Email`, en el contexto de Miembros. Se justifica porque cumple los
tres criterios de un Value Object: no tiene identidad propia (dos correos con el mismo texto
son el mismo correo), es inmutable, y tiene una regla de validación que le pertenece a él y no
al agregado que lo contiene. Se serializa como texto plano mediante `@JsonValue`, de modo que
el contrato del API es idéntico al del monolito.

Los demás campos **no** se envolvieron en Value Objects, y eso también es una decisión de
diseño: `nombre`, `descripcion` o `especialidad` son datos simples sin reglas asociadas.
Envolverlos sería aplicar DDD como ritual en lugar de como diseño. El criterio es sencillo:
un Value Object se justifica cuando encapsula una regla, no cuando encapsula un `String`.

### La decisión de diseño central: `Clase` → `Entrenador`

En el monolito, `Clase` tenía una relación directa:

```java
@ManyToOne
private Entrenador entrenador;   // monolito
```

Esa relación **no puede sobrevivir a la separación**: `clasesdb` y `entrenadoresdb` son
bases de datos distintas y no existe forma de hacer un JOIN entre ellas. Mantenerla
obligaría a compartir base de datos, lo que destruiría la independencia de ambos servicios.

La solución de DDD para referenciar entre agregados —y con más razón entre contextos
acotados— es **referenciar por identidad**:

```java
private Long entrenadorId;       // microservicios
```

`Clase` no conoce la clase `Entrenador`, no la importa y no depende de su esquema. Solo
guarda el identificador con el que puede preguntar por él cuando lo necesita.

## 4. Comunicación REST entre microservicios (entregable opcional — implementado)

Cuando el contexto de Clases necesita mostrar *quién* dicta una clase, no lee la base de
datos ajena: **pregunta por HTTP** al servicio dueño de ese dato.

```
GET /api/clases  (8082)
        │
        └──► GET /api/entrenadores/{id}  (8083)   [EntrenadorClient + RestTemplate]
```

- `EntrenadorClient` (`co.analisys.clases.client`) encapsula la llamada saliente. Es el
  único punto del servicio que conoce la existencia de entrenadores-service.
- `EntrenadorDTO` es la **vista local** que el contexto de Clases tiene del agregado
  Entrenador: solo los campos que necesita, no el modelo completo del otro contexto.
- `ClaseDTO` es lo que devuelve `GET /api/clases`: los datos de la clase enriquecidos con
  el nombre y la especialidad del entrenador.

**Tolerancia a fallos:** si entrenadores-service está caído o el `entrenadorId` no existe,
la llamada no propaga el error. `EntrenadorClient` devuelve `Optional.empty()` y la clase
se responde con `"No disponible"` en los campos del entrenador. El contexto de Clases sigue
siendo funcional por sí solo — la información del entrenador es un enriquecimiento, no una
dependencia dura. Los timeouts (2s conexión, 3s lectura) evitan que una caída del otro
servicio deje colgado a este.

## 5. Estructura del proyecto

```
taller-gym/
├── architecture-diagram.drawio     # Diagrama UML de componentes
├── miembros-service/               # Puerto 8081
├── clases-service/                 # Puerto 8082
├── entrenadores-service/           # Puerto 8083
└── equipos-service/                # Puerto 8084
```

Cada servicio es un proyecto Maven **independiente** (su propio `pom.xml` y su propio
`mvnw`), con la misma organización interna:

```
co.analisys.<contexto>/
├── model/          # Aggregate Root (y Value Object Email en Miembros)
├── repository/     # Repositorio del agregado
├── service/        # Servicio de dominio
├── controller/     # Adaptador REST
├── dto/            # DTOs de entrada, para que el agregado no exponga setters
├── exception/      # Excepciones de dominio y @RestControllerAdvice
└── DataLoader.java # Datos de ejemplo, equivalentes a los del monolito
```

`clases-service` añade, por su integración REST:

```
├── client/         # EntrenadorClient — llamada saliente al otro contexto
├── dto/            # EntrenadorDTO (vista local) y ClaseDTO (respuesta enriquecida)
└── config/         # RestTemplateConfig — RestTemplate con timeouts
```

## 6. Cómo ejecutar

Cada servicio se levanta en su propia terminal:

```bash
cd miembros-service && ./mvnw spring-boot:run
```

```bash
cd clases-service && ./mvnw spring-boot:run
```

```bash
cd entrenadores-service && ./mvnw spring-boot:run
```

```bash
cd equipos-service && ./mvnw spring-boot:run
```

> Para la demo de la integración REST, levantar **entrenadores-service antes que
> clases-service** y crear primero al menos un entrenador, para tener un `entrenadorId`
> real al que referenciar.

Consola H2 de cada servicio: `http://localhost:<puerto>/h2-console`

## 7. Endpoints

| Método | URL | Descripción |
|---|---|---|
| POST | `:8081/api/miembros` | Registrar miembro (201) |
| GET | `:8081/api/miembros` | Listar miembros |
| GET | `:8081/api/miembros/{id}` | Consultar un miembro |
| POST | `:8082/api/clases` | Programar clase (201) |
| GET | `:8082/api/clases` | Listar clases **con datos del entrenador** |
| GET | `:8082/api/clases/{id}` | Consultar una clase |
| POST | `:8082/api/clases/{id}/inscripciones` | Inscribir un miembro — **valida el cupo** |
| DELETE | `:8082/api/clases/{id}/inscripciones/{miembroId}` | Cancelar una inscripción |
| POST | `:8083/api/entrenadores` | Agregar entrenador (201) |
| GET | `:8083/api/entrenadores` | Listar entrenadores |
| GET | `:8083/api/entrenadores/{id}` | Consultar entrenador (usado por Clases) |
| POST | `:8084/api/equipos` | Agregar equipo (201) |
| GET | `:8084/api/equipos` | Listar equipos |
| GET | `:8084/api/equipos/{id}` | Consultar un equipo |
| PATCH | `:8084/api/equipos/{id}/inventario` | Ajustar inventario — **no permite stock negativo** |

Los cuatro endpoints POST/GET de cada contexto son los del monolito original; el resto son
las operaciones de negocio que hacen falta para que los agregados protejan sus invariantes.

Cuando una operación viola un invariante, el `@RestControllerAdvice` de cada servicio responde
**400** con el mensaje de la regla; un recurso inexistente responde **404**.

## 8. Pruebas realizadas

Con los cuatro servicios corriendo simultáneamente (`./scripts/probar-todo.sh`):

1. **Datos de ejemplo** — cada servicio arranca con su `DataLoader`, igual que el monolito
   original: 2 miembros, 2 entrenadores, 2 clases y 2 equipos.
2. **POST y GET sobre los cuatro servicios** — cada uno responde en su propio puerto contra su
   propia base de datos, sin interferir con los demás. Los POST devuelven `201 Created`.
3. **Integración REST** — `GET /api/clases` devuelve `entrenadorNombre` y
   `entrenadorEspecialidad` resueltos en vivo desde entrenadores-service.
4. **Entrenador inexistente** (`entrenadorId: 999`) — la clase se devuelve con
   `"No disponible"`, sin error.
5. **Caída de entrenadores-service** (`./scripts/probar-resiliencia.sh`) — con el servicio
   apagado, `GET /api/clases` sigue respondiendo en ~40 ms, degradando solo los campos del
   entrenador.
6. **Invariantes de los agregados** — cada agregado rechaza con `400` lo que rompería su regla:

| Operación | Respuesta del agregado |
|---|---|
| Inscribir un tercer miembro en una clase de capacidad 2 | `La clase Crossfit Matutino ya alcanzo su capacidad maxima de 2` |
| Inscribir dos veces al mismo miembro | `El miembro 1 ya esta inscrito en la clase Crossfit Matutino` |
| Retirar 9999 unidades de un equipo con 16 | `No hay suficientes unidades de Mancuernas: disponibles 16, solicitadas 9999` |
| Programar una clase con horario en el pasado | `No se puede programar una clase en el pasado` |
| Registrar un miembro con email `esto-no-es-un-email` | `El email 'esto-no-es-un-email' no tiene un formato valido` |

### Scripts

| Script | Qué hace |
|---|---|
| `./scripts/levantar-todo.sh` | Compila lo que falte y arranca los 4 en segundo plano |
| `./scripts/probar-todo.sh` | Batería completa, incluidos los invariantes |
| `./scripts/probar-resiliencia.sh` | Apaga entrenadores y prueba que Clases sobrevive |
| `./scripts/detener-todo.sh` | Detiene los 4 servicios |

### Ejemplos de petición

Crear un entrenador:

```bash
curl -X POST http://localhost:8083/api/entrenadores -H "Content-Type: application/json" -d '{"nombre":"Carlos Ramirez","especialidad":"Crossfit"}'
```

Programar una clase referenciando a ese entrenador:

```bash
curl -X POST http://localhost:8082/api/clases -H "Content-Type: application/json" -d '{"nombre":"Crossfit Matutino","horario":"2026-09-01T07:00:00","capacidadMaxima":20,"entrenadorId":1}'
```

Listar clases con los datos del entrenador resueltos por REST:

```bash
curl http://localhost:8082/api/clases
```

Respuesta:

```json
[
  {
    "id": 1,
    "nombre": "Yoga Matutino",
    "horario": "2026-08-31T08:00:00",
    "capacidadMaxima": 20,
    "entrenadorId": 1,
    "entrenadorNombre": "Carlos Rodriguez",
    "entrenadorEspecialidad": "Yoga",
    "totalInscritos": 0,
    "cuposDisponibles": 20,
    "miembrosInscritos": []
  }
]
```

Inscribir un miembro, y ver cómo el agregado protege su capacidad:

```bash
curl -X POST http://localhost:8082/api/clases/1/inscripciones -H "Content-Type: application/json" -d '{"miembroId":1}'
```
