package io.github.joako601.finanzas.nube.red

import java.math.BigDecimal

/** Error al analizar una respuesta JSON del backend. */
internal class JsonFormatoException(mensaje: String) : IllegalArgumentException(mensaje)

/** Árbol JSON inmutable y sin dependencias externas (compatible con Android y JVM). */
internal sealed interface ValorJson

internal data class ObjetoJson(val campos: Map<String, ValorJson>) : ValorJson {
    fun textoObligatorio(clave: String): String {
        val valor = campos[clave] as? TextoJson
            ?: throw JsonFormatoException("Campo de texto requerido ausente o inválido: $clave")
        return valor.valor
    }

    fun textoOpcional(clave: String): String? = when (val valor = campos[clave]) {
        null, NuloJson -> null
        is TextoJson -> valor.valor
        else -> throw JsonFormatoException("Campo de texto opcional inválido: $clave")
    }

    fun enteroLargo(clave: String): Long {
        val valor = campos[clave] as? NumeroJson
            ?: throw JsonFormatoException("Campo entero requerido ausente o inválido: $clave")
        return valor.raw.toLongOrNull()
            ?: throw JsonFormatoException("Campo $clave no es un entero largo válido")
    }

    fun numeroTextoObligatorio(clave: String): String = when (val valor = campos[clave]) {
        is NumeroJson -> valor.raw
        is TextoJson -> valor.valor
        else -> throw JsonFormatoException("Campo numérico requerido ausente o inválido: $clave")
    }

    fun decimalOpcional(clave: String): BigDecimal? = when (val valor = campos[clave]) {
        null, NuloJson -> null
        is NumeroJson -> valor.raw.toBigDecimalOrNull()
            ?: throw JsonFormatoException("Campo decimal inválido: $clave")
        is TextoJson -> valor.valor.toBigDecimalOrNull()
            ?: throw JsonFormatoException("Campo decimal inválido: $clave")
        else -> throw JsonFormatoException("Campo decimal opcional inválido: $clave")
    }

    fun booleano(clave: String, defecto: Boolean = false): Boolean = when (val valor = campos[clave]) {
        null, NuloJson -> defecto
        is BooleanoJson -> valor.valor
        else -> throw JsonFormatoException("Campo booleano inválido: $clave")
    }
}

internal data class ArregloJson(val elementos: List<ValorJson>) : ValorJson

internal data class TextoJson(val valor: String) : ValorJson

internal data class NumeroJson(val raw: String) : ValorJson

internal data class BooleanoJson(val valor: Boolean) : ValorJson

internal data object NuloJson : ValorJson

/**
 * Lector estricto de JSON (RFC 8259) sin dependencias de terceros para evitar entradas
 * adicionales en `verification-metadata.xml` y funcionar idéntico en Android y pruebas JVM.
 */
internal object JsonLector {
    private const val PROFUNDIDAD_MAXIMA = 32

    fun analizar(entrada: String): ValorJson {
        val lector = CursorJson(entrada)
        val valor = lector.leerValor(profundidad = 0)
        lector.saltarEspacios()
        if (!lector.alFinal()) {
            throw JsonFormatoException("Contenido inesperado al final del JSON en posición ${lector.posicion}")
        }
        return valor
    }

    fun analizarObjeto(entrada: String): ObjetoJson =
        analizar(entrada) as? ObjetoJson
            ?: throw JsonFormatoException("Se esperaba un objeto JSON")

    fun analizarArreglo(entrada: String): ArregloJson =
        analizar(entrada) as? ArregloJson
            ?: throw JsonFormatoException("Se esperaba un arreglo JSON")

    private class CursorJson(private val texto: String) {
        var posicion: Int = 0
            private set

        fun alFinal(): Boolean = posicion >= texto.length

        fun saltarEspacios() {
            while (posicion < texto.length && texto[posicion] in ESPACIOS) {
                posicion++
            }
        }

        fun leerValor(profundidad: Int): ValorJson {
            if (profundidad > PROFUNDIDAD_MAXIMA) {
                throw JsonFormatoException("Profundidad máxima de JSON excedida")
            }
            saltarEspacios()
            if (alFinal()) throw JsonFormatoException("Fin inesperado de entrada JSON")
            return when (texto[posicion]) {
                '{' -> leerObjeto(profundidad + 1)
                '[' -> leerArreglo(profundidad + 1)
                '"' -> TextoJson(leerCadena())
                't' -> {
                    consumirLiteral("true")
                    BooleanoJson(true)
                }
                'f' -> {
                    consumirLiteral("false")
                    BooleanoJson(false)
                }
                'n' -> {
                    consumirLiteral("null")
                    NuloJson
                }
                '-', in '0'..'9' -> NumeroJson(leerNumero())
                else -> throw JsonFormatoException("Carácter inesperado '${texto[posicion]}' en posición $posicion")
            }
        }

