package io.github.joako601.finanzas.core.data.libro

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.CatalogoInicial
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pruebas de Room contra SQLite real del teléfono. Se ejecutan con
 * `./gradlew :core:data:connectedAndroidTest` con el teléfono conectado por USB.
 */
@RunWith(AndroidJUnit4::class)
class RepositorioLibroRoomTest {

    private lateinit var baseDeDatos: FinanzasDatabase
    private lateinit var repositorio: RepositorioLibroRoom
    private val fecha = LocalDate(2026, 11, 15)

    @Before
    fun abrir() {
        baseDeDatos = FinanzasDatabase.enMemoria(InstrumentationRegistry.getInstrumentation().targetContext)
        repositorio = RepositorioLibroRoom(baseDeDatos)
    }

    @After
    fun cerrar() {
        baseDeDatos.close()
    }

    @Test
    fun la_base_nace_con_el_catalogo() = runTest {
        val nombres = repositorio.observarCuentas().first().map { it.nombre }
        assertEquals(CatalogoInicial.todas.map { it.nombre }, nombres)
    }

    @Test
    fun registrar_y_leer_un_gasto() = runTest {
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val super_ = cuentas.first { it.tipo == TipoCuenta.GASTO }

        val id = repositorio.registrar(Registro.gasto(fecha, Dinero(12_345), efectivo, super_, "tortillas"))

        val leido = repositorio.observarMovimientos().first().single()
        assertEquals(id, leido.id)
        assertEquals(fecha, leido.fecha)
        assertEquals("tortillas", leido.descripcion)
        assertEquals(Dinero(-12_345), repositorio.observarSaldos().first()[efectivo.id])
    }

    /** Oráculo: la suma en SQL coincide con la suma del dominio sobre los mismos datos. */
    @Test
    fun saldos_en_sql_coinciden_con_el_dominio() = runTest {
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val gastos = cuentas.filter { it.tipo == TipoCuenta.GASTO }
        val ingresos = cuentas.filter { it.tipo == TipoCuenta.INGRESO }
        val azar = Random(seed = 42)

        repeat(200) {
            val monto = Dinero(azar.nextLong(1, 10_000_000))
            val movimiento = if (azar.nextBoolean()) {
                Registro.gasto(fecha, monto, efectivo, gastos.random(azar))
            } else {
                Registro.ingreso(fecha, monto, efectivo, ingresos.random(azar))
            }
            repositorio.registrar(movimiento)
        }

        val movimientos = repositorio.observarMovimientos().first()
        assertEquals(200, movimientos.size)
        assertEquals(saldosPorCuenta(movimientos), repositorio.observarSaldos().first())
    }

    @Test
    fun renombrar_y_ajustar_una_cuenta_conserva_id_y_deja_el_saldo_escrito() = runTest {
        val servicio = ServicioLibro(repositorio)
        val hoy = LocalDate(2026, 11, 24)
        val cuenta = servicio.crearCuentaDeDinero("Nu debito", Dinero.pesos(1_000), hoy)
        val super_ = repositorio.observarCuentas().first().first { it.tipo == TipoCuenta.GASTO }
        // Entra un gasto mientras el diálogo de edición sigue mostrando $1,000.
        repositorio.registrar(Registro.gasto(hoy, Dinero.pesos(100), cuenta, super_))

        servicio.editarCuentaDeDinero(cuenta, "  Nu principal ", Dinero.pesos(1_000), Dinero.pesos(500), hoy)

        val guardada = repositorio.observarCuentas().first().single { it.id == cuenta.id }
        assertEquals("Nu principal", guardada.nombre)
        assertEquals(Dinero.pesos(500), repositorio.observarSaldos().first()[cuenta.id])
    }

    @Test
    fun eliminar_borra_tambien_los_asientos() = runTest {
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val super_ = cuentas.first { it.tipo == TipoCuenta.GASTO }
        val id = repositorio.registrar(Registro.gasto(fecha, Dinero(100), efectivo, super_))

        repositorio.eliminar(id)

        assertTrue(repositorio.observarMovimientos().first().isEmpty())
        assertTrue(repositorio.observarSaldos().first().isEmpty(), "quedaron asientos huérfanos")
    }

