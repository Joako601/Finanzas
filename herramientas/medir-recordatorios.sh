#!/usr/bin/env bash
# Oráculo de "los recordatorios no fallan": hora programada contra hora entregada.
#
# Copia la base de la app (versión debug) a evidencia/privado/, que git ignora, y
# consulta SOLO la tabla recordatorio_entregado: clave, tipo, hora programada, hora
# real de entrega y retraso. No lee movimientos ni saldos.
#
# Uso: ANDROID_SERIAL=<dispositivo> herramientas/medir-recordatorios.sh
#      (con un solo dispositivo conectado, ANDROID_SERIAL es opcional)
set -euo pipefail

PAQUETE="io.github.joako601.finanzas.debug"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="$RAIZ/evidencia/privado/recordatorios/$(date +%Y%m%d-%H%M%S)"

mkdir -p "$DESTINO"
chmod 700 "$RAIZ/evidencia/privado"

# Room usa WAL: las escrituras recientes están en -wal hasta el siguiente checkpoint.
for archivo in finanzas.db finanzas.db-wal finanzas.db-shm; do
    adb exec-out run-as "$PAQUETE" cat "databases/$archivo" > "$DESTINO/$archivo" 2>/dev/null || true
done
chmod 600 "$DESTINO"/*

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
echo "Copia de la base en $DESTINO (no se sube a git)."
