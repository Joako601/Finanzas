package io.github.joako601.finanzas.core.data.recordatorios

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import io.github.joako601.finanzas.core.domain.recordatorios.RepositorioRecordatorios
import io.github.joako601.finanzas.core.domain.recordatorios.TipoRecordatorio

/** Un aviso entregado. La clave es la del plan de recordatorios (estable por ciclo). */
@Entity(tableName = "recordatorio_entregado")
internal data class RecordatorioEntregadoEntity(
    @PrimaryKey val clave: String,
    val tipo: String,
    @ColumnInfo(name = "programado_en") val programadoMillis: Long,
    @ColumnInfo(name = "entregado_en") val entregadoMillis: Long,
)

@Dao
internal interface RecordatoriosDao {
    @Query("SELECT clave FROM recordatorio_entregado")
    suspend fun claves(): List<String>

    /** IGNORE: si dos ejecuciones compiten por el mismo aviso, la primera entrega es la que cuenta. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entrega: RecordatorioEntregadoEntity)
}

internal class RepositorioRecordatoriosRoom(private val dao: RecordatoriosDao) : RepositorioRecordatorios {

    override suspend fun clavesEntregadas(): Set<String> = dao.claves().toSet()

    override suspend fun registrarEntrega(
        clave: String,
        tipo: TipoRecordatorio,
        programadoMillis: Long,
        entregadoMillis: Long,
    ) {
        dao.insertar(RecordatorioEntregadoEntity(clave, tipo.name, programadoMillis, entregadoMillis))
    }
}
