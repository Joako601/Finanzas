# Cambios

Aquí se anotan todos los cambios importantes de Kren.

El formato sigue [Keep a Changelog 1.1.0](https://keepachangelog.com/es-ES/1.1.0/) y
las versiones siguen [Versionado Semántico 2.0.0](https://semver.org/lang/es/). El
`versionCode` de Android aparece en cada versión para relacionarla con
[docs/verificacion.md](docs/verificacion.md).

## [Sin publicar]

## [1.0.0] - 2026-11-26

Primera versión estable. `versionCode` 12, base de datos v7. Instalada encima de la
versión anterior en un Samsung Galaxy S24 FE (Android 17) sin perder datos.

### Agregado

- Compras a meses sin intereses (3, 6, 9, 12, 18 y 24 meses), con la mensualidad
  repartida en cada corte (D-022).
- Bienvenida del primer día: tres pasos que abren las pantallas para empezar. Aparece una
  sola vez y solo si el historial está vacío.
- Búsqueda y filtros en Inicio por tipo, cuenta y categoría.
- Análisis: flujo del mes, gasto por categoría, curva de liquidez y patrimonio neto,
  comparados con el periodo anterior (D-023).
- Metas de ahorro con historial de aportes y retiros, y topes de gasto por categoría con
  aviso desde el 80 % (D-024).
- Categorías propias con ícono.
- Perfil: privacidad y seguridad (modo discreto, bloqueo con el PIN del teléfono,
  pantallas protegidas y borrar todos los datos), cuentas y tarjetas archivadas, tema
  claro, oscuro o del sistema, horas de los avisos y Acerca de.
- Tasa anual guardada en cada tarjeta y cuenta preferida en los pagos fijos.
- Distribución `nube`: inicio de sesión en dos pasos con correo o Google, registro y
  recuperación de contraseña. Se instala aparte y no toca la base local (D-020, D-021).
- Identidad Kren: ícono, pantalla de inicio animada y navegación inferior.

### Cambiado

- El disponible real descuenta las mensualidades a meses que todavía no se facturan, en
  lugar de tratar toda la compra como deuda de un solo ciclo.
- Las tarjetas archivadas ya no proyectan pagos en la línea de tiempo.
- Crear una meta con dinero apartado lo anota en el historial en la misma transacción.

### Corregido

- Los avisos de pagos fijos del día anterior que no se entregaron se vuelven a programar.
- El pago mínimo ya no puede ser mayor que el pago para no generar intereses.
- El respaldo rechaza archivos sin la cuenta de capital o con tipos de cuenta inválidos.
- Los nombres de cuentas, categorías y metas se limitan a 60 caracteres.

### Seguridad

- Cliente REST de la distribución `nube`: ante un `401`, un solo refresco coordinado del
  token de Firebase en lugar de reintentos en paralelo.
- La guardia de dependencias permite Credential Manager y `googleid` solo en `nube`.

## Versiones internas (0.x)

Antes de la 1.0, cada módulo se instaló como actualización en el teléfono del autor. Esas
versiones no tienen etiqueta en git.

| versionCode | Fecha | Contenido |
|---|---|---|
| 11 | 2026-11-20 | Carrusel de tarjetas con círculos de posición |
| 9–10 | 2026-11-20 | Saldo a favor en la tarjeta |
| 8 | 2026-11-19 | Editar movimientos, transferencias entre cuentas propias, `targetSdk` 37 (D-019) |
| 7 | 2026-11-19 | Respaldo cifrado, restauración con simulacro y exportación a CSV (D-018) |
| 6 | 2026-11-19 | Disponible real y línea de tiempo de 30 días (D-017) |
| 5 | 2026-11-19 | Pagos y cobros fijos (D-016) |
| 4 | 2026-11-19 | Recordatorios de corte y de pago (D-015) |
| 3 | 2026-11-19 | Identidad visual propia en claro y oscuro (D-014) |
| 2 | 2026-11-18 | Tarjetas de crédito y estados de cuenta (D-013) |
| 1 | 2026-11-18 | Ingresos y egresos manuales en partida doble |

[Sin publicar]: https://github.com/Joako601/Finanzas/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Joako601/Finanzas/releases/tag/v1.0.0
