package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar

/** Cómo se presenta un movimiento en la interfaz. */
public enum class ClaseMovimiento { GASTO, INGRESO, SALDO_INICIAL, PAGO_TARJETA, TRANSFERENCIA, OTRO }

/**
 * Vista de un movimiento en los términos del usuario: "gasté $100 de Efectivo en Súper".
 * [monto] siempre es positivo; el sentido lo da [clase].
 * [cuenta] es la cuenta de dinero de la que sale o a la que llega; [categoria] la
 * categoría de gasto o ingreso; [contraparte] la tarjeta en un pago de tarjeta.
 * Cualquiera es `null` cuando no aplica o cuando el movimiento involucra varias.
 */
public data class MovimientoResumido(
    val movimiento: Movimiento,
    val clase: ClaseMovimiento,
    val monto: Dinero,
    val cuenta: Cuenta?,
    val categoria: Cuenta?,
    val contraparte: Cuenta? = null,
)

/**
 * Interpreta los asientos de un movimiento. Es la operación inversa de [Registro]:
 * lo que Registro arma como gasto, aquí se reconoce como gasto.
 */
public fun Movimiento.resumir(cuentas: Map<CuentaId, Cuenta>): MovimientoResumido {
    val conCuenta = asientos.map { asiento -> asiento to cuentas[asiento.cuenta] }

    // Si alguna cuenta no se encontró, no adivinamos.
    if (conCuenta.any { it.second == null }) {
        return MovimientoResumido(this, ClaseMovimiento.OTRO, montoBruto(), null, null)
    }

    val tipos = conCuenta.mapNotNull { it.second?.tipo }.toSet()

    fun unicaDe(filtro: (TipoCuenta) -> Boolean): Cuenta? =
        conCuenta.mapNotNull { it.second }.filter { filtro(it.tipo) }.distinct().singleOrNull()

    fun sumaDe(tipo: TipoCuenta): Dinero =
        conCuenta.filter { it.second?.tipo == tipo }.map { it.first.monto }.sumar()

    val cuentaDeDinero = unicaDe { it.esDeDinero }
    // Lo que no es cuenta de dinero: categorías de gasto o ingreso, o capital.
    val otrosTipos = tipos - DE_DINERO
    val tocaDinero = tipos.any { it in DE_DINERO }

    return when {
        tocaDinero && otrosTipos == setOf(TipoCuenta.GASTO) ->
            MovimientoResumido(
                this, ClaseMovimiento.GASTO, sumaDe(TipoCuenta.GASTO), cuentaDeDinero, unicaDe { it == TipoCuenta.GASTO },
            )

        tocaDinero && otrosTipos == setOf(TipoCuenta.INGRESO) ->
            MovimientoResumido(
                this, ClaseMovimiento.INGRESO, -sumaDe(TipoCuenta.INGRESO), cuentaDeDinero,
                unicaDe { it == TipoCuenta.INGRESO },
            )

        cuentaDeDinero != null && otrosTipos == setOf(TipoCuenta.CAPITAL) ->
            // En un activo el saldo inicial es positivo; en una tarjeta (deuda), negativo.
            // Se muestra el valor absoluto y la interfaz decide la etiqueta por el tipo de cuenta.
            MovimientoResumido(
                this, ClaseMovimiento.SALDO_INICIAL, sumaDe(TipoCuenta.CAPITAL).absoluto(), cuentaDeDinero, null,
            )

        tipos == setOf(TipoCuenta.ACTIVO, TipoCuenta.PASIVO) && esPagoDeTarjeta(conCuenta) ->
            MovimientoResumido(
                this, ClaseMovimiento.PAGO_TARJETA, sumaDe(TipoCuenta.PASIVO),
                unicaDe { it == TipoCuenta.ACTIVO }, null, unicaDe { it == TipoCuenta.PASIVO },
            )

        tipos.all { it in DE_DINERO } ->
            MovimientoResumido(this, ClaseMovimiento.TRANSFERENCIA, montoBruto(), null, null)

        else -> MovimientoResumido(this, ClaseMovimiento.OTRO, montoBruto(), null, null)
    }
}

private val DE_DINERO = setOf(TipoCuenta.ACTIVO, TipoCuenta.PASIVO)

/** Pago de tarjeta: todo lo que toca al pasivo es cargo (baja la deuda) y todo lo del activo, abono. */
private fun esPagoDeTarjeta(conCuenta: List<Pair<Asiento, Cuenta?>>): Boolean = conCuenta.all { (asiento, cuenta) ->
    when (cuenta?.tipo) {
        TipoCuenta.PASIVO -> asiento.monto.esPositivo
        TipoCuenta.ACTIVO -> asiento.monto.esNegativo
        else -> false
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
