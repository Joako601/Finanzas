# Verificación

Cómo comprobar, sin confiar en nadie, lo que el proyecto afirma. Cada sección dice qué
se afirma, el comando y el resultado esperado.

Requisitos: JDK 17 o más nuevo, Android SDK con build-tools 36.0.0 y plataforma 37, y
`local.properties` con `sdk.dir`.

## 1. El dominio es puro y sus pruebas pasan

```bash
./gradlew :core:domain:check
```

Esperado: `BUILD SUCCESSFUL` y la advertencia `JS Environment Not Selected` (ver D-004).
Resultados en `core/domain/build/test-results/jvmTest/`.

Contraprueba de la guardia de pureza: crear
`core/domain/src/commonMain/kotlin/Prueba.kt` con `internal fun p(): Any = java.math.BigDecimal.ONE`,
correr el mismo comando y ver `Unresolved reference 'java'`. Borrar el archivo.

## 2. La app no pide permisos

```bash
./gradlew assembleRelease check
~/Android/Sdk/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

Esperado, exactamente:

```
package: io.github.joako601.finanzas
permission: io.github.joako601.finanzas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='io.github.joako601.finanzas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Ese permiso lo declara la propia app con nivel `signature` (D-005). Cualquier otra
línea, en particular `android.permission.INTERNET`, es un fallo. La tarea
`verificarPermisosRelease` ya hace esta comprobación sobre el manifiesto fusionado y
deja la lista en `app/build/reportes/permisos/release.txt`.

Pendiente para la fase de auditoría: Exodus Privacy y MobSF sobre el APK firmado.

## 3. Room funciona contra el SQLite real del teléfono

Con el teléfono conectado y la depuración USB autorizada:

```bash
./gradlew :core:data:connectedDebugAndroidTest
```

Esperado: 13 pruebas sin fallas, incluida `MigracionTest` (base de la versión 1 con
datos, migrada a la 2). Reporte en `core/data/build/reports/androidTests/connected/`.

Sin teléfono, sirve el emulador. Si el AVD es de tipo Play Store y aparece como
`unauthorized`, la llave pública de adb debe estar junto al AVD:
`cp ~/.android/adbkey.pub ~/.config/.android/` y reiniciar el emulador.

## 3b. Actualizar la app conserva los datos (D-012)

1. Compilar la versión anterior en un worktree (`git worktree add <dir> <commit>`) e
   instalarla.
2. Capturar datos en ella (un gasto y una cuenta con saldo).
3. Instalar la versión nueva encima con `adb install -r app-debug.apk`.
4. Abrir: los movimientos y saldos deben ser los mismos, y la app no debe cerrarse.

## 4. Las dependencias no cambiaron

La verificación corre sola en cada build gracias a `gradle/verification-metadata.xml`.
Si un artefacto descargado no coincide con su SHA-256, el build falla con
`Dependency verification failed`.

## 5. Captura en dos o tres toques (manual, por ahora)

1. Abrir la app.
2. Tocar **Registrar**: el teclado numérico aparece en el campo de monto.
3. Escribir `85.50`.
4. Tocar una categoría (por ejemplo, **Comida fuera**).
5. Tocar **Guardar**.

Esperado: la hoja se cierra, el movimiento aparece bajo **Hoy** con `−$85.50` y el
saldo de Efectivo baja en $85.50. Son tres toques más escribir el monto.

La medición formal (cinco personas, con tiempo medido) llega con la decisión 3.

## 6. Los recordatorios llegan, no se repiten y respetan los pagos (D-015)

En el emulador (no hace falta esperar días: se mueve el reloj):

```bash
export ANDROID_SERIAL=emulator-5554
adb shell settings put global auto_time 0
adb shell cmd alarm set-time <milisegundos un minuto antes del aviso>
adb shell dumpsys alarm | grep -A2 io.github.joako601.finanzas.debug   # hora y ventana
adb shell dumpsys notification --noredact | grep -A40 io.github.joako601.finanzas.debug
herramientas/medir-recordatorios.sh                                     # programado vs entregado
adb reboot   # al volver, la alarma debe estar programada sin abrir la app
adb shell settings put global auto_time 1
```

En el teléfono real: `herramientas/medir-recordatorios.sh` una vez por semana durante
dos semanas. Pendiente: pruebas con `dumpsys deviceidle force-idle` y
`am set-standby-bucket` (reposo profundo y app poco usada).

## Registro de resultados

