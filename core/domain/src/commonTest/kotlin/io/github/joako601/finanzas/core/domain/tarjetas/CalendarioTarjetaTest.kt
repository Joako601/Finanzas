package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarioTarjetaTest {

    private fun tarjeta(corte: Int, limite: Int) =
        Tarjeta(Cuenta(CuentaId(10), "Tarjeta", TipoCuenta.PASIVO), Dinero.pesos(20_000), corte, limite)

    private fun fecha(texto: String) = LocalDate.parse(texto)

    @Test
    fun meses_cortos_usan_el_ultimo_dia() {
        assertEquals(fecha("2026-02-28"), CalendarioTarjeta.fechaConDia(2026, 2, 31))
        assertEquals(fecha("2028-02-29"), CalendarioTarjeta.fechaConDia(2028, 2, 30))
        assertEquals(fecha("2026-04-30"), CalendarioTarjeta.fechaConDia(2026, 4, 31))
        assertEquals(fecha("2026-12-31"), CalendarioTarjeta.fechaConDia(2026, 12, 31))
    }

    @Test
    fun ultimo_y_siguiente_corte() {
        val t = tarjeta(corte = 15, limite = 5)
        assertEquals(fecha("2026-11-15"), CalendarioTarjeta.ultimoCorteHasta(t, fecha("2026-11-15")))
        assertEquals(fecha("2026-10-15"), CalendarioTarjeta.ultimoCorteHasta(t, fecha("2026-11-14")))
        assertEquals(fecha("2026-11-15"), CalendarioTarjeta.siguienteCorteDesde(t, fecha("2026-11-15")))
        assertEquals(fecha("2026-12-15"), CalendarioTarjeta.siguienteCorteDesde(t, fecha("2026-11-16")))
        // Cruce de año.
        assertEquals(fecha("2027-01-15"), CalendarioTarjeta.siguienteCorteDesde(t, fecha("2026-12-20")))
    }

    @Test
    fun corte_el_31() {
        val t = tarjeta(corte = 31, limite = 20)
        assertEquals(fecha("2026-02-28"), CalendarioTarjeta.ultimoCorteHasta(t, fecha("2026-03-10")))
        assertEquals(fecha("2026-04-30"), CalendarioTarjeta.siguienteCorteDesde(t, fecha("2026-04-01")))
        assertEquals(fecha("2026-03-20"), CalendarioTarjeta.limiteParaCorte(t, fecha("2026-02-28")))
    }

    @Test
    fun limite_en_el_mismo_mes_o_el_siguiente() {
        assertEquals(fecha("2026-11-30"), CalendarioTarjeta.limiteParaCorte(tarjeta(10, 30), fecha("2026-11-10")))
        assertEquals(fecha("2026-12-15"), CalendarioTarjeta.limiteParaCorte(tarjeta(25, 15), fecha("2026-11-25")))
        // Mismo día de corte y límite: el límite es el mes siguiente.
        assertEquals(fecha("2026-12-05"), CalendarioTarjeta.limiteParaCorte(tarjeta(5, 5), fecha("2026-11-05")))
        // Límite el 30 con corte a fin de febrero: el 28 de febrero no es posterior, se va a marzo.
        assertEquals(fecha("2026-03-30"), CalendarioTarjeta.limiteParaCorte(tarjeta(31, 30), fecha("2026-02-28")))
    }

    @Test
    fun prediccion_de_un_corte_exacto_es_el_mismo_corte() {
        val t = tarjeta(corte = 15, limite = 5)
        val (corte, limite) = CalendarioTarjeta.prediccionPara(t, fecha("2026-11-15"))
        assertEquals(fecha("2026-11-15"), corte)
        assertEquals(fecha("2026-12-05"), limite)
    }

    @Test
    fun prediccion_tolera_un_corte_recorrido() {
        // El banco cortó el lunes 16 en lugar del domingo 15: la predicción sigue siendo el 15.
        val t = tarjeta(corte = 15, limite = 5)
        assertEquals(fecha("2026-11-15"), CalendarioTarjeta.prediccionPara(t, fecha("2026-11-16")).first)
        // Corte el 1, recorrido al último día del mes anterior.
        val t1 = tarjeta(corte = 1, limite = 20)
        assertEquals(fecha("2026-12-01"), CalendarioTarjeta.prediccionPara(t1, fecha("2026-11-30")).first)
    }

    /** Invariantes para cualquier configuración de días y cualquier fecha de 2020 a 2035. */
    @Test
    fun invariantes_del_calendario() = runTest {
        val dias = Arb.int(1..31)
        val diasDesde2020 = Arb.int(0..(16 * 366))
        checkAll(dias, dias, diasDesde2020) { corte, limite, desplazamiento ->
            val t = tarjeta(corte, limite)
            val f = LocalDate.fromEpochDays(LocalDate(2020, 1, 1).toEpochDays() + desplazamiento)

            val ultimo = CalendarioTarjeta.ultimoCorteHasta(t, f)
            val siguiente = CalendarioTarjeta.siguienteCorteDesde(t, f)
            assertTrue(ultimo <= f, "último corte $ultimo después de $f")
            assertTrue(siguiente >= f, "siguiente corte $siguiente antes de $f")
            assertTrue(ultimo.daysUntil(f) < 31, "más de un mes desde el último corte")
            assertTrue(f.daysUntil(siguiente) < 31, "más de un mes al siguiente corte")

            val limiteDelCorte = CalendarioTarjeta.limiteParaCorte(t, ultimo)
            assertTrue(ultimo.daysUntil(limiteDelCorte) in 1..31, "límite $limiteDelCorte fuera de rango para $ultimo")
        }
    }
}
