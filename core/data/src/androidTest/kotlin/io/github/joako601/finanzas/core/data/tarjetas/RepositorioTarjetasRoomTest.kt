package io.github.joako601.finanzas.core.data.tarjetas

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RepositorioTarjetasRoomTest {

    private lateinit var baseDeDatos: FinanzasDatabase
    private lateinit var libro: RepositorioLibroRoom
    private lateinit var tarjetas: RepositorioTarjetasRoom
    private lateinit var servicio: ServicioTarjetas
    private val hoy = LocalDate(2026, 11, 18)

    @Before
    fun abrir() {
        baseDeDatos = FinanzasDatabase.enMemoria(InstrumentationRegistry.getInstrumentation().targetContext)
        libro = RepositorioLibroRoom(baseDeDatos)
        tarjetas = RepositorioTarjetasRoom(baseDeDatos.tarjetasDao())
        servicio = ServicioTarjetas(libro, tarjetas)
    }

    @After
    fun cerrar() {
        baseDeDatos.close()
    }

    @Test
    fun crear_tarjeta_guarda_cuenta_datos_y_deuda() = runTest {
        val creada = servicio.crearTarjeta("Nu", Dinero.pesos(30_000), 15, 5, Dinero.pesos(2_500), hoy)

        val leida = tarjetas.observarTarjetas().first().single()
        assertEquals(creada, leida)
        assertEquals(Dinero.pesos(2_500), deudaDe(leida, libro.observarSaldos().first()))
    }

    @Test
    fun si_falla_la_tarjeta_no_queda_la_cuenta() = runTest {
        // Día de corte inválido: el dominio lo rechaza dentro de la transacción.
        assertFailsWith<IllegalArgumentException> {
            servicio.crearTarjeta("Rota", Dinero.pesos(1_000), 40, 5, Dinero.CERO, hoy)
        }
        assertTrue(libro.observarCuentas().first().none { it.nombre == "Rota" }, "quedó una cuenta huérfana")
    }

    @Test
    fun capturar_dos_veces_el_mismo_corte_reemplaza() = runTest {
        val tarjeta = servicio.crearTarjeta("Nu", Dinero.pesos(30_000), 15, 5, Dinero.CERO, hoy)
        servicio.capturarEstadoDeCuenta(tarjeta, LocalDate(2026, 11, 15), LocalDate(2026, 12, 5), "1000", "100")
        servicio.capturarEstadoDeCuenta(tarjeta, LocalDate(2026, 11, 15), LocalDate(2026, 12, 5), "1200", "120")

        val estado = tarjetas.observarEstadosDeCuenta().first().single()
        assertEquals(Dinero.pesos(1_200), estado.pagoSinIntereses)
        assertEquals(LocalDate(2026, 11, 15), estado.cortePredicho)
        assertEquals(LocalDate(2026, 12, 5), estado.limitePredicho)
    }

    @Test
    fun una_cuenta_con_tarjeta_no_se_puede_borrar() = runTest {
        val tarjeta = servicio.crearTarjeta("Nu", Dinero.pesos(30_000), 15, 5, Dinero.CERO, hoy)
        assertFailsWith<SQLiteConstraintException> {
            baseDeDatos.openHelper.writableDatabase.execSQL("DELETE FROM cuenta WHERE id = ${tarjeta.id.valor}")
        }
    }

    @Test
    fun la_tarjeta_debe_apuntar_a_una_cuenta_existente() = runTest {
        assertFailsWith<SQLiteConstraintException> {
            tarjetas.guardarTarjeta(Cuenta(CuentaId(9_999), "Fantasma", TipoCuenta.PASIVO), Dinero.CERO, 1, 20)
        }
    }

    @Test
    fun actualizar_tarjeta_modifica_datos_sin_borrar_estados_de_cuenta() = runTest {
        val tarjeta = servicio.crearTarjeta("Nu", Dinero.pesos(30_000), 15, 5, Dinero.CERO, hoy)
        servicio.capturarEstadoDeCuenta(tarjeta, LocalDate(2026, 11, 15), LocalDate(2026, 12, 5), "1500", "150")

        val actualizada = servicio.actualizarTarjeta(
            actual = tarjeta,
            nombre = "Nu Platino",
            lineaDeCredito = Dinero.pesos(45_000),
            diaDeCorte = 20,
            diaLimite = 10,
        )

        val leida = tarjetas.observarTarjetas().first().single()
        assertEquals(actualizada, leida)
        assertEquals("Nu Platino", leida.cuenta.nombre)
        assertEquals(Dinero.pesos(45_000), leida.lineaDeCredito)
        assertEquals(1, tarjetas.observarEstadosDeCuenta().first().size)
    }
}
