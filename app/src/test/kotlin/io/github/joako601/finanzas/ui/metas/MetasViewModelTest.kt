package io.github.joako601.finanzas.ui.metas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import io.github.joako601.finanzas.core.domain.metas.ErrorMovimientoMeta
import io.github.joako601.finanzas.core.domain.metas.ErrorTope
import io.github.joako601.finanzas.core.domain.metas.MetaAhorro
import io.github.joako601.finanzas.core.domain.metas.AporteMeta
import io.github.joako601.finanzas.core.domain.metas.MetaAhorroId
import io.github.joako601.finanzas.core.domain.metas.RepositorioMetas
import io.github.joako601.finanzas.core.domain.metas.ServicioMetas
import io.github.joako601.finanzas.core.domain.metas.TopeCategoria
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
class MetasViewModelTest {

    private val hoy = LocalDate(2026, 11, 15)
    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val comida = Cuenta(CuentaId(2), "Comida fuera", TipoCuenta.GASTO)
    private val super_ = Cuenta(CuentaId(3), "Súper y despensa", TipoCuenta.GASTO)

    private val libro = LibroFalsoMetas(
        cuentas = listOf(efectivo, comida, super_),
        movimientosIniciales = listOf(
            Registro.gasto(LocalDate(2026, 10, 10), Dinero.pesos(1_850), efectivo, comida),
        ),
    )
    private val tarjetas = TarjetasFalsoMetas()
    private val recurrentes = RecurrentesFalsoMetas()
    private val metas = MetasFalso()

    @Before
    fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun despues() = Dispatchers.resetMain()

    private suspend fun kotlinx.coroutines.test.TestScope.viewModel(): MetasViewModel {
        val vm = MetasViewModel(libro, tarjetas, recurrentes, metas, ServicioMetas(metas)) { hoy }
        backgroundScope.launch { vm.estado.collect {} }
        vm.estado.first { !it.cargando }
        return vm
    }

    @Test
    fun abrir_nuevo_tope_preselecciona_categoria_con_mas_gasto_y_sugiere_limite() = runTest {
        val vm = viewModel()
        vm.abrirNuevoTope()

        val editor = vm.editorTope.value!!
        assertEquals(comida.id, editor.categoriaId)
        assertEquals("1850", editor.montoTexto)

        // Cambiar a otra categoría sin haber editado el monto reemplaza la sugerencia (aquí vacía).
        vm.elegirCategoriaTope(super_.id)
        assertEquals(super_.id, vm.editorTope.value?.categoriaId)
        assertEquals("", vm.editorTope.value?.montoTexto)
    }

    @Test
    fun tocar_categoria_en_analisis_crea_tope_nuevo_o_abre_el_existente() = runTest {
        val vm = viewModel()

        // Sin tope: abre el editor con esa categoría (no la de más gasto).
        vm.abrirTopeDeCategoria(super_.id)
        assertEquals(super_.id, vm.editorTope.value?.categoriaId)
        vm.cambiarMontoTope("2000")
        vm.guardarTope()
        advanceUntilIdle()
        vm.estado.first { it.resumenTopes?.topes?.size == 1 }

        // Con tope: abre su detalle.
        vm.abrirTopeDeCategoria(super_.id)
        assertEquals(super_.id, vm.detalleTopeId.value)
    }

    @Test
    fun fallo_al_eliminar_tope_deja_dialogo_abierto_con_aviso_de_error() = runTest {
        val vm = viewModel()
        vm.abrirNuevoTope(comida.id)
        vm.cambiarMontoTope("2000")
        vm.guardarTope()
        advanceUntilIdle()

        val analisis = vm.estado.first { it.resumenTopes?.topes?.size == 1 }.resumenTopes!!.topes.single()
        metas.fallarAlEliminar = true
        vm.pedirEliminarTope(analisis)
        vm.confirmarEliminarTope()
        advanceUntilIdle()

        assertEquals(true, vm.topePorEliminar.value?.errorAlEliminar)
        assertEquals(false, vm.topePorEliminar.value?.eliminando)
        assertEquals(1, metas.topes.value.size)
    }

