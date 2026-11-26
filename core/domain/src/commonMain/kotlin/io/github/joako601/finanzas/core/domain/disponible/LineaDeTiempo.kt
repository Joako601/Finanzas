package io.github.joako601.finanzas.core.domain.disponible

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.totalEnCuentas
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.siguienteDespues
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.CompraAMeses
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.PagoATarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.corteCapturado
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import io.github.joako601.finanzas.core.domain.tarjetas.situacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.vencidoPendiente
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Qué pasa en una fecha. El orden es el del mismo día: primero lo que sale, al final lo que entra. */
public enum class TipoEvento { PAGO_TARJETA, PAGO_FIJO, CORTE, INGRESO_ESPERADO }

/**
 * Algo que pasará con el dinero en los próximos días.
 *
 * [monto] siempre es positivo; si entra o sale lo dice [tipo] (un corte no mueve dinero y
 * va en cero). [exacto] es falso cuando es un estimado: el monto aproximado de un pago
 * fijo o la deuda de una tarjeta sin estado de cuenta capturado.
 * [vencido]: su fecha ya pasó y sigue sin pagarse.
 * [saldoDespues]: lo que quedaría en efectivo y débito después de este evento.
 * [recurrente] y [tarjeta] dicen de dónde sale, para poder registrarlo o pagarlo.
 */
public data class EventoFuturo(
    val fecha: LocalDate,
    val tipo: TipoEvento,
    val nombre: String,
    val monto: Dinero,
    val exacto: Boolean,
    val vencido: Boolean = false,
    val categoria: CuentaId? = null,
    val recurrente: RecurrenteId? = null,
    val tarjeta: CuentaId? = null,
    val saldoDespues: Dinero = Dinero.CERO,
) {
    /** Un pago fijo con [tarjeta] es un cargo domiciliado: se paga después, con la tarjeta (D-023). */
    val esSalida: Boolean get() = tipo == TipoEvento.PAGO_TARJETA || (tipo == TipoEvento.PAGO_FIJO && tarjeta == null)
}

/** El primer día en que el saldo proyectado queda en negativo, y por cuánto. */
public data class Faltante(val fecha: LocalDate, val falta: Dinero)

/**
 * Los próximos [DIAS] días de dinero y el "disponible real".
 *
 * **Disponible real** = lo que se puede gastar hoy sin quedar en negativo ningún día hasta
 * [fin], contando los pagos y los ingresos que se esperan. Es el saldo proyectado más bajo,
 * y nunca más que lo que hay hoy en efectivo y débito (un ingreso futuro no se gasta hoy).
 *
 * SUPUESTOS (D-017):
 * - S9. Sin estado de cuenta capturado, el pago de la tarjeta se estima con la deuda que
 *   no cubren ya los estados vencidos o vigente, menos las mensualidades MSI que ese corte
 *   todavía no factura (D-022); puede quedarse corto por las compras que todavía no se hacen.
 * - S10. Los pagos fijos cuentan por su monto aproximado; los vencidos siguen pendientes.
 * - S11. Un ingreso cuya fecha ya pasó sin registrarse no se cuenta: no se sabe si llegó.
 * - S14. Un pago fijo domiciliado a una tarjeta no sale del efectivo en su fecha; se paga
 *   con la tarjeta. Si su corte aún no se captura, el pago estimado de la tarjeta puede
 *   quedarse corto por ese cargo, igual que en S9 (D-023).
 * - S12. Los ingresos esperados llegan en su fecha. Si se retrasan, el disponible sale
 *   más alto de lo que conviene hasta registrarlos u omitirlos.
 */
