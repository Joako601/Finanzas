package io.github.joako601.finanzas.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.ui.tarjetas.EstadoTarjetas
import io.github.joako601.finanzas.ui.tarjetas.ETIQUETA_CARRUSEL
import io.github.joako601.finanzas.ui.tarjetas.ResumenTarjeta
import io.github.joako601.finanzas.ui.tarjetas.etiquetaPlastico
import io.github.joako601.finanzas.ui.tarjetas.etiquetaTarjeta
import io.github.joako601.finanzas.ui.tarjetas.SeccionTarjetas
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Las tarjetas se deslizan de lado y los círculos de abajo dicen cuál se ve y cuántas hay. */
@RunWith(AndroidJUnit4::class)
class CarruselTarjetasTest {

    @get:Rule
    val regla = createComposeRule()

    private fun resumen(
        id: Long,
        nombre: String,
        deuda: Dinero = Dinero.pesos(1_000),
        situacion: SituacionTarjeta = SituacionTarjeta.EnCiclo(LocalDate(2026, 12, 15)),
    ): ResumenTarjeta {
        val tarjeta = Tarjeta(Cuenta(CuentaId(id), nombre, TipoCuenta.PASIVO), Dinero.pesos(10_000), 15, 5)
        val analisis = AnalizadorTarjeta.analizar(
            tarjeta = tarjeta,
            deuda = deuda,
            estados = emptyList(),
            pagos = emptyList(),
            hoy = LocalDate(2026, 11, 20),
        ).copy(situacion = situacion)
        return ResumenTarjeta(
            tarjeta = tarjeta,
            deuda = deuda,
            disponible = analisis.desglose.creditoDisponible,
            situacion = situacion,
            analisis = analisis,
        )
    }

    private fun mostrar(vararg tarjetas: ResumenTarjeta) {
        regla.setContent {
            TemaFinanzas {
                SeccionTarjetas(EstadoTarjetas(tarjetas = tarjetas.toList()), onAgregar = {}, onPagar = {}, onCapturar = {})
            }
        }
    }

    @Test
    fun con_dos_tarjetas_se_desliza_y_los_circulos_siguen_a_la_que_se_ve() {
        mostrar(resumen(1, "Nu"), resumen(2, "BBVA credito"))

        regla.onNodeWithContentDescription("Tarjeta 1 de 2").assertIsDisplayed()

        regla.onNodeWithTag(ETIQUETA_CARRUSEL).performTouchInput { swipeLeft() }
        regla.onNodeWithContentDescription("Tarjeta 2 de 2").assertIsDisplayed()

        regla.onNodeWithTag(ETIQUETA_CARRUSEL).performTouchInput { swipeRight() }
        regla.onNodeWithContentDescription("Tarjeta 1 de 2").assertIsDisplayed()
    }

    /**
     * Una con texto largo (saldo a favor y aviso de captura) y otra con texto corto: el
     * plástico y la tarjeta entera deben medir lo mismo para que no cambie al deslizar.
     */
    @Test
    fun las_tarjetas_miden_lo_mismo_aunque_su_texto_no() {
        val larga = resumen(
            1, "Nu",
            deuda = -Dinero.pesos(950),
            situacion = SituacionTarjeta.CapturaPendiente(LocalDate(2026, 11, 15), LocalDate(2026, 12, 5)),
        )
        val corta = resumen(2, "BBVA credito")
        mostrar(larga, corta)

        fun alto(etiqueta: String) = regla.onNodeWithTag(etiqueta, useUnmergedTree = true).getBoundsInRoot().let { it.bottom - it.top }

        assertEquals("Plástico de tarjeta", alto(etiquetaPlastico(larga)), alto(etiquetaPlastico(corta)))
        assertEquals("Tarjeta completa", alto(etiquetaTarjeta(larga)), alto(etiquetaTarjeta(corta)))
        // Medir las tarjetas usa copias que no se dibujan; TalkBack no debe leerlas.
        val conNu = todosLosNodos(raizDeAccesibilidad(regla)).filter { it.text?.toString() == "Nu" }
        assertEquals("Nodos de accesibilidad con el nombre de la tarjeta", 1, conNu.size)
    }

    @Test
    fun con_una_sola_tarjeta_no_hay_circulos() {
        mostrar(resumen(1, "Nu"))

        regla.onNodeWithContentDescription("Tarjeta 1 de 1").assertDoesNotExist()
    }
}
