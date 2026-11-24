package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Nivel semántico de utilización de la línea de crédito. */
public enum class NivelUtilizacionLinea {
    /** 0% a 30%: rango óptimo para salud crediticia y Buró. */
    SALUDABLE,
    /** 31% a 70%: uso moderado. */
    MODERADO,
    /** 71% a 100%: línea cercana al límite. */
    ALTO,
    /** Más del 100%: la deuda supera la línea autorizada. */
    SOBREGIRO,
}

/**
 * Estado de cuenta cuya fecha límite ya pasó (`fechaLimite < hoy`) y sigue con saldo
 * pendiente por cubrir. Complementa a [SituacionTarjeta] para que la interfaz nunca
 * oculte un pago vencido al día siguiente de la fecha límite.
 */
public data class PagoVencidoTarjeta(
    val estado: EstadoDeCuenta,
    val pagado: Dinero,
    val restante: Dinero,
    val diasVencido: Int,
)

/**
 * Progreso de pago del estado de cuenta activo (vigente o vencido), incluyendo
 * abonos parciales y cumplimiento del pago mínimo.
 */
public data class ProgresoPagoCiclo(
    val estado: EstadoDeCuenta,
    val exigibleOriginal: Dinero,
    val pagadoEnCiclo: Dinero,
    val restanteSinIntereses: Dinero,
    val pagoMinimoOriginal: Dinero,
    val restanteMinimo: Dinero,
    val minimoCubierto: Boolean,
    val tieneAbonoParcial: Boolean,
    val proporcionPagada: Float,
    val estaVencido: Boolean,
    val diasParaLimiteOVencido: Int,
)

/**
 * Separación transparente de la deuda de la tarjeta en dos cubetas:
 * 1. [exigibleCorteActual]: lo que resta pagar del estado de cuenta cerrado para no generar intereses.
 * 2. [comprasNuevoCiclo]: compras posteriores al corte (o saldo de meses futuros) que no vencen este mes.
 */
public data class DesgloseCreditoTarjeta(
    val deudaTotal: Dinero,
    val saldoAFavor: Dinero,
    val creditoDisponible: Dinero,
    val sobregiro: Dinero,
    val exigibleCorteActual: Dinero,
    val comprasNuevoCiclo: Dinero,
    val porcentajeUtilizacion: Int,
    val proporcionBarraUtilizacion: Float,
    val nivelUtilizacion: NivelUtilizacionLinea,
)

/** Categoría de consecuencia financiera al simular un monto de pago a la tarjeta. */
public enum class CategoriaSimulacionPago {
    /** El monto cubre el pago para no generar intereses del corte actual (`$0.00` intereses). */
    SIN_INTERESES,
    /** El monto liquida el 100% de la deuda total (corte actual + compras del nuevo ciclo). */
    LIQUIDACION_TOTAL,
    /** El monto supera la deuda total actual y deja saldo a favor en la tarjeta. */
    SALDO_A_FAVOR,
    /** No hay estado de cuenta capturado con corte exigible; un pago parcial podría generar intereses. */
    SIN_ESTADO_CAPTURADO,
    /** Cubre el pago mínimo (evita mora), pero deja saldo financiado que generará intereses + IVA. */
    GENERA_INTERESES_ORDINARIOS,
    /** No alcanza siquiera el pago mínimo pendiente: riesgo de comisión por falta de pago y mora. */
    DEBAJO_DEL_MINIMO,
}

/**
 * Resultado de simular un pago de [montoAPagar] con una tasa anual ordinaria de [tasaAnualPorcentaje].
 *
 * Incluye tanto el interés sobre el saldo no cubierto ([interesSobreSaldoFinanciado]) como el
 * interés sobre el saldo del corte ([interesSobreSaldoPromedioCorte]), ambos con 16% de IVA,
 * para explicar con transparencia cómo calculan los bancos el interés sobre el saldo promedio diario.
 */
public data class SimulacionPagoTarjeta(
    val montoAPagar: Dinero,
    val tasaAnualPorcentaje: Int,
    val categoria: CategoriaSimulacionPago,
    val saldoFinanciado: Dinero,
    val faltanteParaMinimo: Dinero,
    val deudaRestanteTrasPago: Dinero,
    val excedenteSaldoAFavor: Dinero,
    val interesSobreSaldoFinanciado: Dinero,
    val interesSobreSaldoPromedioCorte: Dinero,
)

/**
 * Radiografía completa de una tarjeta en [hoy]: situación del ciclo, progreso de abonos,
 * desglose de deuda, utilización de línea e historial de estados y pagos propios.
 */
public data class AnalisisTarjeta(
    val tarjeta: Tarjeta,
    val deuda: Dinero,
    val situacion: SituacionTarjeta,
    val vencidoPendiente: PagoVencidoTarjeta?,
    val progresoCiclo: ProgresoPagoCiclo?,
    val desglose: DesgloseCreditoTarjeta,
    val ultimoCorte: LocalDate,
    val proximoCorte: LocalDate,
    val estadosPropios: List<EstadoDeCuenta>,
    val pagosPropios: List<PagoATarjeta>,
)

