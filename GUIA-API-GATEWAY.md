# Guion: API Gateway del gimnasio (Juan y Cano)

Oct 13, 2026 · @Juan Esteban Ruiz

## Empieza aquí

La exposición se parte en dos: Juan Esteban explica el porqué, la arquitectura, las rutas y la seguridad (diapositivas 1 a 5), y Juan Andrés explica Eureka, el filtro de agregación y la demo (diapositivas 6 a 9). La demo es el cierre y después se abre a preguntas. Ritmo sugerido: 1 a 2 minutos por diapositiva y lo que sobre para la demo.

Cómo usarlo, en este orden:

1. Lee **Las ideas en una frase**: cada pieza tiene una analogía del gimnasio para no perderte.
2. Lee solo tu guion. No es para recitar: dilo con tus palabras mirando la diapositiva.
3. Practica en voz alta dos veces con el cronómetro.
4. Repasa las **Preguntas probables**, también las de tu compañero: el profe puede preguntar cruzado.

Juan Esteban entrega el repositorio y Juan Andrés verifica la demo en vivo. Cuando Juan Esteban haga el push, Juan Andrés debe hacer `git pull`, leer `api-gateway/README.md` y correr `./scripts/demo-gateway.sh` una vez. Los pasos están en el checklist de abajo.

## Las ideas en una frase

Si te pierdes en vivo, vuelve a la recepción del gimnasio: todo entra por ahí, te piden el carnet y te indican a dónde ir.

| Pieza | Analogía del gimnasio | Qué es en el proyecto |
| --- | --- | --- |
| API Gateway | La recepción: una sola puerta de entrada | Proyecto `api-gateway`, puerto 8085, Spring Cloud Gateway |
| JWT | El carnet digital con tu rol, firmado y difícil de falsificar | Token que emite Keycloak y trae `realm_access.roles` |
| 401 | No traes carnet | Sin token o token inválido |
| 403 | Traes carnet pero tu plan no incluye esa sala | Token válido con rol insuficiente |
| Eureka | La cartelera donde cada instructor anota en qué sala está | `eureka-server`, puerto 8761 |
| `lb://nombre` | Pedir "el instructor de yoga" y no "la sala 3" | Ruta que resuelve un nombre de servicio, no una URL |
| LoadBalancer | El turno de las cajas: te toca la siguiente libre | Reparte por turnos (round-robin) entre instancias |
| Filtro de agregación | La recepcionista que arma tu ficha completa pidiendo datos a tres oficinas a la vez | `ResumenMiembro`, un filtro propio del gateway |
| Degradación controlada | Si pagos está cerrado, te dan la ficha con "pagos: no disponible" en vez de nada | Sección en `null` y texto en `advertencias` |

## Mapa de las 9 diapositivas

| # | Diapositiva | Presenta | La idea en una frase |
| --- | --- | --- | --- |
| 1 | Portada | Juan Esteban | El gateway es la puerta única del gimnasio |
| 2 | Por qué un API Gateway | Juan Esteban | Pasamos de 4 puertos que el cliente debe conocer a 1 |
| 3 | Arquitectura | Juan Esteban | Cliente, Keycloak, gateway, 4 servicios y Eureka, y por dónde pasa cada cosa |
| 4 | Rutas en YAML y Java DSL | Juan Esteban | Dos formas equivalentes de declarar las mismas rutas |
| 5 | Seguridad | Juan Esteban | El gateway valida el JWT y los roles antes de reenviar |
| 6 | Eureka y LoadBalancer | Juan Andrés | Se llama a los servicios por nombre y se reparte la carga |
| 7 | Filtro de agregación | Juan Andrés | Una petición del cliente, tres llamadas en paralelo |
| 8 | Respuesta y degradación | Juan Andrés | Si falla una parte secundaria, el resumen no se cae |
| 9 | Demo en vivo | Juan Andrés | Seis pasos que muestran todo funcionando |

