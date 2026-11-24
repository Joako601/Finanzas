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

## 2. Permisos por distribución (`privada` sin red, `nube` solo lo mínimo)

```bash
./gradlew :app:assemblePrivadaRelease :app:assembleNubeDebug check
~/Android/Sdk/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/privada/release/app-privada-release-unsigned.apk
~/Android/Sdk/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/nube/debug/app-nube-debug.apk
```

Esperado en `privadaRelease`, exactamente (paquete `io.github.joako601.finanzas`, sin `INTERNET`):

```
package: io.github.joako601.finanzas
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED'
permission: io.github.joako601.finanzas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='io.github.joako601.finanzas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Esperado en `nubeDebug`, exactamente (paquete `io.github.joako601.finanzas.nube.debug`, solo
`INTERNET` y `ACCESS_NETWORK_STATE` adicionales, sin `READ_GSERVICES`):

```
package: io.github.joako601.finanzas.nube.debug
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED'
uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
permission: io.github.joako601.finanzas.nube.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='io.github.joako601.finanzas.nube.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Las tareas `verificarPermisos<Variante>` comprueban esto automáticamente en `check` y dejan el
reporte en `app/build/reportes/permisos/<variante>.txt`.

Pendiente para la fase de auditoría: Exodus Privacy y MobSF sobre el APK firmado.

## 2b. Aislamiento de dependencias entre `privada`, `core:*` y `:feature:nube` (D-020)

```bash
./gradlew verificarDependencias
```

