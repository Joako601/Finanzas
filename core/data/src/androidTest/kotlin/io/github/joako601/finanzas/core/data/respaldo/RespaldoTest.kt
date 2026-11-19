package io.github.joako601.finanzas.core.data.respaldo

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.CATEGORIA_PREDIAL
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.data.recurrentes.RepositorioRecurrentesRoom
import io.github.joako601.finanzas.core.data.tarjetas.RepositorioTarjetasRoom
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.ServicioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Simulacro de restauración (D-018): respaldar, cambiar, restaurar y comparar fila por fila. */
@RunWith(AndroidJUnit4::class)
class RespaldoTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var base: FinanzasDatabase
    private lateinit var libro: RepositorioLibroRoom
    private lateinit var respaldo: Respaldo
    private val hoy = LocalDate(2026, 11, 19)
    private val contrasena = "quincena del 15".toCharArray()

    @get:Rule
    val ayudante = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinanzasDatabase::class.java)

    @Before
    fun abrir() {
        base = FinanzasDatabase.enMemoria(contexto)
        libro = RepositorioLibroRoom(base)
        respaldo = Respaldo(contexto, base)
    }

    @After
    fun cerrar() {
        base.close()
    }

    private suspend fun cuenta(nombre: String) = libro.observarCuentas().first().single { it.nombre == nombre }

    /** Datos en todas las tablas: cuentas, movimientos, tarjeta con estado de cuenta y un pago fijo. */
    private suspend fun llenar() {
        val efectivo = ServicioLibro(libro).crearCuentaDeDinero("Débito", Dinero.pesos(5_000), hoy)
        libro.registrar(Registro.gasto(hoy, Dinero(123_450), efectivo, cuenta("Súper y despensa"), "Tacos, \"los buenos\" ñ"))
        val tarjetas = ServicioTarjetas(libro, RepositorioTarjetasRoom(base.tarjetasDao()))
        val nu = tarjetas.crearTarjeta("Nu", Dinero.pesos(30_000), 15, 5, Dinero.pesos(2_500), hoy)
        tarjetas.capturarEstadoDeCuenta(nu, LocalDate(2026, 11, 15), LocalDate(2026, 12, 5), "2500", "250")
        ServicioRecurrentes(libro, RepositorioRecurrentesRoom(base.recurrentesDao())).crear(
            "Quincena", ClaseRegistro.INGRESO, Dinero.pesos(9_000), cuenta("Sueldo"), Periodicidad.QUINCENAL, hoy, hoy,
        )
    }

    /** Todas las filas de todas las tablas de datos, como texto, para comparar dos bases. */
    private fun volcado(db: SupportSQLiteDatabase): Map<String, List<String>> =
        ordenPadresPrimero(db).associateWith { tabla ->
            db.query("SELECT * FROM `$tabla` ORDER BY rowid").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add((0 until cursor.columnCount).joinToString("|") { "${cursor.getColumnName(it)}=${cursor.getString(it)}" })
                    }
                }
            }
        }

    private val db get() = base.openHelper.writableDatabase

    @Test
    fun simulacro_respaldar_cambiar_restaurar_deja_todo_identico() = runTest {
        llenar()
        val antes = volcado(db)
        assertTrue(antes.values.count { it.isNotEmpty() } >= 6, "el simulacro debe cubrir casi todas las tablas: $antes")

        val archivo = respaldo.crear(contrasena)

        // Cambios después del respaldo: un gasto nuevo, una cuenta nueva y un movimiento borrado.
        libro.registrar(Registro.gasto(hoy, Dinero.pesos(99), cuenta("Efectivo"), cuenta("Súper y despensa"), "después"))
        libro.crearCuenta("Ahorro", TipoCuenta.ACTIVO)
        libro.eliminar(libro.observarMovimientos().first().first { it.descripcion.startsWith("Tacos") }.id!!)
        assertNotEquals(antes, volcado(db))

        respaldo.restaurar(archivo, contrasena)

        assertEquals(antes, volcado(db))
    }

    @Test
    fun las_pantallas_abiertas_se_enteran_de_la_restauracion() = runTest {
        llenar()
        val archivo = respaldo.crear(contrasena)
        libro.crearCuenta("Ahorro", TipoCuenta.ACTIVO)

        // Tiempo real (no el virtual de runTest): Room emite desde otro hilo.
        withContext(Dispatchers.Default) {
            val emisiones = Channel<List<String>>(Channel.UNLIMITED)
            val observador = launch { libro.observarCuentas().collect { lista -> emisiones.send(lista.map { it.nombre }) } }
            // Una pantalla que ya está mirando, con la cuenta nueva a la vista.
            assertTrue("Ahorro" in withTimeout(5_000) { emisiones.receive() })

            respaldo.restaurar(archivo, contrasena)

            // Sin volver a suscribirse, recibe la lista restaurada.
            withTimeout(5_000) { while ("Ahorro" in emisiones.receive()) Unit }
            observador.cancel()
        }
    }

    @Test
    fun contrasena_equivocada_no_toca_nada() = runTest {
        llenar()
        val archivo = respaldo.crear(contrasena)
        libro.crearCuenta("Ahorro", TipoCuenta.ACTIVO)
        val antes = volcado(db)

        val error = assertFailsWith<ErrorRespaldo> { respaldo.restaurar(archivo, "otra".toCharArray()) }

        assertEquals(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO, error.motivo)
        assertEquals(antes, volcado(db))
    }

    @Test
    fun cifrado_bien_pero_sin_una_base_adentro_no_toca_nada() = runTest {
        llenar()
        val antes = volcado(db)
        val falso = CifradoRespaldo.cifrar("no soy una base".encodeToByteArray(), contrasena, CifradoRespaldo.MINIMO_ITERACIONES)

        val error = assertFailsWith<ErrorRespaldo> { respaldo.restaurar(falso, contrasena) }

        assertEquals(ErrorRespaldo.Motivo.BASE_INVALIDA, error.motivo)
        assertEquals(antes, volcado(db))
    }

    @Test
    fun un_respaldo_de_una_version_anterior_se_migra_al_restaurar() = runTest {
        val nombre = "respaldo-v3-prueba.db"
        ayudante.createDatabase(nombre, 3).use { v3 ->
            v3.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (1, 'Efectivo', 'ACTIVO', 0, 1)")
            v3.execSQL("INSERT INTO cuenta (id, nombre, tipo, archivada, orden) VALUES (2, 'Súper', 'GASTO', 0, 2)")
            v3.execSQL("INSERT INTO movimiento (id, fecha, descripcion, creado_en) VALUES (1, 20774, 'tortillas', 0)")
            v3.execSQL("INSERT INTO asiento (movimiento_id, cuenta_id, centavos) VALUES (1, 2, 8550)")
            v3.execSQL("INSERT INTO asiento (movimiento_id, cuenta_id, centavos) VALUES (1, 1, -8550)")
        }
        val archivo = contexto.getDatabasePath(nombre)
        val sobre = CifradoRespaldo.cifrar(archivo.readBytes(), contrasena, CifradoRespaldo.MINIMO_ITERACIONES)
        SQLiteDatabase.deleteDatabase(archivo)

        respaldo.restaurar(sobre, contrasena)

        assertEquals(listOf("Efectivo", "Súper", CATEGORIA_PREDIAL), libro.observarCuentas().first().map { it.nombre })
        assertEquals("tortillas", libro.observarMovimientos().first().single().descripcion)
    }

    @Test
    fun un_respaldo_de_una_version_mas_nueva_se_rechaza() = runTest {
        val nombre = "respaldo-futuro-prueba.db"
        ayudante.createDatabase(nombre, 4).use { it.version = 99 }
        val archivo = contexto.getDatabasePath(nombre)
        val sobre = CifradoRespaldo.cifrar(archivo.readBytes(), contrasena, CifradoRespaldo.MINIMO_ITERACIONES)
        SQLiteDatabase.deleteDatabase(archivo)

        val error = assertFailsWith<ErrorRespaldo> { respaldo.restaurar(sobre, contrasena) }

        assertEquals(ErrorRespaldo.Motivo.VERSION_NUEVA, error.motivo)
    }

    @Test
    fun no_quedan_archivos_temporales_sin_cifrar() = runTest {
        llenar()
        val archivo = respaldo.crear(contrasena)
        respaldo.restaurar(archivo, contrasena)

        val sobrantes = contexto.getDatabasePath("x").parentFile!!.listFiles()!!.map { it.name }.filter { it.startsWith("respaldo-") }
        assertEquals(emptyList(), sobrantes)
    }
}
