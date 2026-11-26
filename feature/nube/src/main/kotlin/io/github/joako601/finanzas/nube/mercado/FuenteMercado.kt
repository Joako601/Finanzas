package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.catalogo.ProductoFinanciero
import io.github.joako601.finanzas.nube.catalogo.RepositorioCatalogo
import io.github.joako601.finanzas.nube.catalogo.ResultadoCatalogo
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisas
import io.github.joako601.finanzas.nube.divisas.ResultadoDivisasActuales
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.red.JsonFormatoException
import io.github.joako601.finanzas.nube.red.JsonLector
import io.github.joako601.finanzas.nube.red.ObjetoJson
import io.github.joako601.finanzas.nube.red.RespuestaHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Una actualización del servidor. Cada parte es `null` si esa llamada falló: el repositorio
 * conserva la parte anterior en lugar de borrarla.
 */
internal data class ParteMercado(
    val cotizaciones: List<TipoCambio>?,
    val indicadores: List<IndicadorEconomico>?,
    val productos: List<ProductoFinanciero>?,
    val diasInhabiles: List<String>?,
) {
    val vacia: Boolean get() = cotizaciones == null && indicadores == null && productos == null && diasInhabiles == null
}

internal sealed interface ResultadoFuente {
    data class Exito(val parte: ParteMercado) : ResultadoFuente
    data object SinConexion : ResultadoFuente
    data object SesionExpirada : ResultadoFuente
    data object ErrorServidor : ResultadoFuente
}

internal fun interface FuenteMercado {
    suspend fun obtener(): ResultadoFuente
}

/**
 * Arma los datos de Mercado con los endpoints que ya existen (sin endpoint agregado ni `ETag`
 * por ahora): divisas, indicadores, catálogo y días inhábiles de este año y el siguiente
 * (una fecha límite de diciembre puede caer en enero). Las llamadas van en paralelo.
 */
internal class FuenteMercadoRemota(
    private val cliente: ClienteHttpBackend,
    private val divisas: RepositorioDivisas,
    private val catalogo: RepositorioCatalogo,
    private val hoy: () -> LocalDate = LocalDate::now,
) : FuenteMercado {

    override suspend fun obtener(): ResultadoFuente = coroutineScope {
        val anio = hoy().year
        val rDivisas = async { divisas.obtenerCotizacionesPrincipales() }
        val rCatalogo = async { catalogo.obtenerProductos() }
        val rIndicadores = async { cliente.get("/api/v1/indicadores/actual") }
        val rInhabiles = listOf(anio, anio + 1).map { async { cliente.get("/api/v1/calendario/inhabiles?anio=$it") } }

        val divisas = rDivisas.await()
        val catalogo = rCatalogo.await()
        val indicadores = rIndicadores.await()
        val inhabiles = rInhabiles.map { it.await() }
        val http = listOf(indicadores) + inhabiles

        if (divisas is ResultadoDivisasActuales.SesionExpirada || catalogo is ResultadoCatalogo.SesionExpirada ||
            http.any { it is RespuestaHttp.NoAutorizado }
        ) {
            return@coroutineScope ResultadoFuente.SesionExpirada
        }

        val parte = ParteMercado(
            cotizaciones = (divisas as? ResultadoDivisasActuales.Exito)?.cotizaciones,
            indicadores = (indicadores as? RespuestaHttp.Exito)?.let { parsear(it.cuerpo) { arr -> arr.map(::indicadorDesdeJson) } },
            productos = (catalogo as? ResultadoCatalogo.Exito)?.productos,
            // Los dos años o nada: con uno solo faltarían inhábiles sin avisar.
            diasInhabiles = inhabiles.map { r ->
                (r as? RespuestaHttp.Exito)?.let { parsear(it.cuerpo) { arr -> arr.map { o -> o.textoObligatorio("fecha") } } }
            }.takeIf { listas -> listas.all { it != null } }?.flatMap { it!! },
        )
        val sinConexion = divisas is ResultadoDivisasActuales.SinConexion || catalogo is ResultadoCatalogo.SinConexion ||
            http.any { it is RespuestaHttp.SinConexion }
        when {
            !parte.vacia -> ResultadoFuente.Exito(parte)
            sinConexion -> ResultadoFuente.SinConexion
            else -> ResultadoFuente.ErrorServidor
        }
    }

    private fun <T> parsear(cuerpo: String, leer: (List<ObjetoJson>) -> List<T>): List<T>? = try {
        leer(JsonLector.analizarArreglo(cuerpo).elementos.map { it as? ObjetoJson ?: throw JsonFormatoException("No es objeto") })
    } catch (_: JsonFormatoException) {
        null
    }
}

/** Datos de ejemplo para debug sin backend. Las cifras no son reales. */
internal class FuenteMercadoFalsa(
    private val divisas: RepositorioDivisas,
    private val catalogo: RepositorioCatalogo,
) : FuenteMercado {
    override suspend fun obtener(): ResultadoFuente {
        val fecha = LocalDate.now().toString()
        val fuente = "Ejemplo sin verificar"
        return ResultadoFuente.Exito(
            ParteMercado(
                cotizaciones = (divisas.obtenerCotizacionesPrincipales() as? ResultadoDivisasActuales.Exito)?.cotizaciones,
                indicadores = listOf(
                    IndicadorEconomico(DatosMercado.CETES_28, "CETES 28 días", BigDecimal("7.25"), "%", fecha, fuente),
                    IndicadorEconomico("TASA_OBJETIVO", "Tasa objetivo Banxico", BigDecimal("7.00"), "%", fecha, fuente),
                    IndicadorEconomico("UDI", "UDI", BigDecimal("8.512345"), "MXN", fecha, fuente),
                    IndicadorEconomico("FIX", "Dólar FIX", BigDecimal("18.4521"), "MXN", fecha, fuente),
                ),
                productos = (catalogo.obtenerProductos() as? ResultadoCatalogo.Exito)?.productos,
                diasInhabiles = listOf("2026-11-16", "2026-12-12", "2026-12-25", "2027-01-01", "2027-02-01", "2027-03-15"),
            ),
        )
    }
}
