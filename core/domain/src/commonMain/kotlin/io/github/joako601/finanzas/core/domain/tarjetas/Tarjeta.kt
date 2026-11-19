package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate

/**
 * Una tarjeta de crédito. En el libro es una cuenta de PASIVO (dinero que debes): un
 * gasto con tarjeta aumenta la deuda y un pago la reduce. Aquí se agregan los datos
 * propios de la tarjeta.
 *
 * [diaDeCorte] y [diaLimite] son los días del mes que el usuario indica al darla de
 * alta. Solo sirven para PREDECIR fechas; las fechas reales salen de cada estado de
 * cuenta capturado (D-011).
 */
public data class Tarjeta(
    val cuenta: Cuenta,
    val lineaDeCredito: Dinero,
    val diaDeCorte: Int,
    val diaLimite: Int,
) {
    init {
        require(cuenta.tipo == TipoCuenta.PASIVO) { "Una tarjeta de crédito es una cuenta de pasivo" }
        require(!lineaDeCredito.esNegativo) { "La línea de crédito no puede ser negativa" }
        require(diaDeCorte in 1..31) { "Día de corte fuera de rango" }
        require(diaLimite in 1..31) { "Día límite fuera de rango" }
    }

    val id: CuentaId get() = cuenta.id
}

/**
 * Los datos de un estado de cuenta, tal como vienen impresos, capturados por el usuario.
 * Son la verdad para ese ciclo.
 *
 * [cortePredicho] y [limitePredicho] son lo que la app había predicho para este mismo
 * ciclo antes de la captura. Se guardan para medir qué tan buena es la predicción con
 * datos reales; no se usan para ninguna otra cosa.
 */
public data class EstadoDeCuenta(
    val id: Long?,
    val tarjeta: CuentaId,
    val fechaCorte: LocalDate,
    val fechaLimite: LocalDate,
    val pagoSinIntereses: Dinero,
    val pagoMinimo: Dinero,
    val cortePredicho: LocalDate?,
    val limitePredicho: LocalDate?,
) {
    init {
        require(fechaLimite > fechaCorte) { "La fecha límite debe ser posterior al corte" }
        require(!pagoSinIntereses.esNegativo) { "El pago para no generar intereses no puede ser negativo" }
        require(!pagoMinimo.esNegativo) { "El pago mínimo no puede ser negativo" }
    }
}