    @Test
    fun editar_conserva_id_y_lugar_y_reemplaza_los_asientos() = runTest {
        var reloj = 1_000L
        val repositorio = RepositorioLibroRoom(baseDeDatos) { reloj }
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val super_ = cuentas.first { it.tipo == TipoCuenta.GASTO }
        val comida = cuentas.filter { it.tipo == TipoCuenta.GASTO }[1]
        val tacos = repositorio.registrar(Registro.gasto(fecha, Dinero(10_000), efectivo, super_, "tacos"))
        reloj = 2_000L
        val refresco = repositorio.registrar(Registro.gasto(fecha, Dinero(2_500), efectivo, super_, "refresco"))
        reloj = 9_000L

        repositorio.actualizar(Registro.gasto(fecha, Dinero(8_550), efectivo, comida, "tacos al pastor").copy(id = tacos))

        val movimientos = repositorio.observarMovimientos().first()
        assertEquals(listOf(refresco, tacos), movimientos.map { it.id }, "editar no lo mueve al principio del día")
        val editado = movimientos.last()
        assertEquals("tacos al pastor", editado.descripcion)
        assertEquals(2, editado.asientos.size, "no quedan asientos del original")
        val saldos = repositorio.observarSaldos().first()
        assertEquals(Dinero(-11_050), saldos[efectivo.id])
        assertEquals(Dinero(2_500), saldos[super_.id])
        assertEquals(Dinero(8_550), saldos[comida.id])
    }

    @Test
    fun editar_es_atomico_si_falla_no_cambia_nada() = runTest {
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val super_ = cuentas.first { it.tipo == TipoCuenta.GASTO }
        val id = repositorio.registrar(Registro.gasto(fecha, Dinero(100), efectivo, super_, "original"))
        val antes = repositorio.observarMovimientos().first()

        val fantasma = io.github.joako601.finanzas.core.domain.libro.Cuenta(
            io.github.joako601.finanzas.core.domain.libro.CuentaId(9_999), "Fantasma", TipoCuenta.GASTO,
        )
        assertFailsWith<SQLiteConstraintException> {
            repositorio.actualizar(Registro.gasto(fecha, Dinero(200), efectivo, fantasma, "nuevo").copy(id = id))
        }

        assertEquals(antes, repositorio.observarMovimientos().first())
    }

    @Test
    fun editar_un_movimiento_que_ya_no_existe_falla() = runTest {
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val super_ = cuentas.first { it.tipo == TipoCuenta.GASTO }
        val id = repositorio.registrar(Registro.gasto(fecha, Dinero(100), efectivo, super_))
        repositorio.eliminar(id)

        assertFailsWith<IllegalStateException> {
            repositorio.actualizar(Registro.gasto(fecha, Dinero(200), efectivo, super_).copy(id = id))
        }
        assertTrue(repositorio.observarSaldos().first().isEmpty(), "no se crean asientos sueltos")
    }

    @Test
    fun la_llave_foranea_rechaza_cuentas_inexistentes() = runTest {
        val dao = baseDeDatos.libroDao()
        assertFailsWith<SQLiteConstraintException> {
            dao.insertarCompleto(
                MovimientoEntity(fechaDiaEpoca = 0, descripcion = "", creadoEnMillis = 0),
                listOf(
                    AsientoEntity(movimientoId = 0, cuentaId = 9_999, centavos = 100),
                    AsientoEntity(movimientoId = 0, cuentaId = 9_998, centavos = -100),
                ),
            )
        }
        // La transacción se revirtió completa: tampoco quedó el movimiento.
        assertTrue(repositorio.observarMovimientos().first().isEmpty())
    }

    @Test
    fun crear_cuenta_con_saldo_inicial_es_atomico() = runTest {
        val servicio = ServicioLibro(repositorio)
        val cuenta = servicio.crearCuentaDeDinero("Nu", Dinero(250_000), fecha)

        assertEquals(Dinero(250_000), repositorio.observarSaldos().first()[cuenta.id])
        val capital = repositorio.cuentaDeSaldosIniciales()
        assertEquals(Dinero(-250_000), repositorio.observarSaldos().first()[capital.id])
    }

    @Test
    fun movimientos_se_ordenan_del_mas_reciente_al_mas_antiguo() = runTest {
        val cuentas = repositorio.observarCuentas().first()
        val efectivo = cuentas.first { it.tipo == TipoCuenta.ACTIVO }
        val super_ = cuentas.first { it.tipo == TipoCuenta.GASTO }
        for (dia in listOf(3, 1, 2)) {
            repositorio.registrar(Registro.gasto(LocalDate(2026, 11, dia), Dinero(100), efectivo, super_))
        }
        val fechas = repositorio.observarMovimientos().first().map(Movimiento::fecha).map { it.day }
        assertEquals(listOf(3, 2, 1), fechas)
    }
}
