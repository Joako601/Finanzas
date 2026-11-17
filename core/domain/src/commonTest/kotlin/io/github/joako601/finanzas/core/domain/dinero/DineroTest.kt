package io.github.joako601.finanzas.core.domain.dinero

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DineroTest {

    // Rango que acepta parsear: hasta 13 dígitos enteros.
    private val maximoCapturable = 9_999_999_999_999L * 100 + 99
    private val capturables = Arb.long(0L..maximoCapturable)

    @Test
    fun parsea_formas_validas() {
        val casos = mapOf(
            "100" to 10_000L,
            "100.5" to 10_050L,
            "100.50" to 10_050L,
            "100.05" to 10_005L,
            ".5" to 50L,
            "5." to 500L,
            "0" to 0L,
            "007" to 700L,
            "1,234.56" to 123_456L,
            "1,234,567" to 123_456_700L,
            "$1,234" to 123_400L,
            "$ 12.30" to 1_230L,
            "  45  " to 4_500L,
        )
        for ((texto, centavos) in casos) {
            assertEquals(Dinero(centavos), Dinero.parsear(texto), "texto: '$texto'")
        }
    }

    @Test
    fun rechaza_formas_invalidas() {
        val invalidos = listOf(
            "", " ", "$", ".", "1.005", "12,5", "1,23", "1.234,50", "1,2345", ",123",
            "-5", "+5", "5-", "1e3", "abc", "1.2.3", "１２", "٣", "12 34",
            "99999999999999", // 14 dígitos enteros
        )
        for (texto in invalidos) {
            assertNull(Dinero.parsear(texto), "debió rechazar: '$texto'")
        }
    }

    @Test
    fun formatea_al_estilo_mexicano() {
        assertEquals("$0.00", Dinero.CERO.formatear())
        assertEquals("$0.05", Dinero(5).formatear())
        assertEquals("$1.00", Dinero(100).formatear())
        assertEquals("$999.99", Dinero(99_999).formatear())
        assertEquals("$1,000.00", Dinero(100_000).formatear())
        assertEquals("$1,234,567.89", Dinero(123_456_789).formatear())
        assertEquals("-$12.05", Dinero(-1_205).formatear())
    }

    @Test
    fun pesos_arma_centavos() {
        assertEquals(Dinero(123_450), Dinero.pesos(1_234, 50))
        assertFailsWith<IllegalArgumentException> { Dinero.pesos(1, 100) }
        assertFailsWith<IllegalArgumentException> { Dinero.pesos(-1) }
    }

    @Test
    fun formatear_y_parsear_son_inversos() = runTest {
        checkAll(capturables) { centavos ->
            val dinero = Dinero(centavos)
            assertEquals(dinero, Dinero.parsear(dinero.formatear()))
        }
    }

    @Test
    fun parsear_ignora_comas_de_miles() = runTest {
        checkAll(capturables) { centavos ->
            val conComas = Dinero(centavos).formatear().removePrefix("$")
            val sinComas = conComas.replace(",", "")
            assertEquals(Dinero.parsear(conComas), Dinero.parsear(sinComas))
        }
    }

    @Test
    fun suma_y_resta_son_inversas() = runTest {
        checkAll(capturables, capturables) { a, b ->
            assertEquals(Dinero(a), Dinero(a) + Dinero(b) - Dinero(b))
            assertEquals(Dinero(a) + Dinero(b), Dinero(b) + Dinero(a))
        }
    }

    @Test
    fun desborde_lanza_en_lugar_de_dar_la_vuelta() {
        val maximo = Dinero(Long.MAX_VALUE)
        assertFailsWith<ArithmeticException> { maximo + Dinero(1) }
        assertFailsWith<ArithmeticException> { -maximo - Dinero(1) }
        assertFailsWith<IllegalArgumentException> { Dinero(Long.MIN_VALUE) }
    }

    @Test
    fun sumar_coleccion() {
        assertEquals(Dinero.CERO, emptyList<Dinero>().sumar())
        assertEquals(Dinero(600), listOf(Dinero(100), Dinero(200), Dinero(300)).sumar())
    }
}