    @Test
    fun guardar_tope_invalido_muestra_error_y_no_persiste() = runTest {
        val vm = viewModel()
        vm.abrirNuevoTope(super_.id)
        vm.cambiarMontoTope("0")
        vm.guardarTope()
        advanceUntilIdle()

        assertEquals(setOf(ErrorTope.MONTO_INVALIDO), vm.editorTope.value?.errores)
        assertTrue(metas.topes.value.isEmpty())
    }

    @Test
    fun guardar_y_eliminar_tope_actualiza_estado() = runTest {
        val vm = viewModel()
        vm.abrirNuevoTope(comida.id)
        vm.cambiarMontoTope("2500")
        vm.guardarTope()
        advanceUntilIdle()

        assertNull(vm.editorTope.value)
        val cargado = vm.estado.first { it.resumenTopes?.topes?.size == 1 }
        val analisis = cargado.resumenTopes!!.topes.single()
        assertEquals(Dinero.pesos(2_500), analisis.tope.limiteMensual)

        vm.pedirEliminarTope(analisis)
        vm.confirmarEliminarTope()
        advanceUntilIdle()

        assertTrue(metas.topes.value.isEmpty())
    }

    @Test
    fun crear_aportar_retirar_y_archivar_meta_de_ahorro() = runTest {
        val vm = viewModel()
        vm.abrirNuevaMeta(nombreInicial = "Fondo de emergencia", objetivoInicial = Dinero.pesos(10_000))
        vm.editarMeta { copy(apartadoInicialTexto = "1000") }
        vm.guardarMeta()
        advanceUntilIdle()

        assertNull(vm.editorMeta.value)
        val cargada = vm.estado.first { it.resumenAhorro?.metasActivas?.size == 1 }
            .resumenAhorro!!.metasActivas.single()
        assertEquals(Dinero.pesos(1_000), cargada.meta.apartado)
        // El apartado inicial también queda en el historial, para que la suma cuadre con el saldo.
        assertEquals(listOf(AporteMeta(hoy, Dinero.pesos(1_000))), metas.observarAportes(cargada.meta.id!!).first())

        // Aportar $500 -> $1,500
        vm.abrirMovimientoMeta(cargada, TipoMovimientoMeta.APORTAR)
        vm.cambiarMontoMovimientoMeta("500")
        vm.confirmarMovimientoMeta()
        advanceUntilIdle()

        val trasAporte = vm.estado.first { it.resumenAhorro?.totalApartado == Dinero.pesos(1_500) }
            .resumenAhorro!!.metasActivas.single()

        // Intentar retirar más de lo apartado -> error SUPERA_APARTADO
        vm.abrirMovimientoMeta(trasAporte, TipoMovimientoMeta.RETIRAR)
        vm.cambiarMontoMovimientoMeta("2000")
        vm.confirmarMovimientoMeta()
        advanceUntilIdle()
        assertEquals(ErrorMovimientoMeta.SUPERA_APARTADO, vm.movimientoMeta.value?.error)

        // Retirar todo ($1,500) con archivarAlVaciar = true -> pasa a archivadas con $0
        vm.cambiarMontoMovimientoMeta("1500")
        vm.confirmarMovimientoMeta()
        advanceUntilIdle()

        assertNull(vm.movimientoMeta.value)
        val final = vm.estado.first { it.resumenAhorro?.metasArchivadas?.size == 1 }.resumenAhorro!!
        assertTrue(final.metasActivas.isEmpty())
        assertEquals(Dinero.CERO, final.metasArchivadas.single().meta.apartado)

        // El historial anota el apartado inicial, el aporte y el retiro: suma lo mismo que el saldo ($0).
        backgroundScope.launch { vm.aportesDetalle.collect {} }
        vm.abrirDetalleMeta(cargada.meta.id!!)
        val historial = vm.aportesDetalle.first { it.size == 3 }
        assertEquals(listOf(-Dinero.pesos(1_500), Dinero.pesos(500), Dinero.pesos(1_000)), historial.map { it.monto })
        assertEquals(hoy, historial.first().fecha)
    }

