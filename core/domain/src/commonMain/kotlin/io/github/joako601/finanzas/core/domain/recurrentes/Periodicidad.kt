package io.github.joako601.finanzas.core.domain.recurrentes

import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Cada cuánto se repite un pago o un ingreso.
 *
 * SUPUESTOS (se comparan contra los recibos y depósitos reales):
 * - S7. Si el mes no tiene el día (el 31 en abril, el 30 en febrero), toca el último día
 *   del mes, igual que el corte de una tarjeta (S1).
 * - S8. No se recorre por fines de semana ni días inhábiles. Si la empresa paga el viernes
 *   cuando el 15 cae en sábado, el usuario registra la fecha real y la serie sigue igual.
 */
public sealed interface Periodicidad {

    /** Cada [dias] días: 7 es semanal, 14 es catorcenal. */
    public data class CadaDias(val dias: Int) : Periodicidad {
        init {
            require(dias in 1..366) { "Días fuera de rango" }
        }
    }

    /** Cada [meses] meses, el mismo día del mes que la primera fecha: 1 mensual, 2 bimestral, 12 anual. */
    public data class CadaMeses(val meses: Int) : Periodicidad {
        init {
            require(meses in 1..24) { "Meses fuera de rango" }
        }
    }

    /** Dos días fijos de cada mes, como la quincena: el 15 y el 31 (el 31 es el último día, S7). */
    public data class DosVecesAlMes(val primerDia: Int, val segundoDia: Int) : Periodicidad {
        init {
            require(primerDia in 1..31 && segundoDia in 1..31) { "Día fuera de rango" }
            require(primerDia < segundoDia) { "El primer día va antes que el segundo" }
        }
    }

    public companion object {
        public val SEMANAL: Periodicidad = CadaDias(7)
        public val CATORCENAL: Periodicidad = CadaDias(14)
        public val QUINCENAL: Periodicidad = DosVecesAlMes(15, 31)
        public val MENSUAL: Periodicidad = CadaMeses(1)
        public val BIMESTRAL: Periodicidad = CadaMeses(2)
        public val ANUAL: Periodicidad = CadaMeses(12)
    }
}

/**
 * Primera fecha de la serie que empieza en [inicio]. Con días fijos del mes es el primero
 * de esos días en o después de [inicio]; con las demás, [inicio] mismo.
 */
public fun Periodicidad.primeraDesde(inicio: LocalDate): LocalDate = when (this) {
    is Periodicidad.CadaDias, is Periodicidad.CadaMeses -> inicio
    is Periodicidad.DosVecesAlMes -> diasDelMes(inicio).firstOrNull { it >= inicio } ?: diasDelMes(mesSiguiente(inicio)).first()
}

/** La fecha de la serie que empieza en [inicio] que sigue estrictamente después de [fecha]. */
public fun Periodicidad.siguienteDespues(inicio: LocalDate, fecha: LocalDate): LocalDate {
    val primera = primeraDesde(inicio)
    if (fecha < primera) return primera
    return when (this) {
        is Periodicidad.CadaDias -> primera.plus((primera.daysUntil(fecha) / dias + 1) * dias, DateTimeUnit.DAY)

        is Periodicidad.CadaMeses -> {
            // Se cuenta desde el inicio, no desde la fecha anterior: así el 31 vuelve a ser 31
            // después de un febrero (S7).
            val mesesTranscurridos = indiceDeMes(fecha) - indiceDeMes(primera)
            val k = mesesTranscurridos / meses
            val candidata = enMesDesplazado(primera, k * meses)
            if (candidata > fecha) candidata else enMesDesplazado(primera, (k + 1) * meses)
        }

        is Periodicidad.DosVecesAlMes ->
            diasDelMes(fecha).firstOrNull { it > fecha } ?: diasDelMes(mesSiguiente(fecha)).first()
    }
}

private fun indiceDeMes(fecha: LocalDate): Int = fecha.year * 12 + (fecha.month.number - 1)

/** La fecha con el mismo día que [primera], [desplazamiento] meses después. */
private fun enMesDesplazado(primera: LocalDate, desplazamiento: Int): LocalDate {
    val indice = indiceDeMes(primera) + desplazamiento
    return CalendarioTarjeta.fechaConDia(indice / 12, indice % 12 + 1, primera.day)
}

private fun mesSiguiente(fecha: LocalDate): LocalDate = LocalDate(fecha.year, fecha.month, 1).plus(1, DateTimeUnit.MONTH)

/** Los dos días del mes de [fecha], ya ajustados a meses cortos y sin repetir (S7). */
private fun Periodicidad.DosVecesAlMes.diasDelMes(fecha: LocalDate): List<LocalDate> =
    listOf(primerDia, segundoDia)
        .map { CalendarioTarjeta.fechaConDia(fecha.year, fecha.month.number, it) }
        .distinct()
