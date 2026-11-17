package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar

/** Cómo se presenta un movimiento en la interfaz. */
public enum class ClaseMovimiento { GASTO, INGRESO, SALDO_INICIAL, TRANSFERENCIA, OTRO }

/**
 * Vista de un movimiento en los términos del usuario: "gasté $100 de Efectivo en Súper".
 * [monto] siempre es positivo; el sentido lo da [clase].
 * [cuenta] y [categoria] son `null` cuando el movimiento involucra varias.
 */
public data class MovimientoResumido(
    val movimiento: Movimiento,
    val clase: ClaseMovimiento,
    val monto: Dinero,
    val cuenta: Cuenta?,
    val categoria: Cuenta?,
)

/**
 * Interpreta los asientos de un movimiento. Es la operación inversa de [Registro]:
 * lo que Registro arma como gasto, aquí se reconoce como gasto.
 */
public fun Movimiento.resumir(cuentas: Map<CuentaId, Cuenta>): MovimientoResumido {
    val conCuenta = asientos.map { asiento -> asiento to cuentas[asiento.cuenta] }
    val tipos = conCuenta.mapNotNull { it.second?.tipo }.toSet()

    val deDinero = conCuenta.filter { it.second?.tipo?.esDeDinero == true }
    val cuentaUnica = deDinero.mapNotNull { it.second }.distinct().singleOrNull()

    fun categoriaUnica(tipo: TipoCuenta): Cuenta? =
        conCuenta.mapNotNull { it.second }.filter { it.tipo == tipo }.distinct().singleOrNull()

    fun sumaDe(tipo: TipoCuenta): Dinero =
        conCuenta.filter { it.second?.tipo == tipo }.map { it.first.monto }.sumar()

    // Si alguna cuenta no se encontró, no adivinamos.
    if (conCuenta.any { it.second == null }) {
        return MovimientoResumido(this, ClaseMovimiento.OTRO, montoBruto(), null, null)
    }

    return when (tipos) {
        setOf(TipoCuenta.GASTO, TipoCuenta.ACTIVO),
        setOf(TipoCuenta.GASTO, TipoCuenta.PASIVO),
        setOf(TipoCuenta.GASTO, TipoCuenta.ACTIVO, TipoCuenta.PASIVO),
        -> MovimientoResumido(
            this, ClaseMovimiento.GASTO, sumaDe(TipoCuenta.GASTO), cuentaUnica, categoriaUnica(TipoCuenta.GASTO),
        )

        setOf(TipoCuenta.INGRESO, TipoCuenta.ACTIVO),
        setOf(TipoCuenta.INGRESO, TipoCuenta.PASIVO),
        setOf(TipoCuenta.INGRESO, TipoCuenta.ACTIVO, TipoCuenta.PASIVO),
        -> MovimientoResumido(
            this, ClaseMovimiento.INGRESO, -sumaDe(TipoCuenta.INGRESO), cuentaUnica, categoriaUnica(TipoCuenta.INGRESO),
        )

        setOf(TipoCuenta.CAPITAL, TipoCuenta.ACTIVO),
        setOf(TipoCuenta.CAPITAL, TipoCuenta.PASIVO),
        -> MovimientoResumido(
            this, ClaseMovimiento.SALDO_INICIAL, -sumaDe(TipoCuenta.CAPITAL), cuentaUnica, null,
        )

        setOf(TipoCuenta.ACTIVO),
        setOf(TipoCuenta.PASIVO),
        setOf(TipoCuenta.ACTIVO, TipoCuenta.PASIVO),
        -> MovimientoResumido(this, ClaseMovimiento.TRANSFERENCIA, montoBruto(), null, null)

        else -> MovimientoResumido(this, ClaseMovimiento.OTRO, montoBruto(), null, null)
    }
}

/** Suma de los cargos (lo positivo); en un movimiento que cuadra es igual a los abonos. */
private fun Movimiento.montoBruto(): Dinero = asientos.map { it.monto }.filter { it.esPositivo }.sumar()

/**
 * Saldo de cada cuenta a partir de los movimientos, sumando sus asientos.
 * La base de datos calcula lo mismo con `SUM ... GROUP BY`; esta versión es la
 * referencia contra la que se prueba esa consulta.
 */
public fun saldosPorCuenta(movimientos: Iterable<Movimiento>): Map<CuentaId, Dinero> {
    val saldos = mutableMapOf<CuentaId, Dinero>()
    for (movimiento in movimientos) {
        for (asiento in movimiento.asientos) {
            saldos[asiento.cuenta] = (saldos[asiento.cuenta] ?: Dinero.CERO) + asiento.monto
        }
    }
    return saldos
}

/**
 * Suma de los saldos de las cuentas de activo (efectivo y débito).
 *
 * Deliberadamente NO se llama "disponible real": esa cifra resta lo comprometido
 * (tarjetas, MSI, pagos recurrentes) y llega en una fase posterior.
 */
public fun totalEnCuentas(cuentas: Iterable<Cuenta>, saldos: Map<CuentaId, Dinero>): Dinero =
    cuentas.filter { it.tipo == TipoCuenta.ACTIVO }.map { saldos[it.id] ?: Dinero.CERO }.sumar()
