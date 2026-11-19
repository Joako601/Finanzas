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

## Registro de resultados

| Fecha | Qué | Dónde | Resultado | Quién |
|---|---|---|---|---|
| 2026-11-18 | Recorrido manual de 8 pasos: captura de gasto con teclado automático, saldo, cuenta nueva, ingreso, fecha anterior, rotación, borrado, mensajes de error | Samsung Galaxy S24 FE, APK debug instalado desde archivo | Todo funciona | Autor |
| 2026-11-18 | 13 pruebas instrumentadas de Room, incluida la migración 1→2 | Emulador API 37 (Medium Phone, Play Store) | 13/13 pasan | Claude |
| 2026-11-18 | Actualización real de versionCode 1 a 2 con datos (gasto de $85.50 y cuenta con $5,000) | Emulador API 37 | Datos y saldos intactos; sección de tarjetas visible; sin cierres | Claude |
| 2026-11-18 | Recorrido de tarjetas: alta con deuda $2,500 → captura de estado (corte 15 nov, límite 5 dic, $2,500) → pago de $1,000 desde débito → compra de $300 con tarjeta | Emulador API 37 | Deuda $1,800, falta pagar $1,500, faltan 17 días, total en cuentas sin cambio por la compra a crédito. Captura: `docs/evidencia/2026-11-18-tarjetas-emulador.png` | Claude |

Hallazgos abiertos del recorrido en emulador:
- El botón flotante **Registrar** no expone su texto a accesibilidad (`NAF="true"` en
  uiautomator). Verificar con TalkBack antes de corregir.
- Con el teclado abierto, **Guardar** de la hoja de captura queda debajo del teclado y
  hay que deslizar: suma un paso al camino de "dos o tres toques".

Limitaciones de la prueba manual en el S24 FE: la hizo el autor, que conoce la app (no cuenta para
el oráculo de usabilidad de cinco personas), y no hay capturas de pantalla guardadas.
Las pruebas instrumentadas de Room (sección 3) siguen pendientes de ejecutarse en el
teléfono.
