#!/usr/bin/env bash
# Demo completa de los 30 minutos (Bloques 1-6): DDD, Seguridad (Keycloak),
# RabbitMQ y Kafka. Pensado para correr EN VIVO durante la presentacion: se
# detiene entre bloques con ENTER para que quien presenta narre sin apuro.
#
# Requiere: docker (keycloak, rabbitmq, kafka+zookeeper), los 4 microservicios
# compilados, y python3 (para formatear JSON y leer tokens).
#
# Uso: ./scripts/demo-completa.sh
set -u
cd "$(dirname "$0")/.."

json() { python3 -m json.tool 2>/dev/null || cat; }
titulo() { echo ""; echo "=============================================================="; echo "  $1"; echo "=============================================================="; }
pausa() { echo ""; read -r -p ">>> ENTER para continuar (Ctrl+C para abortar) "; }
codigo() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

get_token() {
    # get_token <client_id> <username> <password>
    curl -s -X POST http://localhost:8080/realms/gimnasio/protocol/openid-connect/token \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password&client_id=$1&client_secret=$1-secret&username=$2&password=$3" \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))"
}

# ============================================================================
titulo "PASO 0 -- Infraestructura (Keycloak, RabbitMQ, Kafka)"
# ============================================================================
docker start biblioteca-keycloak rabbitmq >/dev/null 2>&1
docker compose -f docker-compose.kafka.yml up -d >/dev/null 2>&1
echo "Esperando a que Kafka termine de inicializar..."
for _ in $(seq 1 20); do
    docker logs gym-suite-kafka-1 2>&1 | grep -q "started (kafka.server.KafkaServer)" && break
    sleep 2
done
docker ps --format '  {{.Names}}: {{.Status}}' | grep -Ei "keycloak|rabbitmq|kafka|zookeeper"

# ============================================================================
titulo "PASO 1 -- Levantar los 4 microservicios"
# ============================================================================
./scripts/levantar-todo.sh || { echo "Revisa los logs antes de seguir."; exit 1; }

# ============================================================================
titulo "PASO 2 -- Obtener los tokens de prueba"
# ============================================================================
TOKEN_ADMIN_MIEMBROS=$(get_token miembros-service admin.test Admin123!)
TOKEN_ADMIN_CLASES=$(get_token clases-service admin.test Admin123!)
TOKEN_ADMIN_ENTRENADORES=$(get_token entrenadores-service admin.test Admin123!)
TOKEN_ADMIN_EQUIPOS=$(get_token equipos-service admin.test Admin123!)
TOKEN_MEMBER_MIEMBROS=$(get_token miembros-service member.test Member123!)
TOKEN_TRAINER_CLASES=$(get_token clases-service trainer.test Trainer123!)

for par in "admin/miembros:$TOKEN_ADMIN_MIEMBROS" "admin/clases:$TOKEN_ADMIN_CLASES" \
           "admin/entrenadores:$TOKEN_ADMIN_ENTRENADORES" "admin/equipos:$TOKEN_ADMIN_EQUIPOS" \
           "member/miembros:$TOKEN_MEMBER_MIEMBROS" "trainer/clases:$TOKEN_TRAINER_CLASES"; do
    nombre="${par%%:*}"; tok="${par#*:}"
    if [ -z "$tok" ]; then echo "  FALLO obteniendo token $nombre -- revisa que Keycloak tenga el realm 'gimnasio'"; exit 1; fi
    printf "  OK token %-20s (%s...)\n" "$nombre" "${tok:0:20}"
done
pausa

# ============================================================================
titulo "BLOQUE 1-3 -- Arquitectura DDD (mostrar el diagrama, hablar 6 min)"
# ============================================================================
echo "Sin comandos en este bloque: mostrar architecture-diagram.drawio (pestaña 1)"
echo "y Clase.java / EntrenadorClient.java en el editor."
pausa

