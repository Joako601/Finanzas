package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.divisas.TipoCambio
import java.math.BigDecimal
import java.math.MathContext

/** Monedas del conversor de Mercado, en el orden en que se ofrecen. */
val MONEDAS_CONVERSOR: List<String> = listOf("MXN", "USD", "EUR", "CAD", "GBP", "JPY")

/**
 * Convierte con las tasas guardadas, sin red: pasa siempre por MXN, así también salen los
 * cruces (EUR → USD). Cada [TipoCambio] dice cuántos MXN vale una unidad de su moneda base.
 *
 * Devuelve `null` si falta la tasa de alguna de las dos monedas o es cero. El resultado no se
 * redondea: redondear es cosa de quien lo muestra.
 */
fun convertirLocal(monto: BigDecimal, de: String, a: String, cotizaciones: List<TipoCambio>): BigDecimal? {
    if (de == a) return monto
    val pesos = if (de == "MXN") monto else monto.multiply(tasaEnPesos(de, cotizaciones) ?: return null)
    if (a == "MXN") return pesos
    val tasaDestino = tasaEnPesos(a, cotizaciones) ?: return null
    return pesos.divide(tasaDestino, MathContext.DECIMAL64)
}

private fun tasaEnPesos(moneda: String, cotizaciones: List<TipoCambio>): BigDecimal? =
    cotizaciones.firstOrNull { it.monedaBase == moneda && it.monedaDestino == "MXN" }
        ?.tasa?.takeIf { it.signum() > 0 }
