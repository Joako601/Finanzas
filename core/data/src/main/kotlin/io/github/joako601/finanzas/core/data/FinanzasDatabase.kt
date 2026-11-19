package io.github.joako601.finanzas.core.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.joako601.finanzas.core.data.libro.AsientoEntity
import io.github.joako601.finanzas.core.data.libro.CuentaEntity
import io.github.joako601.finanzas.core.data.libro.LibroDao
import io.github.joako601.finanzas.core.data.libro.MovimientoEntity
import io.github.joako601.finanzas.core.data.recordatorios.RecordatorioEntregadoEntity
import io.github.joako601.finanzas.core.data.recordatorios.RecordatoriosDao
import io.github.joako601.finanzas.core.data.tarjetas.EstadoDeCuentaEntity
import io.github.joako601.finanzas.core.data.tarjetas.TarjetaEntity
import io.github.joako601.finanzas.core.data.tarjetas.TarjetasDao
import io.github.joako601.finanzas.core.domain.libro.CatalogoInicial

/**
 * Base de datos de la app.
 *
 * `exportSchema = true` escribe el esquema de cada versión en core/data/schemas/.
 * Esos JSON van al repositorio: cuando cambie el esquema, la diferencia se revisa en
 * el commit y sirve para probar la migración.
 *
 * Historial de versiones (D-012: cada cambio trae migración, nunca se borra la base):
 * - 1: cuenta, movimiento, asiento.
 * - 2: tarjeta y estado_de_cuenta. Solo agrega tablas, así que Room genera la
 *   migración sola (AutoMigration) comparando los esquemas 1.json y 2.json.
 * - 3: recordatorio_entregado (avisos entregados y su retraso). También automática.
 *
 * Todavía sin cifrar. SQLCipher o no se decide con el modelo de amenazas, antes de la
 * primera versión que use alguien más.
 */
@Database(
    entities = [
        CuentaEntity::class,
        MovimientoEntity::class,
        AsientoEntity::class,
        TarjetaEntity::class,
        EstadoDeCuentaEntity::class,
        RecordatorioEntregadoEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
internal abstract class FinanzasDatabase : RoomDatabase() {

    abstract fun libroDao(): LibroDao

    abstract fun tarjetasDao(): TarjetasDao

    abstract fun recordatoriosDao(): RecordatoriosDao

    companion object {
        const val NOMBRE_ARCHIVO = "finanzas.db"

        fun crear(context: Context): FinanzasDatabase =
            Room.databaseBuilder(context.applicationContext, FinanzasDatabase::class.java, NOMBRE_ARCHIVO)
                .addCallback(SembrarCatalogo)
                .build()

        /** Base en memoria para pruebas: se pierde al cerrarse. */
        fun enMemoria(context: Context): FinanzasDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, FinanzasDatabase::class.java)
                .addCallback(SembrarCatalogo)
                .build()
    }
}

/**
 * Inserta el catálogo inicial la primera vez que se crea la base. Corre dentro de la
 * misma transacción que crea las tablas: o queda la base con su catálogo, o no queda nada.
 * En una actualización (migración) no corre: el catálogo ya existe.
 */
internal object SembrarCatalogo : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        CatalogoInicial.todas.forEachIndexed { indice, semilla ->
            db.execSQL(
                "INSERT INTO cuenta (nombre, tipo, archivada, orden) VALUES (?, ?, 0, ?)",
                arrayOf<Any>(semilla.nombre, semilla.tipo.name, indice + 1),
            )
        }
    }
}