## Guion de Juan Esteban (diapositivas 1 a 5)

Las comillas son una sugerencia de cómo sonar, no un texto para recitar.

### Diapositiva 1: Portada

"Buenas. Vamos a presentar el API Gateway del sistema del gimnasio, la puerta única por la que entra todo cliente a nuestros cuatro microservicios. Yo soy Juan Esteban y les voy a contar el problema, la arquitectura, las rutas y la seguridad. Después Juan Andrés sigue con Eureka, el filtro de agregación y la demo."

**Punto clave:** decir de entrada qué va a cubrir cada uno. Da orden y evita que el profe se pregunte quién explica qué.

### Diapositiva 2: Por qué un API Gateway

"Hoy, para usar el sistema, un cliente tendría que saber que Miembros está en el 8081, Clases en el 8082, Entrenadores en el 8083 y Equipos en el 8084. Y la seguridad, el log y los cambios de dirección quedarían repartidos. Con un API Gateway el cliente solo conoce el puerto 8085. Eso nos da tres cosas, que son los tres temas que siguen: seguridad en el borde, descubrimiento y balanceo, y respuestas agregadas."

**Punto clave:** el gateway no reemplaza la seguridad de los servicios, la complementa. Esa idea vuelve en la diapositiva 5.

### Diapositiva 3: Arquitectura

"Se lee de izquierda a derecha y de arriba hacia abajo. El cliente primero pide un token a Keycloak, en el puerto 8080. Luego manda sus peticiones al gateway con ese token. El gateway valida la firma con las claves de Keycloak y revisa el rol. Después enruta, pero no con puertos sino con nombres: `lb://miembros-service`, por ejemplo. Abajo, las líneas punteadas son los servicios registrándose en Eureka al arrancar, y eso le permite al gateway saber dónde está cada uno."

**Punto clave:** la diferencia entre línea continua (petición) y punteada (registro o validación). Las bases de datos, RabbitMQ y Kafka no cambian; están en el diagrama anterior y en el archivo `arquitectura-api-gateway.drawio`.

### Diapositiva 4: Rutas

"Una ruta tiene tres partes: un id, un predicado, que es el Path que se compara con la URL, y un destino. Declaramos tres rutas en `application.yml` y la de equipos en una clase Java, `GatewayConfig`, con el Java DSL. El resultado es idéntico; usamos los dos estilos a propósito porque el enunciado pide ambos. Los pagos van a miembros-service porque `POST /api/pagos` pertenece al contexto de Miembros. La última fila, el resumen, es la ruta del filtro que explica Juan Andrés: no tiene backend, por eso su destino es `no://op`."

**Punto clave:** YAML y Java DSL son equivalentes. Si preguntan cuál es mejor: YAML para rutas simples y cambiables sin recompilar, Java cuando hace falta lógica o composición.

### Diapositiva 5: Seguridad

"Es la parte que más pesa, así que voy con calma. Paso uno, el usuario inicia sesión en Keycloak y recibe un JWT firmado con sus roles. Paso dos, el gateway comprueba la firma y el rol: sin token responde 401 y con un rol insuficiente 403, y en ambos casos la petición ni siquiera llega al servicio. Paso tres, el header Authorization se reenvía intacto y cada servicio vuelve a validar el token por su cuenta. Eso es defensa en profundidad: si alguien llega directo al puerto de un servicio, ese servicio también lo rechaza. En la tabla ven cuatro casos reales del script de pruebas, que en total corre 33."

**Puntos clave:**

- 401 es "no sé quién eres"; 403 es "sé quién eres pero no puedes".
- No hace falta un filtro que copie el header Authorization: el gateway ya reenvía los headers por defecto. La guía del profesor trae ese filtro; nosotros lo quitamos por redundante y lo comprobamos con una prueba.
- En el realm los roles se llaman `ROLE_ADMIN`, `ROLE_MEMBER` y `ROLE_TRAINER`. El converter solo antepone `ROLE_` si llega un rol sin prefijo.

