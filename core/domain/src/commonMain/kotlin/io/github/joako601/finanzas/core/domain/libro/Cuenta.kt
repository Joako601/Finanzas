package io.github.joako601.finanzas.core.domain.libro

import kotlin.jvm.JvmInline

/**
 * Clasificación contable de una cuenta.
 *
 * En partida doble, "cuenta" significa cualquier lugar donde se anota dinero: lo que
 * el usuario llama cuenta (efectivo, débito) y también lo que llama categoría (comida,
 * sueldo). La interfaz los muestra separados, pero el libro los trata igual.
 *
 * Convención de signos (la estándar de contabilidad, con cargo positivo):
 * - ACTIVO y GASTO aumentan con montos positivos.
 * - PASIVO, CAPITAL e INGRESO aumentan con montos negativos.
 * Así, cada movimiento suma exactamente cero.
 */
public enum class TipoCuenta {
    /** Dinero que tienes: efectivo, cuentas de débito. */
    ACTIVO,

    /** Dinero que debes: tarjetas de crédito (en una fase posterior). */
    PASIVO,

    /** Contrapartida de los saldos iniciales: lo que ya tenías antes de usar la app. */
    CAPITAL,

    /** Categorías de ingreso: quincena, aguinaldo, PTU. */
    INGRESO,

    /** Categorías de gasto: súper, transporte, servicios. */
    GASTO,
    ;

    /** Cuentas donde vive el dinero, en contraste con las categorías. */
    public val esDeDinero: Boolean get() = this == ACTIVO || this == PASIVO

    public val esCategoria: Boolean get() = this == INGRESO || this == GASTO
}

@JvmInline
public value class CuentaId(public val valor: Long)

public data class Cuenta(
    val id: CuentaId,
    val nombre: String,
    val tipo: TipoCuenta,
    val archivada: Boolean = false,
) {
    init {
        require(nombre.isNotBlank()) { "La cuenta necesita nombre" }
        require(nombre.length <= LARGO_MAXIMO_NOMBRE) { "Nombre de cuenta demasiado largo" }
    }

    public companion object {
        public const val LARGO_MAXIMO_NOMBRE: Int = 60
    }
}