# ============================================================================
titulo "BLOQUE 2.2 -- Los 4 servicios responden de forma independiente"
# ============================================================================
for par in "miembros:8081:$TOKEN_ADMIN_MIEMBROS" "clases:8082:$TOKEN_ADMIN_CLASES" \
           "entrenadores:8083:$TOKEN_ADMIN_ENTRENADORES" "equipos:8084:$TOKEN_ADMIN_EQUIPOS"; do
    s="${par%%:*}"; rest="${par#*:}"; p="${rest%%:*}"; tok="${rest#*:}"
    printf "  GET /api/%-14s -> HTTP %s\n" "$s" "$(codigo -H "Authorization: Bearer $tok" http://localhost:$p/api/$s)"
done
pausa

# ============================================================================
titulo "BLOQUE 2.4 -- El agregado Clase protege su invariante de capacidad"
# ============================================================================
MANANA=$(date -d "+60 day" +%Y-%m-%d 2>/dev/null || date -v+60d +%Y-%m-%d)
ENTRENADOR_ID=$(curl -s -X POST http://localhost:8083/api/entrenadores \
    -H "Authorization: Bearer $TOKEN_ADMIN_ENTRENADORES" -H "Content-Type: application/json" \
    -d '{"nombre":"Demo Presentacion","especialidad":"Crossfit"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "Entrenador creado: id=$ENTRENADOR_ID"

CLASE=$(curl -s -X POST http://localhost:8082/api/clases \
    -H "Authorization: Bearer $TOKEN_ADMIN_CLASES" -H "Content-Type: application/json" \
    -d "{\"nombre\":\"Crossfit Matutino Demo\",\"horario\":\"${MANANA}T07:00:00\",\"capacidadMaxima\":2,\"entrenadorId\":$ENTRENADOR_ID}")
echo "$CLASE" | json
CLASE_ID=$(echo "$CLASE" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo ""
echo "Clase id=$CLASE_ID con capacidadMaxima=2. Inscribiendo 3 miembros:"
for m in 1 2 3; do
    echo "-- miembro $m --"
    curl -s -X POST "http://localhost:8082/api/clases/$CLASE_ID/inscripciones" \
        -H "Authorization: Bearer $TOKEN_ADMIN_CLASES" -H "Content-Type: application/json" \
        -d "{\"miembroId\":$m}" | json
done
pausa

# ============================================================================
titulo "BLOQUE 4.0 -- Que hay adentro de un token (decodificado, sin salir de la maquina)"
# ============================================================================
echo "Token de admin.test, decodificado localmente (NUNCA lo pegues en jwt.io en vivo):"
echo "$TOKEN_ADMIN_MIEMBROS" | cut -d. -f2 | tr '_-' '/+' | python3 -c "
import sys, base64, json
payload = sys.stdin.read().strip()
payload += '=' * (-len(payload) % 4)
claims = json.loads(base64.b64decode(payload))
interesantes = {k: claims[k] for k in ('iss', 'azp', 'realm_access', 'exp') if k in claims}
print(json.dumps(interesantes, indent=2, ensure_ascii=False))
"
echo ""
echo "Fijate: 'azp' (quien lo pidio) es 'miembros-service', pero el rol viaja adentro"
echo "del propio token, firmado. Cualquier servicio que confie en el mismo 'iss' puede"
echo "leerlo sin volver a preguntarle nada a Keycloak -- por eso no hace falta loguearse"
echo "una vez por cada microservicio."
pausa

# ============================================================================
titulo "BLOQUE 4.1-4.3 -- Seguridad: 401 sin token, 403 rol insuficiente, 201 OK"
# ============================================================================
echo "-- sin token (esperado 401) --"
curl -s -o /dev/null -w "  HTTP %{http_code}\n" http://localhost:8081/api/miembros

echo "-- member.test creando un miembro (esperado 403, no es ROLE_ADMIN) --"
curl -s -w "\n  HTTP %{http_code}\n" -X POST http://localhost:8081/api/miembros \
    -H "Authorization: Bearer $TOKEN_MEMBER_MIEMBROS" -H "Content-Type: application/json" \
    -d '{"nombre":"Intento no autorizado","email":"x@x.com","fechaInscripcion":"2026-09-28"}'

echo "-- admin.test creando un miembro (esperado 201) --"
curl -s -w "\n  HTTP %{http_code}\n" -X POST http://localhost:8081/api/miembros \
    -H "Authorization: Bearer $TOKEN_ADMIN_MIEMBROS" -H "Content-Type: application/json" \
    -d '{"nombre":"Miembro Demo","email":"demo@gym.com","fechaInscripcion":"2026-09-28"}'
pausa

# ============================================================================
titulo "BLOQUE 4.4 -- Seguridad propagada en la llamada interna clases->entrenadores"
# ============================================================================
echo "GET /api/clases/$CLASE_ID con token de admin (debe traer datos del entrenador):"
curl -s http://localhost:8082/api/clases/$CLASE_ID -H "Authorization: Bearer $TOKEN_ADMIN_CLASES" | json
echo ""
echo "Mostrar ahora en el navegador: http://localhost:8081/swagger-ui/index.html"
pausa

# ============================================================================
titulo "BLOQUE 5.1 -- RabbitMQ: notificacion de inscripcion (ya disparada arriba)"
# ============================================================================
echo "Log de miembros-service (notificaciones de inscripcion):"
grep -i "Notificación" logs/miembros.log | tail -5
pausa

# ============================================================================
titulo "BLOQUE 5.2 -- RabbitMQ: pub/sub de cambio de horario (fanout)"
# ============================================================================
NUEVO_HORARIO="${MANANA}T09:00:00"
curl -s -X PATCH "http://localhost:8082/api/clases/$CLASE_ID/horario" \
    -H "Authorization: Bearer $TOKEN_ADMIN_CLASES" -H "Content-Type: application/json" \
    -d "{\"nuevoHorario\":\"$NUEVO_HORARIO\"}" | json
sleep 2
echo ""
echo "-- miembros-service recibio el cambio: --"
grep -i "Aviso a miembros" logs/miembros.log | tail -2
echo "-- entrenadores-service recibio el mismo cambio: --"
grep -i "Aviso al entrenador" logs/entrenadores.log | tail -2
pausa

# ============================================================================
titulo "BLOQUE 5.3 -- RabbitMQ: Dead Letter Queue de pagos"
# ============================================================================
echo "-- pago valido --"
curl -s -X POST http://localhost:8081/api/pagos \
    -H "Authorization: Bearer $TOKEN_ADMIN_MIEMBROS" -H "Content-Type: application/json" \
    -d '{"miembroId":1,"monto":50000,"concepto":"Mensualidad demo"}' | json
echo "-- pago invalido (monto negativo, debe terminar en la DLQ) --"
curl -s -X POST http://localhost:8081/api/pagos \
    -H "Authorization: Bearer $TOKEN_ADMIN_MIEMBROS" -H "Content-Type: application/json" \
    -d '{"miembroId":1,"monto":-500,"concepto":"Pago corrupto demo"}' | json
sleep 3
echo ""
echo "Log de miembros-service (procesamiento y DLQ):"
grep -iE "Pago .* procesado exitosamente|Intento fallido|requiere atención manual" logs/miembros.log | tail -6
echo ""
echo "Mostrar ahora en el navegador: http://localhost:15672 (queues pagos-queue / pagos-dlq)"
pausa

# ============================================================================
titulo "BLOQUE 6.1 -- Kafka: ocupacion en tiempo real"
# ============================================================================
echo "Mostrar opcionalmente en el navegador: http://localhost:8090 (Kafka UI, topic ocupacion-clases)"
echo ""
echo "Log de clases-service (dashboard de ocupacion, ya disparado por las inscripciones de arriba):"
grep -i "Dashboard" logs/clases.log | tail -5
pausa

# ============================================================================
titulo "BLOQUE 6.2 -- Kafka Streams: resumen de entrenamiento (ventana 7 dias)"
# ============================================================================
curl -s -X POST http://localhost:8081/api/miembros/1/entrenamientos \
    -H "Authorization: Bearer $TOKEN_ADMIN_MIEMBROS" -H "Content-Type: application/json" \
    -d '{"tipoActividad":"Cardio","duracionMinutos":30,"calorias":250}' >/dev/null
curl -s -X POST http://localhost:8081/api/miembros/1/entrenamientos \
    -H "Authorization: Bearer $TOKEN_ADMIN_MIEMBROS" -H "Content-Type: application/json" \
    -d '{"tipoActividad":"Pesas","duracionMinutos":45,"calorias":300}' >/dev/null
echo "2 sesiones de entrenamiento publicadas."
echo "Mostrar ahora en el navegador: http://localhost:8090 -> Topics -> resumen-entrenamiento -> Messages"
echo ""
echo "Ultimo resumen agregado por consola (plan B si Kafka UI no carga):"
echo "(el topic conserva corridas anteriores del taller -- por eso pedimos solo la ultima linea;"
echo " si el contador no arranca en 2 es porque ya habia datos previos, es esperado)"
docker exec gym-suite-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic resumen-entrenamiento --from-beginning --timeout-ms 8000 --property print.key=true 2>/dev/null \
    | grep -v '^\[' | tail -1
pausa

# ============================================================================
titulo "BLOQUE 2.5 -- Tolerancia a fallos (apagar entrenadores-service)"
# ============================================================================
echo "Movido a este punto a proposito: entrenadores-service usa H2 en memoria, asi que"
echo "matarlo borra el entrenador de demo. Todo lo que necesitaba esos datos ya se mostro."
./scripts/probar-resiliencia.sh
echo ""
echo "Volviendo a levantar entrenadores-service (no hace falta para lo que resta, pero"
echo "se deja consistente para el cierre):"
(cd entrenadores-service && nohup ./mvnw -q spring-boot:run > ../logs/entrenadores.log 2>&1 &)
for _ in $(seq 1 30); do codigo=$(codigo http://localhost:8083/v3/api-docs); [ "$codigo" = "200" ] && break; sleep 2; done
echo "entrenadores-service arriba de nuevo."
pausa

# ============================================================================
titulo "BLOQUE 6.3 -- Kafka: recuperacion ante fallos (matar y reiniciar clases-service)"
# ============================================================================
echo "Matando clases-service en vivo..."
PID=$( { pgrep -f "co.analisys.clases.ClasesServiceApplication"; \
         pgrep -f "clases-service/target/clases-service-0.0.1-SNAPSHOT.jar"; } | sort -u | head -1)
if [ -z "$PID" ]; then echo "  No se encontro el proceso de clases-service."; else
    kill -9 $PID
    echo "  clases-service (PID $PID) apagado a la fuerza."
fi

echo ""
echo "AVISO: Kafka tarda 20-40s en detectar la caida antes de reasignar la particion"
echo "       (session.timeout.ms). Es normal la pausa -- explicalo mientras esperas."
echo ""
echo "Reiniciando clases-service..."
(cd clases-service && nohup ./mvnw -q spring-boot:run > ../logs/clases-restart.log 2>&1 &)
for _ in $(seq 1 40); do codigo=$(codigo http://localhost:8082/v3/api-docs); [ "$codigo" = "200" ] && break; sleep 2; done
echo "clases-service arriba de nuevo. Esperando la linea de recuperacion en el log..."
for _ in $(seq 1 25); do
    grep -qi "Recuperación\|Setting offset for partition ocupacion" logs/clases-restart.log 2>/dev/null && break
    sleep 2
done
echo ""
grep -i "Setting offset for partition ocupacion\|Recuperación" logs/clases-restart.log
pausa

# ============================================================================
titulo "FIN DE LA DEMO -- Cierre y preguntas"
# ============================================================================
echo "Para detener todo al terminar: ./scripts/detener-todo.sh"
echo "(los contenedores de Docker -- Keycloak/RabbitMQ/Kafka -- se dejan corriendo"
echo " a proposito, no forman parte de este script)"
