package io.github.joako601.finanzas.registrador

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuración y contadores del registrador, en SharedPreferences (un archivo XML
 * privado de la app). Android la mantiene en memoria, así que leerla en cada
 * notificación es barato.
 */
class Preferencias(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("registrador", Context.MODE_PRIVATE)

    /** Paquetes cuyas notificaciones se registran con su contenido completo. */
    var paquetesPermitidos: Set<String>
        get() = prefs.getStringSet(PERMITIDOS, null) ?: PAQUETES_SUGERIDOS
        set(valor) = prefs.edit().putStringSet(PERMITIDOS, valor).apply()

    /**
     * Paquetes que publicaron alguna notificación desde que se activó el registrador.
     * Solo el nombre del paquete, nunca el contenido: sirve para descubrir el nombre
     * real de la app de un banco y activarla.
     */
    val paquetesVistos: Set<String>
        get() = prefs.getStringSet(VISTOS, emptySet()).orEmpty()

    fun marcarVisto(paquete: String) {
        val vistos = paquetesVistos
        if (paquete !in vistos) prefs.edit().putStringSet(VISTOS, vistos + paquete).apply()
    }

    val eventosRegistrados: Long get() = prefs.getLong(EVENTOS, 0)
    val ultimoEventoMillis: Long get() = prefs.getLong(ULTIMO, 0)
    val conectado: Boolean get() = prefs.getBoolean(CONECTADO, false)
    val cambioDeConexionMillis: Long get() = prefs.getLong(CAMBIO_CONEXION, 0)

    fun contarEvento(ahora: Long) {
        prefs.edit().putLong(EVENTOS, eventosRegistrados + 1).putLong(ULTIMO, ahora).apply()
    }

    fun marcarConexion(conectado: Boolean, ahora: Long) {
        prefs.edit().putBoolean(CONECTADO, conectado).putLong(CAMBIO_CONEXION, ahora).apply()
    }

    fun reiniciarContadores() {
        prefs.edit().remove(EVENTOS).remove(ULTIMO).apply()
    }

    companion object {
        private const val PERMITIDOS = "paquetes_permitidos"
        private const val VISTOS = "paquetes_vistos"
        private const val EVENTOS = "eventos"
        private const val ULTIMO = "ultimo_evento"
        private const val CONECTADO = "conectado"
        private const val CAMBIO_CONEXION = "cambio_conexion"

        /**
         * Nombres de paquete supuestos de Nu México, BBVA México y Mercado Pago.
         * No están verificados: la pantalla indica si cada uno está instalado.
         */
        val PAQUETES_SUGERIDOS: Set<String> = setOf(
            "com.nu.production",
            "com.bancomer.mbanking",
            "com.mercadopago.wallet",
        )
    }
}
