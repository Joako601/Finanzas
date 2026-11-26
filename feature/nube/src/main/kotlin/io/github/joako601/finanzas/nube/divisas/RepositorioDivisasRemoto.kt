package io.github.joako601.finanzas.nube.divisas

import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.red.JsonFormatoException
import io.github.joako601.finanzas.nube.red.JsonLector
import io.github.joako601.finanzas.nube.red.RespuestaHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Cliente remoto de divisas que consume `/api/v1/divisas/actual` y `/api/v1/divisas/convertir`
 * del backend Spring Boot.
 */
internal class RepositorioDivisasRemoto(
    private val clienteHttp: ClienteHttpBackend,
) : RepositorioDivisas {

    override suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales = coroutineScope {
        val respuestas = MONEDAS_PRINCIPALES.map { base ->
            async {
                clienteHttp.get("/api/v1/divisas/actual?base=$base&destino=$MONEDA_DESTINO")
            }
        }.awaitAll()

        if (respuestas.any { it is RespuestaHttp.NoAutorizado }) {
            return@coroutineScope ResultadoDivisasActuales.SesionExpirada
        }

        val exitosas = respuestas.mapNotNull { respuesta ->
            if (respuesta is RespuestaHttp.Exito) parsearTipoCambio(respuesta.cuerpo) else null
        }

        when {
            exitosas.isNotEmpty() -> ResultadoDivisasActuales.Exito(exitosas)
            respuestas.any { it is RespuestaHttp.SinConexion } -> ResultadoDivisasActuales.SinConexion
            else -> ResultadoDivisasActuales.ErrorServidor
        }
    }

    override suspend fun convertir(
        monto: BigDecimal,
        de: String,
        a: String,
    ): ResultadoConversion {
        val origen = de.trim().uppercase(Locale.ROOT)
        val destino = a.trim().uppercase(Locale.ROOT)
        if (!PATRON_ISO.matches(origen) || !PATRON_ISO.matches(destino) || origen == destino) {
            return ResultadoConversion.MontoInvalido
        }
        val normalizado = monto.stripTrailingZeros()
        if (normalizado.scale() > 2 || monto < MONTO_MINIMO || monto > MONTO_MAXIMO) {
            return ResultadoConversion.MontoInvalido
        }
        val montoEscalado = monto.setScale(2, RoundingMode.UNNECESSARY)

        val ruta = "/api/v1/divisas/convertir?monto=${montoEscalado.toPlainString()}&de=$origen&a=$destino"
        return when (val respuesta = clienteHttp.get(ruta)) {
            is RespuestaHttp.Exito -> {
                val conversion = parsearConversion(respuesta.cuerpo)
                if (conversion != null) {
                    ResultadoConversion.Exito(conversion)
                } else {
                    ResultadoConversion.ErrorServidor
                }
            }

            RespuestaHttp.SinConexion -> ResultadoConversion.SinConexion
            RespuestaHttp.NoAutorizado -> ResultadoConversion.SesionExpirada
            is RespuestaHttp.ErrorServidor -> {
                if (respuesta.codigo == 400) ResultadoConversion.MontoInvalido else ResultadoConversion.ErrorServidor
            }
        }
    }

    private fun parsearTipoCambio(cuerpo: String): TipoCambio? = try {
        val obj = JsonLector.analizarObjeto(cuerpo)
        val tasa = obj.numeroTextoObligatorio("tasa").toBigDecimalOrNull()
            ?: throw JsonFormatoException("Tasa de cambio inválida")
        TipoCambio(
            monedaBase = obj.textoObligatorio("monedaBase"),
            monedaDestino = obj.textoObligatorio("monedaDestino"),
            fecha = obj.textoObligatorio("fecha"),
            tasa = tasa,
            fuente = obj.textoObligatorio("fuente"),
            desdeCache = obj.booleano("desdeCache", defecto = false),
        )
    } catch (_: JsonFormatoException) {
        null
    }

    private fun parsearConversion(cuerpo: String): ConversionDivisa? = try {
        val obj = JsonLector.analizarObjeto(cuerpo)
        val montoOrigen = obj.numeroTextoObligatorio("montoOrigen").toBigDecimalOrNull()
            ?: throw JsonFormatoException("montoOrigen inválido")
        val montoDestino = obj.numeroTextoObligatorio("montoDestino").toBigDecimalOrNull()
            ?: throw JsonFormatoException("montoDestino inválido")
        val tasa = obj.numeroTextoObligatorio("tasa").toBigDecimalOrNull()
            ?: throw JsonFormatoException("tasa inválida")
        ConversionDivisa(
            montoOrigen = montoOrigen,
            monedaOrigen = obj.textoObligatorio("monedaOrigen"),
            montoDestino = montoDestino,
            monedaDestino = obj.textoObligatorio("monedaDestino"),
            tasa = tasa,
            fechaTasa = obj.textoObligatorio("fechaTasa"),
            fuente = obj.textoObligatorio("fuente"),
            desdeCache = obj.booleano("desdeCache", defecto = false),
        )
    } catch (_: JsonFormatoException) {
        null
    }

    private companion object {
        val MONEDAS_PRINCIPALES = listOf("USD", "EUR", "CAD", "GBP", "JPY")
        const val MONEDA_DESTINO = "MXN"
        val PATRON_ISO = Regex("^[A-Z]{3}$")
        val MONTO_MINIMO = BigDecimal("0.01")
        val MONTO_MAXIMO = BigDecimal("100000000.00")
    }
}
