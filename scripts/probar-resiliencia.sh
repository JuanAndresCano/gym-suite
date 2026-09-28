#!/usr/bin/env bash
# Demuestra que clases-service sigue funcionando si entrenadores-service se cae.
# Requiere los 4 servicios corriendo y datos ya creados (probar-todo.sh).
set -u
json() { python3 -m json.tool 2>/dev/null || cat; }

echo "=== ANTES: GET /api/clases con entrenadores-service ARRIBA ==="
curl -s http://localhost:8082/api/clases | json

echo ""
echo "=== Apagando entrenadores-service (8083)... ==="
pid=$(pgrep -f "entrenadores-service/target/entrenadores-service-0.0.1-SNAPSHOT.jar")
if [ -n "$pid" ]; then kill $pid; sleep 4; echo "Apagado (PID $pid)"; else echo "No estaba corriendo"; fi
curl -s -o /dev/null -m 3 -w "  entrenadores 8083 -> HTTP %{http_code} (000 = caido)\n" http://localhost:8083/api/entrenadores

echo ""
echo "=== DESPUES: GET /api/clases con entrenadores-service CAIDO ==="
echo "clases-service NO se cae: degrada a 'No disponible' y responde igual de rapido."
time curl -s http://localhost:8082/api/clases | json

echo ""
echo "=== Los otros 3 servicios siguen vivos ==="
for par in miembros:8081 clases:8082 equipos:8084; do
    s="${par%%:*}"; p="${par##*:}"
    printf "  %-14s puerto %s -> HTTP " "$s" "$p"
    curl -s -o /dev/null -m 3 -w "%{http_code}\n" "http://localhost:$p/api/$s"
done
echo ""
echo "Para volver a levantar entrenadores-service:"
echo "  java -jar entrenadores-service/target/entrenadores-service-0.0.1-SNAPSHOT.jar"