| Fecha | Qué | Dónde | Resultado | Quién |
|---|---|---|---|---|
| 2026-11-18 | Recorrido manual de 8 pasos: captura de gasto con teclado automático, saldo, cuenta nueva, ingreso, fecha anterior, rotación, borrado, mensajes de error | Samsung Galaxy S24 FE, APK debug instalado desde archivo | Todo funciona | Autor |
| 2026-11-18 | 13 pruebas instrumentadas de Room, incluida la migración 1→2 | Emulador API 37 (Medium Phone, Play Store) | 13/13 pasan | Claude |
| 2026-11-18 | Actualización real de versionCode 1 a 2 con datos (gasto de $85.50 y cuenta con $5,000) | Emulador API 37 | Datos y saldos intactos; sección de tarjetas visible; sin cierres | Claude |
| 2026-11-18 | Recorrido de tarjetas: alta con deuda $2,500 → captura de estado (corte 15 nov, límite 5 dic, $2,500) → pago de $1,000 desde débito → compra de $300 con tarjeta | Emulador API 37 | Deuda $1,800, falta pagar $1,500, faltan 17 días, total en cuentas sin cambio por la compra a crédito. Captura: `docs/evidencia/2026-11-18-tarjetas-emulador.png` | Claude |
| 2026-11-19 | Estilo visual nuevo (claro y oscuro) y actualización de versionCode 2 a 3 con datos | Emulador API 37 | Datos intactos; capturas en `docs/evidencia/2026-11-19-*.png` | Claude |
| 2026-11-19 | Recordatorios: permiso desde la tarjeta "Activa los avisos"; alarma del 2 dic 9:00 con ventana +1 h; reloj movido a las 8:59:30 → aviso "Paga $1,500.00 de Nu credito antes del 5 dic" entregado a las 9:00:22 (22 s); pago del saldo → la alarma del 4 dic desaparece y no llega aviso; reinicio → alarma reprogramada sin abrir la app | Emulador API 37 | Todo correcto. Captura: `docs/evidencia/2026-11-19-aviso-de-pago.png` | Claude |
| 2026-11-19 | 15 pruebas instrumentadas, incluidas migraciones 1→2, 1→3 y 2→3 con tarjetas | Emulador API 37 | 15/15 pasan | Claude |
| 2026-11-19 | Pagos y cobros fijos: 75 pruebas del dominio (14 nuevas), 5 de la app en la JVM (4 del ViewModel), lint, compilación debug y release, guardia de permisos y verificación de dependencias | Computadora de desarrollo (sin dispositivo) | Todo pasa. **Pendiente en dispositivo:** migración 3→4 y pruebas de Room (compilan, sin ejecutar), recorrido de la sección nueva y capturas en claro, oscuro y texto al 130% | Claude |
| 2026-11-19 | 24 pruebas instrumentadas de Room, incluidas 7 de migración (1→2, 1→3, 2→3, 3→4 con datos, 3→4 sin duplicar categoría, 1→4 en cadena) | Samsung Galaxy S24 FE (Android 17), por depuración inalámbrica | Primera corrida: 2 fallas. Una era un bug real (la 3→4 duplicaba la categoría si ya existía; corregido en `bbf1897`) y otra una prueba desactualizada. Segunda corrida: 24/24 | Claude |
| 2026-11-19 | Actualización real de versionCode 4 a 5 con los datos del autor (base 3→4). Respaldo previo en `evidencia/privado/` | Samsung Galaxy S24 FE | Base en versión 4; 10 movimientos, 20 asientos, tarjeta y 2 estados idénticos; saldo de cada cuenta igual; suma de asientos 0; "Impuestos y predial" agregada una vez. La app abre sin cerrarse y muestra la sección nueva (captura con datos reales solo en `evidencia/privado/`) | Claude |
| 2026-11-19 | Disponible real y línea de tiempo: 88 pruebas del dominio (13 nuevas), 5 de la app, lint, compilación debug y release, guardia de permisos, verificación de dependencias | Computadora de desarrollo | Todo pasa. Pendiente: revisión visual en claro, oscuro y texto al 130% en el S24 FE | Claude |
| 2026-11-19 | Paso 5 revisado por dos subagentes en dos pasadas (lógica y diseño). Primera: 3 errores de cálculo y 4 problemas altos de diseño; segunda: 1 error nuevo (día más bajo en el pasado). Todo corregido. 96 pruebas del dominio y 8 de la app (104 en total), lint, compilación debug y release | Computadora de desarrollo | Todo pasa. Pendiente en el S24 FE: claro, oscuro, texto al 130 %, TalkBack (lectura de "−", "$", "·"), margen inferior de la hoja y si Android pone punto en "vie." o "dic." | Claude |

Hallazgos abiertos del recorrido en emulador:
- El botón flotante **Registrar** no expone su texto a accesibilidad (`NAF="true"` en
  uiautomator). Verificar con TalkBack antes de corregir.
- ~~Con el teclado abierto, **Guardar** de la hoja de captura queda debajo del teclado y
  hay que deslizar.~~ Resuelto el 2026-11-19: Guardar queda fijo sobre el teclado
  (`docs/evidencia/2026-11-19-guardar-sobre-teclado.png`).

Limitaciones de la prueba manual en el S24 FE: la hizo el autor, que conoce la app (no cuenta para
el oráculo de usabilidad de cinco personas), y no hay capturas de pantalla guardadas.
Las pruebas instrumentadas de Room (sección 3) siguen pendientes de ejecutarse en el
teléfono.
