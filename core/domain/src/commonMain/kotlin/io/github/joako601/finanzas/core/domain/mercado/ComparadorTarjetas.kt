package io.github.joako601.finanzas.core.domain.mercado

import io.github.joako601.finanzas.core.domain.dinero.Dinero

/** Una tarjeta del catálogo público, sin depender de `:feature:nube`. */
public data class ProductoTarjeta(
    val id: Long,
    val nombre: String,
    val entidad: String,
    val anualidad: Dinero,
    /** Tasa anual de referencia en %, si el catálogo la trae. */
    val tasaAnual: Double?,
)

/** Lo que se sabe del uso propio, calculado del libro en el teléfono. */
public data class UsoPropio(
    /** Gasto con tarjetas por mes, promedio de los últimos meses. */
    val gastoMensualPromedio: Dinero,
    /** Deuda actual sumada de las tarjetas. */
    val deuda: Dinero,
    /** Tasa anual de la tarjeta propia (o la de referencia en México si no se capturó). */
    val tasaPropia: Int,
)

/**
 * Una fila de la comparación. [anualidadSobreGasto] es la anualidad como % del gasto de un
 * año: dimensiona si conviene una tarjeta con o sin anualidad. [costoAnual] suma la anualidad
 * y el interés (con IVA) de mantener la deuda actual un año; [diferenciaAnual] lo compara con
 * mantenerla en la tarjeta propia (negativo = sale más barata). Sin tasa, no se estima interés.
 */
public data class FilaComparacion(
    val producto: ProductoTarjeta,
    val anualidadSobreGasto: Double?,
    val costoAnual: Dinero?,
    val diferenciaAnual: Dinero?,
)

public object ComparadorTarjetas {
    private const val FACTOR_IVA = 1.16

    /** Ordenadas de menor a mayor costo anual estimado; las que no se pueden estimar, al final. */
    public fun comparar(productos: List<ProductoTarjeta>, uso: UsoPropio): List<FilaComparacion> {
        val gastoAnual = uso.gastoMensualPromedio.centavos * 12
        val deuda = maxOf(uso.deuda, Dinero.CERO)
        val costoPropio = porcentajeDe(deuda, uso.tasaPropia * FACTOR_IVA)
        return productos.map { p ->
            val costo = p.tasaAnual?.takeIf { it >= 0 && !it.isNaN() }?.let { p.anualidad + porcentajeDe(deuda, it * FACTOR_IVA) }
            FilaComparacion(
                producto = p,
                anualidadSobreGasto = if (gastoAnual > 0) p.anualidad.centavos * 100.0 / gastoAnual else null,
                costoAnual = costo,
                diferenciaAnual = costo?.let { it - costoPropio },
            )
        }.sortedWith(compareBy(nullsLast()) { it.costoAnual })
    }
}
