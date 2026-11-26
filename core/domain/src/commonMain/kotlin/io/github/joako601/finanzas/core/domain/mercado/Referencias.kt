package io.github.joako601.finanzas.core.domain.mercado

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlin.math.pow
import kotlin.math.roundToLong

/** IVA sobre intereses de tarjeta en México, igual que [io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta]. */
private const val FACTOR_IVA = 1.16

internal fun porcentajeDe(monto: Dinero, tasaAnualPorciento: Double): Dinero =
    Dinero((monto.centavos * tasaAnualPorciento / 100.0).roundToLong())

/**
 * Lo que cuesta un año de [deuda] en la tarjeta frente a lo que rendiría ese dinero a la
 * tasa de referencia (CETES 28 días). Es un estimado simple: interés anual sin capitalizar,
 * con IVA en la tarjeta y CETES antes de impuestos.
 */
public data class ComparacionDeuda(
    val tasaTarjetaAnual: Int,
    val tasaReferenciaAnual: Double,
    val costoAnualDeuda: Dinero,
    val rendimientoAnualReferencia: Dinero,
) {
    /** Cuánto más cuesta la deuda que lo que rinde el mismo dinero invertido. */
    val diferencia: Dinero get() = costoAnualDeuda - rendimientoAnualReferencia
}

public fun compararDeudaContraReferencia(
    tasaTarjetaAnual: Int,
    tasaReferenciaAnual: Double,
    deuda: Dinero,
): ComparacionDeuda? {
    if (!deuda.esPositivo || tasaTarjetaAnual <= 0 || tasaReferenciaAnual.isNaN() || tasaReferenciaAnual < 0) return null
    return ComparacionDeuda(
        tasaTarjetaAnual = tasaTarjetaAnual,
        tasaReferenciaAnual = tasaReferenciaAnual,
        costoAnualDeuda = porcentajeDe(deuda, tasaTarjetaAnual * FACTOR_IVA),
        rendimientoAnualReferencia = porcentajeDe(deuda, tasaReferenciaAnual),
    )
}

/** Valor presente de una compra a meses sin intereses frente a pagarla de contado. */
public data class EstimacionMsi(
    val total: Dinero,
    val meses: Int,
    val tasaReferenciaAnual: Double,
    val valorPresente: Dinero,
) {
    /** Lo que rinde aproximadamente el dinero que no se paga de contado. */
    val ventaja: Dinero get() = total - valorPresente
}

/**
 * Estima cuánto vale hoy pagar a MSI si el dinero se queda invirtiendo a [tasaReferenciaAnual].
 *
 * Supuestos: las mensualidades son las de
 * [io.github.joako601.finanzas.core.domain.tarjetas.CompraAMeses] (piso de total·k/meses en
 * centavos), la primera se paga un mes después de la compra, y la tasa se capitaliza mensual
 * (tasa/12). Sin impuestos ni comisiones.
 */
public object CalculadoraMsi {
    public fun estimar(total: Dinero, meses: Int, tasaReferenciaAnual: Double): EstimacionMsi? {
        if (!total.esPositivo || meses <= 0 || tasaReferenciaAnual.isNaN() || tasaReferenciaAnual < 0) return null
        val r = tasaReferenciaAnual / 100.0 / 12.0
        var presente = 0.0
        for (k in 1..meses) {
            val cuota = total.centavos * k / meses - total.centavos * (k - 1) / meses
            presente += cuota / (1.0 + r).pow(k)
        }
        return EstimacionMsi(total, meses, tasaReferenciaAnual, Dinero(presente.roundToLong()))
    }
}
