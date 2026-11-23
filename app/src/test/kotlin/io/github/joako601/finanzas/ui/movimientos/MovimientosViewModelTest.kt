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
    private val gimnasio = Cuenta(CuentaId(5), "Gimnasio", TipoCuenta.GASTO, archivada = true)
    private val nu = Cuenta(CuentaId(6), "Nu", TipoCuenta.PASIVO)

    private val libro = LibroEnMemoria(listOf(efectivo, debito, super_, comida, gimnasio, nu))

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

    @Test
    fun editar_un_gasto_de_categoria_archivada_la_conserva() = runTest {
        val id = libro.registrar(Registro.gasto(hoy, Dinero.pesos(400), efectivo, gimnasio, "mensualidad"))
        val vm = viewModel()
        vm.abrirEdicion(resumenDe(vm, id))

        vm.cambiarMonto("450")
        vm.guardarCaptura()

        assertNull("guardó sin pedir otra categoría", vm.captura.value)
        assertEquals(Dinero.pesos(450), saldosPorCuenta(libro.movimientos.value)[gimnasio.id])
    }

    @Test
    fun editar_un_gasto_con_tarjeta_conserva_la_tarjeta() = runTest {
        val id = libro.registrar(Registro.gasto(hoy, Dinero.pesos(300), nu, super_))
        val vm = viewModel()
        vm.abrirEdicion(resumenDe(vm, id))

        vm.cambiarMonto("350")
        vm.guardarCaptura()

        assertNull(vm.captura.value)
        assertEquals(-Dinero.pesos(350), saldosPorCuenta(libro.movimientos.value)[nu.id])
    }

    @Test
    fun una_transferencia_que_toca_una_tarjeta_no_se_abre_para_editar() = runTest {
        // Disposición de efectivo: sube la deuda de la tarjeta y entra dinero a efectivo.
        val id = libro.registrar(
            Movimiento(
                null, hoy, "",
                listOf(
                    io.github.joako601.finanzas.core.domain.libro.Asiento(efectivo.id, Dinero.pesos(1_000)),
                    io.github.joako601.finanzas.core.domain.libro.Asiento(nu.id, -Dinero.pesos(1_000)),
                ),
            ),
        )
        val vm = viewModel()
        val resumen = resumenDe(vm, id)

        assertEquals(false, vm.esEditable(resumen))
        vm.abrirEdicion(resumen)
        assertNull(vm.transferencia.value)
    }

    @Test
    fun si_la_base_falla_al_editar_queda_el_aviso_y_el_formulario() = runTest {
        val id = libro.registrar(Registro.gasto(hoy, Dinero.pesos(100), efectivo, super_))
        libro.fallarAlActualizar = true
        val vm = viewModel()
        vm.abrirEdicion(resumenDe(vm, id))

        vm.cambiarMonto("90")
        vm.guardarCaptura()

        assertEquals(true, vm.captura.value?.errorAlGuardar)
        assertEquals(false, vm.captura.value?.guardando)
    }

    @Test
    fun editar_cuenta_con_saldo_negativo_conserva_el_signo_y_permite_ajustarlo() = runTest {
        val vm = viewModel()
        vm.abrirEditarCuenta(SaldoCuenta(efectivo, -Dinero.pesos(50)))

        assertEquals("-50", vm.nuevaCuenta.value?.saldoTexto)

        vm.cambiarSaldoCuenta("-20.50")
        assertEquals("-20.50", vm.nuevaCuenta.value?.saldoTexto)
        vm.guardarNuevaCuenta()

        assertNull(vm.nuevaCuenta.value)
        // El ajuste es (-20.50) - (-50.00) = +29.50 en efectivo.
        assertEquals(Dinero(2_950), saldosPorCuenta(libro.movimientos.value)[efectivo.id])
    }

    @Test
    fun crear_cuenta_invoca_callback_con_el_id_creado() = runTest {
        val vm = viewModel()
        var creadaId: CuentaId? = null
        vm.abrirNuevaCuenta()
        vm.cambiarNombreCuenta("BBVA")
        vm.cambiarSaldoCuenta("1000")
        vm.guardarNuevaCuenta { creadaId = it }

        assertNull(vm.nuevaCuenta.value)
        assertTrue(creadaId != null)
        assertEquals("BBVA", libro.cuentasActuales().last().nombre)
    }
}

/** Libro en memoria con registrar, actualizar y eliminar reales, para probar el ViewModel. */
private class LibroEnMemoria(cuentas: List<Cuenta>) : RepositorioLibro {
    private val cuentas = MutableStateFlow(cuentas)
    private val capital = Cuenta(CuentaId(99), "Saldos iniciales", TipoCuenta.CAPITAL)
    val movimientos = MutableStateFlow(emptyList<Movimiento>())
    private var siguiente = 1L
    private var siguienteCuenta = 100L
    var fallarAlActualizar = false

    fun cuentasActuales(): List<Cuenta> = cuentas.value

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas
    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = movimientos.map { saldosPorCuenta(it) }
    override fun observarMovimientos(): Flow<List<Movimiento>> = movimientos
    override suspend fun registrar(movimiento: Movimiento): MovimientoId {
        val id = MovimientoId(siguiente++)
        movimientos.value = listOf(movimiento.copy(id = id)) + movimientos.value
        return id
    }
    override suspend fun actualizar(movimiento: Movimiento) {
        if (fallarAlActualizar) throw IllegalStateException("base no disponible")
        check(movimientos.value.any { it.id == movimiento.id })
        movimientos.value = movimientos.value.map { if (it.id == movimiento.id) movimiento else it }
    }
    override suspend fun eliminar(id: MovimientoId) {
        movimientos.value = movimientos.value.filterNot { it.id == id }
    }
    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta {
        val nueva = Cuenta(CuentaId(siguienteCuenta++), nombre, tipo)
        cuentas.value = cuentas.value + nueva
        return nueva
    }
    override suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String) {
        cuentas.value = cuentas.value.map { if (it.id == id) it.copy(nombre = nombre) else it }
    }
    override suspend fun cuentaDeSaldosIniciales(): Cuenta = capital
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}
