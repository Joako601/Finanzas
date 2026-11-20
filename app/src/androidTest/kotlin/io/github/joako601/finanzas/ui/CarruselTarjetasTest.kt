package io.github.joako601.finanzas.ui

import androidx.compose.ui.test.assertIsDisplayed
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
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.ui.tarjetas.EstadoTarjetas
import io.github.joako601.finanzas.ui.tarjetas.ETIQUETA_CARRUSEL
import io.github.joako601.finanzas.ui.tarjetas.ResumenTarjeta
import io.github.joako601.finanzas.ui.tarjetas.SeccionTarjetas
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Las tarjetas se deslizan de lado y los círculos de abajo dicen cuál se ve y cuántas hay. */
@RunWith(AndroidJUnit4::class)
class CarruselTarjetasTest {

    @get:Rule
    val regla = createComposeRule()

    private fun resumen(id: Long, nombre: String) = ResumenTarjeta(
        tarjeta = Tarjeta(Cuenta(CuentaId(id), nombre, TipoCuenta.PASIVO), Dinero.pesos(10_000), 15, 5),
        deuda = Dinero.pesos(1_000),
        disponible = Dinero.pesos(9_000),
        situacion = SituacionTarjeta.EnCiclo(LocalDate(2026, 12, 15)),
    )

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

    @Test
    fun con_una_sola_tarjeta_no_hay_circulos() {
        mostrar(resumen(1, "Nu"))

        regla.onNodeWithContentDescription("Tarjeta 1 de 1").assertDoesNotExist()
    }
}