    @Test
    fun retirar_y_registrar_gasto_deja_el_monto_listo_para_inicio() = runTest {
        val vm = viewModel()
        vm.abrirNuevaMeta(nombreInicial = "Viaje", objetivoInicial = Dinero.pesos(10_000))
        vm.editarMeta { copy(apartadoInicialTexto = "3000") }
        vm.guardarMeta()
        advanceUntilIdle()
        val meta = vm.estado.first { it.resumenAhorro?.metasActivas?.size == 1 }.resumenAhorro!!.metasActivas.single()

        vm.abrirMovimientoMeta(meta, TipoMovimientoMeta.RETIRAR)
        vm.cambiarMontoMovimientoMeta("1200")
        vm.cambiarRegistrarComoGasto(true)
        vm.confirmarMovimientoMeta()
        advanceUntilIdle()

        assertEquals(GastoDesdeRetiro(Dinero.pesos(1_200), "Viaje"), vm.gastoDesdeRetiro.value)
        vm.consumirGastoDesdeRetiro()
        assertNull(vm.gastoDesdeRetiro.value)
    }

    @Test
    fun aportar_desde_detalle_cierra_la_hoja_y_chips_suman_incrementalmente() = runTest {
        val vm = viewModel()
        vm.abrirNuevaMeta(nombreInicial = "Viaje", objetivoInicial = Dinero.pesos(5_000))
        vm.guardarMeta()
        advanceUntilIdle()

        val meta = vm.estado.first { it.resumenAhorro?.metasActivas?.size == 1 }.resumenAhorro!!.metasActivas.single()
        vm.abrirDetalleMeta(meta.meta.id!!)
        assertEquals(meta.meta.id, vm.detalleMetaId.value)

        vm.abrirMovimientoMeta(meta, TipoMovimientoMeta.APORTAR)
        // Crítico: abrir el diálogo de movimiento desde la hoja debe limpiar detalleMetaId.
        assertNull(vm.detalleMetaId.value)

        // Los chips +$500 y +$100 suman incrementalmente -> "600".
        vm.sumarMontoMovimientoMeta(Dinero.pesos(500))
        vm.sumarMontoMovimientoMeta(Dinero.pesos(100))
        assertEquals("600", vm.movimientoMeta.value?.montoTexto)
    }

    @Test
    fun sobre_reserva_en_metas_coincide_con_disponible_real_descontando_pagos_proximos() = runTest {
        val capital = Cuenta(CuentaId(99), "Saldos iniciales", TipoCuenta.CAPITAL)
        val libroConSaldo = LibroFalsoMetas(
            cuentas = listOf(efectivo, comida, super_, capital),
            movimientosIniciales = listOf(
                Registro.saldoInicial(hoy, efectivo, Dinero.pesos(5_000), capital),
            ),
        )
        // Pago fijo próximo de $3,000 -> disponible real = $2,000 (aunque en cuentas haya $5,000).
        recurrentes.lista.value = listOf(
            Recurrente(
                id = RecurrenteId(1),
                nombre = "Renta",
                clase = ClaseRegistro.GASTO,
                montoEstimado = Dinero.pesos(3_000),
                categoria = comida.id,
                periodicidad = Periodicidad.MENSUAL,
                inicio = LocalDate(2026, 11, 25),
                proxima = LocalDate(2026, 11, 25),
            ),
        )
        val vm = MetasViewModel(libroConSaldo, tarjetas, recurrentes, metas, ServicioMetas(metas)) { hoy }
        backgroundScope.launch { vm.estado.collect {} }
        vm.estado.first { !it.cargando }

        vm.abrirNuevaMeta(nombreInicial = "Fondo", objetivoInicial = Dinero.pesos(10_000))
        vm.editarMeta { copy(apartadoInicialTexto = "3500", reservarDeDisponible = true) }
        vm.guardarMeta()
        advanceUntilIdle()

        val resumen = vm.estado.first { it.resumenAhorro?.metasActivas?.size == 1 }.resumenAhorro!!
        // $3,500 apartados - $2,000 disponible real = $1,500 de sobre-reserva (igual que en Inicio).
        assertEquals(Dinero.pesos(1_500), resumen.sobreReserva)
    }
}

