# Registrador de notificaciones (fase 0)

Herramienta desechable para juntar el corpus de notificaciones bancarias reales antes
de diseñar el lector de la versión 0.2. **Nunca se publica**: solo existe la variante
debug y se instala por USB.

## Qué responde

- Qué bancos mandan monto y comercio, y cuáles mandan avisos genéricos sin datos.
- Dónde viene el texto: `android.text`, `android.bigText`, `android.textLines`
  (notificaciones agrupadas) u otro extra.
- Si un mismo aviso se actualiza (la misma `clave` aparece varias veces).
- Si Android oculta el contenido de algún aviso por considerarlo sensible. La API
  pública no permite preguntarlo (verificado en el SDK 37: `Ranking` no tiene
  `hasSensitiveContent()`), así que se detecta viendo el texto que llega.
- Cuánto tiempo estuvo el servicio desconectado por el ahorro de batería.

## Instalar (con el teléfono conectado por USB)

```bash
./gradlew :herramientas:registrador-notificaciones:installDebug
```

En el teléfono:

1. Abrir **Registrador de notificaciones** → **Conceder acceso** → activar el interruptor.
   Si Android dice que el ajuste está restringido: Ajustes → Aplicaciones → Registrador
   → menú ⋮ → "Permitir ajustes restringidos", y repetir. (Al instalar por adb no
   debería pasar; si pasa, anotarlo como hallazgo.)
2. **Batería: poner "Sin restricciones"** → Batería → Sin restricciones.
3. En la lista de apps, revisar que Nu, BBVA y Mercado Pago aparezcan con su nombre.
   Si alguna dice "(no instalada o nombre desconocido)" teniéndola instalada, el
   nombre de paquete supuesto está mal: esperar a que esa app mande una notificación,
   buscarla en la lista y activarla.

## Durante las dos semanas

Usar el teléfono normal: compras, transferencias, devoluciones, rechazos, pago de
tarjeta. Una vez por semana:

```bash
herramientas/registrador-notificaciones/exportar.sh
```

y tomar capturas de pantalla de la lista de movimientos de cada app bancaria de esa
semana. Esas capturas son la verdad contra la que se etiqueta el registro.

## Privacidad

- El registro contiene el texto completo de las notificaciones de las apps activadas,
  incluidos códigos de verificación si el banco los manda por notificación.
- Vive en el almacenamiento privado de la app, excluido de respaldos y transferencias.
- `exportar.sh` lo copia a `evidencia/privado/`, que git ignora, con permisos 600.
- Al terminar la fase 0: **Borrar registro** en la app y desinstalarla.

## Formato

JSON Lines, una línea por evento (`v` = versión del formato). Eventos: `publicada`,
`retirada`, `activa_al_conectar`, `conectado`, `desconectado`. Ver
`src/main/kotlin/.../Bitacora.kt`.
