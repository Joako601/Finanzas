package io.github.joako601.finanzas.core.domain.dinero

import kotlin.jvm.JvmInline

/**
 * Una cantidad de pesos mexicanos, guardada como número entero de centavos.
 *
 * Por qué `Long` y no `BigDecimal` o `Double`:
 * - `Double` no representa 0.10 de forma exacta; sumar centavos acumula errores.
 * - `BigDecimal` solo existe en la JVM y el dominio es multiplataforma.
 * - Un `Long` de centavos cubre ±92 mil billones de pesos, sobra.
 *
 * Es una `value class`: en tiempo de ejecución es un `long` sin objeto alrededor,
 * pero el compilador no deja confundir un `Dinero` con cualquier otro `Long`.
 *
 * El rango es simétrico: se excluye `Long.MIN_VALUE` para que negar nunca desborde.
 * Toda operación aritmética que desbordaría lanza [ArithmeticException] en lugar de
 * dar la vuelta en silencio.
 */
@JvmInline
public value class Dinero(public val centavos: Long) : Comparable<Dinero> {

    init {
        require(centavos != Long.MIN_VALUE) { "Fuera del rango representable" }
    }

    public val esCero: Boolean get() = centavos == 0L
    public val esPositivo: Boolean get() = centavos > 0L
    public val esNegativo: Boolean get() = centavos < 0L

    public operator fun plus(otro: Dinero): Dinero = Dinero(sumaExacta(centavos, otro.centavos))

    public operator fun minus(otro: Dinero): Dinero = Dinero(sumaExacta(centavos, -otro.centavos))

    public operator fun unaryMinus(): Dinero = Dinero(-centavos)

    public fun absoluto(): Dinero = if (centavos < 0) -this else this

    override fun compareTo(other: Dinero): Int = centavos.compareTo(other.centavos)

    /**
     * Formato mexicano: signo de pesos, comas de miles y punto decimal.
     * Ejemplos: `$0.00`, `$1,234.50`, `-$12.05`.
     */
    public fun formatear(): String {
        val negativo = centavos < 0
        val absoluto = if (negativo) -centavos else centavos
        val enteros = (absoluto / 100).toString()
        val fraccion = (absoluto % 100).toString().padStart(2, '0')
        val agrupado = buildString(enteros.length + enteros.length / 3) {
            enteros.forEachIndexed { indice, digito ->
                if (indice > 0 && (enteros.length - indice) % 3 == 0) append(',')
                append(digito)
            }
        }
        return (if (negativo) "-$" else "$") + agrupado + "." + fraccion
    }

    override fun toString(): String = formatear()

    public companion object {
        public val CERO: Dinero = Dinero(0L)

        /**
         * Límite de dígitos enteros que acepta [parsear]: hasta 9,999,999,999,999 pesos.
         * Evita desbordes al convertir texto y rechaza capturas absurdas.
         */
        public const val MAXIMO_DIGITOS_ENTEROS: Int = 13

        /** Atajo para pruebas y constantes: `pesos(1234, 50)` es `$1,234.50`. */
        public fun pesos(pesos: Long, centavos: Long = 0L): Dinero {
            require(centavos in 0L..99L) { "Los centavos van de 0 a 99" }
            require(pesos >= 0L) { "Para cantidades negativas usa el operador unario menos" }
            return Dinero(sumaExacta(multiplicaPorCienExacto(pesos), centavos))
        }

        /**
         * Convierte lo que escribe una persona en una cantidad no negativa.
         *
         * Acepta: `100`, `100.5`, `100.50`, `.5`, `5.`, `1,234.56`, `$ 1,234`.
         * Rechaza (devuelve `null`):
         * - más de dos decimales (`1.005`): no se redondea en silencio;
         * - coma decimal o grupos mal formados (`12,5`, `1,23`, `1.234,50`);
         * - signos, letras o dígitos no ASCII;
         * - más de [MAXIMO_DIGITOS_ENTEROS] dígitos enteros.
         *
         * Nunca devuelve negativos: el signo lo decide el tipo de movimiento, no el texto.
         */
        public fun parsear(texto: String): Dinero? {
            var limpio = texto.trim()
            if (limpio.startsWith("$")) limpio = limpio.substring(1).trim()
            if (limpio.isEmpty()) return null

            val punto = limpio.indexOf('.')
            if (punto != limpio.lastIndexOf('.')) return null

            val parteEntera = if (punto >= 0) limpio.substring(0, punto) else limpio
            val parteDecimal = if (punto >= 0) limpio.substring(punto + 1) else ""

            if (parteEntera.isEmpty() && parteDecimal.isEmpty()) return null
            if (parteDecimal.length > 2 || !parteDecimal.all(::esDigitoAscii)) return null

            val digitosEnteros = digitosSinComas(parteEntera) ?: return null
            if (digitosEnteros.length > MAXIMO_DIGITOS_ENTEROS) return null

            val pesos = if (digitosEnteros.isEmpty()) 0L else digitosEnteros.toLong()
            val centavos = if (parteDecimal.isEmpty()) 0L else parteDecimal.padEnd(2, '0').toLong()
            return Dinero(pesos * 100 + centavos)
        }

        /**
         * Valida la parte entera y le quita las comas de miles.
         * Sin comas: solo dígitos. Con comas: primer grupo de 1 a 3 dígitos y los
         * siguientes de exactamente 3.
         */
        private fun digitosSinComas(parteEntera: String): String? {
            if (!parteEntera.contains(',')) {
                return if (parteEntera.all(::esDigitoAscii)) parteEntera else null
            }
            val grupos = parteEntera.split(',')
            val primero = grupos.first()
            if (primero.length !in 1..3 || !primero.all(::esDigitoAscii)) return null
            for (grupo in grupos.drop(1)) {
                if (grupo.length != 3 || !grupo.all(::esDigitoAscii)) return null
            }
            return grupos.joinToString(separator = "")
        }

        // `Char.isDigit()` acepta dígitos de otros alfabetos (árabe, devanagari...).
        private fun esDigitoAscii(caracter: Char): Boolean = caracter in '0'..'9'
    }
}

/** Suma una colección de cantidades; la suma de una colección vacía es [Dinero.CERO]. */
public fun Iterable<Dinero>.sumar(): Dinero = fold(Dinero.CERO) { acumulado, cantidad -> acumulado + cantidad }

// `Math.addExact` es de la JVM; esta es la versión común. Hay desborde cuando ambos
// sumandos tienen el mismo signo y el resultado tiene el contrario. `Long.MIN_VALUE`
// también cuenta como desborde porque queda fuera del rango simétrico de Dinero.
private fun sumaExacta(a: Long, b: Long): Long {
    val resultado = a + b
    if ((a xor resultado) and (b xor resultado) < 0 || resultado == Long.MIN_VALUE) {
        throw ArithmeticException("Desborde al sumar dinero")
    }
    return resultado
}

private fun multiplicaPorCienExacto(valor: Long): Long {
    if (valor > Long.MAX_VALUE / 100 || valor < -(Long.MAX_VALUE / 100)) {
        throw ArithmeticException("Desborde al convertir pesos a centavos")
    }
    return valor * 100
}
