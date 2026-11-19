package io.github.joako601.finanzas.core.domain.recordatorios

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.PagoATarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.corteCapturado
import io.github.joako601.finanzas.core.domain.tarjetas.situacionTarjeta
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

public enum class TipoRecordatorio {
    /** Un día antes del corte: lo que se compre después entra al siguiente estado de cuenta. */
    ANTES_DEL_CORTE,

    /** Dos días después del corte, si su estado de cuenta no se ha capturado. */
    CAPTURAR_ESTADO,

    /** Tres días y un día antes de la fecha límite, si falta pagar. */
    PAGO_PROXIMO,

    /** Un día antes de un pago fijo (CFE, internet, predial). Los ingresos esperados no avisan. */
    PAGO_RECURRENTE,
}

/**
 * Un aviso que la app debe mostrar en [momento] (hora local del teléfono).
 *
 * [clave] lo identifica de forma estable: la misma tarjeta, el mismo ciclo y el mismo
 * tipo producen siempre la misma clave. Con ella se evita avisar dos veces y se mide
 * el retraso de entrega.
 *
 * [nombre] es la tarjeta o el pago fijo del que se avisa.
 * [fechaReferencia] es el corte (antes del corte, capturar), la fecha límite (pago de
 * tarjeta) o la fecha del pago fijo.
 * [monto] es lo que falta para no generar intereses, o el monto estimado del pago fijo.
 */
public data class Recordatorio(
    val clave: String,
    val tipo: TipoRecordatorio,
    val momento: LocalDateTime,
    val nombre: String,
    val fechaReferencia: LocalDate,
    val monto: Dinero? = null,
)

/**
 * Qué recordatorios tocan, calculado SIEMPRE con los datos del momento. Por eso el
 * contenido nunca queda viejo: si el usuario ya pagó, el aviso de pago desaparece del
 * plan aunque la alarma se haya programado antes.
 *
 * Horas fijas por ahora (se volverán configurables):
 * - avisos de pago y de corte a las 9:00, para actuar durante el día;
 * - aviso de captura a las 19:00, cuando es más probable revisar el correo o la app del banco.
 *
 * SUPUESTO S6: lo comprado el día del corte entra en ese estado de cuenta; lo comprado
 * desde el día siguiente, en el próximo. Se verifica con los estados capturados.
 */
public object PlanRecordatorios {

    public val HORA_DIA: LocalTime = LocalTime(9, 0)
    public val HORA_NOCHE: LocalTime = LocalTime(19, 0)

    /** Días antes de la fecha límite en que se avisa del pago. */
    public val DIAS_AVISO_PAGO: List<Int> = listOf(3, 1)

    /** Días después del corte en que se pide capturar el estado de cuenta. */
    public const val DIAS_PARA_CAPTURAR: Int = 2

    /**
     * Todos los recordatorios cuyo día es [hoy] o posterior, más los de días anteriores
     * que siguen siendo útiles (por ejemplo, capturar un estado cuyo límite no ha pasado).
     * Ordenados por momento.
     */
    public fun calcular(
        tarjetas: List<Tarjeta>,
        estados: List<EstadoDeCuenta>,
        pagos: List<PagoATarjeta>,
        hoy: LocalDate,
        recurrentes: List<Recurrente> = emptyList(),
    ): List<Recordatorio> = (
        tarjetas
            .filterNot { it.cuenta.archivada }
            .flatMap { tarjeta -> paraTarjeta(tarjeta, estados.filter { it.tarjeta == tarjeta.id }, pagos, hoy) } +
            recurrentes.mapNotNull { paraRecurrente(it, hoy) }
        ).sortedBy { it.momento }

    /** Un día antes de cada pago fijo pendiente, a las 9:00. Si ese día ya pasó, no se avisa. */
    private fun paraRecurrente(recurrente: Recurrente, hoy: LocalDate): Recordatorio? {
        val id = recurrente.id?.valor ?: return null
        if (recurrente.clase != ClaseRegistro.GASTO) return null
        val dia = recurrente.proxima.minus(1, DateTimeUnit.DAY)
        if (dia < hoy) return null
        return Recordatorio(
            clave = "recurrente:$id:${recurrente.proxima}",
            tipo = TipoRecordatorio.PAGO_RECURRENTE,
            momento = dia.atTime(HORA_DIA),
            nombre = recurrente.nombre,
            fechaReferencia = recurrente.proxima,
            monto = recurrente.montoEstimado,
        )
    }

