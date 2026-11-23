package io.github.joako601.finanzas.core.data.tarjetas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TarjetasDao {

    @Query(
        """
        SELECT c.id AS cuenta_id, c.nombre, c.tipo, c.archivada,
               t.linea_centavos, t.dia_corte, t.dia_limite
        FROM tarjeta t
        JOIN cuenta c ON c.id = t.cuenta_id
        ORDER BY c.orden, c.id
        """,
    )
    fun observarTarjetas(): Flow<List<TarjetaConCuenta>>

    @Insert
    suspend fun insertarTarjeta(tarjeta: TarjetaEntity)

    @Query("UPDATE cuenta SET nombre = :nombre WHERE id = :cuentaId")
    suspend fun actualizarNombreCuentaTarjeta(cuentaId: Long, nombre: String)

    @Query(
        """
        UPDATE tarjeta
        SET linea_centavos = :lineaCentavos, dia_corte = :diaCorte, dia_limite = :diaLimite
        WHERE cuenta_id = :cuentaId
        """,
    )
    suspend fun actualizarDatosTarjeta(cuentaId: Long, lineaCentavos: Long, diaCorte: Int, diaLimite: Int)

    @Query("SELECT * FROM estado_de_cuenta ORDER BY fecha_corte DESC, id DESC")
    fun observarEstadosDeCuenta(): Flow<List<EstadoDeCuentaEntity>>

    /** REPLACE usa el índice único (tarjeta, corte): una nueva captura del mismo corte sustituye a la anterior. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuentaEntity)
}
