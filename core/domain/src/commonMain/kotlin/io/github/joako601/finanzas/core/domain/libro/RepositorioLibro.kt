package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Puerto de persistencia del libro. El dominio define qué necesita y core:data lo
 * implementa con Room. Es lo mismo que un repositorio de Spring Data visto desde el
 * servicio, salvo que las lecturas devuelven `Flow`: un flujo que emite de nuevo cada
 * vez que cambian los datos, para que la pantalla se actualice sola.
 */
public interface RepositorioLibro {

    /** Todas las cuentas y categorías, en el orden en que se muestran. */
    public fun observarCuentas(): Flow<List<Cuenta>>

    /** Saldo de cada cuenta que tiene al menos un asiento. */
    public fun observarSaldos(): Flow<Map<CuentaId, Dinero>>

    /** Saldo actual de una cuenta, legible dentro de [enTransaccion]. */
    public suspend fun saldoDe(id: CuentaId): Dinero = observarSaldos().first()[id] ?: Dinero.CERO

    /** Movimientos del más reciente al más antiguo. */
    public fun observarMovimientos(): Flow<List<Movimiento>>

    /** Guarda un movimiento nuevo con sus asientos, de forma atómica. */
    public suspend fun registrar(movimiento: Movimiento): MovimientoId

    public suspend fun eliminar(id: MovimientoId)

    /**
     * Reemplaza fecha, nota y asientos de un movimiento guardado ([Movimiento.id] no nulo).
     * Conserva su id y el momento en que se registró, de forma atómica.
     */
    public suspend fun actualizar(movimiento: Movimiento)

    public suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta

    /** Actualiza el nombre de una cuenta existente conservando su id y movimientos. */
    public suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String)

    /**
     * Archiva o reactiva una cuenta. Archivada deja de aparecer para registrar y en los totales,
     * pero sus movimientos y su saldo se conservan para el historial.
     */
    public suspend fun cambiarArchivada(id: CuentaId, archivada: Boolean)

    /** Ícono local de una categoría; `null` vuelve al que corresponde por nombre. */
    public suspend fun actualizarIcono(id: CuentaId, icono: String?)

    /** La cuenta de capital contra la que se registran los saldos iniciales. */
    public suspend fun cuentaDeSaldosIniciales(): Cuenta

    /** Ejecuta [bloque] en una sola transacción: o se guarda todo o nada. */
    public suspend fun <T> enTransaccion(bloque: suspend () -> T): T
}
