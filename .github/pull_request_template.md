## Qué cambia

<!-- Una o dos frases. Enlaza el issue: "Cierra #123". -->

## Por qué

## Cómo se probó

- [ ] `./gradlew check` en verde
- [ ] Pruebas instrumentadas, si toca la base o la UI (`:core:data:connectedDebugAndroidTest`, `:app:connectedPrivadaPruebasAndroidTest`)
- [ ] Instalada encima de la versión anterior sin perder datos, si cambia el esquema

## Lista de revisión

- [ ] Si cambia el esquema: versión de Room, esquema exportado y prueba de migración
- [ ] Sin permiso `INTERNET` ni librerías de red fuera de `:feature:nube`
- [ ] Montos en centavos (`Dinero`), nunca `Double`
- [ ] Textos visibles en `strings.xml`
- [ ] Dependencia nueva: hash en `verification-metadata.xml` y razón en `docs/decisiones.md`
- [ ] Decisión de arquitectura anotada como `D-0xx`, si aplica
- [ ] Entrada en `CHANGELOG.md` bajo `[Sin publicar]`, si el usuario lo nota
- [ ] Capturas en claro y oscuro, si cambia la UI (sin datos reales)
