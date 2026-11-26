package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.resumir
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Una compra a meses sin intereses (D-022). En el libro es un gasto normal con tarjeta: la
 * deuda sube completa el día de la compra, como la ve el banco en la línea de crédito. Aquí
 * solo se calcula qué parte ya se facturó y cuánto falta.
 *
 * SUPUESTO S13: cada corte en o después de la compra factura una mensualidad (una compra el
 * mismo día del corte entra en ese corte). Si el banco la difiere un mes, la app adelanta una.
 */
public data class CompraAMeses(
    val movimiento: Movimiento,
    val tarjeta: Tarjeta,
    val total: Dinero,
    val meses: Int,
) {
    init {
        require(meses > 0) { "Una compra a meses necesita plazo" }
        require(total.esPositivo) { "Una compra a meses necesita monto" }
    }

    /** La mensualidad (la primera, si el total no se divide exacto). */
    val cuota: Dinero get() = facturadoTras(1)

    /**
     * Lo facturado tras [n] mensualidades: piso de total·n/meses en centavos, sin desbordar.
     * La última mensualidad completa el total exacto.
     */
    public fun facturadoTras(n: Int): Dinero {
        val k = n.coerceIn(0, meses).toLong()
        val cociente = total.centavos / meses
        val residuo = total.centavos % meses
        return Dinero(cociente * k + residuo * k / meses)
    }

    /** Cuántas mensualidades se han facturado al [fecha]: los cortes desde la compra, hasta el plazo. */
    public fun facturadasAl(fecha: LocalDate): Int {
        var n = 0
        var corte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, movimiento.fecha)
        while (corte <= fecha && n < meses) {
            n++
            corte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, corte.plus(1, DateTimeUnit.DAY))
        }
        return n
    }

    /** Lo que sigue en la deuda pero el banco todavía no pide: las mensualidades sin facturar. */
    public fun porFacturarAl(fecha: LocalDate): Dinero = total - facturadoTras(facturadasAl(fecha))

    public fun activaAl(fecha: LocalDate): Boolean = facturadasAl(fecha) < meses
}

/** Las compras a meses de [tarjetas]: gastos con tarjeta que tienen plazo. */
public fun comprasAMeses(
    movimientos: Iterable<Movimiento>,
    cuentas: Map<CuentaId, Cuenta>,
    tarjetas: List<Tarjeta>,
): List<CompraAMeses> {
    val porId = tarjetas.associateBy { it.id }
    return movimientos.mapNotNull { movimiento ->
        val meses = movimiento.meses ?: return@mapNotNull null
        val resumen = movimiento.resumir(cuentas)
        val tarjeta = resumen.cuenta?.id?.let(porId::get)
        if (resumen.clase != ClaseMovimiento.GASTO || tarjeta == null) null else CompraAMeses(movimiento, tarjeta, resumen.monto, meses)
    }
}
