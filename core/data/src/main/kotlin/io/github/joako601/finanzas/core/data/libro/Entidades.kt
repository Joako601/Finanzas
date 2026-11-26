package io.github.joako601.finanzas.core.data.libro

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Tablas de la base. Usan solo tipos primitivos; la conversión a los tipos del
 * dominio (Dinero, LocalDate, CuentaId) vive en Mapeo.kt. Así Room no necesita
 * TypeConverters y el esquema exportado se lee como SQL normal.
 */

@Entity(tableName = "cuenta")
internal data class CuentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** Nombre del enum TipoCuenta. Texto y no ordinal: reordenar el enum no corrompe datos. */
    val tipo: String,
    val archivada: Boolean = false,
    /** Orden de presentación en la interfaz. */
    val orden: Int,
    /** v7: ícono local elegido para una categoría propia; nulo = se deduce del nombre. */
    val icono: String? = null,
)

@Entity(
    tableName = "movimiento",
    // La lista se ordena por fecha; sin índice cada consulta recorrería la tabla entera.
    indices = [Index("fecha")],
)
internal data class MovimientoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Días desde 1970-01-01. Un entero ordena y filtra por rango más rápido que un texto. */
    @ColumnInfo(name = "fecha") val fechaDiaEpoca: Long,
    val descripcion: String,
    /** Momento de captura en milisegundos UTC; desempata movimientos del mismo día. */
    @ColumnInfo(name = "creado_en") val creadoEnMillis: Long,
    /** Plazo de una compra a meses sin intereses; `null` = de contado (D-022). */
    val meses: Int? = null,
)

@Entity(
    tableName = "asiento",
    foreignKeys = [
        // Borrar un movimiento borra sus asientos: nunca quedan asientos huérfanos.
        ForeignKey(
            entity = MovimientoEntity::class,
            parentColumns = ["id"],
            childColumns = ["movimiento_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        // No se puede borrar una cuenta que tiene asientos: se archiva.
        ForeignKey(
            entity = CuentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["cuenta_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    // Índices en ambas llaves foráneas: sin ellos, cada borrado y cada saldo
    // recorrerían la tabla de asientos completa.
    indices = [Index("movimiento_id"), Index("cuenta_id")],
)
internal data class AsientoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "movimiento_id") val movimientoId: Long,
    @ColumnInfo(name = "cuenta_id") val cuentaId: Long,
    val centavos: Long,
)

/** Un movimiento con sus asientos, cargados por Room en una sola consulta transaccional. */
internal data class MovimientoConAsientos(
    @Embedded val movimiento: MovimientoEntity,
    @Relation(parentColumn = "id", entityColumn = "movimiento_id")
    val asientos: List<AsientoEntity>,
)

/** Fila de la consulta de saldos. */
internal data class SaldoFila(
    @ColumnInfo(name = "cuenta_id") val cuentaId: Long,
    val centavos: Long,
)
