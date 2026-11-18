package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LibroTest {

    private val fecha = LocalDate(2026, 11, 15)
    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val debito = Cuenta(CuentaId(2), "Débito", TipoCuenta.ACTIVO)
    private val capital = Cuenta(CuentaId(3), "Saldos iniciales", TipoCuenta.CAPITAL)
    private val super_ = Cuenta(CuentaId(4), "Súper", TipoCuenta.GASTO)
    private val sueldo = Cuenta(CuentaId(5), "Sueldo", TipoCuenta.INGRESO)
    private val cuentas = listOf(efectivo, debito, capital, super_, sueldo).associateBy { it.id }

    private val montos = Arb.long(1L..100_000_000L)

    @Test
    fun movimiento_que_no_cuadra_no_se_puede_construir() {
        assertFailsWith<IllegalArgumentException> {
            Movimiento(null, fecha, "", listOf(Asiento(efectivo.id, Dinero(100)), Asiento(super_.id, Dinero(-99))))
        }
        assertFailsWith<IllegalArgumentException> {
            Movimiento(null, fecha, "", listOf(Asiento(efectivo.id, Dinero(100))))
        }
        assertFailsWith<IllegalArgumentException> { Asiento(efectivo.id, Dinero.CERO) }
    }

    @Test
    fun gasto_baja_la_cuenta_y_sube_la_categoria() {
        val gasto = Registro.gasto(fecha, Dinero(25_000), efectivo, super_, "  despensa  ")
        val saldos = saldosPorCuenta(listOf(gasto))
        assertEquals(Dinero(-25_000), saldos[efectivo.id])
        assertEquals(Dinero(25_000), saldos[super_.id])
        assertEquals("despensa", gasto.descripcion)
    }

    @Test
    fun ingreso_sube_la_cuenta() {
        val ingreso = Registro.ingreso(fecha, Dinero(1_500_000), debito, sueldo)
        assertEquals(Dinero(1_500_000), saldosPorCuenta(listOf(ingreso))[debito.id])
    }

    @Test
    fun registro_rechaza_combinaciones_sin_sentido() {
        assertFailsWith<IllegalArgumentException> { Registro.gasto(fecha, Dinero(100), efectivo, sueldo) }
        assertFailsWith<IllegalArgumentException> { Registro.gasto(fecha, Dinero(100), super_, super_) }
        assertFailsWith<IllegalArgumentException> { Registro.gasto(fecha, Dinero(-100), efectivo, super_) }
        assertFailsWith<IllegalArgumentException> { Registro.ingreso(fecha, Dinero(100), debito, super_) }
        assertFailsWith<IllegalArgumentException> { Registro.saldoInicial(fecha, efectivo, Dinero(100), super_) }
    }

    @Test
    fun resumir_reconoce_lo_que_registro_arma() = runTest {
        checkAll(montos) { centavos ->
            val monto = Dinero(centavos)

            val gasto = Registro.gasto(fecha, monto, efectivo, super_).resumir(cuentas)
            assertEquals(ClaseMovimiento.GASTO, gasto.clase)
            assertEquals(monto, gasto.monto)
            assertEquals(efectivo, gasto.cuenta)
            assertEquals(super_, gasto.categoria)

            val ingreso = Registro.ingreso(fecha, monto, debito, sueldo).resumir(cuentas)
            assertEquals(ClaseMovimiento.INGRESO, ingreso.clase)
            assertEquals(monto, ingreso.monto)
            assertEquals(debito, ingreso.cuenta)
            assertEquals(sueldo, ingreso.categoria)

            val inicial = Registro.saldoInicial(fecha, debito, monto, capital).resumir(cuentas)
            assertEquals(ClaseMovimiento.SALDO_INICIAL, inicial.clase)
            assertEquals(monto, inicial.monto)
        }
    }

    @Test
    fun resumir_no_adivina_si_falta_una_cuenta() {
        val gasto = Registro.gasto(fecha, Dinero(100), efectivo, super_)
        val resumen = gasto.resumir(mapOf(efectivo.id to efectivo))
        assertEquals(ClaseMovimiento.OTRO, resumen.clase)
        assertEquals(Dinero(100), resumen.monto)
    }

    /**
     * Propiedad central de la partida doble: con cualquier secuencia de gastos e
     * ingresos, la suma de todos los saldos es cero, y el total en cuentas es
     * ingresos menos gastos.
     */
    @Test
    fun el_libro_siempre_cuadra() = runTest {
        checkAll(Arb.list(montos, 0..50), Arb.list(Arb.boolean(), 50..50)) { lista, esGasto ->
            val movimientos = lista.mapIndexed { i, centavos ->
                if (esGasto[i]) {
                    Registro.gasto(fecha, Dinero(centavos), efectivo, super_)
                } else {
                    Registro.ingreso(fecha, Dinero(centavos), efectivo, sueldo)
                }
            }
            val saldos = saldosPorCuenta(movimientos)
            assertTrue(saldos.values.sumar().esCero)

            val esperado = lista.mapIndexed { i, c -> if (esGasto[i]) Dinero(-c) else Dinero(c) }.sumar()
            assertEquals(esperado, totalEnCuentas(cuentas.values, saldos))
        }
    }

    @Test
    fun desde_captura_valida_todo_junto() {
        val resultado = Registro.desdeCaptura(ClaseRegistro.GASTO, "", null, null, fecha, "")
        assertIs<ResultadoRegistro.Invalido>(resultado)
        assertEquals(
            setOf(ErrorRegistro.MONTO_VACIO, ErrorRegistro.SIN_CUENTA, ErrorRegistro.SIN_CATEGORIA),
            resultado.errores,
        )
    }

    @Test
    fun desde_captura_detecta_errores_de_monto_y_categoria() {
        fun errores(monto: String, categoria: Cuenta, clase: ClaseRegistro = ClaseRegistro.GASTO) =
            (Registro.desdeCaptura(clase, monto, efectivo, categoria, fecha, "") as ResultadoRegistro.Invalido).errores

        assertEquals(setOf(ErrorRegistro.MONTO_INVALIDO), errores("12,5", super_))
        assertEquals(setOf(ErrorRegistro.MONTO_CERO), errores("0.00", super_))
        assertEquals(setOf(ErrorRegistro.CATEGORIA_NO_CORRESPONDE), errores("10", sueldo))
        assertEquals(setOf(ErrorRegistro.CATEGORIA_NO_CORRESPONDE), errores("10", super_, ClaseRegistro.INGRESO))
    }

    @Test
    fun desde_captura_arma_el_gasto() {
        val resultado = Registro.desdeCaptura(ClaseRegistro.GASTO, "$1,250.50", efectivo, super_, fecha, "tacos")
        assertIs<ResultadoRegistro.Valido>(resultado)
        assertEquals(Registro.gasto(fecha, Dinero(125_050), efectivo, super_, "tacos"), resultado.movimiento)
    }

    @Test
    fun nota_demasiado_larga() {
        val nota = "x".repeat(Movimiento.LARGO_MAXIMO_DESCRIPCION + 1)
        val resultado = Registro.desdeCaptura(ClaseRegistro.GASTO, "10", efectivo, super_, fecha, nota)
        assertEquals(setOf(ErrorRegistro.NOTA_DEMASIADO_LARGA), (resultado as ResultadoRegistro.Invalido).errores)
    }

    @Test
    fun catalogo_inicial_no_repite_nombres() {
        val nombres = CatalogoInicial.todas.map { it.nombre }
        assertEquals(nombres.size, nombres.toSet().size)
        assertEquals(1, CatalogoInicial.todas.count { it.tipo == TipoCuenta.CAPITAL })
    }
}
