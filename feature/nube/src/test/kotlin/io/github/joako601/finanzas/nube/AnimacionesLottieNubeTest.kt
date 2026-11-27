package io.github.joako601.finanzas.nube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Valida en la JVM que todos los archivos Lottie de `:feature:nube` (`res/raw/anim_*.json`)
 * sean JSON bien formados y cumplan los campos mínimos de Bodymovin.
 */
class AnimacionesLottieNubeTest {

    private val esperados = setOf(
        "anim_registro_exito.json",
        "anim_correo_enviado.json",
        "anim_login_exito.json",
        "anim_login_error.json",
    )

    @Test
    fun todos_los_archivos_lottie_de_nube_son_validos() {
        val carpetaRaw = listOf(
            File("src/main/res/raw"),
            File("feature/nube/src/main/res/raw"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("No se encontró la carpeta src/main/res/raw de :feature:nube", carpetaRaw)

        val archivos = carpetaRaw!!.listFiles { f -> f.isFile && f.name.startsWith("anim_") && f.name.endsWith(".json") }
            ?.sortedBy { it.name }
            .orEmpty()

        assertEquals(esperados, archivos.map { it.name }.toSet())

        archivos.forEach { archivo ->
            val contenido = archivo.readText(Charsets.UTF_8)
            val raiz = AnalizadorJsonEstricto(contenido).parsearObjeto()
            validarDocumentoBodymovin(archivo.name, raiz)
        }
    }

    private fun validarDocumentoBodymovin(nombre: String, raiz: Map<String, Any?>) {
        val obligatorios = listOf("v", "fr", "ip", "op", "w", "h", "layers")
        obligatorios.forEach { clave ->
            assertTrue("$nombre debe incluir '$clave'", raiz.containsKey(clave))
            assertNotNull("$nombre tiene '$clave' nulo", raiz[clave])
        }

        val version = raiz["v"] as? String
        assertTrue("$nombre: 'v' debe ser texto no vacío", !version.isNullOrBlank())

        val fr = (raiz["fr"] as? Number)?.toDouble() ?: error("$nombre: 'fr' no es número")
        val ip = (raiz["ip"] as? Number)?.toDouble() ?: error("$nombre: 'ip' no es número")
        val op = (raiz["op"] as? Number)?.toDouble() ?: error("$nombre: 'op' no es número")
        val w = (raiz["w"] as? Number)?.toDouble() ?: error("$nombre: 'w' no es número")
        val h = (raiz["h"] as? Number)?.toDouble() ?: error("$nombre: 'h' no es número")

        assertTrue("$nombre: fr ($fr) debe ser > 0", fr > 0.0)
        assertTrue("$nombre: op ($op) debe ser > ip ($ip)", op > ip)
        assertTrue("$nombre: w ($w) debe ser > 0", w > 0.0)
        assertTrue("$nombre: h ($h) debe ser > 0", h > 0.0)

        val layers = raiz["layers"] as? List<*> ?: error("$nombre: 'layers' no es arreglo")
        assertFalse("$nombre: 'layers' no puede estar vacío", layers.isEmpty())
        layers.forEachIndexed { idx, capa ->
            assertTrue("$nombre: capa $idx debe ser objeto JSON", capa is Map<*, *>)
        }

        verificarSinClavesInvalidas(nombre, raiz)
    }

    private fun verificarSinClavesInvalidas(nombre: String, nodo: Any?) {
        when (nodo) {
            is Map<*, *> -> nodo.forEach { (k, v) ->
                val clave = k as? String ?: error("$nombre: clave no textual")
                assertTrue("$nombre: clave vacía no permitida", clave.isNotBlank())
                assertNotNull("$nombre: valor nulo en clave '$clave'", v)
                verificarSinClavesInvalidas(nombre, v)
            }
            is List<*> -> nodo.forEach { elem ->
                assertNotNull("$nombre: elemento nulo en arreglo", elem)
                verificarSinClavesInvalidas(nombre, elem)
            }
        }
    }
}

/** Analizador JSON estricto sin dependencias de Android para pruebas JVM. */
private class AnalizadorJsonEstricto(private val texto: String) {
    private var pos = 0

    fun parsearObjeto(): Map<String, Any?> {
        saltarEspacios()
        val resultado = leerObjeto()
        saltarEspacios()
        require(pos == texto.length) { "Contenido extra al final del JSON en posición $pos" }
        return resultado
    }

    private fun leerValor(): Any? {
        saltarEspacios()
        require(pos < texto.length) { "Fin inesperado del JSON" }
        return when (texto[pos]) {
            '{' -> leerObjeto()
            '[' -> leerArreglo()
            '"' -> leerCadena()
            't' -> consumirLiteral("true", true)
            'f' -> consumirLiteral("false", false)
            'n' -> consumirLiteral("null", null)
            else -> leerNumero()
        }
    }

    private fun leerObjeto(): Map<String, Any?> {
        esperar('{')
        saltarEspacios()
        val mapa = LinkedHashMap<String, Any?>()
        if (pos < texto.length && texto[pos] == '}') {
            pos++
            return mapa
        }
        while (true) {
            saltarEspacios()
            val clave = leerCadena()
            saltarEspacios()
            esperar(':')
            val valor = leerValor()
            mapa[clave] = valor
            saltarEspacios()
            when {
                pos < texto.length && texto[pos] == ',' -> pos++
                pos < texto.length && texto[pos] == '}' -> {
                    pos++
                    return mapa
                }
                else -> error("Se esperaba ',' o '}' en posición $pos")
            }
        }
    }

    private fun leerArreglo(): List<Any?> {
        esperar('[')
        saltarEspacios()
        val lista = mutableListOf<Any?>()
        if (pos < texto.length && texto[pos] == ']') {
            pos++
            return lista
        }
        while (true) {
            lista += leerValor()
            saltarEspacios()
            when {
                pos < texto.length && texto[pos] == ',' -> pos++
                pos < texto.length && texto[pos] == ']' -> {
                    pos++
                    return lista
                }
                else -> error("Se esperaba ',' o ']' en posición $pos")
            }
        }
    }

    private fun leerCadena(): String {
        esperar('"')
        val sb = StringBuilder()
        while (pos < texto.length) {
            val c = texto[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    require(pos < texto.length) { "Escape incompleto" }
                    val esc = texto[pos++]
                    when (esc) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = texto.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> error("Escape inválido \\$esc")
                    }
                }
                else -> sb.append(c)
            }
        }
        error("Cadena sin cerrar")
    }

    private fun leerNumero(): Number {
        val inicio = pos
        if (texto[pos] == '-') pos++
        while (pos < texto.length && texto[pos].isDigit()) pos++
        var esDecimal = false
        if (pos < texto.length && texto[pos] == '.') {
            esDecimal = true
            pos++
            while (pos < texto.length && texto[pos].isDigit()) pos++
        }
        if (pos < texto.length && (texto[pos] == 'e' || texto[pos] == 'E')) {
            esDecimal = true
            pos++
            if (pos < texto.length && (texto[pos] == '+' || texto[pos] == '-')) pos++
            while (pos < texto.length && texto[pos].isDigit()) pos++
        }
        val literal = texto.substring(inicio, pos)
        return if (esDecimal) literal.toDouble() else literal.toLong()
    }

    private fun <T> consumirLiteral(esperado: String, valor: T): T {
        require(texto.startsWith(esperado, pos)) { "Literal inválido en posición $pos" }
        pos += esperado.length
        return valor
    }

    private fun esperar(c: Char) {
        require(pos < texto.length && texto[pos] == c) { "Se esperaba '$c' en posición $pos" }
        pos++
    }

    private fun saltarEspacios() {
        while (pos < texto.length && texto[pos].isWhitespace()) pos++
    }
}
