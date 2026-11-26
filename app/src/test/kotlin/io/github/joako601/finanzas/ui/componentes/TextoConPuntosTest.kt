package io.github.joako601.finanzas.ui.componentes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoConPuntosTest {

    private val monto = "$1,234.50"

    @Test
    fun sin_progreso_es_el_monto_y_completo_es_el_monto_oculto() {
        assertEquals(monto, textoConPuntos(monto, 0f))
        assertEquals(MONTO_OCULTO, textoConPuntos(monto, 1f))
    }

    @Test
    fun en_medio_conserva_signo_comas_y_punto_y_el_largo() {
        listOf(0.1f, 0.4f, 0.7f, 0.95f).forEach { p ->
            val texto = textoConPuntos(monto, p)
            assertEquals(monto.length, texto.length)
            monto.forEachIndexed { i, c -> if (!c.isDigit()) assertEquals(c, texto[i]) }
            assertTrue(texto.all { it.isDigit() || it == '•' || it in "$,." })
        }
    }

    @Test
    fun los_digitos_de_la_izquierda_caen_antes_que_los_de_la_derecha() {
        val texto = textoConPuntos(monto, 0.6f)
        val digitos = texto.filterIndexed { i, _ -> monto[i].isDigit() }
        // Una vez que aparece el primer punto, todo lo de su izquierda ya es punto.
        val ultimoPunto = digitos.lastIndexOf('•')
        assertTrue("debe haber puntos a la mitad: $texto", ultimoPunto >= 0)
        assertTrue("los puntos van de izquierda a derecha: $texto", digitos.take(ultimoPunto + 1).all { it == '•' })
        assertTrue("a la derecha todavía corren números: $texto", digitos.last().isDigit())
    }

    @Test
    fun antes_de_caer_los_digitos_corren() {
        val texto = textoConPuntos(monto, 0.2f)
        assertTrue(texto.none { it == '•' })
        assertTrue("el número debe cambiar mientras corre: $texto", texto != monto)
    }
}
