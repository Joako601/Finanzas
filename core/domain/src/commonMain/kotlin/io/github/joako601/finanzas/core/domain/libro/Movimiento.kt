package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

@JvmInline
public value class MovimientoId(public val valor: Long)

/**
 * Una línea de un movimiento: cuánto entra o sale de una cuenta.
 * Positivo = cargo (aumenta activos y gastos); negativo = abono. Ver [TipoCuenta].
 */
public data class Asiento(
    val cuenta: CuentaId,
    val monto: Dinero,
) {
    init {
        require(!monto.esCero) { "Un asiento en cero no aporta nada" }
    }
}

/**
 * Un hecho económico registrado en el libro: un gasto, un ingreso, un saldo inicial y,
 * más adelante, transferencias, pagos de tarjeta, compras a MSI y devoluciones.
 *
 * Invariante de partida doble: tiene al menos dos asientos y suman exactamente cero.
 * No se puede construir un movimiento que no cuadre; el constructor lo rechaza.
 * Gracias a eso, la suma de todos los saldos del libro siempre es cero, y cualquier
 * descuadre es un error de programación que se detecta en el momento de crearlo.
 *
 * [id] es `null` mientras el movimiento no se ha guardado.
 * [meses]: plazo de una compra a meses sin intereses con tarjeta; `null` si es de contado.
 */
public data class Movimiento(
    val id: MovimientoId?,
    val fecha: LocalDate,
    val descripcion: String,
    val asientos: List<Asiento>,
    val meses: Int? = null,
) {
    init {
        require(asientos.size >= 2) { "Un movimiento necesita al menos dos asientos" }
        require(asientos.map { it.monto }.sumar().esCero) { "Los asientos no suman cero" }
        require(descripcion.length <= LARGO_MAXIMO_DESCRIPCION) { "Descripción demasiado larga" }
        require(meses == null || meses in PLAZOS_MSI) { "Plazo de meses sin intereses no válido" }
    }

    public companion object {
        public const val LARGO_MAXIMO_DESCRIPCION: Int = 200

        /** Plazos que ofrecen los bancos en México para compras a meses sin intereses. */
        public val PLAZOS_MSI: List<Int> = listOf(3, 6, 9, 12, 18, 24)
    }
}
