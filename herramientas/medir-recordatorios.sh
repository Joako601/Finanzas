#!/usr/bin/env bash
# Oráculo de "los recordatorios no fallan": hora programada contra hora entregada.
#
# Copia la base COMPLETA de la app (versión debug; incluye movimientos y saldos) a
# evidencia/privado/, que git ignora, consulta solo la tabla recordatorio_entregado,
# guarda ese resultado en resultado.txt y borra la copia de la base al terminar.
#
# Uso: ANDROID_SERIAL=<dispositivo> herramientas/medir-recordatorios.sh
#      (con un solo dispositivo conectado, ANDROID_SERIAL es opcional)
set -euo pipefail

PAQUETE="io.github.joako601.finanzas.debug"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="$RAIZ/evidencia/privado/recordatorios/$(date +%Y%m%d-%H%M%S)"

mkdir -p "$DESTINO"
# Pase lo que pase (Ctrl-C durante adb, una consulta que falla), la copia de la base no se queda en la laptop.
trap 'rm -f "$DESTINO"/finanzas.db "$DESTINO"/finanzas.db-wal "$DESTINO"/finanzas.db-shm' EXIT
chmod 700 "$RAIZ/evidencia/privado"

# Room usa WAL: las escrituras recientes están en -wal hasta el siguiente checkpoint.
for archivo in finanzas.db finanzas.db-wal finanzas.db-shm; do
    adb exec-out run-as "$PAQUETE" cat "databases/$archivo" > "$DESTINO/$archivo" 2>/dev/null || true
done
chmod 600 "$DESTINO"/*

{
sqlite3 -header -column "$DESTINO/finanzas.db" "
SELECT tipo,
       datetime(programado_en / 1000, 'unixepoch', 'localtime') AS programado,
       datetime(entregado_en / 1000, 'unixepoch', 'localtime')  AS entregado,
       (entregado_en - programado_en) / 1000                    AS retraso_s
FROM recordatorio_entregado
ORDER BY programado_en;
"

sqlite3 "$DESTINO/finanzas.db" "
SELECT 'Avisos: ' || COUNT(*) ||
       ' | retraso máximo: ' || COALESCE(MAX(entregado_en - programado_en) / 1000, 0) || ' s' ||
       ' | promedio: ' || COALESCE(AVG(entregado_en - programado_en) / 1000, 0) || ' s'
FROM recordatorio_entregado;
"
} | tee "$DESTINO/resultado.txt"
chmod 600 "$DESTINO/resultado.txt"
echo "Resultado en $DESTINO/resultado.txt (no se sube a git). La copia de la base se borró."
