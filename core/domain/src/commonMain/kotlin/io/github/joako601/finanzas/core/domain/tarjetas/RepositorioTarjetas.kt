package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Puerto de persistencia de las tarjetas y sus estados de cuenta. La cuenta de pasivo de
 * cada tarjeta vive en el libro ([io.github.joako601.finanzas.core.domain.libro.RepositorioLibro]);
 * aquí solo los datos propios de la tarjeta.
 */
public interface RepositorioTarjetas {

    public fun observarTarjetas(): Flow<List<Tarjeta>>

    /** Todos los estados de cuenta capturados, del corte más reciente al más antiguo. */
    public fun observarEstadosDeCuenta(): Flow<List<EstadoDeCuenta>>

    /** Registra los datos de tarjeta de una cuenta de pasivo ya creada. */
    public suspend fun guardarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta

    /** Actualiza nombre, línea de crédito, día de corte y día límite de una tarjeta existente. */
    public suspend fun actualizarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta

    /**
     * Guarda un estado de cuenta. Si ya hay uno de la misma tarjeta con la misma fecha de
     * corte, lo reemplaza (el usuario corrigió una captura).
     */
    public suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuenta)

    /** Elimina un estado de cuenta específico por tarjeta y fecha de corte (al corregir la fecha de corte). */
    /** Guarda la tasa anual que el usuario eligió para esta tarjeta al simular un pago. */
    public suspend fun guardarTasaAnual(tarjeta: CuentaId, tasa: Int)

    public suspend fun eliminarEstadoDeCuenta(tarjetaId: CuentaId, fechaCorte: LocalDate)
}
