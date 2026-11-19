# Hoja de ruta

Estado al 2026-11-19 (noche). Se actualiza al cerrar cada módulo. El detalle de cada decisión
está en `decisiones.md` y la evidencia en `verificacion.md`.

## Hecho

| Módulo | Versión | Evidencia |
|---|---|---|
| Base del proyecto: Gradle, catálogo de versiones, verificación de dependencias, dominio multiplataforma con guardia de pureza | — | D-004, D-010 |
| Ingresos y egresos manuales con partida doble, cuentas de efectivo y débito | versionCode 1 | 23 pruebas de dominio, 7 de Room, prueba manual en el S24 FE |
| Tarjetas de crédito: alta, compras, pagos, captura de estado de cuenta, situación del ciclo | versionCode 2 | 48 pruebas de dominio, 13 de Room con migración 1→2, recorrido en emulador |
| Identidad visual propia en claro y oscuro; Guardar fijo sobre el teclado | versionCode 3 | Capturas en `evidencia/`, instalada en el S24 FE |
| Registrador de notificaciones (herramienta de la fase 0) | — | Instalado en el S24 FE el 2026-11-19; falta que el autor conceda el acceso |
| Recordatorios: antes del corte, capturar estado de cuenta, pago con monto | versionCode 4 | 12 pruebas del plan, 15 de Room, recorrido con reloj movido y reinicio en emulador; falta la medición de dos semanas en el S24 FE |
| Disponible real y línea de tiempo de 30 días (D-017) | versionCode 6 | 24 pruebas de dominio; sin cambios en la base; revisada por el autor en el S24 FE (TalkBack pendiente) |
| Respaldo cifrado con contraseña, restauración con simulacro y exportación a CSV (D-018) | versionCode 7 | 15 pruebas en la JVM, 7 de Room en el S24 FE con el simulacro, 8 del ViewModel; instalada en el S24 FE y simulacro real hecho por el autor |
| Editar movimientos, transferencias entre cuentas propias y `targetSdk` 37 (D-019) | versionCode 8 | 5 pruebas de dominio, 3 de Room en el S24 FE, 6 del ViewModel; falta instalarla |
| Pagos y cobros fijos: periodicidades, registro de un toque, aviso un día antes, categoría de impuestos (D-016) | versionCode 5 | 14 pruebas de dominio, 4 del ViewModel, 24 de Room en el S24 FE; instalada en el S24 FE con migración 3→4 verificada sobre datos reales |

## Siguiente, en orden

1. ~~Instalar el registrador de notificaciones~~ (hecho; corre dos semanas).
2. ~~Recordatorios~~ (hecho; corre la medición de dos semanas con
   `herramientas/medir-recordatorios.sh`).
3. ~~Pagos recurrentes e ingresos esperados~~ (hecho, D-016; instalado en el S24 FE).
4. **Compras a MSI** (pospuesto el 2026-11-19: el autor todavía no lo usa; el disponible
   real trata una compra a meses como deuda completa hasta entonces, S9): repartidas en los estados de cuenta siguientes; el gasto cuenta
   el mes de la compra y el flujo, mes a mes.
5. ~~Línea de tiempo de 30 días y "disponible real"~~ (hecho, D-017).
6. ~~Exportación CSV y respaldo cifrado~~ (hecho, D-018; simulacro real en el S24 FE).
7. ~~Pequeños pendientes~~ (hecho: editar movimientos, transferencias, `targetSdk` 37;
   D-019). Queda verificar con TalkBack el botón Registrar, pospuesto por el autor.

Con eso queda completo el alcance de la **versión 0.1**.

## Antes de la prueba cerrada en Google Play

- Modelo de amenazas (STRIDE, LINDDUN, MASVS L1 + privacidad) y casos del MASTG con
  evidencia.
- Decidir SQLCipher y el diseño de llaves (Keystore, recuperación).
- Métricas numéricas aprobadas: tiempo para registrar un gasto, arranque en frío en
  gama baja, retraso máximo de un recordatorio.
- Prueba de usabilidad con cinco personas.
- Plan de pruebas por marca con datos de mercado de México.
- Cómo se cobra sin romper "sin internet" y qué reciben los usuarios de Huawei.

## Versión 0.2: captura automática

Lector de notificaciones diseñado a partir del corpus del registrador, con movimientos
pendientes de confirmar con un toque.

## Fase 0 de evidencia (en paralelo)

| Material | Estado |
|---|---|
| Notificaciones bancarias reales | Registrador instalado; captura en curso desde que se conceda el acceso |
| Estados de cuenta | Reemplazado por la captura dentro de la app (D-011) |
| Regulación y contratos (Banxico, CONDUSEF, CNBV) | Pendiente |
| Recibos de servicios | Pendiente; necesario para el paso 3 |
| Reseñas de competidores y mercado de teléfonos | Pendiente; necesario antes de Play |
| Gastos propios | Se están capturando en la app |
