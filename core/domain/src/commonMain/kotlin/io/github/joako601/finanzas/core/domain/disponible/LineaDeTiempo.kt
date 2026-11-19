package io.github.joako601.finanzas.core.domain.disponible

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.totalEnCuentas
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.siguienteDespues
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.PagoATarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.corteCapturado
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import io.github.joako601.finanzas.core.domain.tarjetas.situacionTarjeta
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Qué pasa en una fecha futura. El orden es el del mismo día: primero lo que sale, al final lo que entra. */
public enum class TipoEvento { PAGO_TARJETA, PAGO_FIJO, CORTE, INGRESO_ESPERADO }

/**
 * Algo que pasará con el dinero en los próximos días.
 *
 * [monto] siempre es positivo; si entra o sale lo dice [tipo] (un corte no mueve dinero y
 * va en cero). [exacto] es falso cuando es un estimado: el monto aproximado de un pago
 * fijo o la deuda de una tarjeta cuyo estado de cuenta no se ha capturado.
 * [vencido]: un pago fijo cuya fecha ya pasó y no se ha registrado.
 * [saldoDespues]: lo que quedaría en efectivo y débito después de este evento.
 */
public data class EventoFuturo(
    val fecha: LocalDate,
    val tipo: TipoEvento,
    val nombre: String,
    val monto: Dinero,
    val exacto: Boolean,
    val vencido: Boolean = false,
    val categoria: CuentaId? = null,
    val saldoDespues: Dinero = Dinero.CERO,
) {
    val esSalida: Boolean get() = tipo == TipoEvento.PAGO_TARJETA || tipo == TipoEvento.PAGO_FIJO
}

/** El primer día en que el saldo proyectado queda en negativo, y por cuánto. */
public data class Faltante(val fecha: LocalDate, val falta: Dinero)

/**
 * Los próximos [DIAS] días de dinero y el "disponible real".
 *
 * Disponible real = dinero en efectivo y débito menos lo que hay que pagar hasta
 * [horizonte]: el próximo ingreso esperado o, si no hay, el día 30. Los ingresos no se
 * suman al disponible: marcan hasta cuándo tiene que alcanzar.
 *
 * SUPUESTOS (D-017):
 * - S9. Sin estado de cuenta capturado, el pago de la tarjeta se estima con la deuda
 *   actual que no cubre ya el estado vigente. Es prudente: exagera si hay compras a MSI
 *   o compras después del corte.
 * - S10. Los pagos fijos cuentan por su monto aproximado; los vencidos siguen pendientes.
 * - S11. Un ingreso esperado cuya fecha ya pasó no se cuenta: no se sabe si llegó.
 */
