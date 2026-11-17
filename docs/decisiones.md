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

## D-009 compileSdk 37, targetSdk 36

**Fecha:** 2026-11-16

**Decisión:** compilar contra el API 37, porque Compose UI 1.12 y Lifecycle 2.11 lo
exigen. Declarar `targetSdk` 36.

**Por qué no target 37:** el target activa los comportamientos nuevos de Android 17 en
teléfonos con Android 17, y hoy no hay uno donde probarlos. Lint lo marca como
advertencia (`OldTargetApi`) y se acepta.

**Pendiente de verificar contra la fuente oficial:** el requisito vigente de nivel de
API objetivo de Google Play, antes de la primera prueba cerrada.

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
y se revisa la diferencia del XML en el commit.

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
| material-icons | **No se usa**: el único ícono se copió como vector XML | — |