Contraprueba: agregar `implementation(libs.firebase.auth)` temporalmente a `core/data/build.gradle.kts`
y correr `./gradlew :core:data:verificarDependencias`. Debe fallar listando los artefactos de
Firebase y Play Services en `debugRuntimeClasspath` y `releaseRuntimeClasspath`. Revertir el cambio.

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
3. Instalar la versión nueva encima con `adb install -r app-privada-debug.apk`.
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
| 2026-11-19 | Paso 5 revisado por dos subagentes en dos pasadas (lógica y diseño). Primera: 3 errores de cálculo y 4 problemas altos de diseño; segunda: 1 error nuevo (día más bajo en el pasado). Todo corregido. 99 pruebas del dominio y 5 de la app (104 en total), lint, compilación debug y release | Computadora de desarrollo | Todo pasa. Pendiente en el S24 FE: claro, oscuro, texto al 130 %, TalkBack (lectura de "−", "$", "·"), margen inferior de la hoja y si Android pone punto en "vie." o "dic." | Claude |
| 2026-11-19 | Paso 5 en el S24 FE (versionCode 6): el autor lo revisó en su teléfono y le gustó. TalkBack pospuesto por el autor | Samsung Galaxy S24 FE | Aprobado por el autor. Sin capturas guardadas por Claude (el teléfono estaba bloqueado) | Autor |
| 2026-11-19 | Respaldo cifrado y CSV (D-018): 105 pruebas del dominio (6 nuevas), 9 de cifrado en la JVM, 13 de la app (8 nuevas), lint, compilación debug y release | Computadora de desarrollo | Todo pasa | Claude |
| 2026-11-19 | 31 pruebas instrumentadas de Room, 7 nuevas del respaldo, entre ellas el **simulacro de restauración** (respaldar → cambiar → restaurar → comparar fila por fila) y la restauración de un respaldo de la versión 3 | Samsung Galaxy S24 FE, paquete de prueba aparte (no toca la app del autor) | Primera corrida: 5 fallas por un nombre de categoría equivocado en la prueba y 1 por medir con tiempo virtual. Corregidas las pruebas, sin cambiar el código: 31/31. Recorrido y capturas: ver la fila siguiente | Claude |
| 2026-11-19 | v7 instalada como actualización (respaldo previo en `evidencia/privado/`). **Simulacro real hecho por el autor**: crear respaldo → gasto de prueba → restaurar. Luego Claude comparó la base del teléfono con la copia por adb de antes de instalar | Samsung Galaxy S24 FE | Base **idéntica** línea por línea (el gasto de prueba desapareció); sin temporales en `databases/`; sin cierres. Capturas en claro, oscuro y texto al 130 %: los botones con borde salían grises (estilo por omisión de Material 3) y se corrigieron a borde y texto `onSurface`, reinstalado y verificado. Capturas: `docs/evidencia/2026-11-19-respaldo-*.png`. TalkBack pendiente | Autor y Claude |
| 2026-11-19 | Paso 7: transferencias, editar movimientos y `targetSdk` 37 (D-019, D-009). 110 pruebas del dominio (5 nuevas), 9 de cifrado, 19 de la app (6 nuevas), lint sin advertencias, compilación debug y release | Computadora de desarrollo | Todo pasa | Claude |
| 2026-11-19 | 34 pruebas instrumentadas de Room, 3 nuevas de editar | Samsung Galaxy S24 FE, paquete de prueba aparte | 34/34. **Pendiente:** instalar la v8 y recorrer transferir, editar y eliminar desde la edición; revisar con TalkBack el botón Registrar | Claude |
| 2026-11-19 | Revisión de los pasos 6 y 7 por un subagente con skills (calidad, seguridad, TDD, diseño, accesibilidad), sin acceso al teléfono | Computadora de desarrollo | Sin hallazgos graves; 1 medio (respaldo fallido deja archivo vacío) y 10 menores. Corregidos todos menos uno, que se documentó como límite (avisos repetidos tras restaurar uno viejo). Pruebas de mutación: quitar `updateAAD` y quitar `\t\r` del CSV ahora hacen fallar las pruebas | Subagente y Claude |
| 2026-11-19 | Tras las correcciones: 112 pruebas del dominio, 10 de cifrado, 27 de la app en la JVM; lint sin advertencias | Computadora de desarrollo | Todo pasa | Claude |
| 2026-11-19 | 36 pruebas instrumentadas de Room, 2 nuevas del respaldo (no se reusan ids en un teléfono nuevo; se borran temporales huérfanos al arrancar) | Samsung Galaxy S24 FE, paquete de prueba aparte | 36/36 | Claude |
| 2026-11-19 | Prueba de accesibilidad de la pantalla principal (`AccesibilidadTest`, 4 casos) con el tipo de compilación `pruebas` | Samsung Galaxy S24 FE | Primera corrida: falló al arrancar por Espresso 3.5.0 (no corre en Android 17); se fijó 3.7.0. Segunda: no encontró la pantalla porque el teléfono estaba bloqueado. Tercera, desbloqueado: **encontró un error real**: el botón Registrar no tenía nombre en el árbol de accesibilidad (TalkBack diría solo "Botón"). Corregido en `64ac8f5`. Mi primera versión de la prueba medía el tamaño dibujado (40 dp) y marcaba en falso 6 botones; se cambió a leer `AccessibilityNodeInfo`, donde miden 48 dp. Final: 4/4. Sin el arreglo, 2 fallan. La app del autor no se tocó (sigue en versionCode 7) | Claude |
| 2026-11-19 | v8 instalada como actualización (respaldo previo en `evidencia/privado/`). Capturas de la pantalla principal en oscuro, claro y texto al 130 %, y del diálogo de transferencia | Samsung Galaxy S24 FE | Base **idéntica** antes y después; versionCode 8, targetSdk 37; abre sin cierres. "Transferir" junto a "Agregar cuenta"; nada cortado al 130 % ("Estado de cuenta" pasa a dos líneas). Capturas con datos del autor solo en `evidencia/privado/`. Se vieron dos detalles previos, anotados abajo | Claude |
| 2026-11-20 | v9: saldo a favor en la tarjeta y faltante que cuadra con el disponible. 117 pruebas del dominio (5 nuevas, una con el caso real del S24 FE), pruebas de la app y lint (solo avisos de versiones nuevas de AGP, Compose BOM y Lifecycle). Instalada como actualización con respaldo previo | Computadora y Samsung Galaxy S24 FE | Base idéntica antes y después; abre sin cierres. **Pendiente:** captura de la tarjeta Nu y del disponible (el teléfono estaba bloqueado) | Claude |
| 2026-11-20 | Registrador 0.2.0 reinstalado (el autor lo había desinstalado), con interruptor y botón de ajustes rápidos. Acceso a notificaciones concedido por adb | Samsung Galaxy S24 FE | Conectado. Sus ajustes se reiniciaron: Mercado Pago quedó marcado otra vez | Claude |
| 2026-11-20 | v10: las tarjetas se deslizan de lado con círculos de posición (`HorizontalPager` de Compose, sin dependencias nuevas). `CarruselTarjetasTest` (2 casos: deslizar cambia "Tarjeta 1 de 2" a "2 de 2" y de regreso; con una sola tarjeta no hay círculos) más `AccesibilidadTest` | Samsung Galaxy S24 FE, paquete de prueba aparte | 6/6 con letra al 0.9 y al 130 %. Al 130 % la prueba de 48 dp falló en falso: la fila de la transferencia quedaba cortada por la orilla de la pantalla y Android reporta solo la parte visible (26 dp). Ahora no mide lo cortado y exige al menos 5 elementos medibles | Claude |
| 2026-11-20 | v10 instalada como actualización (respaldos previos en `evidencia/privado/`). Capturas de ambas tarjetas en oscuro y en claro al 130 % | Samsung Galaxy S24 FE | Base **idéntica** antes y después (dos instalaciones); versionCode 10. El alto no salta al deslizar (el carrusel toma el de la tarjeta más alta). **Hallazgo en la captura:** en claro el círculo no elegido casi no se veía (`outlineVariant` sobre el fondo, ≈1.1:1). Corregido: gris medio (`onSurfaceVariant`, ≈4.8:1 en claro) y la tarjeta elegida se marca además con un círculo alargado, no solo con color. Las capturas también cierran el pendiente de la v9: la tarjeta Nu dice "Saldo a favor $950.00" y el disponible "El domingo 29 nov te faltarían $3,128.50" | Claude |
| 2026-11-20 | v11: todas las tarjetas del carrusel miden lo mismo aunque su texto no. Antes de dibujar se mide cada parte amarilla y cada parte de abajo al ancho real de una página (`SubcomposeLayout`) y todas toman el alto de la más alta; el disponible queda abajo del amarillo y los botones abajo de la tarjeta. Prueba nueva `las_tarjetas_miden_lo_mismo_aunque_su_texto_no` | Samsung Galaxy S24 FE, paquete de prueba aparte | **Rojo** sin igualar: amarillo de 142 dp contra 124 dp. Verde con el cambio: 7/7 al 0.9 y al 130 %. Las copias usadas para medir aparecen en el árbol de pruebas de Compose pero **no** en el árbol que recibe TalkBack (un solo nodo "Nu"); la comprobación se hace sobre ese árbol y una mutación que dibuja el nombre dos veces la hace fallar. El lector del árbol de accesibilidad quedó compartido en `ArbolAccesibilidad.kt` | Claude |
| 2026-11-20 | v11 instalada como actualización (respaldo previo en `evidencia/privado/`). Capturas de ambas tarjetas en oscuro y en claro al 130 % | Samsung Galaxy S24 FE | Base **idéntica** antes y después; versionCode 11. En las capturas coinciden el borde del amarillo, los botones y el borde inferior de ambas tarjetas, y ya no queda hueco bajo la más corta | Claude |
| 2026-11-21 | Hito 1 fase 1 (D-020, D-021): flavors `privada` y `nube`, módulo `:feature:nube` con Firebase Auth (BoM `35.2.0`), hashes SHA-256, `network_security_config`, guardia de dependencias, contrato `RepositorioSesion` + 5 pruebas JVM y placeholders de 4 pantallas. Contraprueba: agregar `firebase-auth` a `:core:data` hizo fallar `:core:data:verificarDependencias` | Computadora de desarrollo (sin dispositivo) | `./gradlew check :app:assemblePrivadaRelease :app:assembleNubeDebug` en verde con caché de configuración. `aapt2 dump permissions`: `privadaRelease` sin `INTERNET` (`io.github.joako601.finanzas`), `nubeDebug` solo con `INTERNET` y `ACCESS_NETWORK_STATE` (`io.github.joako601.finanzas.nube.debug`, `READ_GSERVICES` retirado). Pendiente: probar visualmente en dispositivo al completar las pantallas reales | Agente |
| 2026-11-24 | Auditoría de todo el repositorio (4 subagentes, `ponytail-audit`) y corrección de 19 de 20 hallazgos en 29 commits (`577626e..HEAD`). El 11 (política de contraseñas y restricción de la API key) se configura en la consola de Firebase. Verificación de cada commit por 3 subagentes: encontraron 1 error en un arreglo (`signOut` si fallaba el registro) y 4 detalles, todos corregidos | Computadora de desarrollo | `check`, `assemblePrivadaRelease`, `assembleNubeDebug` y compilación de todas las pruebas instrumentadas en verde. Pruebas nuevas comprobadas por mutación (fallan si se quita el arreglo) | Claude |
| 2026-11-24 | 86 pruebas instrumentadas de Room (2 nuevas: respaldo con una fila que el dominio rechaza; renombrar y ajustar saldo con un gasto entrando) y 28 de la interfaz de la app (`privadaPruebas`) | Samsung Galaxy S24 FE, paquete de prueba aparte | 86/86 y 28/28. Confirma que leer Flows de Room al validar el respaldo no se bloquea en el teléfono | Claude |
| 2026-11-24 | Build debug con las correcciones instalada como actualización (versionCode 11, sin cambios de esquema) | Samsung Galaxy S24 FE | Instalada; `finanzas.db` presente después. **Sin respaldo previo ni comparación antes/después**, a diferencia de las instalaciones anteriores. Pendiente: recorrido manual ("Hoy" a medianoche, menú → Bóveda, CSV con ajuste negativo) | Claude |
| 2026-11-24 | Kren (`69b186b`: nombre, logo, animación de entrada, catálogo remoto y divisas, traído del worktree de Antigravity) junto con las correcciones de la auditoría, instalado como actualización. La instalación anterior del mismo día (desde `main` sin Kren) había reemplazado la versión Kren del teléfono | Samsung Galaxy S24 FE | Respaldo previo en `evidencia/privado/respaldo-kren-20261124-112140`. Base **idéntica** antes y después: las 9 tablas con las mismas filas y la misma huella sha256 del contenido; `integrity_check` ok. La app abre como "Kren" con los datos (`evidencia/privado/2026-11-24-kren-principal-oscuro.png`) | Claude |

