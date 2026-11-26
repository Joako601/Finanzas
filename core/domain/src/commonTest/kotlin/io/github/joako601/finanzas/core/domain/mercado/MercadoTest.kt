package io.github.joako601.finanzas.core.domain.mercado

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MercadoTest {

    @Test
    fun deuda_contra_cetes() {
        val c = compararDeudaContraReferencia(60, 7.5, Dinero.pesos(10_000))!!
        assertEquals(Dinero.pesos(6_960), c.costoAnualDeuda) // 60 % con IVA
        assertEquals(Dinero.pesos(750), c.rendimientoAnualReferencia)
        assertEquals(Dinero.pesos(6_210), c.diferencia)
    }

    @Test
    fun deuda_sin_saldo_o_tasa_invalida_no_compara() {
        assertNull(compararDeudaContraReferencia(60, 7.5, Dinero.CERO))
        assertNull(compararDeudaContraReferencia(60, Double.NaN, Dinero.pesos(1)))
        assertNull(compararDeudaContraReferencia(0, 7.5, Dinero.pesos(1)))
    }

    @Test
    fun msi_con_tasa_cero_no_tiene_ventaja() {
        val e = CalculadoraMsi.estimar(Dinero.pesos(12_000), 12, 0.0)!!
        assertEquals(Dinero.pesos(12_000), e.valorPresente)
        assertEquals(Dinero.CERO, e.ventaja)
    }

    @Test
    fun msi_a_12_meses_al_9_5() {
        val e = CalculadoraMsi.estimar(Dinero.pesos(12_000), 12, 9.5)!!
        // Anualidad de 1,000 a 12 meses al 9.5/12 %: 1,000 × (1 − 1.0079167⁻¹²) / 0.0079167 = 11,404.65.
        assertEquals(Dinero.pesos(595, 35), e.ventaja)
    }

    @Test
    fun msi_centavos_y_montos_grandes() {
        assertEquals(Dinero(1), CalculadoraMsi.estimar(Dinero(1), 24, 0.0)!!.valorPresente)
        val grande = Dinero.pesos(9_999_999_999_999)
        val e = CalculadoraMsi.estimar(grande, 24, 11.0)!!
        assertTrue(e.ventaja.esPositivo && e.valorPresente < grande)
        assertNull(CalculadoraMsi.estimar(Dinero.pesos(100), 0, 9.0))
    }

    @Test
    fun alerta_avisa_una_vez_y_se_rearma() {
        val baja = AlertaCambio("a", "USD", CondicionAlerta.BAJA_DE, 17.0)
        val sube = AlertaCambio("b", "USD", CondicionAlerta.SUBE_DE, 21.0)

        val r1 = EvaluadorAlertas.evaluar(listOf(baja, sube), mapOf("USD" to 16.9))
        assertEquals(listOf("a"), r1.disparadas.map { it.alerta.id })

        // Misma condición en la siguiente actualización: no se repite.
        val r2 = EvaluadorAlertas.evaluar(r1.alertas, mapOf("USD" to 16.8))
        assertTrue(r2.disparadas.isEmpty())

        // Sale del umbral y vuelve a entrar: avisa otra vez.
        val r3 = EvaluadorAlertas.evaluar(r2.alertas, mapOf("USD" to 17.5))
        val r4 = EvaluadorAlertas.evaluar(r3.alertas, mapOf("USD" to 16.95))
        assertEquals(listOf("a"), r4.disparadas.map { it.alerta.id })

        val r5 = EvaluadorAlertas.evaluar(r4.alertas, mapOf("USD" to 21.2))
        assertEquals(listOf("b"), r5.disparadas.map { it.alerta.id })
    }

    @Test
    fun alerta_sin_tasa_no_cambia() {
        val a = AlertaCambio("a", "EUR", CondicionAlerta.SUBE_DE, 20.0, cumplida = true)
        val r = EvaluadorAlertas.evaluar(listOf(a), mapOf("USD" to 30.0))
        assertEquals(listOf(a), r.alertas)
        assertTrue(r.disparadas.isEmpty())
    }

    @Test
    fun comparador_ordena_por_costo_y_dimensiona_anualidad() {
        val sinAnualidad = ProductoTarjeta(1, "Sin anualidad", "A", Dinero.CERO, 70.0)
        val conAnualidad = ProductoTarjeta(2, "Con anualidad", "B", Dinero.pesos(1_200), 40.0)
        val sinTasa = ProductoTarjeta(3, "Sin tasa", "C", Dinero.pesos(500), null)
        val uso = UsoPropio(Dinero.pesos(10_000), Dinero.pesos(10_000), 60)

        val filas = ComparadorTarjetas.comparar(listOf(sinAnualidad, sinTasa, conAnualidad), uso)

        assertEquals(listOf(2L, 1L, 3L), filas.map { it.producto.id })
        // 1,200 + 10,000 × 40 % × 1.16 = 5,840; la propia cuesta 6,960.
        assertEquals(Dinero.pesos(5_840), filas[0].costoAnual)
        assertEquals(-Dinero.pesos(1_120), filas[0].diferenciaAnual)
        assertEquals(1.0, filas[0].anualidadSobreGasto)
        assertNull(filas[2].costoAnual)
    }

    @Test
    fun comparador_sin_gasto_no_divide_entre_cero() {
        val p = ProductoTarjeta(1, "X", "A", Dinero.pesos(500), 50.0)
        val fila = ComparadorTarjetas.comparar(listOf(p), UsoPropio(Dinero.CERO, Dinero.CERO, 60)).single()
        assertNull(fila.anualidadSobreGasto)
        assertEquals(Dinero.pesos(500), fila.diferenciaAnual)
    }
}
