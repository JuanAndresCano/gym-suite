#!/usr/bin/env bash
# Demo del API Gateway (diapositiva 9): levanta Eureka, los 4 servicios, el gateway y una
# segunda instancia de entrenadores (8093), y recorre los 6 pasos de la guia con pausas.
#
# Requiere: Docker (el script levanta Keycloak, RabbitMQ y Kafka con los docker-compose
# del repo), java 21+, curl y python3.
#
# Uso:  ./scripts/demo-gateway.sh            levanta todo y corre la demo
#       ./scripts/demo-gateway.sh --detener  apaga todo lo que levanto
set -u
cd "$(dirname "$0")/.."
LOGS=logs
PIDFILE=$LOGS/demo-gateway.pids
GW=http://localhost:8085
mkdir -p "$LOGS"

json() { python3 -m json.tool 2>/dev/null || cat; }
titulo() { echo ""; echo "=============================================================="; echo "  $1"; echo "=============================================================="; }
pausa() { echo ""; read -r -p ">>> ENTER para continuar (Ctrl+C para abortar) "; }
codigo() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

detener() {
    [ -f "$PIDFILE" ] || { echo "No hay procesos registrados en $PIDFILE."; return; }
    while IFS=: read -r nombre pid; do
        kill "$pid" 2>/dev/null && echo "  $nombre detenido (PID $pid)"
    done < "$PIDFILE"
    rm -f "$PIDFILE"
}

if [ "${1:-}" = "--detener" ]; then detener; exit 0; fi

get_token() {
    # get_token <usuario> <password>  (password grant del client miembros-service)
    curl -s -X POST http://localhost:8080/realms/gimnasio/protocol/openid-connect/token \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password&client_id=miembros-service&client_secret=miembros-service-secret&username=$1&password=$2" \
        | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))"
}

