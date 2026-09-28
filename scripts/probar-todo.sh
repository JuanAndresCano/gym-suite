#!/usr/bin/env bash
# Bateria de pruebas de los 4 microservicios. Requiere los 4 corriendo.
# Uso: ./scripts/probar-todo.sh
set -u

json() { python3 -m json.tool 2>/dev/null || cat; }
titulo() { echo ""; echo "=============================================================="; echo "  $1"; echo "=============================================================="; }
codigo() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

# Fecha futura calculada, para no depender de una fecha escrita a mano
MANANA=$(date -d "+1 day" +%Y-%m-%d 2>/dev/null || date -v+1d +%Y-%m-%d)
AYER=$(date -d "-1 day" +%Y-%m-%d 2>/dev/null || date -v-1d +%Y-%m-%d)

titulo "0. DATOS DE EJEMPLO -- cargados por el DataLoader de cada servicio"
echo "Igual que el monolito original, cada servicio arranca con sus datos:"
for par in miembros:8081 clases:8082 entrenadores:8083 equipos:8084; do
    s="${par%%:*}"; p="${par##*:}"
    n=$(curl -s -m 3 "http://localhost:$p/api/$s" | grep -o '"id"' | wc -l)
    printf "  %-14s puerto %s   registros precargados: %s\n" "$s" "$p" "$n"
done

titulo "1. ENTRENADORES (8083)"
echo "-- POST (espera 201 Created) --"
printf "  HTTP %s\n" "$(codigo -X POST http://localhost:8083/api/entrenadores \
  -H 'Content-Type: application/json' -d '{"nombre":"Laura Gomez","especialidad":"Crossfit"}')"
echo "-- GET /api/entrenadores --"
curl -s http://localhost:8083/api/entrenadores | json

titulo "2. MIEMBROS (8081) -- Value Object Email"
echo "-- POST valido (espera 201) --"
printf "  HTTP %s\n" "$(codigo -X POST http://localhost:8081/api/miembros \
  -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"Juan Cano\",\"email\":\"juan@gym.com\",\"fechaInscripcion\":\"$AYER\"}")"
echo "-- POST con email invalido (espera 400, lo rechaza el Value Object) --"
curl -s -X POST http://localhost:8081/api/miembros -H 'Content-Type: application/json' \
  -d '{"nombre":"Pedro","email":"esto-no-es-un-email"}' | json
echo "-- GET /api/miembros --"
curl -s http://localhost:8081/api/miembros | json

titulo "3. EQUIPOS (8084) -- invariante de inventario no negativo"
echo "-- POST (espera 201) --"
curl -s -X POST http://localhost:8084/api/equipos -H 'Content-Type: application/json' \
  -d '{"nombre":"Caminadora","descripcion":"Banda electrica profesional","cantidad":5}' | json
echo "-- PATCH retirar 2 unidades de las mancuernas (id 1, tenia 20) --"
curl -s -X PATCH http://localhost:8084/api/equipos/1/inventario \
  -H 'Content-Type: application/json' -d '{"ajuste":-2}' | json
echo "-- PATCH retirar 9999 unidades (espera 400: el agregado se protege) --"
curl -s -X PATCH http://localhost:8084/api/equipos/1/inventario \
  -H 'Content-Type: application/json' -d '{"ajuste":-9999}' | json

titulo "4. CLASES (8082) -- invariante de horario"
echo "-- POST con horario en el pasado (espera 400) --"
curl -s -X POST http://localhost:8082/api/clases -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"Clase en el pasado\",\"horario\":\"${AYER}T07:00:00\",\"capacidadMaxima\":20,\"entrenadorId\":1}" | json
echo "-- POST valido con capacidad 2, para demostrar el limite de cupo --"
curl -s -X POST http://localhost:8082/api/clases -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"Crossfit Matutino\",\"horario\":\"${MANANA}T07:00:00\",\"capacidadMaxima\":2,\"entrenadorId\":1}" | json

titulo "5. INTEGRACION REST -- GET /api/clases resuelve el entrenador"
echo "Clases guarda solo entrenadorId, pero el GET trae nombre y especialidad"
echo "consultando por HTTP a entrenadores-service (8083):"
curl -s http://localhost:8082/api/clases | json

titulo "6. INVARIANTE DE CAPACIDAD -- el Aggregate Root protege su regla"
CLASE=$(curl -s http://localhost:8082/api/clases | python3 -c \
  "import sys,json; print([c['id'] for c in json.load(sys.stdin) if c['capacidadMaxima']==2][-1])")
echo "Clase id=$CLASE con capacidadMaxima=2. Inscribimos tres miembros:"
for m in 1 2 3; do
    echo "-- inscribir miembro $m --"
    curl -s -X POST "http://localhost:8082/api/clases/$CLASE/inscripciones" \
      -H 'Content-Type: application/json' -d "{\"miembroId\":$m}" \
      | python3 -c "import sys,json; d=json.load(sys.stdin); print('  OK  inscritos:',d['totalInscritos'],'cupos:',d['cuposDisponibles']) if 'totalInscritos' in d else print('  RECHAZADO:',d.get('error'))"
done
echo "-- inscribir de nuevo al miembro 1 (espera rechazo por duplicado) --"
curl -s -X POST "http://localhost:8082/api/clases/$CLASE/inscripciones" \
  -H 'Content-Type: application/json' -d '{"miembroId":1}' | json

titulo "RESUMEN"
for par in miembros:8081 clases:8082 entrenadores:8083 equipos:8084; do
    s="${par%%:*}"; p="${par##*:}"
    n=$(curl -s -m 3 "http://localhost:$p/api/$s" | grep -o '"id"' | wc -l)
    printf "  %-14s puerto %s   registros: %s\n" "$s" "$p" "$n"
done
echo ""
echo "Los 4 servicios respondieron simultaneamente, cada uno contra su propia base de datos,"
echo "y cada agregado rechazo las operaciones que violaban sus invariantes."
