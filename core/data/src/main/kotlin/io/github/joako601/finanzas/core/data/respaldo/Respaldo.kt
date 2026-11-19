package io.github.joako601.finanzas.core.data.respaldo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Respaldo completo y cifrado de la base, y su restauración (D-018).
 *
 * **Crear:** se abre una base nueva y vacía con el esquema actual (Room la crea igual que
 * la de la app, sin el catálogo inicial) y se le copian todas las filas dentro de una
 * transacción de la base principal, para que el respaldo sea una foto consistente. Ese
 * archivo SQLite se cifra con [CifradoRespaldo]. No se copia el archivo de la base tal
 * cual porque Room la usa en modo WAL: el archivo solo no tiene los cambios recientes.
 * (`VACUUM INTO` lo resolvería, pero llega hasta Android 11 y aquí el mínimo es Android 8.)
 *
 * **Restaurar:** se descifra a un archivo temporal, se revisa que sea una base de Finanzas
 * sana y de una versión que esta app conoce, y se abre con Room, que la migra a la versión
 * actual si es de una versión anterior. Luego, en UNA transacción de la base principal, se
 * borra todo y se copian las filas del respaldo. Si algo falla, no cambia nada. Como pasa
 * dentro de una transacción de Room, las pantallas abiertas se actualizan solas.
 */
public class Respaldo internal constructor(
    private val context: Context,
    private val base: FinanzasDatabase,
) {
    init {
        // Si el proceso murió a mitad de un respaldo, aquí quedó una copia sin cifrar.
        SQLiteDatabase.deleteDatabase(context.getDatabasePath(SALIDA))
        SQLiteDatabase.deleteDatabase(context.getDatabasePath(ENTRADA))
    }

    /** El archivo cifrado con todos los datos, listo para guardarse donde el usuario elija. */
    public suspend fun crear(contrasena: CharArray): ByteArray = withContext(Dispatchers.IO) {
        conTemporal(SALIDA) { archivo ->
            val copia = abrirConRoom(archivo)
            try {
                // Lectura consistente de la principal y una sola escritura a disco en la copia.
                base.runInTransaction {
                    copia.runInTransaction {
                        copiarTodo(origen = base.openHelper.writableDatabase, destino = copia.openHelper.writableDatabase)
                    }
                }
            } finally {
                copia.close()
            }
            CifradoRespaldo.cifrar(archivo.readBytes(), contrasena)
        }
    }

    /**
     * Reemplaza TODOS los datos por los del respaldo. Lanza [ErrorRespaldo] sin tocar nada
     * si la contraseña no es, el archivo está dañado o es de una versión más nueva.
     */
    public suspend fun restaurar(sobre: ByteArray, contrasena: CharArray): Unit = withContext(Dispatchers.IO) {
        val datos = CifradoRespaldo.descifrar(sobre, contrasena)
        conTemporal(ENTRADA) { archivo ->
            archivo.writeBytes(datos)
            revisar(archivo)
            val copia = abrirConRoom(archivo)
            try {
                // Abrirla corre las migraciones si el respaldo es de una versión anterior.
                val origen = copia.openHelper.writableDatabase
                base.runInTransaction { copiarTodo(origen = origen, destino = base.openHelper.writableDatabase) }
            } catch (e: IllegalStateException) {
                // Room no reconoce el esquema (identity hash) o falta una migración.
                throw ErrorRespaldo(ErrorRespaldo.Motivo.BASE_INVALIDA, e)
            } catch (e: SQLiteException) {
                throw ErrorRespaldo(ErrorRespaldo.Motivo.BASE_INVALIDA, e)
            } finally {
                copia.close()
            }
        }
    }

    /** Integridad y versión, con SQLite directo y antes de que Room la abra (Room no sabe bajar de versión). */
    private fun revisar(archivo: File) {
        val version = try {
            SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val sana = db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
                val deRoom = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table'",
                    null,
                ).use { it.moveToFirst() }
                if (!sana || !deRoom) throw ErrorRespaldo(ErrorRespaldo.Motivo.BASE_INVALIDA)
                db.version
            }
        } catch (e: SQLiteException) {
            throw ErrorRespaldo(ErrorRespaldo.Motivo.BASE_INVALIDA, e)
        }
        val actual = base.openHelper.readableDatabase.version
        if (version > actual) throw ErrorRespaldo(ErrorRespaldo.Motivo.VERSION_NUEVA)
        if (version < 1) throw ErrorRespaldo(ErrorRespaldo.Motivo.BASE_INVALIDA)
    }

    /** Sin WAL: al cerrarse, todo queda en el archivo principal. Sin el catálogo inicial: llegan las filas copiadas. */
    private fun abrirConRoom(archivo: File): FinanzasDatabase =
        FinanzasDatabase.constructor(context, archivo.path)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    private inline fun <T> conTemporal(nombre: String, bloque: (File) -> T): T {
        val archivo = context.getDatabasePath(nombre)
        SQLiteDatabase.deleteDatabase(archivo)
        try {
            return bloque(archivo)
        } finally {
            // Borra también -journal, -wal y -shm: ahí quedan datos sin cifrar.
            SQLiteDatabase.deleteDatabase(archivo)
        }
    }

    public companion object {
        private const val SALIDA = "respaldo-salida.db"
        private const val ENTRADA = "respaldo-entrada.db"

        /** Si el archivo empieza como un respaldo de Finanzas, para avisar antes de pedir la contraseña. */
        public fun pareceRespaldo(archivo: ByteArray): Boolean = CifradoRespaldo.pareceRespaldo(archivo)
    }
}

