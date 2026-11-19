"""Maneja la interfaz de un dispositivo Android por adb, para recorridos de verificación.

Lee el árbol de accesibilidad con uiautomator y toca los elementos por su texto.
Respeta ANDROID_SERIAL: con varios dispositivos conectados, exportar
ANDROID_SERIAL=emulator-5554 para no tocar otro teléfono.

Uso (ejecutar con python3 -I):
  manejar-ui.py tocar "Texto" [n]     toca la n-ésima coincidencia (1 por omisión)
  manejar-ui.py tocarxy X Y           toca unas coordenadas (elementos sin texto accesible)
  manejar-ui.py escribir "85.50"      escribe en el campo con foco
  manejar-ui.py borrar                borra el campo con foco
  manejar-ui.py deslizar              desliza hacia arriba para ver más abajo
  manejar-ui.py atras                 botón atrás
  manejar-ui.py esperar SEGUNDOS
  manejar-ui.py textos                lista los textos visibles
  manejar-ui.py captura archivo.png   guarda una captura de pantalla
"""
import re
import subprocess
import sys
import time


def adb(*args, salida_binaria=False):
    r = subprocess.run(["adb", *args], capture_output=True)
    if r.returncode != 0:
        sys.exit(f"adb {' '.join(args)} falló: {r.stderr.decode(errors='replace')}")
    return r.stdout if salida_binaria else r.stdout.decode(errors="replace")


NODO = re.compile(
    r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
)


def nodos():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    arbol = adb("shell", "cat", "/sdcard/ui.xml")
    return [(m.group(1), m.group(2), *map(int, m.group(3, 4, 5, 6))) for m in NODO.finditer(arbol)]


def tocar(texto, indice=1):
    for _ in range(10):
        coincidencias = [n for n in nodos() if texto in (n[0], n[1])]
        if len(coincidencias) >= indice:
            _, _, x1, y1, x2, y2 = coincidencias[indice - 1]
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(0.8)
            return
        time.sleep(0.5)
    visibles = [n[0] or n[1] for n in nodos() if n[0] or n[1]]
    sys.exit(f"No encontré '{texto}' en pantalla. Visibles: {visibles}")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    accion = sys.argv[1]
    if accion == "tocar":
        tocar(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 1)
    elif accion == "tocarxy":
        adb("shell", "input", "tap", sys.argv[2], sys.argv[3])
        time.sleep(0.8)
    elif accion == "escribir":
        adb("shell", "input", "text", sys.argv[2].replace(" ", "%s"))
        time.sleep(0.5)
    elif accion == "borrar":
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END", *(["KEYCODE_DEL"] * 25))
        time.sleep(0.5)
    elif accion == "deslizar":
        adb("shell", "input", "swipe", "540", "1300", "540", "400", "300")
        time.sleep(0.8)
    elif accion == "atras":
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.8)
    elif accion == "esperar":
        time.sleep(float(sys.argv[2]))
    elif accion == "textos":
        for t, d, *_ in nodos():
            if t or d:
                print(t or f"[{d}]")
    elif accion == "captura":
        with open(sys.argv[2], "wb") as f:
            f.write(adb("exec-out", "screencap", "-p", salida_binaria=True))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
