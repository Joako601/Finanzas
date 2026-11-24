package io.github.joako601.finanzas.nube.divisas

import java.math.BigDecimal

/** Resultado de consultar las cotizaciones principales frente a MXN. */
sealed interface ResultadoDivisasActuales {
    data class Exito(val cotizaciones: List<TipoCambio>) : ResultadoDivisasActuales
    data object SinConexion : ResultadoDivisasActuales
    data object SesionExpirada : ResultadoDivisasActuales
    data object ErrorServidor : ResultadoDivisasActuales
}

/** Resultado de convertir un monto entre dos divisas. */
sealed interface ResultadoConversion {
    data class Exito(val conversion: ConversionDivisa) : ResultadoConversion
    data object MontoInvalido : ResultadoConversion
    data object SinConexion : ResultadoConversion
    data object SesionExpirada : ResultadoConversion
    data object ErrorServidor : ResultadoConversion
}

/** Contrato de acceso a divisas (remoto hacia Spring Boot o falso para pruebas/demo). */
interface RepositorioDivisas {
    suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales

    suspend fun convertir(
        monto: BigDecimal,
        de: String,
        a: String,
    ): ResultadoConversion
}
