package io.github.joako601.finanzas.nube.divisas

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Implementación local de demostración y pruebas unitarias de [RepositorioDivisas].
 */
class RepositorioDivisasFalso : RepositorioDivisas {

    private val tasasContraMxn = mapOf(
        "USD" to BigDecimal("19.8500"),
        "EUR" to BigDecimal("21.4200"),
        "CAD" to BigDecimal("14.3100"),
        "MXN" to BigDecimal.ONE,
    )

    override suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales =
        ResultadoDivisasActuales.Exito(
            listOf("USD", "EUR", "CAD").map { base ->
                TipoCambio(
                    monedaBase = base,
                    monedaDestino = "MXN",
                    fecha = FECHA_DEMO,
                    tasa = tasasContraMxn.getValue(base),
                    fuente = "Demostración local",
                    desdeCache = true,
                )
            },
        )

    override suspend fun convertir(
        monto: BigDecimal,
        de: String,
        a: String,
    ): ResultadoConversion {
        val montoEscalado = monto.setScale(2, RoundingMode.HALF_UP)
        if (montoEscalado <= BigDecimal.ZERO) return ResultadoConversion.MontoInvalido
        val tasaOrigenMxn = tasasContraMxn[de.uppercase()] ?: return ResultadoConversion.MontoInvalido
        val tasaDestinoMxn = tasasContraMxn[a.uppercase()] ?: return ResultadoConversion.MontoInvalido
        val tasa = tasaOrigenMxn.divide(tasaDestinoMxn, 6, RoundingMode.HALF_UP)
        val destino = montoEscalado.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
        return ResultadoConversion.Exito(
            ConversionDivisa(
                montoOrigen = montoEscalado,
                monedaOrigen = de.uppercase(),
                montoDestino = destino,
                monedaDestino = a.uppercase(),
                tasa = tasa,
                fechaTasa = FECHA_DEMO,
                fuente = "Demostración local",
                desdeCache = true,
            ),
        )
    }

    private companion object {
        const val FECHA_DEMO = "2026-11-23"
    }
}
