package io.github.joako601.finanzas.core.domain.mercado

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

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

/** Meses del libro que se promedian para el gasto con tarjeta. */
public const val MESES_USO_PROPIO: Int = 3

/**
 * Arma [UsoPropio] con el libro del teléfono; `null` si no hay tarjetas activas.
 *
 * - Gasto: los gastos pagados con tarjeta de los últimos [MESES_USO_PROPIO] meses (desde el
 *   mismo día de hace tres meses, sin incluirlo, hasta [hoy]), entre tres.
 * - Deuda: la suma de lo que se debe en cada tarjeta; un saldo a favor no resta.
 * - Tasa: la de la tarjeta con más deuda que tenga tasa capturada, o la de referencia en México.
 */
public fun calcularUsoPropio(
    tarjetas: List<Tarjeta>,
    saldos: Map<CuentaId, Dinero>,
    movimientos: List<Movimiento>,
    cuentas: Map<CuentaId, Cuenta>,
    hoy: LocalDate,
): UsoPropio? {
    val activas = tarjetas.filterNot { it.cuenta.archivada }
    if (activas.isEmpty()) return null
    val ids = activas.map { it.id }.toSet()
    val desde = hoy.minus(MESES_USO_PROPIO, DateTimeUnit.MONTH)
    val gasto = movimientos.asSequence()
        .filter { it.fecha > desde && it.fecha <= hoy }
        .map { it.resumir(cuentas) }
        .filter { it.clase == ClaseMovimiento.GASTO && it.cuenta?.id in ids }
        .map { it.monto }
        .toList()
        .sumar()
    val deudas = activas.associateWith { maxOf(deudaDe(it, saldos), Dinero.CERO) }
    val tasa = deudas.entries
        .filter { it.key.tasaAnual != null }
        .maxByOrNull { it.value }
        ?.key?.tasaAnual
        ?: AnalizadorTarjeta.TASA_ANUAL_REFERENCIA_MEXICO
    return UsoPropio(
        gastoMensualPromedio = Dinero(gasto.centavos / MESES_USO_PROPIO),
        deuda = deudas.values.sumar(),
        tasaPropia = tasa,
    )
}