    private fun paraTarjeta(
        tarjeta: Tarjeta,
        estados: List<EstadoDeCuenta>,
        pagos: List<PagoATarjeta>,
        hoy: LocalDate,
    ): List<Recordatorio> = buildList {
        val id = tarjeta.id.valor

        // 1. Antes del próximo corte (mañana en adelante: el de hoy ya no tiene aviso útil).
        val proximoCorte = CalendarioTarjeta.siguienteCorteDesde(tarjeta, hoy.plus(1, DateTimeUnit.DAY))
        add(
            Recordatorio(
                clave = "corte:$id:$proximoCorte",
                tipo = TipoRecordatorio.ANTES_DEL_CORTE,
                momento = proximoCorte.minus(1, DateTimeUnit.DAY).atTime(HORA_DIA),
                nombre = tarjeta.cuenta.nombre,
                fechaReferencia = proximoCorte,
            ),
        )

        // 2. Capturar el estado de cuenta del último corte y del próximo, si faltan.
        val ultimoCorte = CalendarioTarjeta.ultimoCorteHasta(tarjeta, hoy)
        for (corte in listOf(ultimoCorte, proximoCorte).distinct()) {
            val capturado = corteCapturado(estados, corte)
            val diaAviso = corte.plus(DIAS_PARA_CAPTURAR, DateTimeUnit.DAY)
            val limitePredicho = CalendarioTarjeta.limiteParaCorte(tarjeta, corte)
            // Pedir la captura solo mientras sirve: antes de que pase su fecha límite.
            if (!capturado && limitePredicho >= hoy && diaAviso <= limitePredicho) {
                add(
                    Recordatorio(
                        clave = "capturar:$id:$corte",
                        tipo = TipoRecordatorio.CAPTURAR_ESTADO,
                        momento = diaAviso.atTime(HORA_NOCHE),
                        nombre = tarjeta.cuenta.nombre,
                        fechaReferencia = corte,
                    ),
                )
            }
        }

        // 3. Pago pendiente del estado de cuenta vigente.
        val situacion = situacionTarjeta(tarjeta, estados, pagos, hoy)
        if (situacion is SituacionTarjeta.PagoPendiente) {
            val limite = situacion.estado.fechaLimite
            for (dias in DIAS_AVISO_PAGO) {
                val dia = limite.minus(dias, DateTimeUnit.DAY)
                // Si el estado se capturó tarde y el día del aviso ya pasó, no se manda.
                if (dia >= hoy) {
                    add(
                        Recordatorio(
                            clave = "pago:$id:$limite:$dias",
                            tipo = TipoRecordatorio.PAGO_PROXIMO,
                            momento = dia.atTime(HORA_DIA),
                            nombre = tarjeta.cuenta.nombre,
                            fechaReferencia = limite,
                            monto = situacion.restante,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Separa el plan en lo que hay que mostrar ahora y la próxima hora a programar.
     *
     * - Se muestran los que ya vencieron y no se han entregado, siempre que su día sea
     *   hoy o los [diasDeAtraso] anteriores (si el teléfono estuvo apagado una semana,
     *   no se dispara una ráfaga de avisos viejos).
     * - El siguiente es el primero con momento posterior a [ahora].
     */
    public fun seleccionar(
        plan: List<Recordatorio>,
        ahora: LocalDateTime,
        entregados: Set<String>,
        diasDeAtraso: Int = 1,
    ): Seleccion {
        val primerDiaValido = ahora.date.minus(diasDeAtraso, DateTimeUnit.DAY)
        val vencidos = plan.filter {
            it.momento <= ahora && it.clave !in entregados && it.momento.date >= primerDiaValido
        }
        val siguiente = plan.filter { it.momento > ahora && it.clave !in entregados }.minByOrNull { it.momento }
        return Seleccion(vencidos, siguiente)
    }

    public data class Seleccion(val mostrarAhora: List<Recordatorio>, val siguiente: Recordatorio?)
}
