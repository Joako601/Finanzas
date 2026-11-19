package io.github.joako601.finanzas.core.domain.tarjetas

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.math.abs

/**
 * Predicción de fechas de corte y límite a partir de los días que indicó el usuario.
 *
 * SUPUESTOS (no confirmados con fuente; se miden con cada estado de cuenta capturado):
 * - S1. Si el mes no tiene el día de corte (corte el 31 en abril, el 30 en febrero), el
 *   corte cae el último día del mes.
 * - S2. La fecha límite es la primera fecha posterior al corte cuyo día es [Tarjeta.diaLimite]
 *   (con la misma regla S1 para meses cortos).
 * - S3. No se ajusta por días inhábiles. Si el banco recorre el corte o la fecha límite,
 *   la captura real lo corrige y el error queda medido.
 */
public object CalendarioTarjeta {

    /** La fecha del mes con ese día, o el último día del mes si el mes es más corto (S1). */
    public fun fechaConDia(anio: Int, mes: Int, dia: Int): LocalDate {
        val primero = LocalDate(anio, mes, 1)
        val diasDelMes = primero.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
        return LocalDate(anio, mes, minOf(dia, diasDelMes))
    }

    /** Fecha de corte en el mes de [referencia]. */
    public fun corteDelMes(tarjeta: Tarjeta, referencia: LocalDate): LocalDate =
        fechaConDia(referencia.year, referencia.month.number, tarjeta.diaDeCorte)

    /** El corte más reciente en o antes de [fecha]. */
    public fun ultimoCorteHasta(tarjeta: Tarjeta, fecha: LocalDate): LocalDate {
        val esteMes = corteDelMes(tarjeta, fecha)
        return if (esteMes <= fecha) esteMes else corteDelMes(tarjeta, fecha.minus(1, DateTimeUnit.MONTH))
    }

    /** El primer corte en o después de [fecha]. */
    public fun siguienteCorteDesde(tarjeta: Tarjeta, fecha: LocalDate): LocalDate {
        val esteMes = corteDelMes(tarjeta, fecha)
        return if (esteMes >= fecha) esteMes else corteDelMes(tarjeta, fecha.plus(1, DateTimeUnit.MONTH))
    }

    /** Fecha límite que corresponde a un corte (S2). */
    public fun limiteParaCorte(tarjeta: Tarjeta, corte: LocalDate): LocalDate {
        val esteMes = fechaConDia(corte.year, corte.month.number, tarjeta.diaLimite)
        return if (esteMes > corte) {
            esteMes
        } else {
            val siguiente = corte.plus(1, DateTimeUnit.MONTH)
            fechaConDia(siguiente.year, siguiente.month.number, tarjeta.diaLimite)
        }
    }

    /**
     * Lo que la app habría predicho para el ciclo cuyo corte real es [corteReal]: el
     * corte predicho más cercano (el de ese mes, el anterior o el siguiente) y su límite.
     */
    public fun prediccionPara(tarjeta: Tarjeta, corteReal: LocalDate): Pair<LocalDate, LocalDate> {
        val candidatos = listOf(
            corteDelMes(tarjeta, corteReal.minus(1, DateTimeUnit.MONTH)),
            corteDelMes(tarjeta, corteReal),
            corteDelMes(tarjeta, corteReal.plus(1, DateTimeUnit.MONTH)),
        )
        val corte = candidatos.minBy { abs(it.daysUntil(corteReal)) }
        return corte to limiteParaCorte(tarjeta, corte)
    }
}
