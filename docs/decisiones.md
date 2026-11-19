# Decisiones

Notas cortas, escritas al momento de decidir. Cada una dice qué se decidió, por qué,
qué se descartó y cómo se verifica. Si una decisión cambia, se agrega una nota nueva
que la reemplaza; no se borra la anterior.

---

## D-001 Construir por iteraciones en lugar de cerrar las siete decisiones primero

**Fecha:** 2026-11-16

**Decisión:** construir por módulos pequeños desde ya, en paralelo con la fase 0 de
evidencia, y ajustar sobre la marcha.

**Por qué:** mientras no haya otros usuarios, los únicos datos son los del autor; casi
todo se puede corregir barato hasta la primera versión pública.

**Qué se fija desde el inicio**, porque cambiarlo después cuesta mucho: dinero en
centavos, dominio multiplataforma, partida doble, sin permiso `INTERNET` y base
excluida de respaldos.

**Qué espera a tener evidencia:** reglas de ciclo de tarjeta y MSI (estados de cuenta
reales), lector de notificaciones (corpus), cifrado de la base (modelo de amenazas
antes de la primera prueba cerrada en Play).

---

## D-002 Partida doble

**Fecha:** 2026-11-16

**Decisión:** cada movimiento son asientos que suman cero. Las categorías son cuentas
de tipo `GASTO` o `INGRESO`; los saldos iniciales se contrapartida con una cuenta de
`CAPITAL`. La interfaz no muestra nada de esto.

**Por qué:** transferencias entre cuentas propias, pago de tarjeta desde débito,
devoluciones y MSI se resuelven con el mismo mecanismo, sin casos especiales en el
esquema. Pasar después de un modelo simple ("monto con signo y categoría") a partida
doble es la migración más dolorosa posible; al revés casi nunca hace falta.

**Descartado:** tabla única de movimientos con signo. Es más simple hoy, pero una
transferencia se vuelve dos filas enlazadas a mano y el pago de tarjeta, un caso especial.

**Cómo se verifica:** `Movimiento` no se puede construir si no cuadra (prueba
`movimiento_que_no_cuadra_no_se_puede_construir`); prueba de propiedades
`el_libro_siempre_cuadra`; prueba instrumentada `saldos_en_sql_coinciden_con_el_dominio`.

---

## D-003 Dinero como `Long` de centavos en una `value class`

**Fecha:** 2026-11-16

**Decisión:** `Dinero(centavos: Long)`, con rango simétrico (sin `Long.MIN_VALUE`),
suma con detección de desborde y analizador estricto de texto que nunca redondea en
silencio (`1.005` se rechaza).

**Descartado:** `Double` (no representa 0.10 exacto); `BigDecimal` (solo JVM).

**Cómo se verifica:** `DineroTest`, incluida la propiedad `formatear_y_parsear_son_inversos`.

---

## D-004 El dominio tiene dos objetivos (JVM y JS) para que la regla de pureza sea real

**Fecha:** 2026-11-16

