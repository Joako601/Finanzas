package io.github.joako601.finanzas.core.data.recurrentes

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.ServicioRecurrentes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RepositorioRecurrentesRoomTest {

    private lateinit var baseDeDatos: FinanzasDatabase
    private lateinit var libro: RepositorioLibroRoom
    private lateinit var repositorio: RepositorioRecurrentesRoom
    private lateinit var servicio: ServicioRecurrentes
    private val hoy = LocalDate(2026, 11, 19)

    @Before
    fun abrir() {
        baseDeDatos = FinanzasDatabase.enMemoria(InstrumentationRegistry.getInstrumentation().targetContext)
        libro = RepositorioLibroRoom(baseDeDatos)
        repositorio = RepositorioRecurrentesRoom(baseDeDatos.recurrentesDao())
        servicio = ServicioRecurrentes(libro, repositorio)
    }

    @After
    fun cerrar() {
        baseDeDatos.close()
    }

    private suspend fun categoria(nombre: String) = libro.observarCuentas().first().single { it.nombre == nombre }

    @Test
    fun cada_periodicidad_se_guarda_y_se_lee_igual_ordenadas_por_proxima() = runTest {
        val luz = categoria("Luz, agua y gas")
        val sueldo = categoria("Sueldo")
        val periodicidades = listOf(Periodicidad.SEMANAL, Periodicidad.ANUAL, Periodicidad.QUINCENAL)
        val creados = periodicidades.mapIndexed { i, periodicidad ->
            val clase = if (i == 2) ClaseRegistro.INGRESO else ClaseRegistro.GASTO
            servicio.crear(
                "Fijo $i", clase, Dinero.pesos(100L + i), if (i == 2) sueldo else luz, periodicidad,
                LocalDate(2026, 12, 20 - i * 5), hoy,
            )
        }

        val leidos = repositorio.observarRecurrentes().first()
        assertEquals(creados.sortedBy { it.proxima }, leidos)
    }

    @Test
    fun registrar_escribe_el_gasto_y_avanza_la_fecha() = runTest {
        val efectivo = categoria("Efectivo")
        val cfe = servicio.crear(
            "CFE", ClaseRegistro.GASTO, Dinero.pesos(650), categoria("Luz, agua y gas"), Periodicidad.BIMESTRAL,
            LocalDate(2026, 11, 25), hoy,
        )

        val resultado = servicio.registrar(cfe, "712.40", efectivo, categoria("Luz, agua y gas"), LocalDate(2026, 11, 24))

        assertIs<ResultadoRegistro.Valido>(resultado)
        assertEquals(Dinero(-71_240), libro.observarSaldos().first()[efectivo.id])
        assertEquals(LocalDate(2027, 1, 25), repositorio.observarRecurrentes().first().single().proxima)
    }

    @Test
    fun eliminar_la_serie_no_toca_los_movimientos() = runTest {
        val efectivo = categoria("Efectivo")
        val luz = categoria("Luz, agua y gas")
        val cfe = servicio.crear("CFE", ClaseRegistro.GASTO, Dinero.pesos(650), luz, Periodicidad.BIMESTRAL, hoy, hoy)
        servicio.registrar(cfe, "650", efectivo, luz, hoy)

        servicio.eliminar(cfe)

        assertTrue(repositorio.observarRecurrentes().first().isEmpty())
        assertEquals(1, libro.observarMovimientos().first().size)
    }

    @Test
    fun la_categoria_debe_existir() = runTest {
        assertFailsWith<SQLiteConstraintException> {
            repositorio.guardar(
                Recurrente(null, "X", ClaseRegistro.GASTO, Dinero.pesos(1), CuentaId(9_999), Periodicidad.MENSUAL, hoy, hoy),
            )
        }
    }

    @Test
    fun la_categoria_de_impuestos_existe_en_bases_nuevas() = runTest {
        assertEquals(TipoCuenta.GASTO, categoria("Impuestos y predial").tipo)
    }
}
