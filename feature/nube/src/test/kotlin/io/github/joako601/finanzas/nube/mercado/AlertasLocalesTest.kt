package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.core.domain.mercado.CondicionAlerta
import io.github.joako601.finanzas.core.domain.mercado.EvaluacionAlertas
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AlertasLocalesTest {

    @get:Rule val carpeta = TemporaryFolder()

    private fun usd(tasa: String) = listOf(TipoCambio("USD", "MXN", "2026-11-26", BigDecimal(tasa), "Banxico", false))

    @Test
    fun avisaUnaVezAlCruzarYSeRearmaAlSalir() = runTest {
        val alertas = AlertasLocales(carpeta.root)
        val disparos = mutableListOf<EvaluacionAlertas.Disparo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { alertas.disparos.collect { disparos += it } }
        alertas.agregar("USD", CondicionAlerta.BAJA_DE, 17.0, tasaActual = 17.5)

        alertas.evaluar(usd("16.90"))
        alertas.evaluar(usd("16.80"))
        assertEquals(1, disparos.size)
        assertEquals(16.9, disparos.single().tasa, 0.0)

        alertas.evaluar(usd("17.20"))
        alertas.evaluar(usd("16.95"))
        assertEquals(2, disparos.size)
    }

    @Test
    fun siYaSeCumpleAlCrearlaNoAvisaDeInmediato() = runTest {
        val alertas = AlertasLocales(carpeta.root)
        val disparos = mutableListOf<EvaluacionAlertas.Disparo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { alertas.disparos.collect { disparos += it } }
        alertas.agregar("USD", CondicionAlerta.SUBE_DE, 17.0, tasaActual = 17.5)

        assertTrue(alertas.alertas.value.single().cumplida)
        alertas.evaluar(usd("17.60"))
        assertTrue(disparos.isEmpty())
    }

    @Test
    fun sobreviveAReiniciarYSeQuitaYBorra() {
        AlertasLocales(carpeta.root).agregar("EUR", CondicionAlerta.SUBE_DE, 21.5, tasaActual = null)

        val otra = AlertasLocales(carpeta.root).apply { recargar() }
        val alerta = otra.alertas.value.single()
        assertEquals("EUR", alerta.moneda)
        assertEquals(21.5, alerta.umbral, 0.0)
        assertFalse(alerta.cumplida)

        otra.agregar("USD", CondicionAlerta.BAJA_DE, 17.0, tasaActual = null)
        otra.quitar(alerta.id)
        assertEquals(listOf("USD"), otra.alertas.value.map { it.moneda })

        otra.borrar()
        assertTrue(otra.alertas.value.isEmpty())
        assertTrue(AlertasLocales(carpeta.root).apply { recargar() }.alertas.value.isEmpty())
    }

    @Test
    fun unaLineaDanadaNoTumbaLasDemas() {
        File(carpeta.root, "alertas.txt").writeText("basura\nid1;USD;BAJA_DE;17.0;false\nid2;USD;NADA;1;false")

        val alertas = AlertasLocales(carpeta.root).apply { recargar() }

        assertEquals(listOf("id1"), alertas.alertas.value.map { it.id })
    }

    @Test
    fun sinTasaDeLaMonedaLaAlertaNoCambia() {
        val alertas = AlertasLocales(carpeta.root)
        alertas.agregar("JPY", CondicionAlerta.BAJA_DE, 0.2, tasaActual = null)

        alertas.evaluar(usd("10"))

        assertFalse(alertas.alertas.value.single().cumplida)
    }
}
