package io.github.joako601.finanzas.ui.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Asiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TarjetasViewModelTest {

    private val debito = Cuenta(CuentaId(1), "Débito", TipoCuenta.ACTIVO)
    private val efectivo = Cuenta(CuentaId(2), "Efectivo", TipoCuenta.ACTIVO)
    private val capital = Cuenta(CuentaId(3), "Saldos iniciales", TipoCuenta.CAPITAL)
    private val nu = Tarjeta(Cuenta(CuentaId(4), "Nu", TipoCuenta.PASIVO), Dinero.pesos(30_000), diaDeCorte = 15, diaLimite = 5)

    /** Corte del 15 de octubre, límite del 5 de noviembre, sin pagar. */
    private val estadoOctubre = EstadoDeCuenta(
        id = 1, tarjeta = nu.id, fechaCorte = LocalDate(2026, 10, 15), fechaLimite = LocalDate(2026, 11, 5),
        pagoSinIntereses = Dinero.pesos(2_500), pagoMinimo = Dinero.pesos(250), cortePredicho = null, limitePredicho = null,
    )

    @Before
    fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun despues() = Dispatchers.resetMain()

    private fun saldo(cuenta: Cuenta, centavos: Long) = Movimiento(
        null, LocalDate(2026, 9, 1), "", listOf(Asiento(cuenta.id, Dinero(centavos)), Asiento(capital.id, Dinero(-centavos))),
    )

    private suspend fun TestScope.viewModel(hoy: LocalDate, movimientos: List<Movimiento>): TarjetasViewModel {
        val libro = LibroFalsoTarjetas(listOf(debito, efectivo, capital, nu.cuenta), movimientos)
        val tarjetas = TarjetasFalso(listOf(nu), listOf(estadoOctubre))
        val vm = TarjetasViewModel(libro, tarjetas, ServicioTarjetas(libro, tarjetas)) { hoy }
        backgroundScope.launch { vm.estado.collect {} }
        vm.estado.first { it.tarjetas.isNotEmpty() }
        return vm
    }

    @Test
    fun capturar_con_un_estado_vencido_de_un_ciclo_anterior_abre_el_corte_nuevo() = runTest {
        // 10 de diciembre: ya pasó el límite predicho del corte del 15 de noviembre y no se capturó.
        val vm = viewModel(LocalDate(2026, 12, 10), listOf(saldo(nu.cuenta, -250_000), saldo(debito, 1_000_000)))
        val resumen = vm.estado.value.tarjetas.single()
        assertTrue(resumen.situacion is SituacionTarjeta.EnCiclo)

        vm.abrirCaptura(resumen)

        val captura = vm.captura.value!!
        // Debe abrir el corte del 15 de noviembre en blanco, no editar el de octubre.
        assertEquals(LocalDate(2026, 11, 15), captura.fechaCorte)
        assertNull(captura.fechaCorteOriginal)
        assertEquals("", captura.pagoSinIntereses)
    }

    @Test
    fun pagar_preselecciona_la_primera_cuenta_que_cubre_el_pago() = runTest {
        val vm = viewModel(
            LocalDate(2026, 11, 1),
            listOf(saldo(nu.cuenta, -250_000), saldo(debito, 10_000), saldo(efectivo, 500_000)),
        )

        vm.abrirPago(vm.estado.value.tarjetas.single())

        assertEquals("2500", vm.pago.value?.montoTexto)
        assertEquals(efectivo.id, vm.pago.value?.desdeId)
    }

    @Test
    fun si_ninguna_cuenta_cubre_el_pago_preselecciona_la_primera() = runTest {
        val vm = viewModel(
            LocalDate(2026, 11, 1),
            listOf(saldo(nu.cuenta, -250_000), saldo(debito, 10_000), saldo(efectivo, 20_000)),
        )

        vm.abrirPago(vm.estado.value.tarjetas.single())

        assertEquals(debito.id, vm.pago.value?.desdeId)
    }
}

private class LibroFalsoTarjetas(cuentas: List<Cuenta>, movimientos: List<Movimiento>) : RepositorioLibro {
    private val cuentas = MutableStateFlow(cuentas)
    private val movimientos = MutableStateFlow(movimientos)

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas
    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = movimientos.map { saldosPorCuenta(it) }
    override fun observarMovimientos(): Flow<List<Movimiento>> = movimientos
    override suspend fun registrar(movimiento: Movimiento): MovimientoId = error("no se usa")
    override suspend fun actualizar(movimiento: Movimiento) = error("no se usa")
    override suspend fun eliminar(id: MovimientoId) = error("no se usa")
    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta = error("no se usa")
    override suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String) = error("no se usa")
    override suspend fun cuentaDeSaldosIniciales(): Cuenta = error("no se usa")
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}

private class TarjetasFalso(tarjetas: List<Tarjeta>, estados: List<EstadoDeCuenta>) : RepositorioTarjetas {
    private val tarjetas = MutableStateFlow(tarjetas)
    private val estados = MutableStateFlow(estados)

    override fun observarTarjetas(): Flow<List<Tarjeta>> = tarjetas
    override fun observarEstadosDeCuenta(): Flow<List<EstadoDeCuenta>> = estados
    override suspend fun guardarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta =
        error("no se usa")
    override suspend fun actualizarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta =
        error("no se usa")
    override suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuenta) = error("no se usa")
    override suspend fun eliminarEstadoDeCuenta(tarjetaId: CuentaId, fechaCorte: LocalDate) = error("no se usa")
}
