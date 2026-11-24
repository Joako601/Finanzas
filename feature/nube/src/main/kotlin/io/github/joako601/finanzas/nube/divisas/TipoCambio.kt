package io.github.joako601.finanzas.nube.divisas

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Cotización vigente de una divisa frente a otra devuelta por el backend. */
data class TipoCambio(
    val monedaBase: String,
    val monedaDestino: String,
    val fecha: String,
    val tasa: BigDecimal,
    val fuente: String,
    val desdeCache: Boolean,
)

/** Resultado de convertir un monto entre dos divisas en el backend. */
data class ConversionDivisa(
    val montoOrigen: BigDecimal,
    val monedaOrigen: String,
    val montoDestino: BigDecimal,
    val monedaDestino: String,
    val tasa: BigDecimal,
    val fechaTasa: String,
    val fuente: String,
    val desdeCache: Boolean,
)

/** Par rápido disponible en el conversor de divisas. */
data class ParDivisas(
    val origen: String,
    val destino: String,
) {
    val etiqueta: String get() = "$origen → $destino"
    val descripcionAccesible: String get() = "$origen a $destino"
}

val PARES_CONVERSOR_DISPONIBLES: List<ParDivisas> = listOf(
    ParDivisas("USD", "MXN"),
    ParDivisas("MXN", "USD"),
    ParDivisas("EUR", "MXN"),
    ParDivisas("CAD", "MXN"),
)

private val simbolosMx = DecimalFormatSymbols(Locale.US)

private val formatoDosDecimales: ThreadLocal<DecimalFormat> = object : ThreadLocal<DecimalFormat>() {
    override fun initialValue(): DecimalFormat = DecimalFormat("#,##0.00", simbolosMx)
}

private val formatoCuatroDecimales: ThreadLocal<DecimalFormat> = object : ThreadLocal<DecimalFormat>() {
    override fun initialValue(): DecimalFormat = DecimalFormat("#,##0.0000", simbolosMx)
}

/** Formatea un monto monetario con 2 decimales y símbolo apropiado (ej. "$1,985.40 MXN", "€100.00 EUR"). */
fun formatearMontoDivisa(monto: BigDecimal, moneda: String): String {
    val codigo = moneda.uppercase(Locale.ROOT)
    val simbolo = when (codigo) {
        "MXN", "USD", "CAD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> ""
    }
    val redondeado = monto.setScale(2, RoundingMode.HALF_UP)
    val texto = (formatoDosDecimales.get() ?: DecimalFormat("#,##0.00", simbolosMx)).format(redondeado)
    return "$simbolo$texto $codigo"
}

/** Formatea una tasa de cambio con 4 decimales para conservar precisión en pantalla. */
fun formatearTasaDivisa(tasa: BigDecimal): String {
    val redondeada = tasa.setScale(4, RoundingMode.HALF_UP)
    return (formatoCuatroDecimales.get() ?: DecimalFormat("#,##0.0000", simbolosMx)).format(redondeada)
}
