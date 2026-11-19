package io.github.joako601.finanzas.core.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.joako601.finanzas.core.data.libro.AsientoEntity
import io.github.joako601.finanzas.core.data.libro.CuentaEntity
import io.github.joako601.finanzas.core.data.libro.LibroDao
import io.github.joako601.finanzas.core.data.libro.MovimientoEntity
import io.github.joako601.finanzas.core.data.recordatorios.RecordatorioEntregadoEntity
import io.github.joako601.finanzas.core.data.recordatorios.RecordatoriosDao
import io.github.joako601.finanzas.core.data.recurrentes.RecurrenteEntity
import io.github.joako601.finanzas.core.data.recurrentes.RecurrentesDao
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
 * - 4: recurrente (pagos e ingresos fijos). Automática, más [De3a4], que agrega la
 *   categoría "Impuestos y predial" a las bases creadas antes de que existiera.
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
        RecurrenteEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = De3a4::class),
    ],
)
internal abstract class FinanzasDatabase : RoomDatabase() {

    abstract fun libroDao(): LibroDao

    abstract fun tarjetasDao(): TarjetasDao

    abstract fun recordatoriosDao(): RecordatoriosDao

    abstract fun recurrentesDao(): RecurrentesDao

    companion object {
        const val NOMBRE_ARCHIVO = "finanzas.db"

        fun crear(context: Context): FinanzasDatabase =
            constructor(context, NOMBRE_ARCHIVO)
                .addCallback(SembrarCatalogo)
                .build()

        /**
         * La configuración común de la base de la app y de las copias de respaldo. Toda
         * migración manual (`addMigrations`) va AQUÍ: si solo la tuviera [crear], los
         * respaldos de versiones anteriores no se podrían restaurar.
         */
        fun constructor(context: Context, nombre: String): RoomDatabase.Builder<FinanzasDatabase> =
            Room.databaseBuilder(context.applicationContext, FinanzasDatabase::class.java, nombre)

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

/** Nombre de la categoría que llegó con la versión 4 (también está en [CatalogoInicial]). */
internal const val CATEGORIA_PREDIAL = "Impuestos y predial"

/**
 * Después de crear la tabla `recurrente`, agrega la categoría de impuestos al final del
 * catálogo si no existe. Corre dentro de la transacción de la migración.
 *
 * El MAX va en una subconsulta: un `SELECT MAX(...) FROM cuenta WHERE ...` siempre
 * devuelve una fila aunque el WHERE no deje ninguna, y la insertaría dos veces.
 */
internal class De3a4 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO cuenta (nombre, tipo, archivada, orden)
            SELECT ?, 'GASTO', 0, (SELECT COALESCE(MAX(orden), 0) + 1 FROM cuenta)
            WHERE NOT EXISTS (SELECT 1 FROM cuenta WHERE nombre = ?)
            """.trimIndent(),
            arrayOf<Any>(CATEGORIA_PREDIAL, CATEGORIA_PREDIAL),
        )
    }
}
