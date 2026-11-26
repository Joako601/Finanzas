package io.github.joako601.finanzas.core.data.recurrentes

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import io.github.joako601.finanzas.core.data.libro.CuentaEntity
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Un pago o ingreso fijo. La periodicidad se guarda en columnas simples para que el
 * esquema se lea como SQL: `periodo` dice cuál es y las demás sus números
 * (DIAS y MESES usan `cada`; DOS_VECES_AL_MES usa `primer_dia` y `segundo_dia`).
 * Las fechas son días desde 1970-01-01, como en `movimiento`.
 */
@Entity(
    tableName = "recurrente",
    foreignKeys = [
        // Las categorías no se borran (se archivan); RESTRICT lo garantiza.
        ForeignKey(entity = CuentaEntity::class, parentColumns = ["id"], childColumns = ["categoria_id"], onDelete = ForeignKey.RESTRICT),
        // v7. Las cuentas tampoco se borran (se archivan).
        ForeignKey(entity = CuentaEntity::class, parentColumns = ["id"], childColumns = ["cuenta_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoria_id"), Index("cuenta_id")],
)
internal data class RecurrenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** Nombre del enum ClaseRegistro: GASTO o INGRESO. */
    val clase: String,
    @ColumnInfo(name = "monto_centavos") val montoCentavos: Long,
    @ColumnInfo(name = "categoria_id") val categoriaId: Long,
    val periodo: String,
    val cada: Int?,
    @ColumnInfo(name = "primer_dia") val primerDia: Int?,
    @ColumnInfo(name = "segundo_dia") val segundoDia: Int?,
    val inicio: Long,
    val proxima: Long,
    /** v7: nula en los anteriores = la cuenta se elige al registrar. */
    @ColumnInfo(name = "cuenta_id") val cuentaId: Long? = null,
)

@Dao
internal interface RecurrentesDao {
    @Query("SELECT * FROM recurrente ORDER BY proxima, id")
    fun observar(): Flow<List<RecurrenteEntity>>

    @Insert
    suspend fun insertar(recurrente: RecurrenteEntity): Long

    @Query(
        """
        UPDATE recurrente
        SET nombre = :nombre,
            clase = :clase,
            monto_centavos = :montoCentavos,
            categoria_id = :categoriaId,
            periodo = :periodo,
            cada = :cada,
            primer_dia = :primerDia,
            segundo_dia = :segundoDia,
            inicio = :inicio,
            proxima = :proxima,
            cuenta_id = :cuentaId
        WHERE id = :id
        """,
    )
    suspend fun actualizar(
        id: Long,
        nombre: String,
        clase: String,
        montoCentavos: Long,
        categoriaId: Long,
        periodo: String,
        cada: Int?,
        primerDia: Int?,
        segundoDia: Int?,
        inicio: Long,
        proxima: Long,
        cuentaId: Long?,
    )

    @Query("UPDATE recurrente SET proxima = :proxima WHERE id = :id")
    suspend fun cambiarProxima(id: Long, proxima: Long)

    @Query("DELETE FROM recurrente WHERE id = :id")
    suspend fun eliminar(id: Long)
}

internal class RepositorioRecurrentesRoom(private val dao: RecurrentesDao) : RepositorioRecurrentes {

    override fun observarRecurrentes(): Flow<List<Recurrente>> = dao.observar().map { filas -> filas.map { it.aDominio() } }

    override suspend fun guardar(recurrente: Recurrente): RecurrenteId = RecurrenteId(dao.insertar(recurrente.aEntidad()))

    override suspend fun actualizar(recurrente: Recurrente) {
        val id = requireNotNull(recurrente.id) { "Se requiere id para actualizar" }.valor
        val entidad = recurrente.aEntidad()
        dao.actualizar(
            id = id,
            nombre = entidad.nombre,
            clase = entidad.clase,
            montoCentavos = entidad.montoCentavos,
            categoriaId = entidad.categoriaId,
            periodo = entidad.periodo,
            cada = entidad.cada,
            primerDia = entidad.primerDia,
            segundoDia = entidad.segundoDia,
            inicio = entidad.inicio,
            proxima = entidad.proxima,
            cuentaId = entidad.cuentaId,
        )
    }

    override suspend fun cambiarProxima(id: RecurrenteId, proxima: LocalDate) {
        dao.cambiarProxima(id.valor, proxima.toEpochDays())
    }

    override suspend fun eliminar(id: RecurrenteId) {
        dao.eliminar(id.valor)
    }
}

private const val DIAS = "DIAS"
private const val MESES = "MESES"
private const val DOS_VECES_AL_MES = "DOS_VECES_AL_MES"

private fun RecurrenteEntity.aDominio(): Recurrente = Recurrente(
    id = RecurrenteId(id),
    nombre = nombre,
    clase = ClaseRegistro.valueOf(clase),
    montoEstimado = Dinero(montoCentavos),
    categoria = CuentaId(categoriaId),
    periodicidad = when (periodo) {
        DIAS -> Periodicidad.CadaDias(checkNotNull(cada))
        MESES -> Periodicidad.CadaMeses(checkNotNull(cada))
        DOS_VECES_AL_MES -> Periodicidad.DosVecesAlMes(checkNotNull(primerDia), checkNotNull(segundoDia))
        else -> error("Periodo desconocido: $periodo")
    },
    inicio = LocalDate.fromEpochDays(inicio),
    proxima = LocalDate.fromEpochDays(proxima),
    cuenta = cuentaId?.let(::CuentaId),
)

private fun Recurrente.aEntidad(): RecurrenteEntity {
    val p = periodicidad
    return RecurrenteEntity(
        nombre = nombre,
        clase = clase.name,
        montoCentavos = montoEstimado.centavos,
        categoriaId = categoria.valor,
        periodo = when (p) {
            is Periodicidad.CadaDias -> DIAS
            is Periodicidad.CadaMeses -> MESES
            is Periodicidad.DosVecesAlMes -> DOS_VECES_AL_MES
        },
        cada = when (p) {
            is Periodicidad.CadaDias -> p.dias
            is Periodicidad.CadaMeses -> p.meses
            is Periodicidad.DosVecesAlMes -> null
        },
        primerDia = (p as? Periodicidad.DosVecesAlMes)?.primerDia,
        segundoDia = (p as? Periodicidad.DosVecesAlMes)?.segundoDia,
        inicio = inicio.toEpochDays(),
        proxima = proxima.toEpochDays(),
        cuentaId = cuenta?.valor,
    )
}
