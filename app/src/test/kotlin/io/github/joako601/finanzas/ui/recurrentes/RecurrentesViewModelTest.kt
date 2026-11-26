package io.github.joako601.finanzas.ui.recurrentes

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.recurrentes.ServicioRecurrentes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class RecurrentesViewModelTest {

    private val hoy = LocalDate(2026, 11, 19)
    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val luz = Cuenta(CuentaId(2), "Luz, agua y gas", TipoCuenta.GASTO)
    private val nu = Cuenta(CuentaId(7), "Nu", TipoCuenta.PASIVO)
    private val cfe = Recurrente(
        RecurrenteId(1), "CFE", ClaseRegistro.GASTO, Dinero.pesos(650), luz.id, Periodicidad.BIMESTRAL,
        LocalDate(2026, 11, 25), LocalDate(2026, 11, 25),
    )

    @Test
    fun inicio_muestra_vencidos_y_proximos_7_dias_hasta_3_o_el_mas_cercano() {
        fun r(dias: Int) = ResumenRecurrente(cfe, luz, dias)
        // Vencido, hoy, en 7 días y en 8: entran los tres primeros.
        assertEquals(listOf(-2, 0, 7), recurrentesCercanos(listOf(r(-2), r(0), r(7), r(8))).map { it.diasParaProxima })
        // Más de 3 cercanos: solo los 3 primeros (los vencidos van antes por el orden de fecha).
        assertEquals(3, recurrentesCercanos(listOf(r(-5), r(-1), r(1), r(2))).size)
        // Ninguno en la semana: el más cercano, para que la sección no quede vacía.
        assertEquals(listOf(20), recurrentesCercanos(listOf(r(20), r(40))).map { it.diasParaProxima })
        assertEquals(emptyList<ResumenRecurrente>(), recurrentesCercanos(emptyList()))
    }

    private val libro = LibroFalso(listOf(efectivo, luz, nu))
    private val recurrentes = RecurrentesFalso(listOf(cfe))

    @Before
    fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun despues() = Dispatchers.resetMain()

    private fun viewModel() = RecurrentesViewModel(libro, recurrentes, ServicioRecurrentes(libro, recurrentes)) { hoy }

    /**
     * La pantalla real se suscribe al estado; sin suscriptor el StateFlow no carga. El
     * estado se calcula en Dispatchers.Default (un hilo real), así que se espera su
     * primera emisión con datos y se devuelve el primer pago fijo.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.cargar(vm: RecurrentesViewModel): ResumenRecurrente {
        backgroundScope.launch { vm.estado.collect {} }
        return vm.estado.first { it.recurrentes.isNotEmpty() }.recurrentes.first()
    }

    @Test
    fun registrar_con_monto_vacio_deja_el_dialogo_con_el_error() = runTest {
        val vm = viewModel()
        vm.abrirRegistro(cargar(vm))
        vm.editarRegistro { copy(monto = "") }
        vm.guardarRegistro()
        advanceUntilIdle()

        assertEquals(setOf(ErrorRegistro.MONTO_VACIO), vm.registro.value?.errores)
        assertEquals(false, vm.registro.value?.guardando)
        assertTrue(libro.registrados.isEmpty())
    }

    @Test
    fun registrar_valido_cierra_el_dialogo_y_avanza_la_fecha() = runTest {
        val vm = viewModel()
        vm.abrirRegistro(cargar(vm))
        assertEquals("650", vm.registro.value?.monto)
        vm.guardarRegistro()
        advanceUntilIdle()

        assertNull(vm.registro.value)
        assertEquals(1, libro.registrados.size)
        assertEquals(LocalDate(2027, 1, 25), recurrentes.lista.value.single().proxima)
    }

    @Test
    fun si_falla_la_base_el_dialogo_sigue_abierto_con_el_aviso() = runTest {
        recurrentes.fallar = true
        val vm = viewModel()
        vm.abrirRegistro(cargar(vm))
        vm.omitir()
        advanceUntilIdle()

        assertEquals(true, vm.registro.value?.errorAlGuardar)
        assertEquals(false, vm.registro.value?.guardando)
    }

    @Test
    fun la_cuenta_preferida_se_guarda_y_se_preselecciona_al_registrar() = runTest {
        val vm = viewModel()
        cargar(vm)
        vm.abrirNuevo()
        vm.editarNuevo { copy(nombre = "Netflix", monto = "300", categoriaId = luz.id, cuentaId = nu.id) }
        vm.guardarNuevo()
        advanceUntilIdle()

        val guardado = recurrentes.lista.value.single { it.nombre == "Netflix" }
        assertEquals(nu.id, guardado.cuenta)
        val resumen = vm.estado.first { e -> e.recurrentes.any { it.recurrente.nombre == "Netflix" } }
            .recurrentes.single { it.recurrente.nombre == "Netflix" }
        vm.abrirRegistro(resumen)
        assertEquals(nu.id, vm.registro.value?.cuentaId)

        // Un ingreso no llega a una tarjeta: al cambiar de clase se limpia.
        vm.abrirNuevo()
        vm.editarNuevo { copy(cuentaId = nu.id) }
        vm.editarNuevo { copy(clase = ClaseRegistro.INGRESO) }
        assertNull(vm.nuevo.value?.cuentaId)
    }

    @Test
    fun cambiar_de_pago_a_ingreso_borra_la_categoria_elegida() = runTest {
        val vm = viewModel()
        cargar(vm)
        vm.abrirNuevo()
        vm.editarNuevo { copy(categoriaId = luz.id) }
        vm.editarNuevo { copy(clase = ClaseRegistro.INGRESO) }

        assertNull(vm.nuevo.value?.categoriaId)
    }

    @Test
    fun abrir_registro_con_monto_de_miles_no_incluye_coma_y_permite_editar_sin_perder_ancla() = runTest {
        recurrentes.lista.value = listOf(
            Recurrente(
                id = RecurrenteId(2),
                nombre = "Renta",
                clase = ClaseRegistro.GASTO,
                montoEstimado = Dinero.pesos(8_500),
                categoria = luz.id,
                periodicidad = Periodicidad.MENSUAL,
                inicio = LocalDate(2026, 1, 31),
                proxima = LocalDate(2026, 2, 28),
            ),
        )
        val vm = viewModel()
        val resumen = cargar(vm)
        vm.abrirRegistro(resumen)
        assertEquals("8500", vm.registro.value?.monto)

        // Editar solo el nombre no debe destruir el ancla del día 31 ni cambiar la próxima fecha.
        vm.abrirEdicionDesdeRegistro()
        vm.editarNuevo { copy(nombre = "Renta departamento") }
        vm.guardarNuevo()
        advanceUntilIdle()

        val actualizado = recurrentes.lista.value.single()
        assertEquals("Renta departamento", actualizado.nombre)
        assertEquals(LocalDate(2026, 1, 31), actualizado.inicio)
        assertEquals(LocalDate(2026, 2, 28), actualizado.proxima)
        assertEquals(LocalDate(2026, 3, 31), actualizado.despuesDeProxima())
    }
}

private class LibroFalso(cuentas: List<Cuenta>) : RepositorioLibro {
    private val cuentas = MutableStateFlow(cuentas)
    val registrados = mutableListOf<Movimiento>()

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas
    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = flowOf(emptyMap())
    override fun observarMovimientos(): Flow<List<Movimiento>> = flowOf(registrados.toList())
    override suspend fun registrar(movimiento: Movimiento): MovimientoId {
        registrados += movimiento
        return MovimientoId(registrados.size.toLong())
    }
    override suspend fun eliminar(id: MovimientoId) = Unit
    override suspend fun actualizar(movimiento: Movimiento) = Unit
    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta = error("no se usa")
    override suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String) = error("no se usa")
    override suspend fun actualizarIcono(id: CuentaId, icono: String?) = error("no se usa")
    override suspend fun cambiarArchivada(id: CuentaId, archivada: Boolean) = error("no se usa")
    override suspend fun cuentaDeSaldosIniciales(): Cuenta = error("no se usa")
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}

private class RecurrentesFalso(inicial: List<Recurrente>) : RepositorioRecurrentes {
    val lista = MutableStateFlow(inicial)
    var fallar = false

    override fun observarRecurrentes(): Flow<List<Recurrente>> = lista
    override suspend fun guardar(recurrente: Recurrente): RecurrenteId {
        val id = RecurrenteId(lista.value.size + 100L)
        lista.value = lista.value + recurrente.copy(id = id)
        return id
    }
    override suspend fun actualizar(recurrente: Recurrente) {
        if (fallar) throw IllegalStateException("base no disponible")
        lista.value = lista.value.map { if (it.id == recurrente.id) recurrente else it }
    }
    override suspend fun cambiarProxima(id: RecurrenteId, proxima: LocalDate) {
        if (fallar) throw IllegalStateException("base no disponible")
        lista.value = lista.value.map { if (it.id == id) it.copy(proxima = proxima) else it }
    }
    override suspend fun eliminar(id: RecurrenteId) = Unit
}
