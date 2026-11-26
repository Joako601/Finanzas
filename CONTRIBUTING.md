# Cómo contribuir

Gracias por tu interés en Kren. Es un proyecto personal con el código publicado y
**todos los derechos reservados** (ver [LICENSE](LICENSE)). Por eso los reportes de
errores y las ideas son la mejor forma de ayudar. Si quieres enviar código, abre antes
un issue para platicarlo.

## Reportar un error o proponer una idea

1. Busca en los [issues](https://github.com/Joako601/Finanzas/issues) si ya existe.
2. Usa la plantilla que corresponda.
3. **Nunca incluyas datos financieros reales** (saldos, números de tarjeta, capturas con
   montos verdaderos). Usa el modo discreto o datos de prueba.

Las vulnerabilidades no van en issues públicos: sigue [SECURITY.md](SECURITY.md).

## Preparar el entorno

Requisitos y pasos en el [README](README.md#cómo-compilar). Antes de abrir un pull
request, esto debe pasar en verde:

```bash
./gradlew check
```

Si tu cambio toca la base de datos, también estas, en un dispositivo:

```bash
./gradlew :core:data:connectedDebugAndroidTest
./gradlew :app:connectedPrivadaPruebasAndroidTest
```

## Reglas del proyecto

Estas reglas existen para no perder los datos de nadie y no romper la privacidad:

- **Migraciones obligatorias.** Cada cambio de esquema sube la versión de Room, exporta
  el esquema a `core/data/schemas/` y agrega su prueba de migración. Nunca se usa
  `fallbackToDestructiveMigration` (D-012).
- **Sube el `versionCode`** de `app/build.gradle.kts` en cada versión que se instale.
- **El dominio es puro.** `:core:domain` no importa nada de Android ni de la JVM. El
  build lo comprueba al compilar para JS (D-004).
- **Dinero en centavos.** Nunca `Double` ni `Float` para montos (D-003).
- **Sin red en `privada`.** Nada de `INTERNET` ni librerías HTTP fuera de
  `:feature:nube` (D-005, D-020).
- **Dependencias nuevas.** Van en `gradle/libs.versions.toml`, con su hash en
  `gradle/verification-metadata.xml` y su razón (más la alternativa descartada) en
  `docs/decisiones.md`. Para regenerar los hashes:
  `./gradlew --write-verification-metadata sha256 help`, y revisa el diff.
- **Decisiones.** Una decisión de arquitectura nueva se anota como `D-0xx` en
  `docs/decisiones.md`, con fecha, contexto, decisión y consecuencias.
- **Textos.** Todo texto visible va en `strings.xml`, en español de México.

## Estilo de código

- Kotlin oficial (`kotlin.code.style=official`), con nombres de dominio en español.
- Funciones y clases pequeñas, con KDoc donde la regla de negocio no es obvia.
- Pruebas: Kotest en el dominio (`commonTest`) y JUnit 4 en Android.

## Commits y ramas

- Una rama por cambio a partir de `main`: `feat/…`, `fix/…`, `docs/…`.
- Mensajes con [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/),
  en español: `feat: compras a meses sin intereses`, `fix(tarjetas): …`.
- Si el cambio se nota para el usuario, agrégalo en `CHANGELOG.md` bajo
  `[Sin publicar]`.

## Publicar una versión (mantenedor)

1. Mueve `[Sin publicar]` a `[x.y.z] - AAAA-MM-DD` en `CHANGELOG.md`.
2. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
3. Instálala encima de la anterior en un teléfono real y anótalo en
   `docs/verificacion.md`.
4. Crea la etiqueta anotada `vX.Y.Z` y la GitHub Release con las notas del changelog.