private class TarjetasFalsoMetas : RepositorioTarjetas {
    val tarjetas = MutableStateFlow<List<Tarjeta>>(emptyList())
    val estados = MutableStateFlow<List<EstadoDeCuenta>>(emptyList())
    override fun observarTarjetas(): Flow<List<Tarjeta>> = tarjetas
    override fun observarEstadosDeCuenta(): Flow<List<EstadoDeCuenta>> = estados
    override suspend fun guardarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta =
        error("no se usa")
    override suspend fun actualizarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta =
        error("no se usa")
    override suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuenta) = error("no se usa")
    override suspend fun guardarTasaAnual(tarjeta: CuentaId, tasa: Int) = error("no se usa")
    override suspend fun eliminarEstadoDeCuenta(tarjetaId: CuentaId, fechaCorte: LocalDate) = error("no se usa")
}

private class RecurrentesFalsoMetas : RepositorioRecurrentes {
    val lista = MutableStateFlow<List<Recurrente>>(emptyList())
    override fun observarRecurrentes(): Flow<List<Recurrente>> = lista
    override suspend fun guardar(recurrente: Recurrente): RecurrenteId = error("no se usa")
    override suspend fun actualizar(recurrente: Recurrente) = error("no se usa")
    override suspend fun cambiarProxima(id: RecurrenteId, proxima: LocalDate) = error("no se usa")
    override suspend fun eliminar(id: RecurrenteId) = error("no se usa")
}

private class LibroFalsoMetas(
    cuentas: List<Cuenta>,
    movimientosIniciales: List<Movimiento>,
) : RepositorioLibro {
    private val cuentas = MutableStateFlow(cuentas)
    private val movimientos = MutableStateFlow(movimientosIniciales)

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas
    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = movimientos.map { saldosPorCuenta(it) }
    override fun observarMovimientos(): Flow<List<Movimiento>> = movimientos
    override suspend fun registrar(movimiento: Movimiento): MovimientoId = error("no se usa")
    override suspend fun actualizar(movimiento: Movimiento) = error("no se usa")
    override suspend fun eliminar(id: MovimientoId) = error("no se usa")
    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta = error("no se usa")
    override suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String) = error("no se usa")
    override suspend fun actualizarIcono(id: CuentaId, icono: String?) = error("no se usa")
    override suspend fun cambiarArchivada(id: CuentaId, archivada: Boolean) = error("no se usa")
    override suspend fun cuentaDeSaldosIniciales(): Cuenta = error("no se usa")
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}

private class MetasFalso : RepositorioMetas {
    val topes = MutableStateFlow<List<TopeCategoria>>(emptyList())
    val metas = MutableStateFlow<List<MetaAhorro>>(emptyList())
    private var siguienteMeta = 1L
    var fallarAlEliminar = false

    override fun observarTopes(): Flow<List<TopeCategoria>> = topes
    override suspend fun guardarTope(tope: TopeCategoria) {
        topes.value = topes.value.filterNot { it.categoriaId == tope.categoriaId } + tope
    }
    override suspend fun eliminarTope(categoriaId: CuentaId) {
        if (fallarAlEliminar) throw IllegalStateException("base no disponible")
        topes.value = topes.value.filterNot { it.categoriaId == categoriaId }
    }
    override fun observarMetas(): Flow<List<MetaAhorro>> = metas
    override suspend fun crearMeta(meta: MetaAhorro, fecha: LocalDate): MetaAhorroId {
        val id = MetaAhorroId(siguienteMeta++)
        metas.value = metas.value + meta.copy(id = id)
        if (meta.apartado.esPositivo) aportes.value = aportes.value + (id to listOf(AporteMeta(fecha, meta.apartado)))
        return id
    }
    override suspend fun actualizarMeta(meta: MetaAhorro) {
        metas.value = metas.value.map { if (it.id == meta.id) meta else it }
    }
    override suspend fun eliminarMeta(id: MetaAhorroId) {
        metas.value = metas.value.filterNot { it.id == id }
    }
    val aportes = MutableStateFlow<Map<MetaAhorroId, List<AporteMeta>>>(emptyMap())
    override fun observarAportes(meta: MetaAhorroId): Flow<List<AporteMeta>> = aportes.map { it[meta].orEmpty() }
    override suspend fun moverApartado(meta: MetaAhorro, fecha: LocalDate, monto: Dinero) {
        actualizarMeta(meta)
        val id = meta.id!!
        aportes.value = aportes.value + (id to listOf(AporteMeta(fecha, monto)) + aportes.value[id].orEmpty())
    }
}
