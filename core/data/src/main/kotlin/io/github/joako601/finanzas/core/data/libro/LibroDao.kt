package io.github.joako601.finanzas.core.data.libro

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a datos del libro. Room genera la implementación en tiempo de compilación y
 * valida cada consulta contra el esquema: un error de SQL aquí falla al compilar.
 *
 * Las funciones que devuelven `Flow` vuelven a emitir cuando cambia cualquiera de las
 * tablas que leen; las `suspend` corren fuera del hilo principal.
 *
 * Es clase abstracta y no interfaz para poder tener métodos `@Transaction` con cuerpo.
 */
@Dao
internal abstract class LibroDao {

    @Query("SELECT * FROM cuenta ORDER BY orden, id")
    abstract fun observarCuentas(): Flow<List<CuentaEntity>>

    @Query("SELECT * FROM cuenta WHERE tipo = :tipo ORDER BY orden, id LIMIT 1")
    abstract suspend fun primeraDeTipo(tipo: String): CuentaEntity?

    @Query("SELECT COALESCE(MAX(orden), 0) FROM cuenta")
    abstract suspend fun ordenMaximo(): Int

    @Insert
    abstract suspend fun insertarCuenta(cuenta: CuentaEntity): Long

    /** El saldo se calcula en SQLite sumando asientos, no cargando todo a memoria. */
    @Query("SELECT cuenta_id, SUM(centavos) AS centavos FROM asiento GROUP BY cuenta_id")
    abstract fun observarSaldos(): Flow<List<SaldoFila>>

    /**
     * @Transaction garantiza que el movimiento y sus asientos se leen del mismo estado
     * de la base (Room hace dos consultas por la @Relation).
     */
    @Transaction
    @Query("SELECT * FROM movimiento ORDER BY fecha DESC, creado_en DESC, id DESC")
    abstract fun observarMovimientos(): Flow<List<MovimientoConAsientos>>

    @Insert
    protected abstract suspend fun insertarMovimiento(movimiento: MovimientoEntity): Long

    @Insert
    protected abstract suspend fun insertarAsientos(asientos: List<AsientoEntity>)

    /** Inserta el movimiento y sus asientos de forma atómica. */
    @Transaction
    open suspend fun insertarCompleto(movimiento: MovimientoEntity, asientos: List<AsientoEntity>): Long {
        val id = insertarMovimiento(movimiento)
        insertarAsientos(asientos.map { it.copy(movimientoId = id) })
        return id
    }

    /** Los asientos se borran solos por la llave foránea con CASCADE. */
    @Query("DELETE FROM movimiento WHERE id = :id")
    abstract suspend fun eliminarMovimiento(id: Long)
}
