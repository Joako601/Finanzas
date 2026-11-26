package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.divisas.TipoCambio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ConversorLocalTest {

    private fun tasa(moneda: String, valor: String) =
        TipoCambio(moneda, "MXN", "2026-11-26", BigDecimal(valor), "Banxico", desdeCache = false)

    private val tasas = listOf(tasa("USD", "18.50"), tasa("EUR", "20.00"), tasa("JPY", "0.125"))

    @Test
    fun `de una moneda a pesos multiplica por la tasa`() {
        assertEquals(0, BigDecimal("1850").compareTo(convertirLocal(BigDecimal("100"), "USD", "MXN", tasas)))
    }

    @Test
    fun `de pesos a una moneda divide entre la tasa`() {
        assertEquals(0, BigDecimal("8").compareTo(convertirLocal(BigDecimal("1"), "MXN", "JPY", tasas)))
    }

    @Test
    fun `los cruces pasan por pesos`() {
        // 100 EUR = 2000 MXN = 108.108... USD
        val usd = convertirLocal(BigDecimal("100"), "EUR", "USD", tasas)!!
        assertEquals("108.11", usd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    }

    @Test
    fun `misma moneda devuelve el monto`() {
        assertEquals(BigDecimal("5"), convertirLocal(BigDecimal("5"), "CAD", "CAD", emptyList()))
    }

    @Test
    fun `sin tasa o con tasa cero no convierte`() {
        assertNull(convertirLocal(BigDecimal("1"), "CAD", "MXN", tasas))
        assertNull(convertirLocal(BigDecimal("1"), "MXN", "GBP", tasas + tasa("GBP", "0")))
    }
}
