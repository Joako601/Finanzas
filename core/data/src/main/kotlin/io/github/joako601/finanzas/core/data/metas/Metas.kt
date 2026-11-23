package io.github.joako601.finanzas.core.data.metas

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import io.github.joako601.finanzas.core.data.libro.CuentaEntity
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.metas.MetaAhorro
import io.github.joako601.finanzas.core.domain.metas.MetaAhorroId
import io.github.joako601.finanzas.core.domain.metas.RepositorioMetas
import io.github.joako601.finanzas.core.domain.metas.TopeCategoria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Tope mensual tranquilo asociado a una categoría de gasto (`cuenta.id`).
 * Una categoría tiene a lo sumo un tope activo (`categoria_id` es la llave primaria).
 */
@Entity(
    tableName = "tope_categoria",
    foreignKeys = [
        ForeignKey(
            entity = CuentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoria_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
internal data class TopeCategoriaEntity(
    @PrimaryKey @ColumnInfo(name = "categoria_id") val categoriaId: Long,
    @ColumnInfo(name = "limite_centavos") val limiteCentavos: Long,
)

/**
 * Apartado o meta de ahorro virtual ("cajita").
 * [fechaLimite] se guarda como días desde 1970-01-01 (o NULL si no tiene fecha objetivo).
 */
@Entity(tableName = "meta_ahorro")
internal data class MetaAhorroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    @ColumnInfo(name = "objetivo_centavos") val objetivoCentavos: Long,
    @ColumnInfo(name = "apartado_centavos") val apartadoCentavos: Long,
    @ColumnInfo(name = "fecha_limite") val fechaLimite: Long?,
    @ColumnInfo(name = "reservar_de_disponible") val reservarDeDisponible: Boolean,
    val archivada: Boolean,
)

@Dao
internal interface MetasDao {

    @Query("SELECT * FROM tope_categoria ORDER BY categoria_id")
    fun observarTopes(): Flow<List<TopeCategoriaEntity>>

    @Upsert
    suspend fun guardarTope(tope: TopeCategoriaEntity)

    @Query("DELETE FROM tope_categoria WHERE categoria_id = :categoriaId")
    suspend fun eliminarTope(categoriaId: Long)

    @Query("SELECT * FROM meta_ahorro ORDER BY archivada ASC, id ASC")
    fun observarMetas(): Flow<List<MetaAhorroEntity>>

    @Insert
    suspend fun insertarMeta(meta: MetaAhorroEntity): Long

    @Query(
        """
        UPDATE meta_ahorro
        SET nombre = :nombre,
            objetivo_centavos = :objetivoCentavos,
            apartado_centavos = :apartadoCentavos,
            fecha_limite = :fechaLimite,
            reservar_de_disponible = :reservarDeDisponible,
            archivada = :archivada
        WHERE id = :id
        """,
    )
    suspend fun actualizarMeta(
        id: Long,
        nombre: String,
        objetivoCentavos: Long,
        apartadoCentavos: Long,
        fechaLimite: Long?,
        reservarDeDisponible: Boolean,
        archivada: Boolean,
    )

    @Query("DELETE FROM meta_ahorro WHERE id = :id")
    suspend fun eliminarMeta(id: Long)
}

internal class RepositorioMetasRoom(private val dao: MetasDao) : RepositorioMetas {

    override fun observarTopes(): Flow<List<TopeCategoria>> =
        dao.observarTopes().map { filas -> filas.map { it.aDominio() } }

    override suspend fun guardarTope(tope: TopeCategoria) {
        dao.guardarTope(TopeCategoriaEntity(tope.categoriaId.valor, tope.limiteMensual.centavos))
    }

    override suspend fun eliminarTope(categoriaId: CuentaId) {
        dao.eliminarTope(categoriaId.valor)
    }

    override fun observarMetas(): Flow<List<MetaAhorro>> =
        dao.observarMetas().map { filas -> filas.map { it.aDominio() } }

    override suspend fun crearMeta(meta: MetaAhorro): MetaAhorroId =
        MetaAhorroId(dao.insertarMeta(meta.aEntidad()))

    override suspend fun actualizarMeta(meta: MetaAhorro) {
        val id = requireNotNull(meta.id) { "Se requiere id para actualizar la meta" }.valor
        dao.actualizarMeta(
            id = id,
            nombre = meta.nombre,
            objetivoCentavos = meta.objetivo.centavos,
            apartadoCentavos = meta.apartado.centavos,
            fechaLimite = meta.fechaLimite?.toEpochDays(),
            reservarDeDisponible = meta.reservarDeDisponible,
            archivada = meta.archivada,
        )
    }

    override suspend fun eliminarMeta(id: MetaAhorroId) {
        dao.eliminarMeta(id.valor)
    }
}

private fun TopeCategoriaEntity.aDominio(): TopeCategoria = TopeCategoria(
    categoriaId = CuentaId(categoriaId),
    limiteMensual = Dinero(limiteCentavos),
)

private fun MetaAhorroEntity.aDominio(): MetaAhorro = MetaAhorro(
    id = MetaAhorroId(id),
    nombre = nombre,
    objetivo = Dinero(objetivoCentavos),
    apartado = Dinero(apartadoCentavos),
    fechaLimite = fechaLimite?.let { LocalDate.fromEpochDays(it) },
    reservarDeDisponible = reservarDeDisponible,
    archivada = archivada,
)

private fun MetaAhorro.aEntidad(): MetaAhorroEntity = MetaAhorroEntity(
    id = id?.valor ?: 0L,
    nombre = nombre,
    objetivoCentavos = objetivo.centavos,
    apartadoCentavos = apartado.centavos,
    fechaLimite = fechaLimite?.toEpochDays(),
    reservarDeDisponible = reservarDeDisponible,
    archivada = archivada,
)
