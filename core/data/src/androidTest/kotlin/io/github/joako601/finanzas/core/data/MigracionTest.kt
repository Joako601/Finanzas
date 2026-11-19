package io.github.joako601.finanzas.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Oráculo de D-012: una base creada con la versión 1 (la que ya tienes en el teléfono),
 * con datos, migra a la versión 2 sin perder nada.
 */
@RunWith(AndroidJUnit4::class)
class MigracionTest {

    private val nombre = "migracion-prueba.db"

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FinanzasDatabase::class.java,
    )

    @Test
    fun de_1_a_2_conserva_cuentas_movimientos_y_saldos() = runTest {
        // Base en versión 1 con el SQL de su esquema exportado, y datos como los de un usuario real.
        ayudante.createDatabase(nombre, 1).use { db ->
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (1, 'Efectivo', 'ACTIVO', 0, 1)")
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (2, 'Saldos iniciales', 'CAPITAL', 0, 2)")
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (3, 'Súper', 'GASTO', 0, 3)")
            db.execSQL("INSERT INTO movimiento (id, fecha, descripcion, creado_en) VALUES (1, 20774, 'Saldo inicial', 0)")
            db.execSQL("INSERT INTO asiento (movimiento_id, cuenta_id, centavos) VALUES (1, 1, 100000)")
            db.execSQL("INSERT INTO asiento (movimiento_id, cuenta_id, centavos) VALUES (1, 2, -100000)")
            db.execSQL("INSERT INTO movimiento (id, fecha, descripcion, creado_en) VALUES (2, 20775, 'tortillas', 0)")
            db.execSQL("INSERT INTO asiento (movimiento_id, cuenta_id, centavos) VALUES (2, 3, 8550)")
            db.execSQL("INSERT INTO asiento (movimiento_id, cuenta_id, centavos) VALUES (2, 1, -8550)")
        }

        // Migra y valida que el resultado sea idéntico al esquema 2.json exportado.
        ayudante.runMigrationsAndValidate(nombre, 2, true).close()

        // Abre la base migrada con Room, como lo hará la app nueva, y lee con el repositorio.
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val baseDeDatos = Room.databaseBuilder(contexto, FinanzasDatabase::class.java, nombre).build()
        try {
            val repositorio = RepositorioLibroRoom(baseDeDatos)
            assertEquals(listOf("Efectivo", "Saldos iniciales", "Súper"), repositorio.observarCuentas().first().map { it.nombre })

            val movimientos = repositorio.observarMovimientos().first()
            assertEquals(2, movimientos.size)
            assertEquals("tortillas", movimientos.first().descripcion)

            val saldos = repositorio.observarSaldos().first()
            assertEquals(saldosPorCuenta(movimientos), saldos)
            // Efectivo: $1,000.00 de saldo inicial menos $85.50 de tortillas.
            assertEquals(Dinero(91_450), saldos[CuentaId(1)])

            // Las tablas nuevas existen y están vacías.
            assertTrue(baseDeDatos.tarjetasDao().observarTarjetas().first().isEmpty())
        } finally {
            baseDeDatos.close()
        }
    }

    /** Quien instala hoy desde la versión 1 pasa por 1→2→3 de un jalón. */
    @Test
    fun de_1_a_3_en_cadena() {
        ayudante.createDatabase(nombre, 1).use { db ->
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (1, 'Efectivo', 'ACTIVO', 0, 1)")
        }
        ayudante.runMigrationsAndValidate(nombre, 3, true).use { db ->
            db.query("SELECT COUNT(*) FROM recordatorio_entregado").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT nombre FROM cuenta").use { c ->
                c.moveToFirst()
                assertEquals("Efectivo", c.getString(0))
            }
        }
    }

    /** El caso del teléfono del autor: versión 2 con tarjeta y estado de cuenta capturado. */
    @Test
    fun de_2_a_3_conserva_tarjetas_y_estados() {
        ayudante.createDatabase(nombre, 2).use { db ->
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (5, 'Nu', 'PASIVO', 0, 5)")
            db.execSQL("INSERT INTO tarjeta (cuenta_id, linea_centavos, dia_corte, dia_limite) VALUES (5, 3000000, 15, 5)")
            db.execSQL(
                "INSERT INTO estado_de_cuenta (tarjeta_id, fecha_corte, fecha_limite, pago_sin_intereses, pago_minimo, " +
                    "corte_predicho, limite_predicho, capturado_en) VALUES (5, 20772, 20792, 250000, 25000, 20772, 20792, 0)",
            )
        }
        ayudante.runMigrationsAndValidate(nombre, 3, true).use { db ->
            db.query("SELECT dia_corte, linea_centavos FROM tarjeta WHERE cuenta_id = 5").use { c ->
                c.moveToFirst()
                assertEquals(15, c.getInt(0))
                assertEquals(3_000_000L, c.getLong(1))
            }
            db.query("SELECT pago_sin_intereses FROM estado_de_cuenta WHERE tarjeta_id = 5").use { c ->
                c.moveToFirst()
                assertEquals(250_000L, c.getLong(0))
            }
        }
    }
}
