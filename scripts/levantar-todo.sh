#!/usr/bin/env bash
# Levanta los 4 microservicios en segundo plano.
# Uso: ./scripts/levantar-todo.sh
set -u
cd "$(dirname "$0")/.."
LOGS=logs
mkdir -p "$LOGS"

SERVICIOS="miembros:8081 clases:8082 entrenadores:8083 equipos:8084"

# Aviso temprano: si un puerto ya esta ocupado, el servicio no va a poder arrancar
ocupados=""
for par in $SERVICIOS; do
    p="${par##*:}"
    if ss -ltn 2>/dev/null | grep -q ":$p "; then ocupados="$ocupados $p"; fi
done
if [ -n "$ocupados" ]; then
    echo "AVISO: los puertos$ocupados ya estan ocupados."
    echo "       Probablemente quedo un servicio corriendo de antes."
    echo "       Corre ./scripts/detener-todo.sh primero."
    echo ""
fi

# Compila lo que falte
for par in $SERVICIOS; do
    s="${par%%:*}"
    if [ ! -f "$s-service/target/$s-service-0.0.1-SNAPSHOT.jar" ]; then
        echo "Compilando $s-service (primera vez)..."
        (cd "$s-service" && ./mvnw -q -B package -DskipTests) || { echo "FALLO al compilar $s-service"; exit 1; }
    fi
done

# Arranca entrenadores primero: clases lo consulta por REST
PIDS=""
for par in entrenadores:8083 miembros:8081 equipos:8084 clases:8082; do
    s="${par%%:*}"; p="${par##*:}"
    java -jar "$s-service/target/$s-service-0.0.1-SNAPSHOT.jar" > "$LOGS/$s.log" 2>&1 &
    PIDS="$PIDS $s:$!"
    echo "  $s-service arrancando en el puerto $p (PID $!)"
done

echo ""
echo "Esperando a que los 4 respondan..."
fallo=0
for par in $SERVICIOS; do
    s="${par%%:*}"; p="${par##*:}"
    # El PID que arrancamos nosotros, para no confundirnos con un proceso ajeno
    mipid=$(echo "$PIDS" | tr ' ' '\n' | grep "^$s:" | cut -d: -f2)
    code=000
    for _ in $(seq 1 60); do
        # Si nuestro proceso murio, no tiene sentido seguir esperando
        if ! kill -0 "$mipid" 2>/dev/null; then break; fi
        code=$(curl -s -o /dev/null -m 2 -w "%{http_code}" "http://localhost:$p/api/$s")
        [ "$code" = "200" ] && break
        sleep 1
    done

    if ! kill -0 "$mipid" 2>/dev/null; then
        # Nuestro proceso murio: alguien mas puede estar respondiendo en ese puerto
        echo "  FALLO $s-service (puerto $p) -- el proceso murio al arrancar"
        motivo=$(grep -m1 -iE "Port .* was already in use|APPLICATION FAILED TO START" "$LOGS/$s.log" 2>/dev/null)
        [ -n "$motivo" ] && echo "        $motivo"
        echo "        revisa $LOGS/$s.log"
        fallo=1
    elif [ "$code" = "200" ]; then
        echo "  OK    $s-service   http://localhost:$p/api/$s"
    else
        echo "  FALLO $s-service (puerto $p) -- no respondio a tiempo, revisa $LOGS/$s.log"
        fallo=1
    fi
done

echo ""
if [ "$fallo" = "1" ]; then
    echo "Al menos un servicio NO quedo arriba. No sigas con la demo hasta arreglarlo."
    exit 1
fi
echo "Listo. Ahora: ./scripts/probar-todo.sh"