public object AnalizadorTarjeta {

    /** Tasa anual ordinaria promedio de referencia en tarjetas bancarias en México (60%). */
    public const val TASA_ANUAL_REFERENCIA_MEXICO: Int = 60

    /** Tasa de IVA aplicable a los intereses ordinarios de crédito al consumo en México (16%). */
    private const val FACTOR_CON_IVA_MEXICO: Double = 1.16
    private const val MESES_POR_ANIO: Double = 12.0

    public fun analizar(
        tarjeta: Tarjeta,
        deuda: Dinero,
        estados: List<EstadoDeCuenta>,
        pagos: List<PagoATarjeta>,
        hoy: LocalDate,
    ): AnalisisTarjeta {
        val estadosPropios = estados
            .filter { it.tarjeta == tarjeta.id }
            .sortedByDescending { it.fechaCorte }
        val pagosPropios = pagos
            .filter { it.tarjeta == tarjeta.id }
            .sortedByDescending { it.fecha }

        val situacion = situacionTarjeta(tarjeta, estadosPropios, pagosPropios, hoy, deuda)

        val vencidoPendiente = if (
            situacion !is SituacionTarjeta.PagoPendiente &&
            situacion !is SituacionTarjeta.Pagado
        ) {
            vencidoPendiente(estadosPropios, pagosPropios, hoy, deuda)
        } else {
            null
        }

        val progresoCiclo = when {
            situacion is SituacionTarjeta.PagoPendiente -> construirProgreso(
                estado = situacion.estado,
                pagado = situacion.pagado,
                restante = situacion.restante,
                estaVencido = false,
                dias = situacion.diasParaLimite,
            )
            vencidoPendiente != null -> construirProgreso(
                estado = vencidoPendiente.estado,
                pagado = vencidoPendiente.pagado,
                restante = vencidoPendiente.restante,
                estaVencido = true,
                dias = vencidoPendiente.diasVencido,
            )
            else -> null
        }

        val deudaTotal = maxOf(deuda, Dinero.CERO)
        val saldoAFavor = if (deuda.esNegativo) -deuda else Dinero.CERO
        val creditoDisponible = if (deuda.esNegativo) {
            tarjeta.lineaDeCredito - deuda
        } else {
            maxOf(tarjeta.lineaDeCredito - deuda, Dinero.CERO)
        }
        val sobregiro = if (deuda > tarjeta.lineaDeCredito) {
            deuda - tarjeta.lineaDeCredito
        } else {
            Dinero.CERO
        }

        val exigibleCorteActual = progresoCiclo?.restanteSinIntereses ?: Dinero.CERO
        val comprasNuevoCiclo = maxOf(deudaTotal - exigibleCorteActual, Dinero.CERO)

        val porcentajeUtilizacion = when {
            !deudaTotal.esPositivo -> 0
            !tarjeta.lineaDeCredito.esPositivo -> 100
            else -> ((deudaTotal.centavos.toDouble() * 100.0) / tarjeta.lineaDeCredito.centavos.toDouble())
                .roundToInt()
                .coerceAtLeast(1)
        }
        val proporcionBarra = when {
            !deudaTotal.esPositivo -> 0f
            !tarjeta.lineaDeCredito.esPositivo -> 1f
            else -> (deudaTotal.centavos.toDouble() / tarjeta.lineaDeCredito.centavos.toDouble())
                .toFloat()
                .coerceIn(0.02f, 1f)
        }
        val nivelUtilizacion = when {
            sobregiro.esPositivo -> NivelUtilizacionLinea.SOBREGIRO
            porcentajeUtilizacion > 70 -> NivelUtilizacionLinea.ALTO
            porcentajeUtilizacion > 30 -> NivelUtilizacionLinea.MODERADO
            else -> NivelUtilizacionLinea.SALUDABLE
        }

        val desglose = DesgloseCreditoTarjeta(
            deudaTotal = deudaTotal,
            saldoAFavor = saldoAFavor,
            creditoDisponible = creditoDisponible,
            sobregiro = sobregiro,
            exigibleCorteActual = exigibleCorteActual,
            comprasNuevoCiclo = comprasNuevoCiclo,
            porcentajeUtilizacion = porcentajeUtilizacion,
            proporcionBarraUtilizacion = proporcionBarra,
            nivelUtilizacion = nivelUtilizacion,
        )

        return AnalisisTarjeta(
            tarjeta = tarjeta,
            deuda = deuda,
            situacion = situacion,
            vencidoPendiente = vencidoPendiente,
            progresoCiclo = progresoCiclo,
            desglose = desglose,
            ultimoCorte = CalendarioTarjeta.ultimoCorteHasta(tarjeta, hoy),
            proximoCorte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, hoy),
            estadosPropios = estadosPropios,
            pagosPropios = pagosPropios,
        )
    }

    /**
     * Simula la consecuencia financiera de abonar [montoAPagar] a la tarjeta.
     */
    public fun simularPago(
        analisis: AnalisisTarjeta,
        montoAPagar: Dinero,
        tasaAnualPorcentaje: Int = TASA_ANUAL_REFERENCIA_MEXICO,
    ): SimulacionPagoTarjeta {
        val monto = maxOf(montoAPagar, Dinero.CERO)
        val tasaSegura = tasaAnualPorcentaje.coerceIn(1, 200)
        val desglose = analisis.desglose
        val progreso = analisis.progresoCiclo

        val exigible = desglose.exigibleCorteActual
        val minimoRestante = progreso?.restanteMinimo ?: Dinero.CERO
        val deudaTotal = desglose.deudaTotal

        val saldoFinanciado = maxOf(exigible - monto, Dinero.CERO)
        val faltanteMinimo = maxOf(minimoRestante - monto, Dinero.CERO)
        val deudaRestante = maxOf(deudaTotal - monto, Dinero.CERO)
        val excedenteAFavor = if (monto > deudaTotal) {
            (monto - deudaTotal) + desglose.saldoAFavor
        } else {
            Dinero.CERO
        }

        val categoria = when {
            exigible.esPositivo && monto < minimoRestante ->
                CategoriaSimulacionPago.DEBAJO_DEL_MINIMO
            exigible.esPositivo && monto < exigible ->
                CategoriaSimulacionPago.GENERA_INTERESES_ORDINARIOS
            monto > deudaTotal ->
                CategoriaSimulacionPago.SALDO_A_FAVOR
            deudaTotal.esPositivo && monto == deudaTotal ->
                CategoriaSimulacionPago.LIQUIDACION_TOTAL
            progreso == null && deudaTotal.esPositivo && monto < deudaTotal ->
                CategoriaSimulacionPago.SIN_ESTADO_CAPTURADO
            else ->
                CategoriaSimulacionPago.SIN_INTERESES
        }

        val generaInteres = categoria == CategoriaSimulacionPago.DEBAJO_DEL_MINIMO ||
            categoria == CategoriaSimulacionPago.GENERA_INTERESES_ORDINARIOS

        val interesRestante = if (generaInteres && saldoFinanciado.esPositivo) {
            calcularInteresMensualConIva(saldoFinanciado, tasaSegura)
        } else {
            Dinero.CERO
        }

        // Base del saldo promedio diario del ciclo: el exigible original del estado de cuenta (o el restante si es mayor).
        val baseSaldoCorte = maxOf(progreso?.exigibleOriginal ?: exigible, saldoFinanciado)
        val interesSaldoPromedio = if (generaInteres && baseSaldoCorte.esPositivo) {
            calcularInteresMensualConIva(baseSaldoCorte, tasaSegura)
        } else {
            Dinero.CERO
        }

        return SimulacionPagoTarjeta(
            montoAPagar = monto,
            tasaAnualPorcentaje = tasaSegura,
            categoria = categoria,
            saldoFinanciado = saldoFinanciado,
            faltanteParaMinimo = faltanteMinimo,
            deudaRestanteTrasPago = deudaRestante,
            excedenteSaldoAFavor = excedenteAFavor,
            interesSobreSaldoFinanciado = interesRestante,
            interesSobreSaldoPromedioCorte = maxOf(interesSaldoPromedio, interesRestante),
        )
    }

    private fun calcularInteresMensualConIva(
        base: Dinero,
        tasaAnualPorcentaje: Int,
    ): Dinero {
        if (!base.esPositivo || tasaAnualPorcentaje <= 0) return Dinero.CERO
        val tasaMensual = (tasaAnualPorcentaje.toDouble() / 100.0) / MESES_POR_ANIO
        val centavosInteresConIva = (base.centavos.toDouble() * tasaMensual * FACTOR_CON_IVA_MEXICO).roundToLong()
        return Dinero(centavosInteresConIva)
    }

    private fun construirProgreso(
        estado: EstadoDeCuenta,
        pagado: Dinero,
        restante: Dinero,
        estaVencido: Boolean,
        dias: Int,
    ): ProgresoPagoCiclo {
        val totalEfectivoCiclo = pagado + restante
        val proporcion = if (totalEfectivoCiclo.esPositivo) {
            (pagado.centavos.toDouble() / totalEfectivoCiclo.centavos.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            1f
        }
        val restanteMinimo = minOf(restante, maxOf(estado.pagoMinimo - pagado, Dinero.CERO))
        return ProgresoPagoCiclo(
            estado = estado,
            exigibleOriginal = estado.pagoSinIntereses,
            pagadoEnCiclo = pagado,
            restanteSinIntereses = restante,
            pagoMinimoOriginal = estado.pagoMinimo,
            restanteMinimo = restanteMinimo,
            minimoCubierto = restanteMinimo.esCero,
            tieneAbonoParcial = pagado.esPositivo && restante.esPositivo,
            proporcionPagada = proporcion,
            estaVencido = estaVencido,
            diasParaLimiteOVencido = dias,
        )
    }
}
