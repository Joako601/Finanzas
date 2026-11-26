package io.github.joako601.finanzas.core.data.metas

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.metas.AporteMeta
import io.github.joako601.finanzas.core.domain.metas.MetaAhorro
import io.github.joako601.finanzas.core.domain.metas.TopeCategoria
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RepositorioMetasRoomTest {

    private lateinit var base: FinanzasDatabase
    private lateinit var libro: RepositorioLibroRoom
    private lateinit var repositorio: RepositorioMetasRoom

    @Before
    fun antes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        base = FinanzasDatabase.enMemoria(context)
        libro = RepositorioLibroRoom(base)
        repositorio = RepositorioMetasRoom(base.metasDao())
    }

    @After
    fun despues() = base.close()

    @Test
    fun guarda_actualiza_y_elimina_tope_de_categoria() = runTest {
        val comida = libro.observarCuentas().first().first { it.tipo == TipoCuenta.GASTO }

        repositorio.guardarTope(TopeCategoria(comida.id, Dinero.pesos(2_500)))
        assertEquals(listOf(TopeCategoria(comida.id, Dinero.pesos(2_500))), repositorio.observarTopes().first())

        // Upsert actualiza el mismo tope sin duplicar.
        repositorio.guardarTope(TopeCategoria(comida.id, Dinero.pesos(3_000)))
        assertEquals(listOf(TopeCategoria(comida.id, Dinero.pesos(3_000))), repositorio.observarTopes().first())

        repositorio.eliminarTope(comida.id)
        assertTrue(repositorio.observarTopes().first().isEmpty())
    }

    @Test
    fun crea_actualiza_y_elimina_meta_de_ahorro() = runTest {
        val id = repositorio.crearMeta(
            MetaAhorro(
                nombre = "Fondo de emergencia",
                objetivo = Dinero.pesos(20_000),
                apartado = Dinero.pesos(3_500),
                fechaLimite = LocalDate(2027, 6, 30),
                reservarDeDisponible = true,
            ),
            LocalDate(2026, 11, 1),
        )
        val guardada = repositorio.observarMetas().first().single()
        assertEquals(id, guardada.id)
        assertEquals("Fondo de emergencia", guardada.nombre)
        assertEquals(Dinero.pesos(3_500), guardada.apartado)
        assertEquals(LocalDate(2027, 6, 30), guardada.fechaLimite)

        repositorio.actualizarMeta(guardada.copy(apartado = Dinero.pesos(5_000), reservarDeDisponible = false))
        val actualizada = repositorio.observarMetas().first().single()
        assertEquals(Dinero.pesos(5_000), actualizada.apartado)
        assertEquals(false, actualizada.reservarDeDisponible)

        repositorio.eliminarMeta(id)
        assertTrue(repositorio.observarMetas().first().isEmpty())
    }

    @Test
    fun mover_apartado_guarda_meta_e_historial_y_borrar_la_meta_borra_su_historial() = runTest {
        val id = repositorio.crearMeta(
            MetaAhorro(nombre = "Viaje", objetivo = Dinero.pesos(10_000), apartado = Dinero.pesos(1_000), fechaLimite = null, reservarDeDisponible = true),
            LocalDate(2026, 11, 19),
        )
        val meta = repositorio.observarMetas().first().single()

        repositorio.moverApartado(meta.copy(apartado = Dinero.pesos(1_500)), LocalDate(2026, 11, 20), Dinero.pesos(500))
        repositorio.moverApartado(meta.copy(apartado = Dinero.pesos(1_300)), LocalDate(2026, 11, 22), -Dinero.pesos(200))

        assertEquals(Dinero.pesos(1_300), repositorio.observarMetas().first().single().apartado)
        assertEquals(
            // El apartado con que se creó también queda en el historial: la suma cuadra con el saldo.
            listOf(
                AporteMeta(LocalDate(2026, 11, 22), -Dinero.pesos(200)),
                AporteMeta(LocalDate(2026, 11, 20), Dinero.pesos(500)),
                AporteMeta(LocalDate(2026, 11, 19), Dinero.pesos(1_000)),
            ),
            repositorio.observarAportes(id).first(),
        )

        repositorio.eliminarMeta(id)
        assertTrue(repositorio.observarAportes(id).first().isEmpty())
    }
}
