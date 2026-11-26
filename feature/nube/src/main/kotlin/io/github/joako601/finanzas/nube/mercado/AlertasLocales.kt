package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.core.domain.mercado.AlertaCambio
import io.github.joako601.finanzas.core.domain.mercado.CondicionAlerta
import io.github.joako601.finanzas.core.domain.mercado.EvaluacionAlertas
import io.github.joako601.finanzas.core.domain.mercado.EvaluadorAlertas
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Alertas de tipo de cambio, guardadas solo en el teléfono: un archivo de texto en
 * `noBackupFilesDir/mercado` (fuera de los respaldos), una alerta por línea.
 *
 * [evaluar] se llama cada vez que llegan cotizaciones nuevas; lo que haya que avisar sale por
 * [disparos] y la app lo convierte en notificación. Borrar los datos locales elimina el archivo
 * por fuera: [recargar] vuelve a leerlo.
 *
 * Las funciones hacen E/S de disco: se llaman fuera del hilo principal.
 */
class AlertasLocales internal constructor(private val carpeta: File) {

    private val archivo get() = File(carpeta, ARCHIVO)
    private val _alertas = MutableStateFlow<List<AlertaCambio>>(emptyList())
    private val _disparos = MutableSharedFlow<EvaluacionAlertas.Disparo>(extraBufferCapacity = 16)

    val alertas: StateFlow<List<AlertaCambio>> = _alertas.asStateFlow()
    val disparos: SharedFlow<EvaluacionAlertas.Disparo> = _disparos.asSharedFlow()

    @Synchronized
    fun recargar() {
        _alertas.value = leer()
    }

    /** Nace con el estado actual de la tasa: si ya se cumple, no avisa hasta que salga y vuelva a entrar. */
    @Synchronized
    fun agregar(moneda: String, condicion: CondicionAlerta, umbral: Double, tasaActual: Double?) {
        val nueva = AlertaCambio(UUID.randomUUID().toString(), moneda, condicion, umbral)
        val alerta = nueva.copy(cumplida = tasaActual != null && nueva.seCumpleCon(tasaActual))
        escribir(leer() + alerta)
    }

    @Synchronized
    fun quitar(id: String) {
        escribir(leer().filterNot { it.id == id })
    }

    @Synchronized
    internal fun evaluar(cotizaciones: List<TipoCambio>) {
        val actuales = leer()
        if (actuales.isEmpty()) return
        val evaluacion = EvaluadorAlertas.evaluar(actuales, tasasEnPesos(cotizaciones))
        if (evaluacion.alertas != actuales) escribir(evaluacion.alertas)
        evaluacion.disparadas.forEach { _disparos.tryEmit(it) }
    }

    /** Cerrar sesión a propósito. */
    @Synchronized
    fun borrar() {
        archivo.delete()
        File(carpeta, "$ARCHIVO.tmp").delete()
        _alertas.value = emptyList()
    }

    private fun leer(): List<AlertaCambio> {
        val lineas = try {
            archivo.takeIf { it.isFile }?.readLines()
        } catch (_: IOException) {
            null
        } ?: return emptyList()
        return lineas.mapNotNull(::desdeLinea)
    }

    private fun escribir(alertas: List<AlertaCambio>) {
        carpeta.mkdirs()
        val temporal = File(carpeta, "$ARCHIVO.tmp")
        temporal.writeText(alertas.joinToString("\n", transform = ::aLinea))
        if (!temporal.renameTo(archivo)) {
            temporal.delete()
            throw IOException("No se pudieron guardar las alertas")
        }
        _alertas.value = alertas
    }

    companion object {
        /** Relativo a `noBackupFilesDir`; el borrado de datos locales de la app lo usa. */
        const val RUTA = "mercado/alertas.txt"
        private const val ARCHIVO = "alertas.txt"
    }
}

/** Pesos por unidad de cada moneda (USD → 17.05), como los espera [EvaluadorAlertas]. */
internal fun tasasEnPesos(cotizaciones: List<TipoCambio>): Map<String, Double> =
    cotizaciones.filter { it.monedaDestino == "MXN" }.associate { it.monedaBase to it.tasa.toDouble() }

private fun aLinea(a: AlertaCambio): String = listOf(a.id, a.moneda, a.condicion.name, a.umbral, a.cumplida).joinToString(";")

/** Una línea dañada se descarta sin tumbar las demás. */
private fun desdeLinea(linea: String): AlertaCambio? {
    val p = linea.split(';')
    if (p.size != 5) return null
    return try {
        AlertaCambio(p[0], p[1], CondicionAlerta.valueOf(p[2]), p[3].toDouble(), p[4].toBooleanStrict())
    } catch (_: IllegalArgumentException) {
        null
    }
}