**Hallazgo:** con un único objetivo JVM, `commonMain` compila como código JVM y
`java.math.BigDecimal` compila sin error. La premisa del plan original ("KMP con un
objetivo JVM hace fallar cualquier `java.*`") es falsa.

**Decisión:** agregar un objetivo `js()` sin entorno de ejecución, y hacer que `check`
dependa de `compileCommonMainKotlinMetadata`.

**Por qué sin entorno:** declarar `nodejs()` hace que Kotlin descargue Node.js y
paquetes de npm para correr pruebas. Lo bloqueó la regla `FAIL_ON_PROJECT_REPOS` de
settings, y está bien que lo bloquee. Las pruebas corren solo en la JVM.

**Descartado:** un objetivo nativo (`linuxX64`), que descarga el compilador de
Kotlin/Native (cientos de MB) solo para esta guardia.

**Cómo se verifica:** agregar a `commonMain` un archivo con
`java.math.BigDecimal.ONE` y correr `./gradlew :core:domain:check`. Debe fallar con
`Unresolved reference 'java'`. Sin la dependencia de `check`, NO falla (comprobado).

---

## D-005 Sin permiso `INTERNET`, con guardia en el build

**Fecha:** 2026-11-16

**Decisión:** el manifiesto no declara permisos. La tarea `verificarPermisos<Variante>`
(se ejecuta con `check`) lee el manifiesto fusionado y falla ante cualquier permiso
fuera de la lista permitida.

**Lista permitida:** solo `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`,
que declara androidx.core con nivel `signature`: solo nuestra propia app puede usarlo y
no da acceso a nada del sistema.

**Cómo se verifica:** ver `docs/verificacion.md`, sección de permisos (aapt2 sobre el
APK de release).

---

## D-006 Base excluida del respaldo en la nube Y de la transferencia entre teléfonos

**Fecha:** 2026-11-16

**Decisión:** `allowBackup="false"` más `dataExtractionRules` que excluye todos los
dominios en `cloud-backup` y `device-transfer`, más `fullBackupContent` para Android 11
y anteriores.

**Por qué las tres:** en Android 12+, `allowBackup="false"` no impide la transferencia
directa al cambiar de teléfono. Solo `dataExtractionRules` la impide.

**Consecuencia que hay que aceptar:** hoy, cambiar de teléfono o borrar datos de la app
significa perder los datos. Se resuelve con la exportación CSV y el respaldo cifrado
propio, antes de que alguien más use la app.

---

## D-007 Inyección de dependencias manual (Koin pospuesto)

**Fecha:** 2026-11-16

**Decisión:** un `Contenedor` escrito a mano en la app.

**Por qué:** con tres objetos, un contenedor manual se lee de un vistazo, no agrega
dependencias, y un error de cableado falla al compilar (Koin lo detecta al ejecutar).

**Se revisa cuando:** haya varios módulos `feature:*` o el contenedor pase de unas 15
líneas de cableado.

---

## D-008 Sin Navigation Compose por ahora

**Fecha:** 2026-11-16

**Decisión:** una sola pantalla con hoja inferior y diálogos. La captura en hoja
inferior, sobre la lista, ahorra la transición a otra pantalla.

**Se revisa cuando:** haya una segunda pantalla real (tarjetas, recordatorios).
Entonces se evalúa Navigation 3 frente a Navigation Compose.

---

## D-009 compileSdk 37, targetSdk 36 (37 desde el paso 7)

**Fecha:** 2026-11-16

**Decisión:** compilar contra el API 37, porque Compose UI 1.12 y Lifecycle 2.11 lo
exigen. Declarar `targetSdk` 36.

**Por qué no target 37:** el target activa los comportamientos nuevos de Android 17 en
teléfonos con Android 17, y hoy no hay uno donde probarlos. Lint lo marca como
advertencia (`OldTargetApi`) y se acepta.

**Pendiente de verificar contra la fuente oficial:** el requisito vigente de nivel de
API objetivo de Google Play, antes de la primera prueba cerrada.

**Actualización 2026-11-19 (paso 7):** `targetSdk` 37. Ya hay un teléfono con Android 17
(el S24 FE del autor). Se revisó la página oficial "Behavior changes: apps targeting
Android 17" (developer.android.com/about/versions/17/behavior-changes-17). Lo que podría
tocar a esta app:
- La app no fija orientación ni tamaño, así que no le afecta que ya no se pueda pedir
  que se respeten en pantallas grandes.
- Con teclado físico, los campos de contraseña del respaldo ocultan todos los caracteres.
  Es deseable.
- La app no usa reflexión, así que no le afectan el nuevo `MessageQueue` sin candados ni
  los campos `static final` que ya no se pueden modificar.

El resto no aplica: widgets, red local, SMS, contactos, audio en segundo plano,
Bluetooth, código nativo dinámico, Content Capture y Certificate Transparency (no hay
red). Las alarmas y notificaciones no cambian para el target 37. Lint ya no marca
`OldTargetApi`.

---

## D-010 Verificación de dependencias con SHA-256

**Fecha:** 2026-11-16

**Decisión:** `gradle/verification-metadata.xml` con el SHA-256 de cada artefacto,
y la distribución de Gradle fijada con `distributionSha256Sum` en el wrapper.

**Limitación honesta:** los hashes se generaron confiando en la primera descarga
("confiar en el primer uso"). Garantizan que las descargas futuras sean idénticas a las
de hoy, no que las de hoy fueran legítimas. Las firmas PGP no se verifican porque la
mayoría de los artefactos de Google Maven no están firmados.

**Al agregar o actualizar una dependencia:**
`./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease check :core:data:assembleDebugAndroidTest`
y se revisa la diferencia del XML en el commit. Al regenerar, Gradle borra los
comentarios del archivo: hay que restaurar el que explica `trusted-artifacts`.

---

## D-011 El usuario captura los datos de su estado de cuenta; la app no los calcula

**Fecha:** 2026-11-18

**Decisión:** cada ciclo, el usuario registra en la app los datos impresos en su estado
de cuenta: fecha de corte, fecha límite, pago para no generar intereses y pago mínimo.
La app los toma como verdad para los recordatorios. No se piden PDF.

**Por qué:**
- El dato capturado ES la fuente: no hay que replicar reglas de cada banco (días
  inhábiles, MSI, centavos sobrantes) para acertar al centavo.
- El cálculo de "pago para no generar intereses" con MSI, devoluciones y pagos
  anticipados deja de ser requisito de la primera versión de tarjetas.
- No se manejan PDF con datos personales de nadie.

**Lo que la app todavía tiene que predecir:** el recordatorio "antes del corte" llega
antes de que exista el estado de cuenta de ese ciclo. Se predice la siguiente fecha de
corte y de límite a partir de los ciclos capturados (o del día de corte que el usuario
indique al dar de alta la tarjeta), y la predicción se reemplaza por el dato real cuando
el usuario lo captura.

**Cómo se verifica:** cada captura deja guardada la predicción que había para ese ciclo.
El error de predicción (días de diferencia en corte y en límite) se mide con los datos
reales de cada usuario, dentro de la propia app, en lugar de con un corpus aparte.

**Costo aceptado:** cuatro campos por tarjeta cada mes. Se mitiga con un recordatorio
"tu estado de cuenta ya debió llegar" unos días después del corte predicho.

**Reemplaza:** el punto 2 de la fase 0 (estados de cuenta de voluntarios) deja de ser
necesario para la versión 0.1.

---

## D-012 Actualizar sin perder datos: migraciones obligatorias desde la versión 1

**Fecha:** 2026-11-18

**Contexto:** el autor ya usa la app con datos reales en su teléfono. Cada módulo nuevo
se instala como actualización sobre la anterior.

**Decisión:**
- Todo cambio de esquema de Room sube la versión de la base y trae su migración
  (automática de Room cuando alcance, escrita a mano cuando no), con una prueba que
  crea la base en la versión anterior, inserta datos, migra y los compara.
  `fallbackToDestructiveMigration` queda prohibido: borraría los datos en silencio.
- Cada módulo sube `versionCode` y `versionName`, para saber qué versión está instalada.
- La llave de firma de depuración (`~/.config/.android/debug.keystore`, SHA-256
  `02:97:04:74:BC:C2:DF:67:AA:91:92:90:96:14:BF:3D:85:96:08:ED:16:38:68:51:D1:3D:53:EB:CE:70:06:31`,
  vigente hasta 2056) se respalda. Si se pierde, Android rechaza la actualización por
  firma distinta y la única salida es desinstalar, que borra los datos.

**Cómo se verifica:** las pruebas de migración en `core:data`, y la actualización real
en el teléfono conservando los movimientos capturados.

**Se revisa cuando:** exista la llave de release para la prueba cerrada en Play. Esa
llave reemplaza a la de depuración y su custodia es otro tema (Play App Signing).

---

## D-013 Módulo de tarjetas: modelo y supuestos explícitos

**Fecha:** 2026-11-18

**Decisión:**
- La tarjeta es una cuenta de PASIVO del libro más una fila en `tarjeta` (línea, día de
  corte, día límite). Comprar con tarjeta es un gasto contra el pasivo; pagarla es un
  movimiento de activo a pasivo que NO cuenta como gasto (el gasto ya se registró al
  comprar). La deuda inicial es un saldo inicial negativo contra "Saldos iniciales".
- Cada estado de cuenta capturado guarda la predicción que había para ese ciclo (D-011).
- Lo que falta pagar = pago para no generar intereses − pagos posteriores al corte.
- Recordatorios: fuera de este módulo (permiso de notificaciones, alarmas, reinicio).

**Supuestos (sin fuente; se miden con las capturas reales, ver `CalendarioTarjeta.kt`
y `SituacionTarjeta.kt`):**
- **S1.** Si el mes no tiene el día de corte, el corte cae el último día del mes.
- **S2.** El límite es la primera fecha posterior al corte con el día límite indicado.
- **S3.** No se ajusta por días inhábiles; el banco puede recorrer fechas.
- **S4.** Un pago hecho el mismo día del corte ya quedó en ese estado de cuenta y no
  cuenta para el siguiente.
- **S5.** Un estado capturado hasta 5 días antes o después del corte predicho
  corresponde a ese corte.

**Descartado:** calcular el pago para no generar intereses a partir de las compras
(requiere MSI, devoluciones y reglas de cada banco; D-011 lo vuelve innecesario).

**Cómo se verifica:** `CalendarioTarjetaTest` (casos y propiedades de 2020 a 2035),
`TarjetasTest`, `RepositorioTarjetasRoomTest` y el recorrido en emulador de
`docs/verificacion.md`. Los supuestos S1–S5 se confirman o se corrigen comparando
`corte_predicho`/`limite_predicho` con las fechas reales capturadas.

---

## D-014 Identidad visual propia en lugar del color dinámico

**Fecha:** 2026-11-19

**Contexto:** el autor eligió como referencia dos pantallas con el lenguaje visual de
un banco colombiano (su logo, su amarillo y sus cintas de colores son marca registrada).

**Decisión:**
- Paleta fija en claro y oscuro: fondo gris muy claro con tarjetas blancas; amarillo
  como color de acción con texto negro; montos en verde (ingreso) y rojo (gasto);
  modo oscuro en gris carbón con íconos naranja. Reemplaza el "color dinámico" del
  stack original (colores tomados del fondo de pantalla).
- Se toma el estilo, no la marca: ni logos, ni cintas, ni el diseño de la tarjeta del
  banco. Una app financiera que se parece a un banco confunde sobre quién la hace y
  Google Play la rechaza por suplantación.
- Contraste: el amarillo nunca es texto sobre fondo claro. En claro, los textos de
  acción son casi negros y el amarillo solo es fondo (botón principal, botón flotante,
  selección).
- Íconos de categoría: 24 vectores copiados de Material Symbols (Apache 2.0), sin la
  librería de íconos. Se asignan por nombre de categoría; las categorías nuevas usan
  un ícono genérico hasta que exista edición de categorías.
- Sin fuente propia: la del sistema con más peso en títulos y cifras. Una fuente
  incluida en el APK queda como opción si hace falta.

**Cómo se verifica:** capturas en `docs/evidencia/2026-11-19-*.png` (claro, oscuro y
hoja de captura con teclado).

---

## D-015 Recordatorios con AlarmManager inexacto y plan recalculado

**Fecha:** 2026-11-19

**Decisión:**
- Tres avisos por tarjeta: un día antes del corte (9:00); dos días después del corte si
  falta capturar el estado de cuenta (19:00); tres días y un día antes de la fecha
  límite si falta pagar, con el monto exacto (9:00).
- El plan se recalcula con los datos del momento en cada ejecución: si ya pagaste, el
  aviso de pago desaparece aunque la alarma ya estuviera programada.
- Una sola alarma inexacta (`setAndAllowWhileIdle`) apunta siempre al siguiente aviso.
  Se reprograma al abrir la app, al cambiar los datos, al sonar la alarma, al reiniciar,
  al cambiar la hora o la zona horaria y al actualizar la app.
- Cada entrega se registra con hora programada y hora real (`recordatorio_entregado`):
  evita repetir avisos y es el oráculo de retraso (`herramientas/medir-recordatorios.sh`).
- Privacidad: el aviso es privado; en la pantalla de bloqueo solo se lee "Tienes un
  recordatorio de pago".
- Permisos nuevos: `POST_NOTIFICATIONS` y `RECEIVE_BOOT_COMPLETED`, agregados a la lista
  permitida de la guardia. Ninguno da red.

**Descartado:**
- WorkManager: trae su propia base Room y agrega `ACCESS_NETWORK_STATE`,
  `FOREGROUND_SERVICE` y `WAKE_LOCK` al manifiesto.
- Alarma exacta: `SCHEDULE_EXACT_ALARM` viene negado por defecto desde Android 14 y
  `USE_EXACT_ALARM` es solo para apps de alarma o calendario. Se reconsidera solo si la
  medición muestra retrasos inaceptables.

**Hallazgo:** Android asigna a la alarma inexacta una ventana de hasta **+1 hora**
(`dumpsys alarm`: `window=+1h0m0s`). Un aviso de las 9:00 puede llegar hasta las 10:00.
Para avisos de pago con días de anticipación parece aceptable; el retraso máximo
aceptable se fija con números en la decisión 3 (métricas).

**Supuesto S6:** lo comprado el día del corte entra en ese estado de cuenta.

**Cómo se verifica:** `PlanRecordatoriosTest` (12 pruebas, una de propiedades);
recorrido en emulador de `verificacion.md` §6; dos semanas de medición en el S24 FE.

---

## D-016 Pagos e ingresos fijos como plantillas que se registran con un toque

**Fecha:** 2026-11-19

**Decisión:**
- Un pago fijo (CFE, internet, predial) o un ingreso esperado (quincena, aguinaldo, PTU)
  es una **plantilla** (`recurrente`), no un movimiento. Guarda nombre, pago o ingreso,
  monto aproximado, categoría, periodicidad, fecha de inicio y próxima fecha pendiente.
- Registrar crea el movimiento con el monto, la cuenta y la fecha **reales** (el recibo de
  luz cambia cada bimestre) y avanza la próxima fecha, en una sola transacción.
  "Omitir esta vez" solo avanza la fecha. Eliminar la serie no toca los movimientos.
- Nada se registra solo: el libro solo contiene lo que el usuario confirmó.
- Periodicidades: cada N días (semanal, catorcenal), cada N meses el mismo día
  (mensual, bimestral, anual) y dos días fijos al mes (quincena: 15 y último día).
  La serie se calcula desde la fecha de inicio, así un 31 vuelve a ser 31 después de
  febrero.
- Si la fecha de alta ya pasó, la app toma la primera fecha de la serie desde hoy.
- Aviso un día antes de cada pago fijo a las 9:00, con el mismo programador de D-015.
  Los ingresos esperados no avisan.
- No hace falta saber el estado ni el municipio: el usuario captura la fecha de cada
  recibo. Sus recibos reales sirven para medir los supuestos, no para configurar.
- Nueva categoría "Impuestos y predial". La migración 3→4 la agrega a las bases
  existentes (al final del catálogo, sin duplicar).

**Supuestos:**
- S7. Si el mes no tiene el día, toca el último día del mes (igual que S1).
- S8. No se recorre por fines de semana ni días inhábiles; si la empresa paga el
  viernes antes, el usuario registra la fecha real y la serie sigue igual.

**Descartado por ahora:**
- Editar una serie (hoy: eliminar y volver a crear).
- Aviso para confirmar que llegó un ingreso.
- Sugerencias precargadas (CFE, Telmex, predial de tu municipio).
- Periodicidades fuera de la lista (por ejemplo, cada 3 meses).

**Cómo se verifica:** `RecurrentesTest` (14 pruebas, una de propiedades: la siguiente
fecha siempre es posterior y no se salta ninguna); `RecurrentesViewModelTest` (4, en la
JVM); `MigracionTest` 3→4 y `RepositorioRecurrentesRoomTest`, que compilan pero no se
han ejecutado en dispositivo (la computadora actual no soporta el emulador).

---

## D-017 Disponible real: el saldo proyectado más bajo de los próximos 30 días

**Fecha:** 2026-11-19 (revisada el mismo día tras la revisión de dos subagentes)

**Contexto:** el autor pospuso MSI (paso 4) porque todavía no lo usa; el paso 5 va antes.

**Decisión:**
- **Disponible real** = lo que se puede gastar hoy sin quedar en negativo ningún día de
  los próximos 30, contando los pagos y los ingresos que se esperan. Es el saldo
  proyectado más bajo, y nunca más que lo que hay hoy en efectivo y débito (un ingreso
  futuro no se gasta hoy).
- La primera versión ("cuentas menos lo que vence antes del próximo ingreso") se
  descartó por dos casos reales que encontró la revisión: un reembolso chico cerraba el
  plazo y escondía la renta, y la quincena de hoy sin registrar dejaba el disponible en
  rojo. La definición nueva resuelve los dos sin reglas especiales.
- Por pagar:
  - **Tarjetas:** el último estado de cuenta vencido sin pagar (si no hay uno vigente
    que ya lo incluya), lo que falta del vigente, y el estimado del primer ciclo sin
    capturar. Entre todos nunca suman más que la deuda.
  - **Pagos fijos:** cada fecha dentro de los 30 días, incluidos los vencidos sin
    registrar.
- **Línea de tiempo de 30 días:** pagos de tarjeta, pagos fijos, cortes e ingresos
  esperados, con el saldo que quedaría después de cada uno. El mismo día va primero lo
  que sale. Avisa el primer día en que no alcanzaría (nunca con fecha pasada).
- Es solo cálculo sobre los datos existentes: sin tablas nuevas ni migración.
- **Interfaz:**
  - El disponible es la cifra principal de la tarjeta de saldos. Debajo va su cuenta
    ("$X en cuentas − $Y para pagos"), el día del saldo más bajo y el acceso a los
    próximos 30 días.
  - La hoja divide lo que define el disponible de lo que viene después, y cada pago se
    puede registrar desde ahí.
  - El pago de tarjeta va sin signo y en color secundario, como en la lista de
    movimientos (diseno-finanzas, sección 4).

**Supuestos:**
- S9. Sin estado de cuenta capturado, el pago de la tarjeta se estima con la deuda que no
  cubren los estados vencido y vigente. Exagera con compras a MSI (hasta el paso 4) y se
  queda corto con las compras que todavía no se hacen.
- S10. Los pagos fijos cuentan por su monto aproximado.
- S11. Un ingreso cuya fecha ya pasó sin registrarse no se cuenta: no se sabe si llegó.
- S12. Los ingresos esperados llegan en su fecha. Si se retrasan, el disponible sale más
  alto de lo que conviene hasta registrarlos u omitirlos.

**Descartado por ahora:** proyectar más de 30 días; elegir el plazo a mano; avisos por
notificación cuando el saldo proyectado quede en negativo.

**Límites conocidos, decididos a propósito** (revisión de dos subagentes):
- Un pago fijo semanal olvidado acumula una salida vencida por semana. Es coherente con
  S10, porque cada una sigue pendiente. Si molesta, se agrupan en una fila.
- Un pago fijo cobrado con tarjeta (Netflix) cuenta como salida de débito en su fecha,
  no en la fecha límite de la tarjeta: `Recurrente` no sabe con qué se paga. Se corrige
  si se agrega esa columna.
- Si un ciclo sin capturar ya pasó su fecha límite, su estimado aparece en el siguiente
  límite y no como vencido. El total sí suma la deuda.
- Tocar una fecha futura de un pago fijo no hace nada: solo la pendiente se registra.

**Cómo se verifica:** `LineaDeTiempoTest` (21 pruebas, con los casos de la revisión). La
de propiedades comprueba que una tarjeta nunca pide más que su deuda, que el disponible
no supera lo de hoy ni ningún saldo proyectado, y que es negativo justo cuando hay
faltante. Quitar el tope por deuda la hace fallar. La revisión visual en el S24 FE sigue
pendiente.


## D-018 Respaldo cifrado con contraseña propia y exportación a CSV

**Fecha:** 2026-11-19

**Contexto:** D-006 deja la base fuera del respaldo de Google y de la transferencia entre
teléfonos, así que perder o cambiar el teléfono era perder los datos. Este paso cierra
ese hueco antes de que alguien más use la app. El autor aprobó las cinco
recomendaciones.

**Decisión:**
- **Respaldo cifrado** de toda la base: cuentas, movimientos, tarjetas, estados de
  cuenta, pagos fijos y avisos entregados.
  - La llave sale de una **contraseña del usuario**: PBKDF2-HMAC-SHA256 con 600 000
    vueltas (la cifra de OWASP) y sal aleatoria. El cifrado es AES-256-GCM. No se usa una
    llave del Keystore porque muere con el teléfono, que es justo el caso a cubrir.
  - Contraseña de 8 caracteres o más, escrita dos veces. Si se olvida, ese respaldo no se
    puede abrir: no hay servidor ni recuperación.
  - Formato propio: `"FINANZAS"`, la versión del formato, las vueltas, la sal, el IV y el
    contenido cifrado. El encabezado va autenticado, así que cualquier byte cambiado se
    detecta. Las vueltas se aceptan solo entre 100 000 y 10 millones, para que un archivo
    manipulado no congele la app.
  - Adentro va una base SQLite completa con el esquema actual. Se arma copiando las filas
    dentro de una transacción, porque el archivo de Room solo, en modo WAL, no trae los
    cambios recientes. `VACUUM INTO` lo resolvería, pero llega hasta Android 11 y el mínimo
    de la app es Android 8.
- **Restaurar:**
  - Revisa la integridad (`PRAGMA integrity_check`) y la versión del esquema.
  - Rechaza un respaldo de una versión más nueva de la app.
  - Migra con las migraciones de Room un respaldo de una versión anterior.
  - Reemplaza **todo** en una sola transacción: o queda el respaldo completo o no cambia
    nada. Las pantallas abiertas se actualizan solas.
  - Pide confirmar con un botón rojo, "Reemplazar mis datos".
- **Exportar a CSV:**
  - Es para leer en Excel o Google Sheets, no para recuperar datos.
  - Una fila por movimiento: fecha, tipo, descripción, cuenta, categoría o destino (la tarjeta pagada o la cuenta que recibe) y monto.
  - UTF-8 con BOM, coma como separador y punto decimal.
  - Los textos que empiezan con `= + - @` llevan un apóstrofo delante para que Excel no los
    ejecute como fórmula (inyección de CSV de OWASP). Eso importará cuando las
    descripciones lleguen de notificaciones del banco.
  - La app avisa que el archivo va sin cifrar.
- Los archivos se eligen con el **selector de Android** (Storage Access Framework): el
  usuario guarda en Descargas, Drive o donde quiera. **Sin permisos nuevos**: ni
  almacenamiento ni internet.
- Sin respaldos automáticos: sin internet no hay a dónde mandarlos solos. La app muestra la
  fecha del último respaldo y, si no hay ninguno, avisa que se pierden los datos con el
  teléfono.
- Acceso desde un menú de tres puntos en la barra superior; no toca el camino corto de
  registro.
- Sin dependencias nuevas: todo el cifrado viene en Android.

**Descartado:**
- Argon2id: resiste mejor la fuerza bruta con tarjetas gráficas, pero necesita una
  librería nativa.
- Respaldos automáticos a una carpeta: seguirían en el mismo teléfono.
- Un respaldo automático de lo actual antes de restaurar: más complejidad. Hoy lo cubre el
  aviso de confirmación.
- Mostrar u ocultar la contraseña mientras se escribe: lo cubre escribirla dos veces.

**Límites conocidos:**
- Todo en memoria: un respaldo de decenas de MB necesitaría flujos. Hoy pesa kilobytes.
- Mientras se restaura, el archivo descifrado vive unos segundos en la carpeta privada de la
  app y se borra al terminar, igual de protegido que la base, que tampoco está cifrada
  (SQLCipher se decide con el modelo de amenazas). Si el proceso muere a mitad, queda ahí
  hasta el siguiente arranque de la app, que lo borra.
- Restaurar un respaldo viejo reemplaza también el registro de avisos entregados: puede
  repetirse un aviso de hoy o de ayer que ya se había mostrado. Se acepta; conservar las
  entregas recientes complicaría la restauración por un aviso duplicado.
- La contraseña pasa por un `String` de Compose y no se puede borrar de memoria a mano.

**Cómo se verifica:**
- `ExportacionCsvTest`: 6 pruebas, entre ellas acentos, comillas, saltos de línea y fórmulas.
- `CifradoRespaldoTest`: 9 pruebas en la JVM. Cubre la ida y vuelta con las vueltas reales,
  la contraseña equivocada, un byte cambiado en el encabezado, en el contenido y en la
  etiqueta, un archivo cortado, un archivo que no es respaldo, las vueltas fuera de rango y
  una versión nueva del formato.
- `RespaldoTest`: 7 pruebas en el S24 FE.
  - El **simulacro**: respaldar, cambiar datos, restaurar y comparar fila por fila.
  - Las pantallas abiertas se actualizan.
  - Con contraseña equivocada o una base inválida no cambia nada.
  - Un respaldo de la versión 3 se migra.
  - Un respaldo de una versión más nueva se rechaza.
  - No quedan temporales sin cifrar.
- `RespaldoViewModelTest`: 8 pruebas.

**Revisión de un subagente (2026-11-19)** con las skills code-review-and-quality,
security-and-hardening, test-driven-development, diseno-finanzas, impeccable y
accessibility-engineering. No encontró nada grave. Lo que se corrigió:
- Un respaldo fallido dejaba un archivo vacío en el destino. Ahora se borra y se avisa;
  también si Android cerró la app mientras se elegía la carpeta.
- Los contadores de ids (`sqlite_sequence`) no viajaban en el respaldo. En un teléfono
  nuevo se habrían reusado ids de lo borrado.
- Los temporales descifrados se borran también al arrancar, y la copia se cierra ante
  cualquier error.
- La base de la app y las copias de respaldo comparten la configuración de Room, para que
  una migración manual futura no falte al restaurar.
- Quitar la autenticación del encabezado no lo detectaba ninguna prueba. Ahora hay una que
  arma el mismo sobre con y sin ella; quitar `updateAAD` la hace fallar.
- El CSV tiene prueba para textos que empiezan con tabulador o retorno.
- El aviso de la hoja ya no queda viejo al reabrirla.
- TalkBack anuncia los errores de los campos.


## D-019 Editar movimientos y transferir entre cuentas propias

**Fecha:** 2026-11-19

**Decisión:**
- **Transferencia** = dinero que pasa entre dos cuentas propias de efectivo o débito
  (retirar del cajero, mover a otra cuenta). Baja el origen y sube el destino. No es gasto
  ni ingreso y no cambia el total en cuentas ni el disponible real. Pasar dinero a una
  tarjeta sigue siendo un pago de tarjeta (D-013), no una transferencia.
  - Se abre con "Transferir", junto a "Agregar cuenta". Solo aparece con dos cuentas o
    más; no toca el camino corto de registro.
  - En la lista va sin signo y en color secundario (`diseno-finanzas`, sección 4), con
    "Débito → Efectivo" debajo. En el CSV, la cuenta es el origen y la columna "Categoría
    o destino" el destino.
- **Editar** = tocar un gasto, ingreso o transferencia abre su formulario con los datos.
  - Al guardar se reemplazan fecha, nota y asientos en una transacción.
  - El movimiento conserva su id y su lugar entre los del mismo día (`creado_en` no
    cambia).
  - Si el movimiento ya se borró, falla sin crear asientos sueltos.
  - Al editar no aparece el teclado solo: lo que se corrige puede ser la categoría.
  - "Eliminar" queda dentro del formulario y pide la misma confirmación de siempre.
- Los saldos iniciales y los pagos de tarjeta siguen sin editarse: tocarlos ofrece
  eliminar, como antes. Un pago de tarjeta tiene su propio diálogo y editarlo cambiaría
  la situación del ciclo; se agrega si hace falta.

**Cómo se verifica:**
- `TransferenciaTest`: 5 pruebas, una de propiedades: una transferencia nunca cambia el
  total en cuentas.
- `RepositorioLibroRoomTest`: 3 pruebas nuevas en el S24 FE.
  - Editar conserva el id y el lugar, y no deja asientos del original.
  - Si falla, no cambia nada.
  - No se puede editar un movimiento borrado.
- `MovimientosViewModelTest`: 10 pruebas.

**Tras la revisión del subagente:**
- Al editar, la cuenta o categoría archivada del movimiento sigue disponible y se conserva.
- Una "transferencia" que toca una tarjeta (disposición de efectivo) no se abre en el
  formulario de transferencia; solo se puede eliminar.
- La fila se lee "de Débito a Efectivo" con TalkBack, en lugar de "flecha derecha".
- La hoja de captura y el diálogo de transferencia dicen "Editar" al editar.

---

## Dependencias y su razón

| Dependencia | Para qué | Alternativa descartada |
|---|---|---|
| kotlinx-coroutines-core | `Flow` en los puertos del dominio | Callbacks: no componen; ya viene con Room y Compose, no agrega peso |
| kotlinx-datetime | `LocalDate` en código común | `java.time`: no existe fuera de la JVM |
| Room (runtime y compiler) | Base SQLite con consultas validadas al compilar | SQLDelight: también es KMP, pero ya está decidido Room en el stack y Room tiene mejor soporte en Android |
| Compose BOM, material3, ui | Interfaz | Vistas XML: más código y sin color dinámico simple |
| activity-compose | `setContent`, edge-to-edge | Ninguna razonable |
| lifecycle-viewmodel-compose, lifecycle-runtime-compose | ViewModel en Compose; `collectAsStateWithLifecycle` deja de leer la base cuando la app no está visible | `collectAsState`: sigue consumiendo en segundo plano |
| kotest-property (solo pruebas) | Pruebas basadas en propiedades | Generadores escritos a mano |
| kotlinx-coroutines-test (solo pruebas) | `runTest` para funciones suspend | `runBlocking`: sin control del tiempo virtual |
| androidx.test runner y ext-junit (solo pruebas) | Pruebas instrumentadas de Room en el teléfono | Robolectric: otra dependencia grande, y no es SQLite real del teléfono |
| kotlinx-coroutines-test en la app (solo pruebas) | `runTest` y `Dispatchers.setMain` para probar ViewModels en la JVM | Ya estaba en el catálogo para el dominio; no agrega archivos |
| junit 4 (solo pruebas) | Pruebas de la app en la JVM, sin teléfono (por ahora: que cada categoría del catálogo tenga ícono). Ya estaba en el grafo verificado por ext-junit; no agrega archivos | kotlin-test-junit: otro artefacto para lo mismo |
| room-testing (solo pruebas) | `MigrationTestHelper`: crea la base en una versión anterior desde su esquema exportado, migra y valida contra el esquema nuevo (D-012) | Probar la migración con SQL suelto: no compara contra el esquema que Room espera |
| espresso-core 3.7.0 (solo pruebas) | Lo usan por dentro las pruebas de Compose. La versión que traen (3.5.0) llama por reflexión a `InputManager.getInstance`, que ya no existe en Android 17: fallaba antes de probar nada en el S24 FE. Se fija la estable más nueva | Bajar el `targetSdk` o probar en otro teléfono: no arregla nada |
| compose ui-test-junit4 y ui-test-manifest (solo pruebas) | Prueba de accesibilidad de la pantalla principal en el teléfono: revisa el árbol de semántica que lee TalkBack (nombre y 48 dp de todo lo tocable). Arrastra Espresso y Hamcrest, también solo de pruebas. Corre con el tipo de compilación `pruebas` (paquete `.pruebas`) porque las pruebas instrumentadas desinstalan la app al terminar | Robolectric: corre en la JVM pero descarga Android completo por su cuenta, fuera de la verificación de dependencias; Accessibility Scanner: manual |
| material-icons | **No se usa**: el único ícono se copió como vector XML | — |