## Guion de Juan Andrés (diapositivas 6 a 9)

Tu parte es la más técnica y la más creativa para la rúbrica. Antes de ensayar, entiende el filtro en cinco pasos; con eso puedes responder casi cualquier pregunta.

### Entiende el filtro antes de exponerlo

1. La ruta `resumen-miembro` (en `application.yml`) responde a `GET /api/resumen/miembros/{id}` y tiene destino `no://op`: no hay backend al que reenviar.
2. En su lugar corre el filtro `ResumenMiembro`, la clase `ResumenMiembroGatewayFilterFactory`, que extiende `AbstractGatewayFilterFactory`.
3. El filtro lanza tres llamadas a la vez con un `WebClient` balanceado (por eso usa nombres como `http://clases-service`): el miembro, todas las clases y los pagos. Cada una tiene un timeout de 3 segundos y lleva el header Authorization original.
4. Cuando las tres terminan (`Mono.zip`), arma un solo JSON: se queda con las clases cuyo `miembrosInscritos` incluye el id, suma los pagos y lo escribe en la respuesta.
5. Si el miembro no existe responde 404. Si fallan clases o pagos, responde 200 con esa parte en `null` y un aviso en `advertencias`.

Los archivos para mirar: `api-gateway/src/main/java/co/analisys/apigateway/filter/ResumenMiembroGatewayFilterFactory.java` y `WebClientConfig.java`.

### Diapositiva 6: Eureka y LoadBalancer

"Hasta ahora las rutas apuntaban a una URL fija, como `localhost:8083`. Eso no sirve si hay dos instancias de un servicio. Con Eureka, cada servicio se registra al arrancar con su nombre. El gateway le pregunta a Eureka por las instancias de, por ejemplo, `entrenadores-service`, y Spring Cloud LoadBalancer elige una por turnos. Lo probamos levantando una segunda instancia de Entrenadores en el puerto 8093 y haciendo seis peticiones por el gateway: respondieron 8083, 8093, 8083, 8093, 8083, 8093, alternando."

**Puntos clave:**

- Un aviso práctico: después de arrancar todo, el gateway tarda entre 30 y 60 segundos en ver los servicios y mientras tanto responde 503. No es un error del código, es la caché del registro de Eureka.
- No cambiamos la llamada REST de Clases hacia Entrenadores, para conservar su degradación controlada.

### Diapositiva 7: Filtro de agregación

"Imaginen la recepción del gimnasio queriendo ver en una sola pantalla cómo va un miembro: sus datos, sus clases con el entrenador de cada una y sus pagos. Sin el filtro, el cliente haría tres peticiones. Con el filtro hace una: `GET /api/resumen/miembros/1`. El gateway no reenvía esa petición; el filtro llama a miembros-service y a clases-service al mismo tiempo, cada llamada con un límite de tres segundos, y reenvía el token original. Como las llamadas salen juntas, el tiempo total es el de la más lenta y no la suma de las tres."

**Punto clave:** "en paralelo" es lo que lo hace creativo y útil. Si preguntan por qué esto va en el gateway y no en un servicio: porque es composición pensada para el cliente, y así los servicios no se llaman entre sí.

### Diapositiva 8: Respuesta y degradación

"Esta es la respuesta: el miembro, sus clases con el nombre del entrenador, el total de clases, los pagos, el total pagado y una lista de advertencias que normalmente está vacía. A la derecha, qué pasa cuando algo falla. Si el miembro no existe, 404; sin token, 401. Y lo más importante: si fallan las clases o los pagos, que son información complementaria, el resumen no se cae. Responde 200 con esa parte en null y una advertencia que dice qué falló. Es el mismo criterio de degradación controlada que ya usa Clases cuando Entrenadores está caído."

