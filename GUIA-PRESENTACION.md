# Guía de presentación — 15 minutos

## Antes de entrar al salón (10 min antes)

```bash
cd ~/microservicios/taller-gym
./scripts/levantar-todo.sh
```

Espera a ver los cuatro `OK`. Luego **detén todo** con `./scripts/detener-todo.sh`.
Esto es solo para confirmar que compila y arranca en la máquina y la red del salón —
no quieres descubrir un puerto ocupado con la profesora mirando.

Ten abierto de antemano:

1. El diagrama `architecture-diagram.drawio` en [app.diagrams.net](https://app.diagrams.net)
2. Postman con las peticiones ya guardadas (o la terminal con los scripts)
3. El editor con `Clase.java` y `EntrenadorClient.java` en pestañas
4. Una terminal en `taller-gym`

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

## Reparto sugerido

| Bloque | Minutos | Quién |
|---|---|---|
| 1. Arquitectura y decisión `entrenadorId` | 3 | Quien mejor domine DDD |
| 2. Demo en vivo | 9 | Quien tenga el proyecto en su máquina |
| 3. Cierre DDD y preguntas | 3 | Ambos |

**Los dos deben poder responder sobre cualquier servicio**, no solo el que programaron. La
pregunta cruzada es el riesgo más probable.
