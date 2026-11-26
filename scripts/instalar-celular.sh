#!/usr/bin/env bash
# Instala una variante de Kren en el celular por depuración inalámbrica sin perder datos:
#   1. Busca el teléfono (mDNS) y se conecta.
#   2. Respalda la base (finanzas.db + -wal + -shm) en evidencia/privado/respaldo-<variante>-<etiqueta>-<fecha>.
#   3. Instala con Gradle (nunca desinstala), abre la app desde cero y compara la huella de la base.
#
# Uso: scripts/instalar-celular.sh [nube|privada] [etiqueta]
#   scripts/instalar-celular.sh nube login
# Sale con error si no encuentra el teléfono, si el respaldo queda vacío o si la base cambia.
set -euo pipefail
cd "$(dirname "$0")/.."

variante="${1:-nube}"
etiqueta="${2:-prueba}"
case "$variante" in
  nube) paquete=io.github.joako601.finanzas.nube.debug; tarea=:app:installNubeDebug ;;
  privada) paquete=io.github.joako601.finanzas.debug; tarea=:app:installPrivadaDebug ;;
  *) echo "Variante desconocida: $variante (usa nube o privada)" >&2; exit 2 ;;
esac

# --- 1. Teléfono ---------------------------------------------------------------------------
serial="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
if [[ -z "$serial" ]]; then
  for _ in 1 2 3 4 5; do
    destino="$(adb mdns services | awk '/_adb-tls-connect/{print $3; exit}')"
    [[ -n "$destino" ]] && break
    sleep 2
  done
  if [[ -z "${destino:-}" ]]; then
    echo "No encuentro el celular: activa Depuración inalámbrica (misma Wi-Fi) y reintenta." >&2
    exit 1
  fi
  timeout 15 adb connect "$destino" >/dev/null || true
  serial="$destino"
  # Sin esto, un connect fallido seguía como "app no instalada" e instalaba sin respaldo.
  if [[ "$(adb -s "$serial" get-state 2>/dev/null)" != "device" ]]; then
    echo "El celular aparece ($destino) pero rechaza la conexión: vuelve a vincularlo con" >&2
    echo "  adb pair <IP:PUERTO-de-vinculación> <CÓDIGO>" >&2
    exit 1
  fi
fi
export ANDROID_SERIAL="$serial"
echo "Celular: $serial"

# --- 2. Respaldo -----------------------------------------------------------------------------
huella() { # filas y sha256 del contenido de cada tabla de datos
  local db="$1" t n h
  for t in $(sqlite3 "$db" "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name"); do
    n=$(sqlite3 "$db" "SELECT COUNT(*) FROM \"$t\"")
    h=$(sqlite3 "$db" "SELECT * FROM \"$t\" ORDER BY 1" | sha256sum | cut -c1-12)
    echo "$t filas=$n $h"
  done
}
copiar_base() { # copia la base del teléfono a la carpeta $1; falla si no hay base
  local f
  for f in finanzas.db finanzas.db-wal finanzas.db-shm; do
    adb exec-out run-as "$paquete" cat "databases/$f" > "$1/$f" 2>/dev/null || rm -f "$1/$f"
  done
  [[ -s "$1/finanzas.db" ]]
}

respaldo="evidencia/privado/respaldo-$variante-$etiqueta-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$respaldo"
hay_base=false
if ! adb shell pm path "$paquete" >/dev/null 2>&1; then
  rmdir "$respaldo"
  echo "La app no estaba instalada: no hay base que respaldar."
else
  # Detenida, la app no escribe en el WAL mientras se copia.
  adb shell am force-stop "$paquete"
  # "si"/"no" solo si run-as funciona; cualquier otra salida es un error y no se instala.
  existe="$(adb shell run-as "$paquete" sh -c "'test -e databases/finanzas.db && echo si || echo no'" 2>/dev/null | tr -d '\r')"
  if [[ "$existe" == "no" ]]; then
    # Pasa en nube si nunca se pasó del login: la base se crea al entrar a finanzas.
    rmdir "$respaldo"
    echo "La app está instalada pero aún no crea su base: no hay nada que respaldar."
  elif [[ "$existe" == "si" ]] && copiar_base "$respaldo"; then
    hay_base=true
    huella "$respaldo/finanzas.db" > "$respaldo/huella-antes.txt"
    echo "Respaldo: $respaldo ($(grep -c . "$respaldo/huella-antes.txt") tablas)"
  else
    rm -rf "$respaldo"
    echo "No pude leer la base: no instalo para no arriesgar datos." >&2
    exit 1
  fi
fi

# --- 3. Instalar, abrir y comparar ---------------------------------------------------------
./gradlew -q --max-workers=2 "$tarea"
adb logcat -c
adb shell am force-stop "$paquete"
adb shell monkey -p "$paquete" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4

if adb logcat -d -t 400 AndroidRuntime:E '*:S' | grep -q "FATAL EXCEPTION"; then
  echo "La app se cerró al abrir (revisa: adb logcat -d AndroidRuntime:E '*:S')." >&2
fi

if $hay_base; then
  despues="$(mktemp -d)"
  trap 'rm -rf "$despues"' EXIT
  copiar_base "$despues"
  huella "$despues/finanzas.db" > "$respaldo/huella-despues.txt"
  if diff -q "$respaldo/huella-antes.txt" "$respaldo/huella-despues.txt" >/dev/null; then
    echo "Instalado. BASE IDÉNTICA."
  else
    echo "Instalado, pero LA BASE CAMBIÓ. Diferencias:" >&2
    diff "$respaldo/huella-antes.txt" "$respaldo/huella-despues.txt" >&2 || true
    exit 3
  fi
else
  echo "Instalado."
fi
