package io.github.joako601.finanzas.ui.movimientos

import android.content.Context
import androidx.core.content.edit
import io.github.joako601.finanzas.core.domain.libro.CuentaId

/**
 * La última cuenta usada, en las preferencias privadas de la app. Es solo un id interno
 * (no un monto ni un nombre), igual que la fecha del último respaldo.
 */
class PreferenciaUltimaCuenta(context: Context) : UltimaCuentaUsada {
    private val preferencias = context.getSharedPreferences("captura", Context.MODE_PRIVATE)

    override fun leer(): CuentaId? =
        preferencias.getLong(CLAVE, SIN_VALOR).takeIf { it != SIN_VALOR }?.let(::CuentaId)

    override fun guardar(id: CuentaId?) {
        preferencias.edit { if (id == null) remove(CLAVE) else putLong(CLAVE, id.valor) }
    }

    private companion object {
        const val CLAVE = "ultima_cuenta"
        const val SIN_VALOR = Long.MIN_VALUE
    }
}
