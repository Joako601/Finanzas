package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ComprasAMesesTest {

    private val debito = Cuenta(CuentaId(1), "Débito", TipoCuenta.ACTIVO)
    private val cuentaNu = Cuenta(CuentaId(2), "Nu", TipoCuenta.PASIVO)
    private val super_ = Cuenta(CuentaId(3), "Súper", TipoCuenta.GASTO)
    private val sueldo = Cuenta(CuentaId(4), "Sueldo", TipoCuenta.INGRESO)
    private val cuentas = listOf(debito, cuentaNu, super_, sueldo).associateBy { it.id }

    // Corte el 15, límite el 5 del mes siguiente.
    private val nu = Tarjeta(cuentaNu, Dinero.pesos(30_000), diaDeCorte = 15, diaLimite = 5)

    private fun fecha(texto: String) = LocalDate.parse(texto)

    private fun compra(fechaCompra: String, centavos: Long, meses: Int?, desde: Cuenta = cuentaNu): Movimiento =
        Registro.gasto(fecha(fechaCompra), Dinero(centavos), desde, super_).copy(meses = meses)

    private fun unica(movimiento: Movimiento) = comprasAMeses(listOf(movimiento), cuentas, listOf(nu)).single()

    @Test
    fun las_mensualidades_reparten_el_total_sin_perder_centavos() {
        val c = unica(compra("2026-11-10", 100_000, 3))
        assertEquals(Dinero(33_333), c.cuota)
        assertEquals(Dinero(33_333), c.facturadoTras(1))
        assertEquals(Dinero(66_666), c.facturadoTras(2))
        assertEquals(Dinero(100_000), c.facturadoTras(3))
        // Más allá del plazo no se factura nada extra.
        assertEquals(Dinero(100_000), c.facturadoTras(5))
    }

    @Test
    fun cada_corte_despues_de_la_compra_factura_una_mensualidad() {
        val c = unica(compra("2026-11-10", 1_200_000, 12))
        assertEquals(0, c.facturadasAl(fecha("2026-11-14")))
        assertEquals(1, c.facturadasAl(fecha("2026-11-15")))
        assertEquals(2, c.facturadasAl(fecha("2026-12-20")))
        assertEquals(Dinero.pesos(10_000), c.porFacturarAl(fecha("2026-12-20")))
    }

    @Test
    fun una_compra_el_dia_del_corte_entra_en_ese_corte() {
        assertEquals(1, unica(compra("2026-11-15", 300_000, 3)).facturadasAl(fecha("2026-11-15")))
    }

    @Test
    fun las_facturadas_no_pasan_del_plazo_y_la_compra_deja_de_estar_activa() {
        val c = unica(compra("2026-01-10", 300_000, 3))
        assertEquals(3, c.facturadasAl(fecha("2026-11-20")))
        assertEquals(Dinero.CERO, c.porFacturarAl(fecha("2026-11-20")))
        assertEquals(false, c.activaAl(fecha("2026-11-20")))
        assertEquals(true, c.activaAl(fecha("2026-02-20")))
    }

    @Test
    fun solo_cuentan_gastos_con_tarjeta_a_meses() {
        val deContado = compra("2026-11-10", 100_000, null)
        val ingreso = Registro.ingreso(fecha("2026-11-10"), Dinero(100_000), debito, sueldo)
        val msi = compra("2026-11-10", 600_000, 6)
        val compras = comprasAMeses(listOf(deContado, ingreso, msi), cuentas, listOf(nu))
        assertEquals(listOf(Dinero(600_000)), compras.map { it.total })
    }

    @Test
    fun registro_solo_guarda_meses_en_un_gasto_con_tarjeta() {
        fun capturar(clase: ClaseRegistro, cuenta: Cuenta, categoria: Cuenta) =
            Registro.desdeCaptura(clase, "600", cuenta, categoria, fecha("2026-11-10"), "", meses = 6)

        val conTarjeta = assertIs<ResultadoRegistro.Valido>(capturar(ClaseRegistro.GASTO, cuentaNu, super_))
        assertEquals(6, conTarjeta.movimiento.meses)
        val conDebito = assertIs<ResultadoRegistro.Valido>(capturar(ClaseRegistro.GASTO, debito, super_))
        assertNull(conDebito.movimiento.meses)
        val ingreso = assertIs<ResultadoRegistro.Valido>(capturar(ClaseRegistro.INGRESO, debito, sueldo))
        assertNull(ingreso.movimiento.meses)
    }

    @Test
    fun un_plazo_que_no_existe_se_rechaza() {
        assertFailsWith<IllegalArgumentException> { compra("2026-11-10", 100_000, 5) }
    }
}
