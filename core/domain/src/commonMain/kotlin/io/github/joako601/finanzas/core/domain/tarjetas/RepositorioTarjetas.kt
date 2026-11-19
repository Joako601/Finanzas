package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import kotlinx.coroutines.flow.Flow

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

    /**
     * Guarda un estado de cuenta. Si ya hay uno de la misma tarjeta con la misma fecha de
     * corte, lo reemplaza (el usuario corrigió una captura).
     */
    public suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuenta)
}
