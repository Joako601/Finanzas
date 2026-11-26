package io.github.joako601.finanzas.mercado

import android.content.Context
import androidx.core.content.edit
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.tarjetas.AjusteInhabil
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioBancario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * Datos públicos de mercado que usa el libro (D-020): tipos simples que llegan desde la
 * distribución nube por `fuenteDatosPublicos`. Solo entran al teléfono; nada personal sale.
 * En la privada, o sin datos guardados, todo queda en `null` y las pantallas se ven como antes.
 */
data class DatosPublicos(
    /** Días inhábiles bancarios publicados; `null` = sin datos (no se marcan ni los fines de semana). */
    val inhabiles: Set<LocalDate>? = null,
    /** CETES a 28 días, en % anual. */
    val cetes28: Double? = null,
) {
    fun calendario(ajustes: Map<CuentaId, AjusteInhabil>): CalendarioBancario =
        inhabiles?.let { CalendarioBancario(it, ajustes) } ?: CalendarioBancario.NINGUNO

    companion object {
        val VACIOS = DatosPublicos()
    }
}

/**
 * La elección del usuario por tarjeta cuando su fecha límite predicha cae en día inhábil (S14).
 * Va en el archivo de avisos para que "Borrar datos" la limpie con lo demás.
 */
class AjustesInhabiles(context: Context) {
    private val prefs = context.getSharedPreferences("finanzas_avisos", Context.MODE_PRIVATE)
    private val estado = MutableStateFlow(leer())
    val ajustes: StateFlow<Map<CuentaId, AjusteInhabil>> = estado.asStateFlow()

    fun elegir(tarjeta: CuentaId, ajuste: AjusteInhabil) {
        prefs.edit { putString(CLAVE + tarjeta.valor, ajuste.name) }
        estado.update { it + (tarjeta to ajuste) }
    }

    /** Tras borrar los datos locales: los ids de tarjeta se vuelven a usar. */
    fun recargar() {
        estado.value = leer()
    }

    private fun leer(): Map<CuentaId, AjusteInhabil> = prefs.all.mapNotNull { (clave, valor) ->
        val id = clave.removePrefix(CLAVE).takeIf { clave.startsWith(CLAVE) }?.toLongOrNull()
        val ajuste = (valor as? String)?.let { runCatching { AjusteInhabil.valueOf(it) }.getOrNull() }
        if (id != null && ajuste != null) CuentaId(id) to ajuste else null
    }.toMap()

    private companion object {
        const val CLAVE = "ajuste_inhabil_"
    }
}