**Punto clave:** la regla de diseño es que sin el miembro no hay resumen (503 o 502 si su servicio no responde), pero sin clases o sin pagos sí.

### Diapositiva 9: Demo en vivo

Di cada paso en una frase y ejecútalo. Se hace con dos terminales: en la 1 corre `./scripts/demo-gateway.sh`, que levanta todo y se detiene con `>>> ENTER` entre pasos; en la 2 van los comandos curl extra de `COMANDOS-DEMO-GATEWAY.txt`, que indica en qué pausa ejecutar cada uno.

1. **Eureka:** abre `http://localhost:8761` (ventana de incógnito) y señala los cuatro servicios y `API-GATEWAY`; entrenadores aparece con 2 instancias.
2. **Sin token:** el script hace `GET /api/clases` por el 8085 y responde 401.
3. **Rol insuficiente:** con el token de `member`, `POST /api/miembros` responde 403; con `admin`, 201. Después las cuatro rutas responden 200.
4. **Resumen:** con el token de `member`, `GET /api/resumen/miembros/1`. El script antes inscribe al miembro 1 en la clase 1 y registra un pago válido, si no, saldrían vacíos. Con curl se muestra que esa ruta da 200 en el gateway y 404 en el servicio directo.
5. **Balanceo:** el script hace 8 peticiones a `/api/entrenadores` y cuenta cuántas atendió cada instancia (8083 y 8093).
6. **Degradación:** el script apaga `clases-service` y repite el resumen; sale con `clases: null` y una advertencia.

**Plan B:** si algo falla en vivo, muestra la salida del script que ya corriste y el dashboard de Eureka, y di con calma qué esperabas ver.

**Cierre:** no hay diapositiva aparte; se dice con la demo todavía en pantalla. "Partimos de la guía del profesor, que cubría rutas y una seguridad básica, y la adaptamos al gimnasio: otro realm, otras rutas, versiones nuevas y roles aplicados en el gateway. Sumamos Eureka, balanceo y el filtro de agregación. Sabemos dónde están los límites y quedamos atentos a sus preguntas."

## Preguntas probables del profesor

Las respuestas marcadas como **no probado** son razonamiento correcto pero no lo ejecutamos. Dígalo así si preguntan, es más creíble que inventar.

**1. ¿Por qué no llamar directo a los servicios, si ya validan el JWT?** Porque el cliente tendría que conocer cuatro direcciones y repetir su lógica de acceso. El gateway centraliza la entrada, la seguridad de borde, el log y las rutas. Los servicios siguen validando por defensa en profundidad.

**2. ¿Qué diferencia hay entre 401 y 403 y quién los responde?** 401 es sin token o token inválido. 403 es token válido con rol insuficiente. Ambos los responde el gateway sin llegar al servicio; en las pruebas esas peticiones ni aparecen en el filtro de log.

**3. ¿Por qué validar el token dos veces?** Defensa en profundidad: si alguien llega directo al puerto 8083, Entrenadores también rechaza una petición sin token válido. El gateway no puede ser la única barrera.

**4. ¿Cómo llega el token al servicio?** El gateway reenvía los headers por defecto, incluido Authorization, intacto. Hay una prueba JUnit que lo comprueba con un backend falso. La guía del profesor trae un filtro que lo copia; lo quitamos por redundante.

**5. ¿De dónde salen los roles?** Del claim `realm_access.roles` del JWT. Un converter los vuelve authorities. En el realm ya se llaman `ROLE_ADMIN`, `ROLE_MEMBER` y `ROLE_TRAINER`; el converter solo antepone `ROLE_` si falta.

**6. ¿Qué pasa si Keycloak está caído cuando arranca el gateway?** Por diseño arranca, porque usamos la autoconfiguración con `issuer-uri`, que resuelve el decoder de forma perezosa. **No probado:** no lo ejecutamos con Keycloak apagado.