public data class LineaDeTiempo(
    val hoy: LocalDate,
    val enCuentas: Dinero,
    val fin: LocalDate,
    /** Ordenados por fecha; los vencidos van primero. */
    val eventos: List<EventoFuturo>,
) {
    val disponible: Dinero = (eventos.map { it.saldoDespues } + enCuentas).min()

    /** Lo que el disponible aparta de lo que hay hoy en cuentas. */
    val comprometido: Dinero get() = enCuentas - disponible

    /** El evento tras el que el saldo queda más bajo, si baja de lo que hay hoy. */
    val puntoMasBajo: EventoFuturo? = eventos.filter { it.saldoDespues < enCuentas }.minByOrNull { it.saldoDespues }

    /**
     * El día hasta el que tiene que alcanzar el disponible: el del [puntoMasBajo], o hoy si
     * ese punto es un pago vencido (nunca una fecha pasada).
     */
    val diaMasBajo: LocalDate? get() = puntoMasBajo?.let { maxOf(it.fecha, hoy) }

    val primerFaltante: Faltante?
        get() {
            val fechaPrimera = when {
                enCuentas.esNegativo -> hoy
                else -> eventos.firstOrNull { it.saldoDespues.esNegativo }?.let { maxOf(it.fecha, hoy) }
            } ?: return null
            val menorEseDia = buildList {
                if (fechaPrimera == hoy) add(enCuentas)
                eventos.forEach { if (maxOf(it.fecha, hoy) == fechaPrimera) add(it.saldoDespues) }
            }.min()
            return Faltante(fechaPrimera, -menorEseDia)
        }

    /**
     * Si en un día posterior falta más que en el [primerFaltante], cuánto y cuándo: es la cifra
     * del disponible. Sin esto, "Hoy te faltan $1,128.50" no cuadraba con un disponible de
     * −$3,128.50 (caso real del S24 FE). Nunca repite la misma fecha que [primerFaltante].
     */
    val faltanteMayor: Faltante?
        get() {
            val primero = primerFaltante ?: return null
            val dia = diaMasBajo ?: return null
            return if (dia > primero.fecha && -disponible > primero.falta) Faltante(dia, -disponible) else null
        }

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
            // Sin valor por omisión: olvidar las MSI exageraría el pago estimado sin avisar.
            compras: List<CompraAMeses>,
        ): LineaDeTiempo {
            val fin = hoy.plus(DIAS, DateTimeUnit.DAY)
            val sinSaldo = buildList {
                tarjetas.filterNot { it.cuenta.archivada }.forEach { addAll(deTarjeta(it, estados, pagos, saldos, compras, hoy, fin)) }
                // Una tarjeta archivada no proyecta pagos: lo domiciliado a ella sale como pago fijo normal
                // (si no, no lo contaría nadie y el disponible se vería más alto de lo real).
                val idsTarjetas = tarjetas.filterNot { it.cuenta.archivada }.map { it.id }.toSet()
                recurrentes.forEach { addAll(deRecurrente(it, hoy, fin, it.cuenta?.takeIf { c -> c in idsTarjetas })) }
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
            return LineaDeTiempo(hoy, enCuentas, fin, eventos)
        }

        /**
         * Pagos y cortes de una tarjeta hasta [fin]. Lo que se pide nunca suma más que la
         * deuda: cada pago toma de lo que queda por cubrir, en este orden:
         * 1. el último estado vencido sin pagar, si no hay un estado vigente (que ya lo incluiría);
         * 2. lo que falta del estado vigente (exacto);
         * 3. el primer ciclo sin capturar cuya fecha límite cae en el plazo, con lo que reste
         *    de la deuda (estimado, S9). Los siguientes no se estiman: sus compras no existen.
         * Los cortes van como referencia (no mueven dinero).
         */
        private fun deTarjeta(
            tarjeta: Tarjeta,
            estados: List<EstadoDeCuenta>,
            pagos: List<PagoATarjeta>,
            saldos: Map<CuentaId, Dinero>,
            compras: List<CompraAMeses>,
            hoy: LocalDate,
            fin: LocalDate,
        ): List<EventoFuturo> = buildList {
            val nombre = tarjeta.cuenta.nombre
            val propios = estados.filter { it.tarjeta == tarjeta.id }
            val deuda = deudaDe(tarjeta, saldos)
            var porCubrir = if (deuda.esPositivo) deuda else Dinero.CERO

            fun pago(fecha: LocalDate, pedido: Dinero, exacto: Boolean, vencido: Boolean = false) {
                val monto = minOf(pedido, porCubrir)
                porCubrir -= monto
                if (monto.esPositivo && fecha <= fin) {
                    add(EventoFuturo(fecha, TipoEvento.PAGO_TARJETA, nombre, monto, exacto, vencido, tarjeta = tarjeta.id))
                }
            }

            when (val situacion = situacionTarjeta(tarjeta, propios, pagos, hoy, deuda)) {
                is SituacionTarjeta.PagoPendiente -> pago(situacion.estado.fechaLimite, situacion.restante, exacto = true)
                is SituacionTarjeta.Pagado -> Unit
                else -> vencidoPendiente(propios, pagos, hoy, deuda)?.let { vencido ->
                    pago(vencido.estado.fechaLimite, vencido.restante, exacto = true, vencido = true)
                }
            }

            // El estado de cuenta se genera al cierre del corte: se empieza por el último corte antes de hoy.
            var corte = CalendarioTarjeta.ultimoCorteHasta(tarjeta, hoy.minus(1, DateTimeUnit.DAY))
            while (true) {
                val limite = CalendarioTarjeta.limiteParaCorte(tarjeta, corte)
                if (limite > fin) break
                if (limite >= hoy && !corteCapturado(propios, corte)) {
                    val porFacturar = compras.filter { it.tarjeta.id == tarjeta.id }.map { it.porFacturarAl(corte) }.sumar()
                    pago(limite, maxOf(porCubrir - porFacturar, Dinero.CERO), exacto = false)
                    break
                }
                corte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, corte.plus(1, DateTimeUnit.DAY))
            }

            var proximoCorte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, hoy)
            while (proximoCorte <= fin) {
                add(EventoFuturo(proximoCorte, TipoEvento.CORTE, nombre, Dinero.CERO, exacto = true, tarjeta = tarjeta.id))
                proximoCorte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, proximoCorte.plus(1, DateTimeUnit.DAY))
            }
        }

        /** Cada fecha de la serie desde la pendiente hasta [fin]. Los ingresos vencidos se omiten (S11). */
        private fun deRecurrente(recurrente: Recurrente, hoy: LocalDate, fin: LocalDate, tarjeta: CuentaId?): List<EventoFuturo> = buildList {
            val esIngreso = recurrente.clase == ClaseRegistro.INGRESO
            val tipo = if (esIngreso) TipoEvento.INGRESO_ESPERADO else TipoEvento.PAGO_FIJO
            var fecha = recurrente.proxima
            while (fecha <= fin) {
                val vencido = fecha < hoy
                if (!(esIngreso && vencido)) {
                    add(
                        EventoFuturo(
                            fecha, tipo, recurrente.nombre, recurrente.montoEstimado, exacto = false,
                            vencido = vencido, categoria = recurrente.categoria, recurrente = recurrente.id, tarjeta = tarjeta,
                        ),
                    )
                }
                fecha = recurrente.periodicidad.siguienteDespues(recurrente.inicio, fecha)
            }
        }
    }
}
