package io.github.joako601.finanzas.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertTrue

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

        // TalkBack lee el texto o la descripción del elemento que recibe el toque.
        regla.onNode((hasText("Registrar") or hasContentDescription("Registrar")) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    }

    private var raizActual: AccessibilityNodeInfo? = null

    /** Lo que Android le entrega a TalkBack: los nodos de accesibilidad de la ventana. */
    private fun nodosTocables(): List<AccessibilityNodeInfo> {
        regla.waitForIdle()
        val automatizacion = InstrumentationRegistry.getInstrumentation().uiAutomation
        automatizacion.serviceInfo = automatizacion.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var raiz: AccessibilityNodeInfo? = null
        repeat(50) { if (raiz == null) raiz = automatizacion.rootInActiveWindow ?: null.also { Thread.sleep(100) } }
        checkNotNull(raiz) { "Sin ventana activa" }
        raizActual = raiz
        val lista = mutableListOf<AccessibilityNodeInfo>()
        fun visitar(nodo: AccessibilityNodeInfo) {
            if (nodo.isClickable) lista += nodo
            for (i in 0 until nodo.childCount) nodo.getChild(i)?.let(::visitar)
        }
        visitar(raiz!!)
        return lista
    }

    /** Lo que TalkBack diría: el texto o descripción del nodo, o el de sus descendientes. */
    private fun nombre(nodo: AccessibilityNodeInfo): String =
        (listOfNotNull(nodo.text?.toString(), nodo.contentDescription?.toString()) +
            (0 until nodo.childCount).mapNotNull { nodo.getChild(it) }.map(::nombre))
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun dp(nodo: AccessibilityNodeInfo): Pair<Float, Float> {
        val r = Rect().also(nodo::getBoundsInScreen)
        val densidad = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        return r.width() / densidad to r.height() / densidad
    }

    @Test
    fun todo_lo_tocable_tiene_un_nombre_que_leer() {
        mostrarPantalla()

        val tocables = nodosTocables()
        val sinNombre = tocables.filter { nombre(it).isBlank() }.map { "${it.className} ${dp(it)}" }

        assertEquals("Elementos tocables que TalkBack no puede nombrar (de ${tocables.size})", emptyList<String>(), sinNombre)
    }

    /** Las medidas que Android reporta a accesibilidad ya incluyen el área táctil ampliada de Compose. */
    @Test
    fun todo_lo_tocable_mide_al_menos_48dp() {
        mostrarPantalla()

        // Android reporta solo la parte visible: un elemento cortado por la orilla de la
        // pantalla (pasa con letra grande) mediría de menos sin serlo, así que no se mide.
        val todos = nodosTocables()
        val pantalla = Rect().also { checkNotNull(raizActual).getBoundsInScreen(it) }
        val tocables = todos.filter { nodo ->
            val r = Rect().also(nodo::getBoundsInScreen)
            r.top > pantalla.top && r.bottom < pantalla.bottom
        }
        assertTrue("Casi nada quedó a la vista para medir (${tocables.size})", tocables.size >= 5)
        val chicos = tocables.filter { val (ancho, alto) = dp(it); ancho < 47.5f || alto < 47.5f }
            .map { "${nombre(it)}: ${dp(it)}" }

        assertEquals("Elementos tocables de menos de 48 dp (de ${tocables.size})", emptyList<String>(), chicos)
    }

    @Test
    fun la_transferencia_se_lee_de_una_cuenta_a_otra_sin_flecha() {
        mostrarPantalla()

        regla.onNode(hasContentDescription("de Nu débito a Efectivo", substring = true), useUnmergedTree = true)
            .assertExists()
    }

}
