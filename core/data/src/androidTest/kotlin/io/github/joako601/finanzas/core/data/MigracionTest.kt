package io.github.joako601.finanzas.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.CatalogoInicial
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
            // Room abre en la versión actual: pasa también por 3→4, que agrega la categoría de impuestos al final.
            assertEquals(
                listOf("Efectivo", "Saldos iniciales", "Súper", CATEGORIA_PREDIAL),
                repositorio.observarCuentas().first().map { it.nombre },
            )

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

    /** El caso del teléfono del autor hoy: versión 3 con datos. Gana la tabla de fijos y la categoría de impuestos. */
    @Test
    fun de_3_a_4_agrega_recurrentes_y_la_categoria_de_impuestos_una_vez() {
        ayudante.createDatabase(nombre, 3).use { db ->
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (1, 'Efectivo', 'ACTIVO', 0, 1)")
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (17, 'Otros gastos', 'GASTO', 0, 17)")
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (30, 'Nu', 'PASIVO', 0, 30)")
            db.execSQL("INSERT INTO tarjeta (cuenta_id, linea_centavos, dia_corte, dia_limite) VALUES (30, 3000000, 15, 5)")
            db.execSQL(
                "INSERT INTO recordatorio_entregado (clave, tipo, programado_en, entregado_en) " +
                    "VALUES ('pago:30:2026-12-05:3', 'PAGO_PROXIMO', 1, 2)",
            )
        }
        ayudante.runMigrationsAndValidate(nombre, 4, true).use { db ->
            db.query("SELECT tipo, orden FROM cuenta WHERE nombre = ?", arrayOf(CATEGORIA_PREDIAL)).use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("GASTO", c.getString(0))
                assertEquals(31, c.getInt(1), "va al final del catálogo")
            }
            db.query("SELECT COUNT(*) FROM cuenta").use { c ->
                c.moveToFirst()
                assertEquals(4, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM recurrente").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT dia_corte FROM tarjeta WHERE cuenta_id = 30").use { c ->
                c.moveToFirst()
                assertEquals(15, c.getInt(0))
            }
            db.query("SELECT clave FROM recordatorio_entregado").use { c ->
                c.moveToFirst()
                assertEquals("pago:30:2026-12-05:3", c.getString(0))
            }
        }
    }

    /** Si la categoría ya existía (base creada con el catálogo nuevo y luego bajada de versión), no se duplica. */
    @Test
    fun de_3_a_4_no_duplica_la_categoria() {
        ayudante.createDatabase(nombre, 3).use { db ->
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (1, ?, 'GASTO', 0, 1)", arrayOf(CATEGORIA_PREDIAL))
        }
        ayudante.runMigrationsAndValidate(nombre, 4, true).use { db ->
            db.query("SELECT COUNT(*) FROM cuenta WHERE nombre = ?", arrayOf(CATEGORIA_PREDIAL)).use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    /** Quien instala hoy desde la versión 1 pasa por todas las migraciones de un jalón. */
    @Test
    fun de_1_a_4_en_cadena() {
        ayudante.createDatabase(nombre, 1).use { db ->
            db.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (1, 'Efectivo', 'ACTIVO', 0, 1)")
        }
        ayudante.runMigrationsAndValidate(nombre, 4, true).use { db ->
            db.query("SELECT nombre FROM cuenta ORDER BY orden").use { c ->
                assertEquals(2, c.count)
                c.moveToFirst()
                assertEquals("Efectivo", c.getString(0))
            }
        }
    }

    /** El nombre que inserta la migración es el mismo del catálogo de bases nuevas. */
    @Test
    fun la_categoria_de_la_migracion_esta_en_el_catalogo() {
        assertTrue(CATEGORIA_PREDIAL in CatalogoInicial.categoriasDeGasto)
    }
}