**7. ¿Qué significa `lb://`?** Que el gateway no use una URL sino que resuelva el nombre del servicio con Eureka y Spring Cloud LoadBalancer, que elige una instancia por turnos.

**8. ¿Cómo demuestran el balanceo?** Levantamos una segunda instancia de entrenadores en el 8093 con `--server.port=8093` y hicimos seis GET a `/api/entrenadores` por el gateway. Los logs mostraron 8083, 8093, 8083, 8093, 8083, 8093.

**9. ¿Por qué el filtro de agregación está en el gateway y no en un servicio?** Porque es composición para el cliente y evita que los servicios se llamen entre sí. El costo es que el gateway conoce a varios servicios; la alternativa sería un servicio de composición o un BFF.

**10. ¿Por qué llaman en paralelo?** Porque el tiempo total es el de la llamada más lenta y no la suma. Se usa `Mono.zip`, con timeout de 3 segundos por llamada.

**11. ¿Qué pasa si un servicio falla durante el resumen?** Si falla clases o pagos, 200 con esa sección en `null` y un texto en `advertencias`. Si el miembro no existe, 404; si su servicio no responde, 503 o 502. Lo probamos apagando clases-service.

**12. ¿El filtro es eficiente?** No del todo, y lo sabemos: Clases no tiene un endpoint de clases por miembro, así que el filtro pide todas y filtra en el gateway. La mejora sería `GET /api/clases?miembroId=`.

**13. ¿Un miembro puede ver el resumen de otro?** Hoy sí: cualquier usuario autenticado puede. La mejora sería ligar el token con el id del miembro y validarlo en el gateway.

**14. ¿Por qué tuvieron que agregar un historial de pagos?** Porque los pagos eran asíncronos por RabbitMQ y solo se registraban en el log, no se podían consultar. Agregamos una entidad `Pago`, que se guarda solo cuando el pago es exitoso, y `GET /api/miembros/{id}/pagos`. Los fallidos siguen yendo a la cola de mensajes muertos y no aparecen. Está en H2 en memoria, así que se pierde al reiniciar.

**15. ¿Qué pasa si Eureka se cae?** En general, los clientes de Eureka conservan una copia local del registro y siguen funcionando un tiempo con las últimas direcciones conocidas, pero no ven instancias nuevas. **No probado.**

**16. ¿El gateway es un punto único de falla?** Sí. En el taller corre una instancia. En producción habría varias detrás de un balanceador.

**17. ¿Por qué versiones distintas a las de la guía?** La guía usa Boot 3.3.3, Java 17 y Spring Cloud 2023.0.3. Nuestro proyecto usa Boot 4.1.1, que Spring Cloud soporta desde la versión 2025.1.2 (Oakwood), y Java 21. Por eso el starter ahora es `spring-cloud-starter-gateway-server-webflux` y las rutas van bajo `spring.cloud.gateway.server.webflux`.

**18. ¿Cómo agregarían un quinto servicio?** Lo registraríamos en Eureka con su `spring.application.name` y añadiríamos una ruta `lb://nombre`. El cliente no cambia.

**19. ¿Qué límites tiene la solución?** Cuatro, y cada uno tiene una salida clara. Los pagos viven en H2 en memoria y se pierden al reiniciar: se resolvería con una base de datos real. El filtro pide todas las clases y filtra en el gateway: se resolvería con un endpoint de clases por miembro. Cualquier usuario autenticado puede pedir el resumen de cualquier miembro: se resolvería ligando el token con el id del miembro. Y Eureka tarda entre 30 y 60 segundos en reflejar un servicio nuevo, que es el comportamiento normal de su caché de registro. Es mejor decirlo nosotros antes de que lo encuentre el profe.

**20. ¿Qué adaptaron de la guía del profesor?** El realm `gimnasio` y las rutas `/api/` en lugar del proyecto de biblioteca, las versiones nuevas (Spring Boot 4.1.1, Java 21 y Spring Cloud 2025.1.2), los roles aplicados en el gateway y no solo la autenticación, y lo que la guía no traía: Eureka, LoadBalancer y el filtro de agregación.

