package io.github.joako601.finanzas.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import io.github.joako601.finanzas.ui.movimientos.AccionesRecurrentes
import io.github.joako601.finanzas.ui.movimientos.AccionesTarjetas
import io.github.joako601.finanzas.ui.movimientos.ContenidoMovimientos
import io.github.joako601.finanzas.ui.movimientos.DiaDeMovimientos
import io.github.joako601.finanzas.ui.movimientos.EstadoMovimientos
import io.github.joako601.finanzas.ui.movimientos.SaldoCuenta
import io.github.joako601.finanzas.ui.recurrentes.EstadoRecurrentes
import io.github.joako601.finanzas.ui.tarjetas.EstadoTarjetas
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/**
 * Accesibilidad de la pantalla principal, revisada en el árbol de semántica que Compose
 * entrega a TalkBack (el mismo que lee el lector de pantalla). No escucha la voz: que
 * "−$123.45" se pronuncie "menos" sigue requiriendo una prueba con TalkBack encendido.
 *
 * Corre en el teléfono con el tipo de compilación "pruebas" (otro paquete), nunca sobre la
 * app que el autor usa.
 */
@RunWith(AndroidJUnit4::class)
class AccesibilidadTest {

    @get:Rule
    val regla = createComposeRule()

    private val hoy = LocalDate(2026, 11, 19)
    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val debito = Cuenta(CuentaId(2), "Nu débito", TipoCuenta.ACTIVO)
    private val super_ = Cuenta(CuentaId(3), "Súper y despensa", TipoCuenta.GASTO)
    private val cuentas = listOf(efectivo, debito, super_)

    /** La pantalla principal con dos cuentas, un gasto y una transferencia, sin ViewModels. */
    private fun mostrarPantalla() {
        val movimientos = listOf(
            Registro.gasto(hoy, Dinero(12_345), efectivo, super_, "tortillas").copy(id = MovimientoId(1)),
            Registro.transferencia(hoy, Dinero.pesos(500), debito, efectivo).copy(id = MovimientoId(2)),
        )
        val porId = cuentas.associateBy { it.id }
        val saldos = saldosPorCuenta(movimientos)
        val estado = EstadoMovimientos(
            cargando = false,
            cuentasDeDinero = listOf(efectivo, debito).map { SaldoCuenta(it, saldos[it.id] ?: Dinero.CERO) },
            categoriasDeGasto = listOf(super_),
            dias = listOf(DiaDeMovimientos(hoy, movimientos.map { it.resumir(porId) })),
        )
        val linea = LineaDeTiempo.calcular(cuentas, saldos, emptyList(), emptyList(), emptyList(), emptyList(), hoy)
        regla.setContent {
            TemaFinanzas {
                ContenidoMovimientos(
                    estado = estado,
                    estadoTarjetas = EstadoTarjetas(),
                    onRegistrar = {},
                    onAgregarCuenta = {},
                    onTransferir = {},
                    lineaDeTiempo = linea,
                    onVerLineaDeTiempo = {},
                    accionesTarjetas = AccionesTarjetas(agregar = {}, pagar = {}, capturar = {}),
                    estadoRecurrentes = EstadoRecurrentes(),
                    accionesRecurrentes = AccionesRecurrentes(agregar = {}, tocar = {}),
                    onTocarMovimiento = {},
                    onRespaldo = {},
                )
            }
        }
    }

    @Test
    fun registrar_es_un_boton_con_nombre_y_de_48dp() {
        mostrarPantalla()

        regla.onNode(hasText("Registrar") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun todo_lo_tocable_tiene_un_nombre_que_leer() {
        mostrarPantalla()

        val tocables = regla.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val sinNombre = tocables.filter { nombre(it).isBlank() }.map { it.boundsInRoot.toString() }

        assertEquals("Elementos tocables sin texto ni descripción (de ${tocables.size})", emptyList<String>(), sinNombre)
    }

    @Test
    fun todo_lo_tocable_mide_al_menos_48dp() {
        mostrarPantalla()

        val tocables = regla.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val chicos = with(regla.density) {
            val minimo = 48.dp.toPx() - 0.5f
            tocables.filter { it.size.height < minimo || it.size.width < minimo }
                .map { "${nombre(it)}: ${it.size.width.toDp()} × ${it.size.height.toDp()}" }
        }

        assertEquals("Elementos tocables de menos de 48 dp (de ${tocables.size})", emptyList<String>(), chicos)
    }

    @Test
    fun la_transferencia_se_lee_de_una_cuenta_a_otra_sin_flecha() {
        mostrarPantalla()

        regla.onNode(hasContentDescription("de Nu débito a Efectivo", substring = true), useUnmergedTree = true)
            .assertExists()
    }

    private fun nombre(nodo: SemanticsNode): String {
        val config = nodo.config
        return listOfNotNull(
            config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
        ).joinToString(" ")
    }
}
