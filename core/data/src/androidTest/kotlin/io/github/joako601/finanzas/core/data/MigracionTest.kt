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
}
