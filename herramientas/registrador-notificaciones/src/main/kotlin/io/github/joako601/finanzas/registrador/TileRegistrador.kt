package io.github.joako601.finanzas.registrador

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Botón "Registrador" en los ajustes rápidos (el panel que se baja desde arriba). Un toque
 * lo prende o lo apaga, sin abrir la app. Se agrega una vez desde el lápiz del panel.
 */
class TileRegistrador : TileService() {

    override fun onStartListening() {
        actualizar()
    }

    override fun onClick() {
        Interruptor.cambiar(this, encender = !Interruptor.encendido(this))
        actualizar()
    }

    private fun actualizar() {
        val tile = qsTile ?: return
        val encendido = Interruptor.encendido(this)
        tile.state = if (encendido) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (encendido) "Registrando" else "Apagado"
        tile.updateTile()
    }
}