/**
 * Copia todas las filas de [origen] a [destino], que deben tener el mismo esquema, y borra
 * antes lo que hubiera en [destino]. Debe llamarse dentro de una transacción de [destino].
 *
 * El orden importa por las llaves foráneas con RESTRICT (se revisan al momento aunque se
 * difieran las demás): se borra de hijas a padres y se inserta de padres a hijas.
 */
internal fun copiarTodo(origen: SupportSQLiteDatabase, destino: SupportSQLiteDatabase) {
    val tablas = ordenPadresPrimero(destino)
    tablas.asReversed().forEach { destino.execSQL("DELETE FROM `$it`") }
    tablas.forEach { tabla ->
        origen.query("SELECT * FROM `$tabla`").use { cursor ->
            while (cursor.moveToNext()) {
                destino.insert(tabla, SQLiteDatabase.CONFLICT_ABORT, fila(cursor))
            }
        }
    }
    copiarSecuencias(origen, destino)
}

/**
 * Los contadores de AUTOINCREMENT (`sqlite_sequence`). Sin ellos, un respaldo restaurado
 * en un teléfono nuevo reusaría el id de algo borrado, y un pago fijo nuevo podría heredar
 * la marca de "aviso entregado" del viejo. Se queda el mayor de los dos: en el mismo
 * teléfono la base principal ya puede ir más adelante.
 */
private fun copiarSecuencias(origen: SupportSQLiteDatabase, destino: SupportSQLiteDatabase) {
    origen.query("SELECT name, seq FROM sqlite_sequence").use { cursor ->
        while (cursor.moveToNext()) {
            val tabla = cursor.getString(0)
            val seq = cursor.getLong(1)
            destino.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = ?", arrayOf<Any>(seq, tabla))
            destino.execSQL(
                "INSERT INTO sqlite_sequence (name, seq) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = ?)",
                arrayOf<Any>(tabla, seq, tabla),
            )
        }
    }
}

private fun fila(cursor: Cursor): ContentValues = ContentValues(cursor.columnCount).apply {
    for (i in 0 until cursor.columnCount) {
        val columna = cursor.getColumnName(i)
        when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> putNull(columna)
            Cursor.FIELD_TYPE_INTEGER -> put(columna, cursor.getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> put(columna, cursor.getDouble(i))
            Cursor.FIELD_TYPE_BLOB -> put(columna, cursor.getBlob(i))
            else -> put(columna, cursor.getString(i))
        }
    }
}

/** Las tablas de datos (sin las internas de SQLite, Android y Room), cada una después de las que referencia. */
internal fun ordenPadresPrimero(db: SupportSQLiteDatabase): List<String> {
    val tablas = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
            "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    val padres = tablas.associateWith { tabla ->
        db.query("PRAGMA foreign_key_list(`$tabla`)").use { cursor ->
            val columna = cursor.getColumnIndexOrThrow("table")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(columna)) } - tabla
        }
    }
    val orden = mutableListOf<String>()
    while (orden.size < tablas.size) {
        val listas = tablas.filter { it !in orden && padres.getValue(it).all { padre -> padre in orden } }
        check(listas.isNotEmpty()) { "Ciclo de llaves foráneas entre ${tablas - orden.toSet()}" }
        orden += listas
    }
    return orden
}
