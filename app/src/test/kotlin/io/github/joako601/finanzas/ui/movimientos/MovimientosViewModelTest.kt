package io.github.joako601.finanzas.ui.movimientos

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
class MovimientosViewModelTest {

    private val hoy = LocalDate(2026, 11, 19)
    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val debito = Cuenta(CuentaId(2), "Nu débito", TipoCuenta.ACTIVO)
    private val super_ = Cuenta(CuentaId(3), "Súper y despensa", TipoCuenta.GASTO)
    private val comida = Cuenta(CuentaId(4), "Comida fuera", TipoCuenta.GASTO)

    private val libro = LibroEnMemoria(listOf(efectivo, debito, super_, comida))

    @Before
    fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun despues() = Dispatchers.resetMain()

    /** Suscribe el estado como lo haría la pantalla y espera a que cargue. */
    private suspend fun kotlinx.coroutines.test.TestScope.viewModel(): MovimientosViewModel {
        val vm = MovimientosViewModel(libro, ServicioLibro(libro)) { hoy }
        backgroundScope.launch { vm.estado.collect {} }
        vm.estado.first { !it.cargando }
        return vm
    }

    private suspend fun resumenDe(vm: MovimientosViewModel, id: MovimientoId) =
        vm.estado.first { e -> e.dias.any { d -> d.movimientos.any { it.movimiento.id == id } } }
            .dias.flatMap { it.movimientos }.single { it.movimiento.id == id }

    @Test
    fun editar_un_gasto_abre_la_hoja_con_sus_datos() = runTest {
        val id = libro.registrar(Registro.gasto(LocalDate(2026, 11, 15), Dinero(123_450), debito, super_, "despensa"))
        val vm = viewModel()

        vm.abrirEdicion(resumenDe(vm, id))

        val captura = vm.captura.value!!
        assertEquals(id, captura.editando)
        assertEquals(ClaseRegistro.GASTO, captura.clase)
        assertEquals("1234.50", captura.montoTexto)
        assertEquals(debito.id, captura.cuentaId)
        assertEquals(super_.id, captura.categoriaId)
        assertEquals(LocalDate(2026, 11, 15), captura.fecha)
        assertEquals("despensa", captura.nota)
    }

    @Test
    fun guardar_la_edicion_actualiza_el_mismo_movimiento() = runTest {
        val id = libro.registrar(Registro.gasto(hoy, Dinero.pesos(100), efectivo, super_, "tacos"))
        val vm = viewModel()
        vm.abrirEdicion(resumenDe(vm, id))

        vm.cambiarMonto("85.50")
        vm.elegirCategoria(comida.id)
        vm.guardarCaptura()

        assertNull(vm.captura.value)
        val unico = libro.movimientos.value.single()
        assertEquals(id, unico.id)
        assertEquals(Dinero(8_550), saldosPorCuenta(listOf(unico))[comida.id])
    }

    @Test
    fun un_monto_entero_se_edita_sin_centavos() = runTest {
        val id = libro.registrar(Registro.gasto(hoy, Dinero.pesos(100), efectivo, super_))
        val vm = viewModel()

        vm.abrirEdicion(resumenDe(vm, id))

        assertEquals("100", vm.captura.value!!.montoTexto)
    }

    @Test
    fun transferir_propone_dos_cuentas_distintas_y_registra() = runTest {
        val vm = viewModel()

        vm.abrirTransferencia()
        val propuesta = vm.transferencia.value!!
        assertEquals(efectivo.id, propuesta.desdeId)
        assertEquals(debito.id, propuesta.haciaId)

        vm.editarTransferencia { copy(montoTexto = "500", desdeId = debito.id, haciaId = efectivo.id, nota = "cajero") }
        vm.guardarTransferencia()

        assertNull(vm.transferencia.value)
        val saldos = saldosPorCuenta(libro.movimientos.value)
        assertEquals(Dinero.pesos(500), saldos[efectivo.id])
        assertEquals(-Dinero.pesos(500), saldos[debito.id])
    }

    @Test
    fun transferir_a_la_misma_cuenta_muestra_el_error() = runTest {
        val vm = viewModel()
        vm.abrirTransferencia()

        vm.editarTransferencia { copy(montoTexto = "500", haciaId = desdeId) }
        vm.guardarTransferencia()

        assertEquals(setOf(ErrorRegistro.MISMA_CUENTA), vm.transferencia.value!!.errores)
        assertTrue(libro.movimientos.value.isEmpty())
    }

    @Test
    fun editar_una_transferencia_actualiza_la_misma() = runTest {
        val id = libro.registrar(Registro.transferencia(hoy, Dinero.pesos(500), debito, efectivo, "cajero"))
        val vm = viewModel()

        vm.abrirEdicion(resumenDe(vm, id))
        assertNull("una transferencia no usa la hoja de gasto o ingreso", vm.captura.value)
        val abierta = vm.transferencia.value!!
        assertEquals(id, abierta.editando)
        assertEquals(debito.id, abierta.desdeId)
        assertEquals(efectivo.id, abierta.haciaId)
        assertEquals("500", abierta.montoTexto)

        vm.editarTransferencia { copy(montoTexto = "700") }
        vm.guardarTransferencia()

        val unico = libro.movimientos.value.single()
        assertEquals(id, unico.id)
        assertEquals(Dinero.pesos(700), saldosPorCuenta(listOf(unico))[efectivo.id])
    }
}

/** Libro en memoria con registrar, actualizar y eliminar reales, para probar el ViewModel. */
private class LibroEnMemoria(cuentas: List<Cuenta>) : RepositorioLibro {
    private val cuentas = MutableStateFlow(cuentas)
    val movimientos = MutableStateFlow(emptyList<Movimiento>())
    private var siguiente = 1L

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas
    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = movimientos.map { saldosPorCuenta(it) }
    override fun observarMovimientos(): Flow<List<Movimiento>> = movimientos
    override suspend fun registrar(movimiento: Movimiento): MovimientoId {
        val id = MovimientoId(siguiente++)
        movimientos.value = listOf(movimiento.copy(id = id)) + movimientos.value
        return id
    }
    override suspend fun actualizar(movimiento: Movimiento) {
        check(movimientos.value.any { it.id == movimiento.id })
        movimientos.value = movimientos.value.map { if (it.id == movimiento.id) movimiento else it }
    }
    override suspend fun eliminar(id: MovimientoId) {
        movimientos.value = movimientos.value.filterNot { it.id == id }
    }
    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta = error("no se usa")
    override suspend fun cuentaDeSaldosIniciales(): Cuenta = error("no se usa")
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}
