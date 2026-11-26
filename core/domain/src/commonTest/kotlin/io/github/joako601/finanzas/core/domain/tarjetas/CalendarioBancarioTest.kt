package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recordatorios.PlanRecordatorios
import io.github.joako601.finanzas.core.domain.recordatorios.TipoRecordatorio
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarioBancarioTest {

    private val id = CuentaId(10)

    // Corte el 1 y límite el 16: el 16 de noviembre de 2026 es lunes (día inhábil por la Revolución).
    private val tarjeta = Tarjeta(Cuenta(id, "Tarjeta", TipoCuenta.PASIVO), Dinero.pesos(20_000), 1, 16)
    private val corte = LocalDate(2026, 11, 1)
    private val revolucion = setOf(LocalDate(2026, 11, 16))

    @Test
    fun sin_datos_nada_cambia_ni_en_fin_de_semana() {
        // Límite el 15 de noviembre de 2026, domingo.
        val t = tarjeta.copy(diaLimite = 15)
        assertEquals(LocalDate(2026, 11, 15), CalendarioBancario.NINGUNO.limite(t, corte))
        assertFalse(CalendarioBancario.NINGUNO.requiereEleccion(t, corte))
    }

    @Test
    fun festivo_en_lunes_sin_eleccion_pide_elegir_y_no_mueve() {
        val cal = CalendarioBancario(revolucion)
        assertTrue(cal.requiereEleccion(tarjeta, corte))
        assertEquals(LocalDate(2026, 11, 16), cal.limite(tarjeta, corte))
    }

    @Test
    fun festivo_en_lunes_hacia_atras_salta_el_fin_de_semana() {
        val cal = CalendarioBancario(revolucion, mapOf(id to AjusteInhabil.ANTERIOR))
        assertEquals(LocalDate(2026, 11, 13), cal.limite(tarjeta, corte))
        assertFalse(cal.requiereEleccion(tarjeta, corte))
    }

    @Test
    fun festivo_en_lunes_hacia_adelante_va_al_martes() {
        val cal = CalendarioBancario(revolucion, mapOf(id to AjusteInhabil.SIGUIENTE))
        assertEquals(LocalDate(2026, 11, 17), cal.limite(tarjeta, corte))
    }

    @Test
    fun puente_de_varios_dias_se_recorre_entero() {
        // Jueves 24 y viernes 25 de diciembre inhábiles, más el fin de semana.
        val cal = CalendarioBancario(setOf(LocalDate(2026, 12, 24), LocalDate(2026, 12, 25)))
        assertEquals(LocalDate(2026, 12, 28), cal.diaHabil(LocalDate(2026, 12, 24), AjusteInhabil.SIGUIENTE))
        assertEquals(LocalDate(2026, 12, 23), cal.diaHabil(LocalDate(2026, 12, 27), AjusteInhabil.ANTERIOR))
    }

    @Test
    fun dia_habil_no_se_mueve() {
        val cal = CalendarioBancario(revolucion, mapOf(id to AjusteInhabil.ANTERIOR))
        val t = tarjeta.copy(diaLimite = 18) // miércoles
        assertEquals(LocalDate(2026, 11, 18), cal.limite(t, corte))
        assertFalse(cal.requiereEleccion(t, corte))
    }

    @Test
    fun recordatorio_de_captura_usa_el_limite_ajustado() {
        // Hoy 14 de noviembre: con el límite movido al viernes 13 ya no sirve pedir la captura.
        val hoy = LocalDate(2026, 11, 14)
        val tipos = { cal: CalendarioBancario ->
            PlanRecordatorios.calcular(listOf(tarjeta), emptyList(), emptyList(), hoy, calendario = cal)
                .filter { it.tipo == TipoRecordatorio.CAPTURAR_ESTADO && it.fechaReferencia == corte }
        }
        assertEquals(1, tipos(CalendarioBancario.NINGUNO).size)
        assertEquals(0, tipos(CalendarioBancario(revolucion, mapOf(id to AjusteInhabil.ANTERIOR))).size)
    }
}