Hallazgos abiertos de la revisión de la v8 en el S24 FE (2026-11-19):
- ~~El disponible decía −$3,128.50 y el texto "Hoy te faltan $1,128.50"~~: el texto
  ahora agrega "El sáb 29 nov te faltarían $3,128.50" (v9, `LineaDeTiempo.faltanteMayor`).
- ~~Una tarjeta con saldo a favor mostraba "Debes −$950.00" y pedía pagar $1,000.00~~:
  ahora dice "Saldo a favor $950.00" y el estado de cuenta queda cubierto (v9).

Hallazgos abiertos del recorrido en emulador:
- ~~El botón flotante **Registrar** no expone su texto a accesibilidad (`NAF="true"` en
  uiautomator).~~ Era real: confirmado y corregido el 2026-11-19 con `AccesibilidadTest`.
  Falta escuchar con TalkBack cómo suenan "−", "$" y "·".
- ~~Con el teclado abierto, **Guardar** de la hoja de captura queda debajo del teclado y
  hay que deslizar.~~ Resuelto el 2026-11-19: Guardar queda fijo sobre el teclado
  (`docs/evidencia/2026-11-19-guardar-sobre-teclado.png`).

Limitaciones de la prueba manual en el S24 FE: la hizo el autor, que conoce la app (no cuenta para
el oráculo de usabilidad de cinco personas), y no hay capturas de pantalla guardadas.
Las pruebas instrumentadas de Room (sección 3) siguen pendientes de ejecutarse en el
teléfono.