        private fun leerObjeto(profundidad: Int): ObjetoJson {
            consumirCaracter('{')
            saltarEspacios()
            val campos = LinkedHashMap<String, ValorJson>()
            if (!alFinal() && texto[posicion] == '}') {
                posicion++
                return ObjetoJson(campos)
            }
            while (true) {
                saltarEspacios()
                if (alFinal() || texto[posicion] != '"') {
                    throw JsonFormatoException("Se esperaba clave de texto en posición $posicion")
                }
                val clave = leerCadena()
                saltarEspacios()
                consumirCaracter(':')
                val valor = leerValor(profundidad)
                campos[clave] = valor
                saltarEspacios()
                if (alFinal()) throw JsonFormatoException("Objeto JSON sin cerrar")
                when (texto[posicion]) {
                    ',' -> posicion++
                    '}' -> {
                        posicion++
                        return ObjetoJson(campos)
                    }
                    else -> throw JsonFormatoException("Se esperaba ',' o '}' en posición $posicion")
                }
            }
        }

        private fun leerArreglo(profundidad: Int): ArregloJson {
            consumirCaracter('[')
            saltarEspacios()
            val elementos = ArrayList<ValorJson>()
            if (!alFinal() && texto[posicion] == ']') {
                posicion++
                return ArregloJson(elementos)
            }
            while (true) {
                elementos.add(leerValor(profundidad))
                saltarEspacios()
                if (alFinal()) throw JsonFormatoException("Arreglo JSON sin cerrar")
                when (texto[posicion]) {
                    ',' -> posicion++
                    ']' -> {
                        posicion++
                        return ArregloJson(elementos)
                    }
                    else -> throw JsonFormatoException("Se esperaba ',' o ']' en posición $posicion")
                }
            }
        }

        private fun leerCadena(): String {
            consumirCaracter('"')
            val sb = StringBuilder()
            while (!alFinal()) {
                val c = texto[posicion++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (alFinal()) throw JsonFormatoException("Secuencia de escape incompleta")
                        when (val esc = texto[posicion++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (posicion + 4 > texto.length) {
                                    throw JsonFormatoException("Escape unicode incompleto")
                                }
                                val hex = texto.substring(posicion, posicion + 4)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                    throw JsonFormatoException("Escape unicode inválido: \\u$hex")
                                }
                                sb.append(hex.toInt(16).toChar())
                                posicion += 4
                            }
                            else -> throw JsonFormatoException("Secuencia de escape inválida: \\$esc")
                        }
                    }
                    c.code < 0x20 -> throw JsonFormatoException("Carácter de control sin escapar en cadena")
                    else -> sb.append(c)
                }
            }
            throw JsonFormatoException("Cadena JSON sin cerrar")
        }

        private fun leerNumero(): String {
            val inicio = posicion
            if (texto[posicion] == '-') posicion++
            if (alFinal()) throw JsonFormatoException("Número JSON incompleto")
            if (texto[posicion] == '0') {
                posicion++
            } else if (texto[posicion] in '1'..'9') {
                while (!alFinal() && texto[posicion] in '0'..'9') posicion++
            } else {
                throw JsonFormatoException("Número JSON inválido en posición $posicion")
            }
            if (!alFinal() && texto[posicion] == '.') {
                posicion++
                if (alFinal() || texto[posicion] !in '0'..'9') {
                    throw JsonFormatoException("Fracción decimal inválida en posición $posicion")
                }
                while (!alFinal() && texto[posicion] in '0'..'9') posicion++
            }
            if (!alFinal() && (texto[posicion] == 'e' || texto[posicion] == 'E')) {
                posicion++
                if (!alFinal() && (texto[posicion] == '+' || texto[posicion] == '-')) posicion++
                val inicioExp = posicion
                if (alFinal() || texto[posicion] !in '0'..'9') {
                    throw JsonFormatoException("Exponente numérico inválido en posición $posicion")
                }
                while (!alFinal() && texto[posicion] in '0'..'9') posicion++
                if (posicion - inicioExp > MAX_DIGITOS_EXPONENTE) {
                    throw JsonFormatoException("Exponente numérico fuera de rango")
                }
            }
            return texto.substring(inicio, posicion)
        }

        private fun consumirLiteral(literal: String) {
            if (!texto.startsWith(literal, startIndex = posicion)) {
                throw JsonFormatoException("Literal esperado '$literal' en posición $posicion")
            }
            posicion += literal.length
        }

        private fun consumirCaracter(esperado: Char) {
            if (alFinal() || texto[posicion] != esperado) {
                throw JsonFormatoException("Se esperaba '$esperado' en posición $posicion")
            }
            posicion++
        }

        companion object {
            private const val MAX_DIGITOS_EXPONENTE = 4
            private val ESPACIOS = setOf(' ', '\t', '\r', '\n')
        }
    }
}
