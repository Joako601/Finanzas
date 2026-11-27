package io.github.joako601.finanzas.core.domain.mercado

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsoPropioTest {
    private val debito = Cuenta(CuentaId(1), "Débito", TipoCuenta.ACTIVO)
    private val cuentaNu = Cuenta(CuentaId(2), "Nu", TipoCuenta.PASIVO)
    private val cuentaBbva = Cuenta(CuentaId(3), "BBVA", TipoCuenta.PASIVO)
    private val super_ = Cuenta(CuentaId(4), "Súper", TipoCuenta.GASTO)
    private val cuentas = listOf(debito, cuentaNu, cuentaBbva, super_).associateBy { it.id }
    private val nu = Tarjeta(cuentaNu, Dinero.pesos(30_000), diaDeCorte = 15, diaLimite = 5, tasaAnual = 80)
    private val bbva = Tarjeta(cuentaBbva, Dinero.pesos(30_000), diaDeCorte = 10, diaLimite = 1, tasaAnual = 50)
    private val hoy = LocalDate(2026, 11, 26)

    private fun gasto(fecha: String, pesos: Long, desde: Cuenta) =
        Registro.gasto(LocalDate.parse(fecha), Dinero.pesos(pesos), desde, super_)

    @Test
    fun promedia_gastos_con_tarjeta_de_tres_meses() {
        val movimientos = listOf(
            gasto("2026-11-26", 1_000, cuentaNu),
            gasto("2026-09-27", 2_000, cuentaBbva),
            gasto("2026-08-26", 9_000, cuentaNu), // justo hace tres meses: fuera
            gasto("2026-11-01", 5_000, debito), // no es tarjeta
        )

        val uso = calcularUsoPropio(listOf(nu, bbva), emptyMap(), movimientos, cuentas, hoy)!!

        assertEquals(Dinero.pesos(1_000), uso.gastoMensualPromedio)
    }

    @Test
    fun deuda_suma_y_tasa_de_la_tarjeta_con_mas_deuda() {
        val saldos = mapOf(cuentaNu.id to -Dinero.pesos(2_000), cuentaBbva.id to -Dinero.pesos(8_000))

        val uso = calcularUsoPropio(listOf(nu, bbva), saldos, emptyList(), cuentas, hoy)!!

        assertEquals(Dinero.pesos(10_000), uso.deuda)
        assertEquals(50, uso.tasaPropia)
    }

    @Test
    fun saldo_a_favor_no_resta_y_sin_tasa_usa_la_de_referencia() {
        val sinTasa = nu.copy(tasaAnual = null)
        val saldos = mapOf(cuentaNu.id to Dinero.pesos(500))

        val uso = calcularUsoPropio(listOf(sinTasa), saldos, emptyList(), cuentas, hoy)!!

        assertEquals(Dinero.CERO, uso.deuda)
        assertEquals(AnalizadorTarjeta.TASA_ANUAL_REFERENCIA_MEXICO, uso.tasaPropia)
    }

    @Test
    fun sin_tarjetas_activas_no_hay_uso() {
        val archivada = nu.copy(cuenta = cuentaNu.copy(archivada = true))

        assertNull(calcularUsoPropio(emptyList(), emptyMap(), emptyList(), cuentas, hoy))
        assertNull(calcularUsoPropio(listOf(archivada), emptyMap(), emptyList(), cuentas, hoy))
    }
}
