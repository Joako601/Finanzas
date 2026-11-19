package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.resumir
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlin.math.abs

/** Qué tiene que saber el usuario de su tarjeta hoy. */
public sealed interface SituacionTarjeta {

    /** Hay un estado de cuenta vigente y falta pagar [restante] antes de la fecha límite. */
    public data class PagoPendiente(
        val estado: EstadoDeCuenta,
        val pagado: Dinero,
        val restante: Dinero,
        val diasParaLimite: Int,
    ) : SituacionTarjeta

    /** El estado de cuenta vigente ya está cubierto: no se generan intereses este ciclo. */
    public data class Pagado(val estado: EstadoDeCuenta, val pagado: Dinero) : SituacionTarjeta

    /** Ya pasó un corte y su estado de cuenta no se ha capturado. */
    public data class CapturaPendiente(val cortePredicho: LocalDate, val limitePredicho: LocalDate) : SituacionTarjeta

    /** Nada pendiente; el siguiente corte predicho es [proximoCorte]. */
    public data class EnCiclo(val proximoCorte: LocalDate) : SituacionTarjeta
}

/**
 * Margen para reconocer que un estado capturado corresponde a un corte predicho:
 * el banco puede recorrer el corte unos días (días inhábiles, supuesto S3).
 */
private const val TOLERANCIA_DIAS_CORTE = 5

/** ¿Alguno de [estados] es el estado de cuenta del [corte] predicho? Misma regla para la pantalla y los avisos. */
internal fun corteCapturado(estados: List<EstadoDeCuenta>, corte: LocalDate): Boolean =
    estados.any { abs(it.fechaCorte.daysUntil(corte)) <= TOLERANCIA_DIAS_CORTE }

/**
 * Calcula la situación de la tarjeta en [hoy].
 *
 * Reglas, en orden:
 * 1. Si hay un estado de cuenta con fecha límite hoy o después (el vigente), se compara
 *    su pago para no generar intereses contra los pagos hechos DESPUÉS de su fecha de
 *    corte. SUPUESTO S4: un pago hecho el mismo día del corte ya quedó reflejado en ese
 *    estado de cuenta y no cuenta para el siguiente.
 * 2. Si no hay vigente y ya pasó un corte predicho sin captura, se pide capturarlo
 *    mientras su fecha límite predicha no haya pasado.
 * 3. Si no, se muestra el siguiente corte.
 */
public fun situacionTarjeta(
    tarjeta: Tarjeta,
    estados: List<EstadoDeCuenta>,
    pagos: List<PagoATarjeta>,
    hoy: LocalDate,
): SituacionTarjeta {
    val propios = estados.filter { it.tarjeta == tarjeta.id }

    val vigente = propios.filter { it.fechaLimite >= hoy }.maxByOrNull { it.fechaCorte }
    if (vigente != null) {
        val pagado = pagos
            .filter { it.tarjeta == tarjeta.id && it.fecha > vigente.fechaCorte }
            .map { it.monto }
            .sumar()
        val restante = vigente.pagoSinIntereses - pagado
        return if (restante.esPositivo) {
            SituacionTarjeta.PagoPendiente(vigente, pagado, restante, hoy.daysUntil(vigente.fechaLimite))
        } else {
            SituacionTarjeta.Pagado(vigente, pagado)
        }
    }

    // El estado de cuenta se genera al cierre del día de corte: se pide a partir del día siguiente.
    val ultimoCorte = CalendarioTarjeta.ultimoCorteHasta(tarjeta, hoy.minus(1, DateTimeUnit.DAY))
    val yaCapturado = corteCapturado(propios, ultimoCorte)
    val limitePredicho = CalendarioTarjeta.limiteParaCorte(tarjeta, ultimoCorte)
    if (!yaCapturado && limitePredicho >= hoy) {
        return SituacionTarjeta.CapturaPendiente(ultimoCorte, limitePredicho)
    }

    return SituacionTarjeta.EnCiclo(CalendarioTarjeta.siguienteCorteDesde(tarjeta, hoy))
}

/** Un pago hecho a una tarjeta desde una cuenta de dinero. */
public data class PagoATarjeta(val tarjeta: CuentaId, val fecha: LocalDate, val monto: Dinero)

/**
 * Extrae los pagos a tarjetas: los movimientos que [resumir] reconoce como pago de tarjeta
 * (sale dinero de efectivo o débito y baja la deuda). Usa la misma regla que la lista de
 * movimientos para que un traspaso entre tarjetas, una compra o un saldo inicial nunca
 * cuenten como pago.
 */
public fun pagosATarjetas(movimientos: Iterable<Movimiento>, cuentas: Map<CuentaId, Cuenta>): List<PagoATarjeta> =
    movimientos
        .map { it.resumir(cuentas) }
        .filter { it.clase == ClaseMovimiento.PAGO_TARJETA }
        .mapNotNull { pago -> pago.contraparte?.let { PagoATarjeta(it.id, pago.movimiento.fecha, pago.monto) } }

/** Deuda actual de la tarjeta: el saldo de un pasivo es negativo cuando se debe. */
public fun deudaDe(tarjeta: Tarjeta, saldos: Map<CuentaId, Dinero>): Dinero = -(saldos[tarjeta.id] ?: Dinero.CERO)
