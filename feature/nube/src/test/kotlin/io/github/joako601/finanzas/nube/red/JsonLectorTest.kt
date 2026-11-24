package io.github.joako601.finanzas.nube.red

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class JsonLectorTest {

    @Test
    fun analiza_objeto_con_tipos_escapes_unicode_y_pares_sustitutos() {
        val json = """
            {
              "id": 7,
              "nombre": "Nu M\u00e9xico \"Cajita\" \uD83D\uDE00",
              "tasa": 12.25,
              "activo": true,
              "imagen": null
            }
        """.trimIndent()

        val obj = JsonLector.analizarObjeto(json)
        assertEquals(7L, obj.enteroLargo("id"))
        assertEquals("Nu México \"Cajita\" 😀", obj.textoObligatorio("nombre"))
        assertEquals(BigDecimal("12.25"), obj.decimalOpcional("tasa"))
        assertTrue(obj.booleano("activo"))
        assertNull(obj.textoOpcional("imagen"))
    }

    @Test
    fun analiza_arreglo_vacio_y_anidado() {
        val vacio = JsonLector.analizarArreglo(" [ ] ")
        assertTrue(vacio.elementos.isEmpty())

        val arreglo = JsonLector.analizarArreglo("""[{"a":1},{"a":-2.5e2}]""")
        assertEquals(2, arreglo.elementos.size)
        val segundo = arreglo.elementos[1] as ObjetoJson
        assertEquals("-2.5e2", segundo.numeroTextoObligatorio("a"))
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_basura_al_final() {
        JsonLector.analizarObjeto("""{"ok":true} basura""")
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_profundidad_excesiva() {
        val profundo = "[".repeat(40) + "]".repeat(40)
        JsonLector.analizar(profundo)
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_cadena_sin_cerrar() {
        JsonLector.analizarObjeto("""{"nombre":"sin cerrar}""")
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_escape_unicode_con_signo() {
        JsonLector.analizarObjeto("""{"a":"\u+041"}""")
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_exponente_descomunal() {
        JsonLector.analizarObjeto("""{"n":1e100000}""")
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_cero_a_la_izquierda() {
        JsonLector.analizarObjeto("""{"n":01}""")
    }

    @Test(expected = JsonFormatoException::class)
    fun rechaza_entrada_vacia() {
        JsonLector.analizar("   ")
    }
}
