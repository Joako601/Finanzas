package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.libro.CuentaId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Hacia dónde se mueve una fecha límite predicha que cae en día inhábil. Lo elige el usuario. */
public enum class AjusteInhabil { ANTERIOR, SIGUIENTE }

/**
 * Días inhábiles bancarios y la elección del usuario por tarjeta.
 *
 * SUPUESTO S14: sábado, domingo y los días de [inhabiles] (los publica el servidor) son
 * inhábiles. Si la fecha límite PREDICHA cae en uno, la app no decide sola: avisa y el
 * usuario elige mover al día hábil anterior o al siguiente ([ajustes]). Sin elección, la
 * fecha queda igual. Un estado de cuenta capturado nunca se ajusta (D-011).
 *
 * [NINGUNO] (sin datos del servidor, o la distribución privada) deja todo como antes de S14:
 * ni los fines de semana se marcan.
 */
public data class CalendarioBancario(
    val inhabiles: Set<LocalDate>,
    val ajustes: Map<CuentaId, AjusteInhabil> = emptyMap(),
) {
    public fun esInhabil(fecha: LocalDate): Boolean =
        fecha.dayOfWeek == DayOfWeek.SATURDAY || fecha.dayOfWeek == DayOfWeek.SUNDAY || fecha in inhabiles

    /** El primer día hábil en la dirección de [ajuste], empezando por [fecha]. */
    public fun diaHabil(fecha: LocalDate, ajuste: AjusteInhabil): LocalDate {
        var dia = fecha
        // Un tope por si el servidor manda un rango absurdo: nunca más de un mes.
        repeat(31) {
            if (!esInhabil(dia)) return dia
            dia = when (ajuste) {
                AjusteInhabil.ANTERIOR -> dia.minus(1, DateTimeUnit.DAY)
                AjusteInhabil.SIGUIENTE -> dia.plus(1, DateTimeUnit.DAY)
            }
        }
        return fecha
    }

    /** La fecha límite predicha con la elección del usuario aplicada (S2 + S14). */
    public fun limite(tarjeta: Tarjeta, corte: LocalDate): LocalDate {
        val nominal = CalendarioTarjeta.limiteParaCorte(tarjeta, corte)
        if (this === NINGUNO) return nominal
        val ajuste = ajustes[tarjeta.id] ?: return nominal
        return diaHabil(nominal, ajuste)
    }

    /** `true` si la fecha límite predicha cae en día inhábil y el usuario aún no eligió. */
    public fun requiereEleccion(tarjeta: Tarjeta, corte: LocalDate): Boolean =
        this !== NINGUNO && tarjeta.id !in ajustes && esInhabil(CalendarioTarjeta.limiteParaCorte(tarjeta, corte))

    public companion object {
        public val NINGUNO: CalendarioBancario = CalendarioBancario(emptySet())
    }
}
