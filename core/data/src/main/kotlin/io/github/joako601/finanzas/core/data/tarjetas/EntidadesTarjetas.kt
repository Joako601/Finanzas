package io.github.joako601.finanzas.core.data.tarjetas

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.joako601.finanzas.core.data.libro.CuentaEntity

/**
 * Datos propios de una tarjeta. La llave primaria ES la cuenta de pasivo del libro:
 * una tarjeta no existe sin su cuenta, y una cuenta tiene a lo más una tarjeta.
 */
@Entity(
    tableName = "tarjeta",
    foreignKeys = [
        ForeignKey(
            entity = CuentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["cuenta_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
internal data class TarjetaEntity(
    @PrimaryKey @ColumnInfo(name = "cuenta_id") val cuentaId: Long,
    @ColumnInfo(name = "linea_centavos") val lineaCentavos: Long,
    @ColumnInfo(name = "dia_corte") val diaCorte: Int,
    @ColumnInfo(name = "dia_limite") val diaLimite: Int,
)

/**
 * Un estado de cuenta capturado. Las fechas son días desde 1970-01-01, igual que en
 * `movimiento`. El índice único impide dos capturas del mismo corte de la misma tarjeta:
 * capturar de nuevo reemplaza.
 */
@Entity(
    tableName = "estado_de_cuenta",
    foreignKeys = [
        ForeignKey(
            entity = TarjetaEntity::class,
            parentColumns = ["cuenta_id"],
            childColumns = ["tarjeta_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tarjeta_id", "fecha_corte"], unique = true)],
)
internal data class EstadoDeCuentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tarjeta_id") val tarjetaId: Long,
    @ColumnInfo(name = "fecha_corte") val fechaCorte: Long,
    @ColumnInfo(name = "fecha_limite") val fechaLimite: Long,
    @ColumnInfo(name = "pago_sin_intereses") val pagoSinIntereses: Long,
    @ColumnInfo(name = "pago_minimo") val pagoMinimo: Long,
    /** Lo que la app había predicho para este ciclo; null si no había predicción. */
    @ColumnInfo(name = "corte_predicho") val cortePredicho: Long?,
    @ColumnInfo(name = "limite_predicho") val limitePredicho: Long?,
    @ColumnInfo(name = "capturado_en") val capturadoEnMillis: Long,
)

/** Resultado de unir `tarjeta` con su `cuenta`. */
internal data class TarjetaConCuenta(
    @ColumnInfo(name = "cuenta_id") val cuentaId: Long,
    val nombre: String,
    val tipo: String,
    val archivada: Boolean,
    @ColumnInfo(name = "linea_centavos") val lineaCentavos: Long,
    @ColumnInfo(name = "dia_corte") val diaCorte: Int,
    @ColumnInfo(name = "dia_limite") val diaLimite: Int,
)
