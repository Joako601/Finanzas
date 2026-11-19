package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TransferenciaTest {

    private val fecha = LocalDate(2026, 11, 19)
    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val debito = Cuenta(CuentaId(2), "Nu débito", TipoCuenta.ACTIVO)
    private val nu = Cuenta(CuentaId(3), "Nu", TipoCuenta.PASIVO)
    private val super_ = Cuenta(CuentaId(4), "Súper y despensa", TipoCuenta.GASTO)
    private val cuentas = listOf(efectivo, debito, nu, super_).associateBy { it.id }

    @Test
    fun retirar_del_cajero_baja_el_debito_y_sube_el_efectivo() {
        val movimiento = Registro.transferencia(fecha, Dinero.pesos(500), desde = debito, hacia = efectivo, nota = "cajero")

        assertEquals(mapOf(debito.id to -Dinero.pesos(500), efectivo.id to Dinero.pesos(500)), saldosPorCuenta(listOf(movimiento)))
        assertEquals("cajero", movimiento.descripcion)
    }

    @Test
    fun se_resume_como_transferencia_con_origen_y_destino() {
        val resumen = Registro.transferencia(fecha, Dinero.pesos(500), debito, efectivo).resumir(cuentas)

        assertEquals(ClaseMovimiento.TRANSFERENCIA, resumen.clase)
        assertEquals(Dinero.pesos(500), resumen.monto)
        assertEquals(debito, resumen.cuenta)
        assertEquals(efectivo, resumen.contraparte)
    }

    @Test
    fun no_cambia_el_total_en_cuentas() = runTest {
        checkAll(Arb.long(1L..100_000_000L)) { centavos ->
            val movimiento = Registro.transferencia(fecha, Dinero(centavos), debito, efectivo)
            assertEquals(Dinero.CERO, totalEnCuentas(cuentas.values, saldosPorCuenta(listOf(movimiento))))
        }
    }

    @Test
    fun solo_entre_efectivo_y_debito_distintos() {
        assertFailsWith<IllegalArgumentException> { Registro.transferencia(fecha, Dinero.pesos(1), debito, debito) }
        // Pasar dinero a la tarjeta es un pago de tarjeta, no una transferencia.
        assertFailsWith<IllegalArgumentException> { Registro.transferencia(fecha, Dinero.pesos(1), debito, nu) }
        assertFailsWith<IllegalArgumentException> { Registro.transferencia(fecha, Dinero.pesos(-1), debito, efectivo) }
    }

    @Test
    fun la_captura_junta_todos_los_errores() {
        val vacia = Registro.desdeTransferencia("", desde = null, hacia = null, fecha = fecha, nota = "")
        assertEquals(
            ResultadoRegistro.Invalido(setOf(ErrorRegistro.MONTO_VACIO, ErrorRegistro.SIN_CUENTA, ErrorRegistro.SIN_DESTINO)),
            vacia,
        )

        val misma = Registro.desdeTransferencia("100", debito, debito, fecha, "")
        assertEquals(ResultadoRegistro.Invalido(setOf(ErrorRegistro.MISMA_CUENTA)), misma)

        val aTarjeta = Registro.desdeTransferencia("100", debito, nu, fecha, "")
        assertEquals(ResultadoRegistro.Invalido(setOf(ErrorRegistro.SIN_DESTINO)), aTarjeta)

        val valida = Registro.desdeTransferencia("1,250.50", debito, efectivo, fecha, " cajero ")
        assertIs<ResultadoRegistro.Valido>(valida)
        assertEquals(Dinero(125_050), valida.movimiento.resumir(cuentas).monto)
    }
}
