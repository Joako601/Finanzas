package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.catalogo.ProductoFinanciero
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import io.github.joako601.finanzas.nube.red.ArregloJson
import io.github.joako601.finanzas.nube.red.JsonFormatoException
import io.github.joako601.finanzas.nube.red.JsonLector
import io.github.joako601.finanzas.nube.red.ObjetoJson
import io.github.joako601.finanzas.nube.red.TextoJson
import java.math.BigDecimal

/** Un indicador económico de México (CETES_28, TASA_OBJETIVO, UDI, FIX). */
data class IndicadorEconomico(
    val clave: String,
    val nombre: String,
    val valor: BigDecimal,
    val unidad: String,
    val fecha: String,
    val fuente: String,
)

/**
 * Lo último que se recibió del servidor para la pestaña Mercado. Inmutable; se reemplaza
 * entero en cada actualización. [uid] es la cuenta que lo pidió: la caché de otra cuenta no
 * se muestra nunca. [recibidoEn] es la hora del teléfono (epoch ms) de la última respuesta.
 * [diasInhabiles] son fechas ISO (`2026-11-16`).
 */
data class DatosMercado(
    val uid: String,
    val recibidoEn: Long,
    val cotizaciones: List<TipoCambio>,
    val indicadores: List<IndicadorEconomico>,
    val productos: List<ProductoFinanciero>,
    val diasInhabiles: List<String>,
) {
    fun indicador(clave: String): IndicadorEconomico? = indicadores.firstOrNull { it.clave == clave }

    companion object {
        const val CETES_28 = "CETES_28"
    }
}

// --- JSON de la caché: mismo lector estricto que las respuestas del backend, escritura a mano. ---

private const val VERSION_CACHE = 1

internal fun DatosMercado.aJson(): String = buildString {
    append("{\"version\":").append(VERSION_CACHE)
    append(",\"uid\":").texto(uid)
    append(",\"recibidoEn\":").append(recibidoEn)
    append(",\"cotizaciones\":").arreglo(cotizaciones) {
        append("{\"monedaBase\":").texto(it.monedaBase)
        append(",\"monedaDestino\":").texto(it.monedaDestino)
        append(",\"fecha\":").texto(it.fecha)
        append(",\"tasa\":").texto(it.tasa.toPlainString())
        append(",\"fuente\":").texto(it.fuente)
        append(",\"desdeCache\":").append(it.desdeCache).append('}')
    }
    append(",\"indicadores\":").arreglo(indicadores) {
        append("{\"clave\":").texto(it.clave)
        append(",\"nombre\":").texto(it.nombre)
        append(",\"valor\":").texto(it.valor.toPlainString())
        append(",\"unidad\":").texto(it.unidad)
        append(",\"fecha\":").texto(it.fecha)
        append(",\"fuente\":").texto(it.fuente).append('}')
    }
    append(",\"productos\":").arreglo(productos) {
        append("{\"id\":").append(it.id)
        append(",\"nombre\":").texto(it.nombre)
        append(",\"categoria\":").texto(it.categoria)
        append(",\"entidad\":").texto(it.entidad)
        append(",\"costoReferenciaPesos\":").append(it.costoReferenciaPesos)
        append(",\"periodicidad\":").texto(it.periodicidad)
        append(",\"detalleReferencia\":").texto(it.detalleReferencia)
        append(",\"fuenteReferencia\":").texto(it.fuenteReferencia)
        append(",\"imageUrl\":").textoONulo(it.imageUrl)
        append(",\"tasaAnualReferencia\":").append(it.tasaAnualReferencia?.toBigDecimal()?.toPlainString() ?: "null")
        append(",\"verificado\":").append(it.verificado)
        append('}')
    }
    append(",\"diasInhabiles\":").arreglo(diasInhabiles) { texto(it) }
    append('}')
}

/** `null` si el archivo está dañado o es de otra versión: se trata como si no hubiera caché. */
internal fun datosMercadoDesdeJson(json: String): DatosMercado? = try {
    val raiz = JsonLector.analizarObjeto(json)
    if (raiz.enteroLargo("version") != VERSION_CACHE.toLong()) {
        null
    } else {
        DatosMercado(
            uid = raiz.textoObligatorio("uid"),
            recibidoEn = raiz.enteroLargo("recibidoEn"),
            cotizaciones = raiz.objetos("cotizaciones").map {
                TipoCambio(
                    monedaBase = it.textoObligatorio("monedaBase"),
                    monedaDestino = it.textoObligatorio("monedaDestino"),
                    fecha = it.textoObligatorio("fecha"),
                    tasa = it.decimal("tasa"),
                    fuente = it.textoObligatorio("fuente"),
                    desdeCache = it.booleano("desdeCache"),
                )
            },
            indicadores = raiz.objetos("indicadores").map(::indicadorDesdeJson),
            productos = raiz.objetos("productos").map {
                ProductoFinanciero(
                    id = it.enteroLargo("id"),
                    nombre = it.textoObligatorio("nombre"),
                    categoria = it.textoObligatorio("categoria"),
                    entidad = it.textoObligatorio("entidad"),
                    costoReferenciaPesos = it.enteroLargo("costoReferenciaPesos"),
                    periodicidad = it.textoObligatorio("periodicidad"),
                    detalleReferencia = it.textoObligatorio("detalleReferencia"),
                    fuenteReferencia = it.textoObligatorio("fuenteReferencia"),
                    imageUrl = it.textoOpcional("imageUrl"),
                    tasaAnualReferencia = it.decimalOpcional("tasaAnualReferencia")?.toDouble(),
                    verificado = it.booleano("verificado"),
                )
            },
            diasInhabiles = (raiz.campos["diasInhabiles"] as? ArregloJson)?.elementos
                ?.map { (it as? TextoJson)?.valor ?: throw JsonFormatoException("Fecha inválida") }
                .orEmpty(),
        )
    }
} catch (_: JsonFormatoException) {
    null
} catch (_: NumberFormatException) {
    null
}

/** Mismo formato que `GET /api/v1/indicadores/actual`. */
internal fun indicadorDesdeJson(obj: ObjetoJson) = IndicadorEconomico(
    clave = obj.textoObligatorio("clave"),
    nombre = obj.textoObligatorio("nombre"),
    valor = obj.decimal("valor"),
    unidad = obj.textoObligatorio("unidad"),
    fecha = obj.textoObligatorio("fecha"),
    fuente = obj.textoObligatorio("fuente"),
)

internal fun ObjetoJson.objetos(clave: String): List<ObjetoJson> =
    (campos[clave] as? ArregloJson ?: throw JsonFormatoException("Arreglo ausente: $clave"))
        .elementos.map { it as? ObjetoJson ?: throw JsonFormatoException("Elemento no es objeto: $clave") }

private fun ObjetoJson.decimal(clave: String): BigDecimal =
    numeroTextoObligatorio(clave).toBigDecimalOrNull() ?: throw JsonFormatoException("Decimal inválido: $clave")

private fun <T> StringBuilder.arreglo(elementos: List<T>, escribir: StringBuilder.(T) -> Unit): StringBuilder {
    append('[')
    elementos.forEachIndexed { i, e ->
        if (i > 0) append(',')
        escribir(e)
    }
    return append(']')
}

private fun StringBuilder.textoONulo(valor: String?): StringBuilder = if (valor == null) append("null") else texto(valor)

private fun StringBuilder.texto(valor: String): StringBuilder {
    append('"')
    for (c in valor) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
    return append('"')
}
