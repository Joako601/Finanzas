#!/usr/bin/env bash
# Copia el registro del teléfono a evidencia/privado/notificaciones/ (ignorado por git).
#
# Usa `run-as`, que solo funciona con apps "debuggable"; el registrador solo existe en
# debug. No borra nada del teléfono: el registro sigue creciendo allá.
#
# Uso: herramientas/registrador-notificaciones/exportar.sh
set -euo pipefail

PAQUETE="io.github.joako601.finanzas.registrador"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
DESTINO="$RAIZ/evidencia/privado/notificaciones"
ARCHIVO="$DESTINO/registro-$(date +%Y%m%d-%H%M%S).jsonl"

mkdir -p "$DESTINO"
chmod 700 "$RAIZ/evidencia/privado"

adb exec-out run-as "$PAQUETE" cat files/registro.jsonl > "$ARCHIVO"
chmod 600 "$ARCHIVO"

LINEAS=$(wc -l < "$ARCHIVO")
echo "Exportadas $LINEAS líneas a $ARCHIVO"
