#!/usr/bin/env bash
# Detiene los 4 microservicios, sin importar como se arrancaron:
#   - java -jar .../target/xxx-service-0.0.1-SNAPSHOT.jar   (levantar-todo.sh)
#   - ./mvnw spring-boot:run                                (arranque manual)
cd "$(dirname "$0")/.."
encontrado=0
for s in miembros clases entrenadores equipos; do
    # Forma 1: el jar empaquetado. Forma 2: maven spring-boot:run (usa -cp target/classes
    # y la clase principal, por eso el patron del jar no lo encuentra).
    pids=$(pgrep -f "$s-service/target/$s-service-0.0.1-SNAPSHOT.jar"; \
           pgrep -f "co.analisys.$s.*ServiceApplication"; \
           pgrep -f "multiModuleProjectDirectory=.*/$s-service")
    pids=$(echo "$pids" | sort -u | tr '\n' ' ')
    for pid in $pids; do
        [ -z "$pid" ] && continue
        kill "$pid" 2>/dev/null && { echo "  $s-service detenido (PID $pid)"; encontrado=1; }
    done
done
sleep 2
# Ultimo recurso: cualquiera que siga ocupando los puertos
for p in 8081 8082 8083 8084; do
    pid=$(ss -ltnp 2>/dev/null | grep ":$p " | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2)
    if [ -n "$pid" ]; then
        kill "$pid" 2>/dev/null && { echo "  puerto $p liberado (PID $pid)"; encontrado=1; }
    fi
done
[ "$encontrado" = "0" ] && echo "No habia servicios corriendo." || echo "Todos los servicios detenidos."