# arrancar <nombre> <carpeta> [argumentos extra de Spring...]
arrancar() {
    local nombre=$1 carpeta=$2; shift 2
    local jar
    jar=$(ls "$carpeta"/target/*-SNAPSHOT.jar 2>/dev/null | head -1)
    # Recompila si no hay jar o si el codigo/pom es mas nuevo (un jar viejo no trae Eureka)
    if [ -z "$jar" ] || [ -n "$(find "$carpeta/src" "$carpeta/pom.xml" -newer "$jar" -print -quit)" ]; then
        echo "Compilando $carpeta (jar ausente o desactualizado)..."
        (cd "$carpeta" && sh ./mvnw -q -B package -DskipTests) || { echo "FALLO al compilar $carpeta"; exit 1; }
        jar=$(ls "$carpeta"/target/*-SNAPSHOT.jar | head -1)
    fi
    java -jar "$jar" "$@" > "$LOGS/$nombre.log" 2>&1 &
    echo "$nombre:$!" >> "$PIDFILE"
    echo "  $nombre arrancando (PID $!)"
}

esperar() {
    # esperar <nombre> <url> [segundos]
    local nombre=$1 url=$2 max=${3:-90}
    for _ in $(seq 1 "$max"); do
        [ "$(codigo -m 2 "$url")" = "200" ] && { echo "  OK    $nombre"; return 0; }
        sleep 1
    done
    echo "  FALLO $nombre no respondio en ${max}s -- revisa $LOGS/"
    return 1
}

veces() { local n; n=$(grep -c "$2" "$1" 2>/dev/null); echo "${n:-0}"; }

# ============================================================================
titulo "PASO 0 -- Infraestructura (Keycloak, RabbitMQ, Kafka)"
# ============================================================================
docker compose -f docker-compose.infra.yml up -d >/dev/null 2>&1
docker compose -f docker-compose.kafka.yml up -d >/dev/null 2>&1
echo "Esperando a Keycloak (puede tardar hasta 90 s la primera vez)..."
for _ in $(seq 1 45); do
    [ "$(codigo -m 2 http://localhost:8080/realms/gimnasio)" = "200" ] && break
    sleep 2
done
if [ "$(codigo -m 5 http://localhost:8080/realms/gimnasio)" != "200" ]; then
    echo "Keycloak no responde en http://localhost:8080/realms/gimnasio (realm 'gimnasio')."
    echo "Revisa: docker compose -f docker-compose.infra.yml logs keycloak"
    exit 1
fi
echo "  OK    Keycloak (realm gimnasio)"
docker ps --format '  {{.Names}}: {{.Status}}' | grep -Ei "keycloak|rabbitmq|kafka|zookeeper"

# ============================================================================
titulo "PASO 1 -- Levantar Eureka, los 4 servicios, el gateway y entrenadores #2 (8093)"
# ============================================================================
[ -f "$PIDFILE" ] && { echo "Hay una demo previa registrada; la apago primero."; detener; sleep 2; }
ocupados=""
for p in 8081 8082 8083 8084 8085 8093 8761; do
    ss -ltn 2>/dev/null | grep -q ":$p " && ocupados="$ocupados $p"
done
if [ -n "$ocupados" ]; then
    echo "Puertos ocupados:$ocupados. Cierra lo que quedo corriendo (./scripts/detener-todo.sh) y reintenta."
    exit 1
fi

arrancar eureka eureka-server
esperar eureka http://localhost:8761/ 60 || exit 1
LOGDEBUG="--logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG"
arrancar entrenadores entrenadores-service $LOGDEBUG
arrancar miembros miembros-service
arrancar equipos equipos-service
arrancar clases clases-service
arrancar entrenadores-2 entrenadores-service --server.port=8093 $LOGDEBUG
arrancar gateway api-gateway
fallo=0
for par in miembros:8081 clases:8082 entrenadores:8083 equipos:8084 entrenadores-2:8093; do
    nombre=${par%%:*}; p=${par##*:}
    esperar "$nombre (puerto $p)" "http://localhost:$p/v3/api-docs" 180 || fallo=1
done
esperar "api-gateway (puerto 8085)" "$GW/actuator/health" 60 || fallo=1
[ "$fallo" = "1" ] && { echo "Algo no levanto; no sigas con la demo."; exit 1; }
echo "Esperando 40 s a que el gateway descargue el registro de Eureka..."
sleep 40

# ============================================================================
titulo "DEMO 1 -- Eureka: servicios registrados"
# ============================================================================
echo "Abre en el navegador: http://localhost:8761"
echo "Registrados ahora mismo:"
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps \
    | python3 -c "
import sys,json
for a in json.load(sys.stdin)['applications']['application']:
    print('  %-22s %d instancia(s)' % (a['name'], len(a['instance'])))"
pausa

# ============================================================================
titulo "DEMO 2 -- Sin token: el gateway responde 401"
# ============================================================================
echo "GET $GW/api/clases (sin Authorization)"
echo "  -> HTTP $(codigo $GW/api/clases)   (esperado 401)"
pausa

# ============================================================================
titulo "DEMO 3 -- Rol insuficiente: member no puede crear miembros (403)"
# ============================================================================
TOKEN_MEMBER=$(get_token member.test Member123!)
TOKEN_ADMIN=$(get_token admin.test Admin123!)
if [ -z "$TOKEN_MEMBER" ] || [ -z "$TOKEN_ADMIN" ]; then echo "No se pudo obtener token de Keycloak."; exit 1; fi
echo "POST $GW/api/miembros con token de member.test"
echo "  -> HTTP $(codigo -X POST $GW/api/miembros -H "Authorization: Bearer $TOKEN_MEMBER" -H 'Content-Type: application/json' -d '{"nombre":"X","email":"x@email.com"}')   (esperado 403)"
echo "Mismo POST con token de admin.test:"
echo "  -> HTTP $(codigo -X POST $GW/api/miembros -H "Authorization: Bearer $TOKEN_ADMIN" -H 'Content-Type: application/json' -d '{"nombre":"Demo Gateway","email":"demo-gateway@email.com"}')   (esperado 201)"
echo ""
echo "Las 4 rutas (miembros, clases, entrenadores y equipos) con token de admin:"
for r in miembros clases entrenadores equipos; do
    echo "  GET $GW/api/$r -> HTTP $(codigo $GW/api/$r -H "Authorization: Bearer $TOKEN_ADMIN")   (esperado 200)"
done
pausa

# ============================================================================
titulo "DEMO 4 -- Filtro de agregacion: resumen del miembro 1"
# ============================================================================
echo "Preparando datos: inscribo al miembro 1 en la clase 1 y registro un pago..."
curl -s -o /dev/null -X POST "$GW/api/clases/1/inscripciones" -H "Authorization: Bearer $TOKEN_MEMBER" -H 'Content-Type: application/json' -d '{"miembroId":1}'
echo "  pago -> HTTP $(codigo -X POST $GW/api/pagos -H "Authorization: Bearer $TOKEN_MEMBER" -H 'Content-Type: application/json' -d '{"miembroId":1,"monto":120000,"concepto":"Mensualidad (demo)"}')   (esperado 202)"
sleep 3
echo ""
echo "GET $GW/api/resumen/miembros/1   (una sola llamada, tres datos: miembro + clases + pagos)"
curl -s "$GW/api/resumen/miembros/1" -H "Authorization: Bearer $TOKEN_MEMBER" | json
pausa

# ============================================================================
titulo "DEMO 5 -- Balanceo de carga: 8 peticiones a /api/entrenadores"
# ============================================================================
a0=$(veces $LOGS/entrenadores.log 'GET "/api/entrenadores"')
b0=$(veces $LOGS/entrenadores-2.log 'GET "/api/entrenadores"')
for _ in $(seq 1 8); do
    curl -s -o /dev/null "$GW/api/entrenadores" -H "Authorization: Bearer $TOKEN_MEMBER"
done
sleep 1
a1=$(veces $LOGS/entrenadores.log 'GET "/api/entrenadores"')
b1=$(veces $LOGS/entrenadores-2.log 'GET "/api/entrenadores"')
echo "  instancia 8083 atendio $((a1 - a0)) peticiones   (logs/entrenadores.log)"
echo "  instancia 8093 atendio $((b1 - b0)) peticiones   (logs/entrenadores-2.log)"
echo "Esperado: reparto casi parejo (round-robin)."
pausa

# ============================================================================
titulo "DEMO 6 -- Degradacion: clases-service caido"
# ============================================================================
pid_clases=$(grep '^clases:' "$PIDFILE" | cut -d: -f2)
kill "$pid_clases" 2>/dev/null
echo "clases-service detenido (PID $pid_clases). Esperando a que muera el puerto 8082..."
for _ in $(seq 1 20); do ss -ltn | grep -q ":8082 " || break; sleep 1; done
echo ""
echo "GET $GW/api/resumen/miembros/1"
curl -s "$GW/api/resumen/miembros/1" -H "Authorization: Bearer $(get_token member.test Member123!)" | json
echo ""
echo "Esperado: 'clases': null y una advertencia; el miembro y los pagos siguen llegando."

titulo "FIN -- para apagar todo:  ./scripts/demo-gateway.sh --detener"