## Checklist antes de exponer

Juan Esteban:

- [ ] Hacer `git push` de todos los commits (gateway, documentación y esta guía). El `origin` es el repo de Cano.
- [ ] Descargar las diapositivas como .pptx o PDF y entregarlas junto con el repositorio.

Juan Andrés (revisa la demo en vivo):

- [ ] Hacer `git pull`, leer `api-gateway/README.md` y entender el filtro con los cinco pasos de arriba.
- [ ] Correr `./scripts/demo-gateway.sh` una vez completo (tarda unos 3 minutos en arrancar) y repasar `COMANDOS-DEMO-GATEWAY.txt`. Opcional: `scripts\test-gateway.ps1`.

Los dos, el día de la exposición:

- [ ] `docker ps` debe mostrar `keycloak-gimnasio`, `rabbitmq`, `gym-suite-zookeeper-1` y `gym-suite-kafka-1`. Si Kafka cae porque Zookeeper aún no estaba listo, basta con volver a arrancarlo.
- [ ] Antes de empezar: `./scripts/demo-gateway.sh --detener` y luego `./scripts/demo-gateway.sh`. El script levanta Keycloak, RabbitMQ y Kafka con Docker, Eureka, los 4 servicios, el gateway y la segunda instancia de entrenadores (8093); no hay que arrancar nada a mano.
- [ ] Esperar a que llegue a la primera pausa `>>> ENTER` (incluye 40 s de espera del registro de Eureka) y confirmar en `http://localhost:8761` que están los servicios y `API-GATEWAY`.
- [ ] Abrir una segunda terminal y pegar la función `tok` de `COMANDOS-DEMO-GATEWAY.txt`.
- [ ] Los tokens duran 300 segundos: la función `tok` pide uno nuevo en cada comando, no generarlos con anticipación.
- [ ] Tener abiertas las diapositivas, este guion y dos terminales (script y comandos), y decidir de antemano quién teclea en la demo.
- [ ] Ensayo completo con cronómetro, al menos una vez.

## Datos clave para saberse de memoria

| Dato | Valor |
| --- | --- |
| Puerto del API Gateway | 8085 |
| Puerto de Eureka Server | 8761 |
| Keycloak | 8080, realm `gimnasio` |
| Servicios | Miembros 8081, Clases 8082, Entrenadores 8083, Equipos 8084 |
| Segunda instancia (solo en la demo) | Entrenadores en el 8093 |
| Versiones | Spring Boot 4.1.1, Java 21, Spring Cloud 2025.1.2 (Oakwood) |
| Starter del gateway | `spring-cloud-starter-gateway-server-webflux` |
| Prefijo de rutas en el YAML | `spring.cloud.gateway.server.webflux.routes` |
| Rutas en YAML | miembros y pagos, clases, entrenadores |
| Ruta en Java DSL | equipos, en `GatewayConfig` |
| Ruta del filtro | `/api/resumen/miembros/{id}`, destino `no://op` |
| Filtro de agregación | `ResumenMiembro`, llamadas en paralelo con `Mono.zip`, timeout de 3 s |
| Roles del realm | `ROLE_ADMIN`, `ROLE_MEMBER`, `ROLE_TRAINER` |
| Respuestas de seguridad | 401 sin token, 403 con rol insuficiente, ambas desde el gateway |
| Respuestas del resumen | 404 miembro inexistente, 400 id no numérico, 503 o 502 si cae el miembro, 200 con `null` y `advertencias` si cae clases o pagos |
| Vida del token | 300 segundos |
| Espera tras arrancar | 30 a 60 segundos hasta que el gateway ve los servicios |
| Pruebas | `demo-gateway.sh` (demo guiada) y `test-gateway.ps1` con 33 casos; 16 pruebas JUnit en el gateway |
