package io.github.joako601.finanzas.ui.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Asiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
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

    private lateinit var libro: LibroFalsoTarjetas
    private lateinit var tarjetas: TarjetasFalso

    private suspend fun TestScope.viewModel(hoy: LocalDate, movimientos: List<Movimiento>): TarjetasViewModel {
        libro = LibroFalsoTarjetas(listOf(debito, efectivo, capital, nu.cuenta), movimientos)
        tarjetas = TarjetasFalso(listOf(nu), listOf(estadoOctubre))
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
    fun la_tasa_elegida_al_simular_queda_guardada_en_la_tarjeta() = runTest {
        val vm = viewModel(LocalDate(2026, 11, 1), listOf(saldo(nu.cuenta, -250_000), saldo(debito, 500_000)))
        vm.abrirPago(vm.estado.value.tarjetas.single())
        assertEquals(AnalizadorTarjeta.TASA_ANUAL_REFERENCIA_MEXICO, vm.pago.value?.tasaAnualPorcentaje)

        vm.editarPago { copy(tasaAnualPorcentaje = 90) }
        vm.cerrarPago()

        // Al volver a abrir, la simulación parte de la tasa guardada.
        val resumen = vm.estado.first { it.tarjetas.single().tarjeta.tasaAnual == 90 }.tarjetas.single()
        vm.abrirPago(resumen)
        assertEquals(90, vm.pago.value?.tasaAnualPorcentaje)
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

    @Test
    fun editar_un_pago_a_tarjeta_conserva_su_id_y_cambia_monto_y_cuenta() = runTest {
        val pago = Registro.pagoDeTarjeta(LocalDate(2026, 11, 1), Dinero.pesos(500), debito, nu.cuenta, "quincena")
            .copy(id = MovimientoId(9))
        val vm = viewModel(LocalDate(2026, 11, 2), listOf(saldo(nu.cuenta, -250_000), saldo(efectivo, 500_000), pago))
        val resumen = pago.resumir(listOf(debito, efectivo, capital, nu.cuenta).associateBy { it.id })

        vm.abrirEdicionPago(resumen)
        assertEquals("500", vm.pago.value?.montoTexto)
        assertEquals(debito.id, vm.pago.value?.desdeId)
        assertNull("sin sugerencias: la deuda ya descuenta este pago", vm.pago.value?.analisis)

        vm.editarPago { copy(montoTexto = "750", desdeId = efectivo.id) }
        vm.guardarPago()

        assertNull(vm.pago.value)
        val guardado = libro.actualizados.single()
        assertEquals(MovimientoId(9), guardado.id)
        assertEquals("quincena", guardado.descripcion)
        assertEquals(-Dinero.pesos(750), guardado.asientos.single { it.cuenta == efectivo.id }.monto)
    }

    @Test
    fun eliminar_un_estado_de_cuenta_capturado_pide_confirmacion_y_lo_borra() = runTest {
        // 1 de noviembre: el estado de octubre sigue por pagar, así que capturar lo abre para editar.
        val vm = viewModel(LocalDate(2026, 11, 1), listOf(saldo(nu.cuenta, -250_000)))
        vm.abrirCaptura(vm.estado.value.tarjetas.single())
        assertEquals(estadoOctubre.fechaCorte, vm.captura.value?.fechaCorteOriginal)

        vm.eliminarEstado()
        assertEquals(1, tarjetas.observarEstadosDeCuenta().first().size)

        vm.pedirEliminarEstado()
        vm.eliminarEstado()

        assertNull(vm.captura.value)
        assertTrue(tarjetas.observarEstadosDeCuenta().first().isEmpty())
    }

    @Test
    fun archivar_tarjeta_pide_confirmacion() = runTest {
        val vm = viewModel(LocalDate(2026, 11, 1), listOf(saldo(nu.cuenta, -250_000)))
        vm.abrirEdicionTarjeta(nu)

        vm.archivarTarjeta()
        assertTrue(libro.archivadas.isEmpty())

        vm.pedirArchivarTarjeta()
        vm.archivarTarjeta()

        assertEquals(listOf(nu.id), libro.archivadas)
        assertNull(vm.edicionTarjeta.value)
    }
}

private class LibroFalsoTarjetas(cuentas: List<Cuenta>, movimientos: List<Movimiento>) : RepositorioLibro {
    private val cuentas = MutableStateFlow(cuentas)
    private val movimientos = MutableStateFlow(movimientos)

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas
    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = movimientos.map { saldosPorCuenta(it) }
    override fun observarMovimientos(): Flow<List<Movimiento>> = movimientos
    override suspend fun registrar(movimiento: Movimiento): MovimientoId = error("no se usa")
    val actualizados = mutableListOf<Movimiento>()
    val archivadas = mutableListOf<CuentaId>()
    override suspend fun actualizar(movimiento: Movimiento) {
        actualizados += movimiento
    }
    override suspend fun eliminar(id: MovimientoId) = error("no se usa")
    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta = error("no se usa")
    override suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String) = error("no se usa")
    override suspend fun actualizarIcono(id: CuentaId, icono: String?) = error("no se usa")
    override suspend fun cambiarArchivada(id: CuentaId, archivada: Boolean) {
        if (archivada) archivadas += id
    }
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
    override suspend fun guardarTasaAnual(tarjeta: CuentaId, tasa: Int) {
        tarjetas.value = tarjetas.value.map { if (it.id == tarjeta) it.copy(tasaAnual = tasa) else it }
    }
    override suspend fun eliminarEstadoDeCuenta(tarjetaId: CuentaId, fechaCorte: LocalDate) {
        estados.value = estados.value.filterNot { it.tarjeta == tarjetaId && it.fechaCorte == fechaCorte }
    }
}