public data class LineaDeTiempo(
    val hoy: LocalDate,
    val enCuentas: Dinero,
    val horizonte: LocalDate,
    /** El ingreso que cierra el horizonte; null si el horizonte son los 30 días. */
    val ingresoQueCierra: EventoFuturo?,
    /** Ordenados por fecha; los pagos vencidos van primero. */
    val eventos: List<EventoFuturo>,
) {
    /** Lo que sale antes del horizonte (incluido el mismo día del ingreso). */
    val comprometido: List<EventoFuturo> get() = eventos.filter { it.esSalida && it.fecha <= horizonte }

    val disponible: Dinero get() = enCuentas - comprometido.map { it.monto }.sumar()

    val primerFaltante: Faltante?
        get() = eventos.firstOrNull { it.saldoDespues.esNegativo }?.let { Faltante(it.fecha, -it.saldoDespues) }

    public companion object {
        public const val DIAS: Int = 30

        public fun calcular(
            cuentas: List<Cuenta>,
            saldos: Map<CuentaId, Dinero>,
            tarjetas: List<Tarjeta>,
            estados: List<EstadoDeCuenta>,
            pagos: List<PagoATarjeta>,
            recurrentes: List<Recurrente>,
            hoy: LocalDate,
        ): LineaDeTiempo {
            val fin = hoy.plus(DIAS, DateTimeUnit.DAY)
            val sinSaldo = buildList {
                tarjetas.filterNot { it.cuenta.archivada }.forEach { addAll(deTarjeta(it, estados, pagos, saldos, hoy, fin)) }
                recurrentes.forEach { addAll(deRecurrente(it, hoy, fin)) }
            }.sortedWith(compareBy({ it.fecha }, { it.tipo.ordinal }))

            val enCuentas = totalEnCuentas(cuentas, saldos)
            var saldo = enCuentas
            val eventos = sinSaldo.map { evento ->
                saldo = when {
                    evento.esSalida -> saldo - evento.monto
                    evento.tipo == TipoEvento.INGRESO_ESPERADO -> saldo + evento.monto
                    else -> saldo
                }
                evento.copy(saldoDespues = saldo)
            }

            val ingreso = eventos.firstOrNull { it.tipo == TipoEvento.INGRESO_ESPERADO && it.fecha > hoy }
            return LineaDeTiempo(hoy, enCuentas, ingreso?.fecha ?: fin, ingreso, eventos)
        }

        /**
         * Pagos y cortes de una tarjeta hasta [fin]:
         * - el estado vigente con lo que falta pagar (exacto);
         * - el primer ciclo sin capturar cuya fecha límite cae antes de [fin], con la deuda
         *   que el vigente no cubre (estimado, S9). Los siguientes no se estiman: sus
         *   compras todavía no existen;
         * - los cortes, como referencia (no mueven dinero).
         */
        private fun deTarjeta(
            tarjeta: Tarjeta,
            estados: List<EstadoDeCuenta>,
            pagos: List<PagoATarjeta>,
            saldos: Map<CuentaId, Dinero>,
            hoy: LocalDate,
            fin: LocalDate,
        ): List<EventoFuturo> = buildList {
            val nombre = tarjeta.cuenta.nombre
            val propios = estados.filter { it.tarjeta == tarjeta.id }

            val situacion = situacionTarjeta(tarjeta, propios, pagos, hoy)
            val cubierto = if (situacion is SituacionTarjeta.PagoPendiente) situacion.restante else Dinero.CERO
            if (situacion is SituacionTarjeta.PagoPendiente && situacion.estado.fechaLimite <= fin) {
                add(EventoFuturo(situacion.estado.fechaLimite, TipoEvento.PAGO_TARJETA, nombre, situacion.restante, exacto = true))
            }

            // El estado de cuenta se genera al cierre del corte: se empieza por el último corte antes de hoy.
            var corte = CalendarioTarjeta.ultimoCorteHasta(tarjeta, hoy.minus(1, DateTimeUnit.DAY))
            while (true) {
                val limite = CalendarioTarjeta.limiteParaCorte(tarjeta, corte)
                if (limite > fin) break
                if (limite >= hoy && !corteCapturado(propios, corte)) {
                    val estimado = deudaDe(tarjeta, saldos) - cubierto
                    if (estimado.esPositivo) add(EventoFuturo(limite, TipoEvento.PAGO_TARJETA, nombre, estimado, exacto = false))
                    break
                }
                corte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, corte.plus(1, DateTimeUnit.DAY))
            }

            var proximoCorte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, hoy)
            while (proximoCorte <= fin) {
                add(EventoFuturo(proximoCorte, TipoEvento.CORTE, nombre, Dinero.CERO, exacto = true))
                proximoCorte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, proximoCorte.plus(1, DateTimeUnit.DAY))
            }
        }

        /** Cada fecha de la serie desde la pendiente hasta [fin]. Los ingresos vencidos se omiten (S11). */
        private fun deRecurrente(recurrente: Recurrente, hoy: LocalDate, fin: LocalDate): List<EventoFuturo> = buildList {
            val esIngreso = recurrente.clase == ClaseRegistro.INGRESO
            val tipo = if (esIngreso) TipoEvento.INGRESO_ESPERADO else TipoEvento.PAGO_FIJO
            var fecha = recurrente.proxima
            while (fecha <= fin) {
                val vencido = fecha < hoy
                if (!(esIngreso && vencido)) {
                    add(
                        EventoFuturo(
                            fecha, tipo, recurrente.nombre, recurrente.montoEstimado,
                            exacto = false, vencido = vencido, categoria = recurrente.categoria,
                        ),
                    )
                }
                fecha = recurrente.periodicidad.siguienteDespues(recurrente.inicio, fecha)
            }
        }
    }
}
