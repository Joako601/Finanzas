package io.github.joako601.finanzas.ui.movimientos

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
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
import io.github.joako601.finanzas.core.domain.metas.TopeCategoria
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
        val vm = MovimientosViewModel(libro, ServicioLibro(libro), fechaDeHoy = { hoy })
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
    fun una_compra_a_meses_guarda_el_plazo_y_al_editarla_lo_conserva() = runTest {
        val vm = viewModel()
        vm.abrirCaptura()
        vm.cambiarMonto("6000")
        vm.elegirCuenta(nu.id)
        vm.elegirCategoria(super_.id)
        vm.elegirMeses(6)
        vm.guardarCaptura()

        val guardado = libro.observarMovimientos().first().single()
        assertEquals(6, guardado.meses)
        vm.abrirEdicion(resumenDe(vm, guardado.id!!))
        assertEquals(6, vm.captura.value!!.meses)
    }

    @Test
    fun buscar_por_texto_sin_acentos_en_nota_o_categoria_y_sumar_el_neto() = runTest {
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 3), Dinero.pesos(300), efectivo, super_, "despensa"))
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 4), Dinero.pesos(120), debito, comida, "super tacos"))
        libro.registrar(Registro.gasto(LocalDate(2026, 10, 30), Dinero.pesos(50), efectivo, super_, ""))
        val vm = viewModel()
        backgroundScope.launch { vm.resultado.collect {} }
        vm.abrirBusqueda()

        vm.cambiarFiltro { copy(texto = "SUPER") }
        val porTexto = vm.resultado.first { it?.conteo == 3 }!!
        // Sin tipo elegido el total es el neto: tres gastos restan.
        assertEquals(-Dinero.pesos(470), porTexto.total)

        vm.cambiarFiltro { copy(mes = LocalDate(2026, 11, 1), cuentaId = efectivo.id) }
        val filtrado = vm.resultado.first { it?.conteo == 1 }!!
        assertEquals(LocalDate(2026, 11, 3), filtrado.dias.single().fecha)

        vm.cambiarFiltro { Filtro(clase = ClaseMovimiento.GASTO) }
        assertEquals(Dinero.pesos(470), vm.resultado.first { it?.conteo == 3 }!!.total)

        vm.cerrarBusqueda()
        assertEquals(null, vm.resultado.first { it == null })
    }

    @Test
    fun filtrar_por_categoria_y_total_del_dia_con_la_misma_regla() = runTest {
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 3), Dinero.pesos(300), efectivo, super_, ""))
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 3), Dinero.pesos(120), efectivo, comida, ""))
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 4), Dinero.pesos(80), debito, comida, ""))
        val vm = viewModel()
        backgroundScope.launch { vm.resultado.collect {} }
        vm.abrirBusqueda()

        vm.cambiarFiltro { copy(categoriaId = comida.id) }
        val porCategoria = vm.resultado.first { it?.conteo == 2 }!!
        assertEquals(-Dinero.pesos(200), porCategoria.total)

        // El día 3 tiene dos gastos: su total neto es −420; con tipo Gasto, +420.
        val dia3 = vm.estado.value.dias.single { it.fecha == LocalDate(2026, 11, 3) }.movimientos
        assertEquals(-Dinero.pesos(420), totalDe(dia3, null))
        assertEquals(Dinero.pesos(420), totalDe(dia3, ClaseMovimiento.GASTO))
    }

    @Test
    fun ver_movimientos_de_una_cuenta_cierra_la_edicion_y_filtra_por_ella() = runTest {
        val vm = viewModel()
        vm.abrirEditarCuenta(vm.estado.value.cuentasDeDinero.first { it.cuenta.id == debito.id })

        vm.verMovimientosDe(debito.id)

        assertNull(vm.nuevaCuenta.value)
        assertEquals(Filtro(cuentaId = debito.id), vm.filtro.value)
    }

    @Test
    fun las_categorias_mas_usadas_en_90_dias_van_primero_y_los_empates_conservan_su_orden() = runTest {
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 10), Dinero.pesos(50), efectivo, comida, ""))
        libro.registrar(Registro.gasto(LocalDate(2026, 11, 11), Dinero.pesos(50), efectivo, comida, ""))
        // Hace más de 90 días: no cuenta.
        repeat(3) { libro.registrar(Registro.gasto(LocalDate(2026, 7, 1), Dinero.pesos(10), efectivo, super_, "")) }
        val vm = viewModel()

        val orden = vm.estado.first { e -> e.dias.size == 3 }.categoriasDeGasto
        assertEquals(listOf(comida, super_), orden)
    }

    @Test
    fun aviso_de_tope_solo_al_acercarse_o_pasarse_y_sin_contar_el_movimiento_editado() = runTest {
        val id = libro.registrar(Registro.gasto(LocalDate(2026, 11, 3), Dinero.pesos(700), efectivo, comida, ""))
        // Otro mes: no cuenta.
        libro.registrar(Registro.gasto(LocalDate(2026, 10, 3), Dinero.pesos(5_000), efectivo, comida, ""))
        val dias = viewModel().estado.value.dias
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(1_000)))
        fun captura(monto: String, editando: MovimientoId? = null) =
            Captura(montoTexto = monto, categoriaId = comida.id, fecha = hoy, editando = editando)

        // 700 + 50 = 75 % del tope: todavía sin aviso.
        assertNull(avisoTope(captura("50"), topes, dias, listOf(comida)))
        // 700 + 150 = 85 %: quedarían 150.
        assertEquals(AvisoTope("Comida fuera", restante = Dinero.pesos(150)), avisoTope(captura("150"), topes, dias, listOf(comida)))
        // 700 + 500: lo pasa por 200.
        assertEquals(AvisoTope("Comida fuera", excedente = Dinero.pesos(200)), avisoTope(captura("500"), topes, dias, listOf(comida)))
        // Al editar el gasto de 700 a 900 no se cuenta dos veces: 900 = 90 %.
        assertEquals(AvisoTope("Comida fuera", restante = Dinero.pesos(100)), avisoTope(captura("900", editando = id), topes, dias, listOf(comida)))
        // Un ingreso nunca avisa.
        assertNull(avisoTope(captura("900").copy(clase = ClaseRegistro.INGRESO), topes, dias, listOf(comida)))
    }

    @Test
    fun no_se_archiva_la_ultima_cuenta_de_dinero_y_reactivar_la_devuelve() = runTest {
        val vm = viewModel()
        vm.cambiarArchivada(efectivo, archivada = true)
        vm.estado.first { e -> e.cuentasDeDinero.map { it.cuenta } == listOf(debito) }
        // Débito es la única activa: no se archiva.
        assertEquals(false, vm.puedeArchivar(debito))
        vm.cambiarArchivada(debito, archivada = true)
        assertEquals(listOf(debito), vm.estado.value.cuentasDeDinero.map { it.cuenta })
        assertEquals(listOf(efectivo.id), vm.estado.value.archivadas.map { it.cuenta.id })

        vm.cambiarArchivada(efectivo.copy(archivada = true), archivada = false)
        vm.estado.first { e -> e.cuentasDeDinero.size == 2 && e.archivadas.isEmpty() }
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
    fun si_la_base_falla_al_eliminar_queda_el_aviso_y_el_movimiento() = runTest {
        val id = libro.registrar(Registro.gasto(hoy, Dinero.pesos(100), efectivo, super_))
        libro.fallarAlEliminar = true
        val vm = viewModel()

        vm.eliminar(id)

        assertEquals(true, vm.errorAlEliminar.value)
        assertEquals(1, libro.observarMovimientos().first().size)
    }

    @Test
    fun editar_cuenta_con_saldo_negativo_conserva_el_signo_y_permite_ajustarlo() = runTest {
        libro.registrar(Registro.gasto(hoy, Dinero.pesos(50), efectivo, super_))
        val vm = viewModel()
        vm.abrirEditarCuenta(SaldoCuenta(efectivo, -Dinero.pesos(50)))

        assertEquals("-50", vm.nuevaCuenta.value?.saldoTexto)

        vm.cambiarSaldoCuenta("-20.50")
        assertEquals("-20.50", vm.nuevaCuenta.value?.saldoTexto)
        vm.guardarNuevaCuenta()

        assertNull(vm.nuevaCuenta.value)
        // Queda exactamente lo escrito: un ajuste de +29.50 sobre los -50.00 del libro.
        assertEquals(Dinero(-2_050), saldosPorCuenta(libro.movimientos.value)[efectivo.id])
    }

    @Test
    fun archivar_cuenta_pide_confirmacion_y_la_saca_de_las_cuentas_sin_borrar_movimientos() = runTest {
        libro.registrar(Registro.gasto(hoy, Dinero.pesos(50), debito, super_))
        val vm = viewModel()
        vm.abrirEditarCuenta(SaldoCuenta(debito, -Dinero.pesos(50)))

        // Sin confirmar no pasa nada.
        vm.archivarCuenta()
        assertEquals(false, libro.observarCuentas().first().single { it.id == debito.id }.archivada)

        vm.pedirArchivarCuenta()
        assertTrue(vm.nuevaCuenta.value!!.confirmandoArchivar)
        vm.archivarCuenta()

        assertNull(vm.nuevaCuenta.value)
        assertTrue(libro.observarCuentas().first().single { it.id == debito.id }.archivada)
        vm.estado.first { e -> e.cuentasDeDinero.none { it.cuenta.id == debito.id } }
        assertEquals(1, libro.observarMovimientos().first().size)
    }

    @Test
    fun la_ultima_cuenta_guardada_se_preselecciona_al_abrir_la_app() = runTest {
        val guardada = UltimaCuentaUsada.EnMemoria(debito.id)
        val vm = MovimientosViewModel(libro, ServicioLibro(libro), { hoy }, guardada)
        backgroundScope.launch { vm.estado.collect {} }
        vm.estado.first { !it.cargando }

        vm.abrirCaptura()
        assertEquals(debito.id, vm.captura.value?.cuentaId)

        vm.cambiarMonto("80")
        vm.elegirCategoria(super_.id)
        vm.elegirCuenta(efectivo.id)
        vm.guardarCaptura()
        assertEquals(efectivo.id, guardada.leer())
    }

    @Test
    fun cancelar_archivar_vuelve_a_la_edicion() = runTest {
        val vm = viewModel()
        vm.abrirEditarCuenta(SaldoCuenta(efectivo, Dinero.CERO))
        vm.pedirArchivarCuenta()
        vm.cancelarArchivarCuenta()

        assertEquals(false, vm.nuevaCuenta.value!!.confirmandoArchivar)
        assertEquals(false, libro.observarCuentas().first().single { it.id == efectivo.id }.archivada)
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
    var fallarAlEliminar = false

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
        if (fallarAlEliminar) throw IllegalStateException("base no disponible")
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
    override suspend fun actualizarIcono(id: CuentaId, icono: String?) {
        cuentas.value = cuentas.value.map { if (it.id == id) it.copy(icono = icono) else it }
    }
    override suspend fun cambiarArchivada(id: CuentaId, archivada: Boolean) {
        cuentas.value = cuentas.value.map { if (it.id == id) it.copy(archivada = archivada) else it }
    }
    override suspend fun cuentaDeSaldosIniciales(): Cuenta = capital
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}
