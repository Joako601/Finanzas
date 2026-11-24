package io.github.joako601.finanzas.ui.metas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.metas.AnalisisMetaAhorro
import io.github.joako601.finanzas.core.domain.metas.AnalisisTope
import io.github.joako601.finanzas.core.domain.metas.AnalizadorMetas
import io.github.joako601.finanzas.core.domain.metas.AnalizadorTopes
import io.github.joako601.finanzas.core.domain.metas.ErrorMeta
import io.github.joako601.finanzas.core.domain.metas.ErrorMovimientoMeta
import io.github.joako601.finanzas.core.domain.metas.ErrorTope
import io.github.joako601.finanzas.core.domain.metas.MetaAhorro
import io.github.joako601.finanzas.core.domain.metas.MetaAhorroId
import io.github.joako601.finanzas.core.domain.metas.RepositorioMetas
import io.github.joako601.finanzas.core.domain.metas.ResumenMetasAhorro
import io.github.joako601.finanzas.core.domain.metas.ResumenTopesMes
import io.github.joako601.finanzas.core.domain.metas.ServicioMetas
import io.github.joako601.finanzas.core.domain.metas.SugerenciaTope
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import io.github.joako601.finanzas.ui.componentes.limpiarMonto
import io.github.joako601.finanzas.ui.diasDesde
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.movimientos.textoEditable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Sub-pestañas dentro del apartado `Metas`. */
enum class SubPestanaMetas {
    TOPES,
    AHORRO,
}

data class EstadoMetas(
    val cargando: Boolean = true,
    val resumenTopes: ResumenTopesMes? = null,
    val resumenAhorro: ResumenMetasAhorro? = null,
    /** Categorías activas de gasto que aún no tienen un tope configurado, ordenadas por gasto reciente. */
    val categoriasSinTope: List<Cuenta> = emptyList(),
    /** Sugerencias de límite basadas en el historial real de gasto por categoría. */
    val sugerenciasPorCategoria: Map<CuentaId, SugerenciaTope> = emptyMap(),
)

/** Diálogo abierto para crear o editar un tope mensual de categoría. */
data class EditorTope(
    val categoriaId: CuentaId?,
    val montoTexto: String = "",
    /** Categoría fija al editar un tope existente (bloquea el cambio de categoría incluso si fue archivada). */
    val categoriaEditada: Cuenta? = null,
    /** `true` en cuanto el usuario edita el monto manualmente; si es `false`, cambiar categoría actualiza la sugerencia. */
    val montoEditadoManualmente: Boolean = false,
    val errores: Set<ErrorTope> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
) {
    val editando: Boolean get() = categoriaEditada != null
}

/** Diálogo de confirmación para eliminar un tope mensual con estado de envío y error. */
data class EliminacionTope(
    val analisis: AnalisisTope,
    val eliminando: Boolean = false,
    val errorAlEliminar: Boolean = false,
)

/** Diálogo abierto para crear o editar una meta / apartado de ahorro. */
data class EditorMeta(
    val nombre: String = "",
    val objetivoTexto: String = "",
    val apartadoInicialTexto: String = "",
    val tieneFechaLimite: Boolean = false,
    val fechaLimite: LocalDate,
    val reservarDeDisponible: Boolean = true,
    val editando: MetaAhorro? = null,
    val errores: Set<ErrorMeta> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

enum class TipoMovimientoMeta { APORTAR, RETIRAR }

/** Diálogo rápido para aportar o retirar/usar dinero de un apartado de ahorro. */
data class MovimientoMetaDialogo(
    val analisis: AnalisisMetaAhorro,
    val tipo: TipoMovimientoMeta,
    val montoTexto: String = "",
    val archivarAlVaciar: Boolean = true,
    val error: ErrorMovimientoMeta? = null,
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/** Diálogo de confirmación para eliminar una meta de ahorro. */
data class EliminacionMeta(
    val meta: MetaAhorro,
    val eliminando: Boolean = false,
    val errorAlEliminar: Boolean = false,
)

/** Estado de una acción directa dentro de la hoja de detalle de una meta (archivar / reactivar). */
data class AccionDetalleMeta(
    val procesando: Boolean = false,
    val error: Boolean = false,
)

/**
 * ViewModel del apartado `Metas` (Topes mensuales + Apartados de ahorro).
 *
 * Combina en `Dispatchers.Default` (100% puro, sin mutaciones laterales) el libro, los topes,
 * las metas de ahorro y un ticker de medianoche.
 */
class MetasViewModel(
    libro: RepositorioLibro,
    tarjetas: RepositorioTarjetas,
    recurrentes: RepositorioRecurrentes,
    repositorioMetas: RepositorioMetas,
    private val servicio: ServicioMetas,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    private val dias = diasDesde(fechaDeHoy)

    val estado: StateFlow<EstadoMetas> = combine(
        combine(libro.observarCuentas(), libro.observarSaldos(), libro.observarMovimientos(), ::Triple),
        combine(tarjetas.observarTarjetas(), tarjetas.observarEstadosDeCuenta(), recurrentes.observarRecurrentes(), ::Triple),
        combine(repositorioMetas.observarTopes(), repositorioMetas.observarMetas(), dias, ::Triple),
    ) { (cuentas, saldos, movimientos), (listaTarjetas, estados, listaRecurrentes), (topes, metas, hoy) ->
        val porId = cuentas.associateBy { it.id }
        val linea = LineaDeTiempo.calcular(
            cuentas = cuentas,
            saldos = saldos,
            tarjetas = listaTarjetas,
            estados = estados,
            pagos = pagosATarjetas(movimientos, porId),
            recurrentes = listaRecurrentes,
            hoy = hoy,
        )
        val resumenTopes = AnalizadorTopes.analizar(
            topes = topes,
            cuentas = cuentas,
            movimientos = movimientos,
            hoy = hoy,
        )
        val resumenAhorro = AnalizadorMetas.analizar(
            metas = metas,
            cuentas = cuentas,
            saldos = saldos,
            hoy = hoy,
            disponibleReal = linea.disponible,
        )
        val sugerencias = AnalizadorTopes.sugerenciasPorCategoria(
            cuentas = cuentas,
            movimientos = movimientos,
            hoy = hoy,
        )
        val idsConTope = topes.mapTo(HashSet()) { it.categoriaId }
        val sinTope = cuentas
            .filter { it.tipo == TipoCuenta.GASTO && !it.archivada && it.id !in idsConTope }
            .sortedWith(
                compareByDescending<Cuenta> { sugerencias[it.id]?.promedioMensual?.centavos ?: 0L }
                    .thenBy { it.nombre },
            )

        EstadoMetas(
            cargando = false,
            resumenTopes = resumenTopes,
            resumenAhorro = resumenAhorro,
            categoriasSinTope = sinTope,
            sugerenciasPorCategoria = sugerencias,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMetas())

    // ---- Flujos de Topes ----

    private val _editorTope = MutableStateFlow<EditorTope?>(null)
    val editorTope: StateFlow<EditorTope?> = _editorTope.asStateFlow()

    private val _detalleTopeId = MutableStateFlow<CuentaId?>(null)
    val detalleTopeId: StateFlow<CuentaId?> = _detalleTopeId.asStateFlow()

    private val _topePorEliminar = MutableStateFlow<EliminacionTope?>(null)
    val topePorEliminar: StateFlow<EliminacionTope?> = _topePorEliminar.asStateFlow()

    // ---- Flujos de Ahorro ----

    private val _editorMeta = MutableStateFlow<EditorMeta?>(null)
    val editorMeta: StateFlow<EditorMeta?> = _editorMeta.asStateFlow()

    private val _movimientoMeta = MutableStateFlow<MovimientoMetaDialogo?>(null)
    val movimientoMeta: StateFlow<MovimientoMetaDialogo?> = _movimientoMeta.asStateFlow()

    private val _detalleMetaId = MutableStateFlow<MetaAhorroId?>(null)
    val detalleMetaId: StateFlow<MetaAhorroId?> = _detalleMetaId.asStateFlow()

    private val _accionDetalleMeta = MutableStateFlow(AccionDetalleMeta())
    val accionDetalleMeta: StateFlow<AccionDetalleMeta> = _accionDetalleMeta.asStateFlow()

    private val _metaPorEliminar = MutableStateFlow<EliminacionMeta?>(null)
    val metaPorEliminar: StateFlow<EliminacionMeta?> = _metaPorEliminar.asStateFlow()

    // ---- Acciones de Topes mensuales ----

    fun abrirNuevoTope(categoriaPreseleccionada: CuentaId? = null) {
        val actualEstado = estado.value
        if (actualEstado.categoriasSinTope.isEmpty()) return
        val elegida = categoriaPreseleccionada
            ?.takeIf { id -> actualEstado.categoriasSinTope.any { it.id == id } }
            ?: actualEstado.categoriasSinTope.first().id
        val sugerido = actualEstado.sugerenciasPorCategoria[elegida]?.montoSugeridoRedondeado
        _editorTope.value = EditorTope(
            categoriaId = elegida,
            montoTexto = sugerido?.textoEditable().orEmpty(),
            categoriaEditada = null,
            montoEditadoManualmente = false,
        )
    }

    fun abrirEditarTope(analisis: AnalisisTope) {
        _detalleTopeId.value = null
        _editorTope.value = EditorTope(
            categoriaId = analisis.categoria.id,
            montoTexto = analisis.tope.limiteMensual.textoEditable(),
            categoriaEditada = analisis.categoria,
            montoEditadoManualmente = true,
        )
    }

    fun cerrarEditorTope() {
        if (_editorTope.value?.guardando == true) return
        _editorTope.value = null
    }

    fun elegirCategoriaTope(categoriaId: CuentaId) {
        val sugerido = estado.value.sugerenciasPorCategoria[categoriaId]?.montoSugeridoRedondeado
        _editorTope.update { actual ->
            if (actual == null || actual.editando || actual.guardando) {
                actual
            } else {
                val nuevoMonto = if (actual.montoEditadoManualmente) {
                    actual.montoTexto
                } else {
                    sugerido?.textoEditable().orEmpty()
                }
                actual.copy(
                    categoriaId = categoriaId,
                    montoTexto = nuevoMonto,
                    errores = actual.errores - ErrorTope.SIN_CATEGORIA - ErrorTope.CATEGORIA_NO_ES_GASTO,
                    errorAlGuardar = false,
                )
            }
        }
    }

    fun cambiarMontoTope(texto: String) {
        _editorTope.update { actual ->
            if (actual == null || actual.guardando) {
                actual
            } else {
                actual.copy(
                    montoTexto = limpiarMonto(texto),
                    montoEditadoManualmente = true,
                    errores = actual.errores - ErrorTope.MONTO_INVALIDO,
                    errorAlGuardar = false,
                )
            }
        }
    }

    fun aplicarSugerenciaTope(monto: Dinero) {
        cambiarMontoTope(monto.textoEditable())
    }

    fun guardarTope() {
        val actual = _editorTope.value ?: return
        if (actual.guardando) return
        val categoria = actual.categoriaEditada
            ?: estado.value.categoriasSinTope.firstOrNull { it.id == actual.categoriaId }
        val errores = servicio.validarTope(categoria, actual.montoTexto)
        if (errores.isNotEmpty()) {
            _editorTope.value = actual.copy(errores = errores)
            return
        }
        val limite = Dinero.parsear(actual.montoTexto)!!
        val categoriaValida = categoria!!
        _editorTope.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                servicio.guardarTope(categoriaValida, limite)
                _editorTope.update { vigente ->
                    if (vigente?.guardando == true && vigente.categoriaId == categoriaValida.id) null else vigente
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editorTope.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    fun abrirDetalleTope(categoriaId: CuentaId) {
        _detalleTopeId.value = categoriaId
    }

    fun cerrarDetalleTope() {
        _detalleTopeId.value = null
    }

    fun pedirEliminarTope(analisis: AnalisisTope) {
        _topePorEliminar.value = EliminacionTope(analisis)
    }

    fun cancelarEliminarTope() {
        if (_topePorEliminar.value?.eliminando == true) return
        _topePorEliminar.value = null
    }

    fun confirmarEliminarTope() {
        val actual = _topePorEliminar.value ?: return
        if (actual.eliminando) return
        _topePorEliminar.value = actual.copy(eliminando = true, errorAlEliminar = false)
        viewModelScope.launch {
            try {
                servicio.eliminarTope(actual.analisis.categoria.id)
                _topePorEliminar.value = null
                _detalleTopeId.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _topePorEliminar.update { it?.copy(eliminando = false, errorAlEliminar = true) }
            }
        }
    }

    // ---- Acciones de Apartados / Metas de ahorro ----

    private fun analisisMetaActual(id: MetaAhorroId?): AnalisisMetaAhorro? {
        if (id == null) return null
        val resumen = estado.value.resumenAhorro ?: return null
        return (resumen.metasActivas + resumen.metasArchivadas).firstOrNull { it.meta.id == id }
    }

    fun abrirNuevaMeta(
        nombreInicial: String = "",
        objetivoInicial: Dinero? = null,
        mesesSugeridos: Int? = null,
    ) {
        if (_editorMeta.value?.guardando == true) return
        val hoy = fechaDeHoy()
        val fechaPorDefecto = hoy.plus(mesesSugeridos ?: 6, DateTimeUnit.MONTH)
        _editorMeta.value = EditorMeta(
            nombre = nombreInicial.take(MetaAhorro.LARGO_MAXIMO_NOMBRE),
            objetivoTexto = objetivoInicial?.textoEditable().orEmpty(),
            tieneFechaLimite = mesesSugeridos != null,
            fechaLimite = fechaPorDefecto,
            reservarDeDisponible = true,
        )
    }

    fun abrirEditarMeta(meta: MetaAhorro) {
        if (_editorMeta.value?.guardando == true) return
        val hoy = fechaDeHoy()
        val vigente = analisisMetaActual(meta.id)?.meta ?: meta
        _detalleMetaId.value = null
        _accionDetalleMeta.value = AccionDetalleMeta()
        _editorMeta.value = EditorMeta(
            nombre = vigente.nombre,
            objetivoTexto = vigente.objetivo.textoEditable(),
            apartadoInicialTexto = vigente.apartado.textoEditable(),
            tieneFechaLimite = vigente.fechaLimite != null,
            fechaLimite = vigente.fechaLimite ?: hoy.plus(6, DateTimeUnit.MONTH),
            reservarDeDisponible = vigente.reservarDeDisponible,
            editando = vigente,
        )
    }

    fun cerrarEditorMeta() {
        if (_editorMeta.value?.guardando == true) return
        _editorMeta.value = null
    }

    fun editarMeta(cambio: EditorMeta.() -> EditorMeta) {
        _editorMeta.update { actual ->
            if (actual == null || actual.guardando) {
                actual
            } else {
                val nueva = actual.cambio()
                nueva.copy(
                    nombre = nueva.nombre.take(MetaAhorro.LARGO_MAXIMO_NOMBRE),
                    objetivoTexto = limpiarMonto(nueva.objetivoTexto),
                    apartadoInicialTexto = limpiarMonto(nueva.apartadoInicialTexto),
                    errores = emptySet(),
                    errorAlGuardar = false,
                )
            }
        }
    }

    fun guardarMeta() {
        val actual = _editorMeta.value ?: return
        if (actual.guardando) return
        val editando = actual.editando
        val errores = servicio.validarMeta(
            nombre = actual.nombre,
            objetivoTexto = actual.objetivoTexto,
            apartadoTexto = if (editando == null) actual.apartadoInicialTexto else "",
        )
        if (errores.isNotEmpty()) {
            _editorMeta.value = actual.copy(errores = errores)
            return
        }
        val objetivo = Dinero.parsear(actual.objetivoTexto)!!
        val fecha = if (actual.tieneFechaLimite) actual.fechaLimite else null
        val idEditado = editando?.id
        _editorMeta.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                if (editando == null) {
                    val inicial = if (actual.apartadoInicialTexto.isBlank()) {
                        Dinero.CERO
                    } else {
                        Dinero.parsear(actual.apartadoInicialTexto)!!
                    }
                    servicio.crearMeta(
                        nombre = actual.nombre,
                        objetivo = objetivo,
                        apartadoInicial = inicial,
                        fechaLimite = fecha,
                        reservarDeDisponible = actual.reservarDeDisponible,
                    )
                } else {
                    // Leer el registro vivo desde estado para nunca sobrescribir apartado/archivada con datos viejos.
                    val base = analisisMetaActual(idEditado)?.meta ?: editando
                    servicio.actualizarMeta(
                        base.copy(
                            nombre = actual.nombre,
                            objetivo = objetivo,
                            fechaLimite = fecha,
                            reservarDeDisponible = actual.reservarDeDisponible,
                        ),
                    )
                }
                _editorMeta.update { vigente ->
                    if (vigente?.guardando == true && vigente.editando?.id == idEditado) null else vigente
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editorMeta.update { vigente ->
                    if (vigente?.guardando == true && vigente.editando?.id == idEditado) {
                        vigente.copy(guardando = false, errorAlGuardar = true)
                    } else {
                        vigente
                    }
                }
            }
        }
    }

    fun abrirMovimientoMeta(analisis: AnalisisMetaAhorro, tipo: TipoMovimientoMeta) {
        if (_movimientoMeta.value?.guardando == true) return
        val vigente = analisisMetaActual(analisis.meta.id) ?: analisis
        if (tipo == TipoMovimientoMeta.RETIRAR && !vigente.meta.apartado.esPositivo) return
        _detalleMetaId.value = null
        _accionDetalleMeta.value = AccionDetalleMeta()
        _movimientoMeta.value = MovimientoMetaDialogo(
            analisis = vigente,
            tipo = tipo,
            montoTexto = "",
            archivarAlVaciar = !vigente.meta.archivada,
        )
    }

    fun cerrarMovimientoMeta() {
        if (_movimientoMeta.value?.guardando == true) return
        _movimientoMeta.value = null
    }

    fun cambiarMontoMovimientoMeta(texto: String) {
        _movimientoMeta.update { actual ->
            if (actual == null || actual.guardando) {
                actual
            } else {
                actual.copy(montoTexto = limpiarMonto(texto), error = null, errorAlGuardar = false)
            }
        }
    }

    /** Suma [delta] al monto escrito en el diálogo (hace honor a los chips `+$100 / +$500 / +$1,000`). */
    fun sumarMontoMovimientoMeta(delta: Dinero) {
        _movimientoMeta.update { actual ->
            if (actual == null || actual.guardando) {
                actual
            } else {
                val base = Dinero.parsear(actual.montoTexto) ?: Dinero.CERO
                val nuevo = try {
                    base + delta
                } catch (_: ArithmeticException) {
                    base
                }
                actual.copy(montoTexto = nuevo.textoEditable(), error = null, errorAlGuardar = false)
            }
        }
    }

    fun cambiarArchivarAlVaciar(archivar: Boolean) {
        _movimientoMeta.update { actual ->
            if (actual == null || actual.guardando) actual else actual.copy(archivarAlVaciar = archivar)
        }
    }

    fun confirmarMovimientoMeta() {
        val actual = _movimientoMeta.value ?: return
        if (actual.guardando) return
        val metaId = actual.analisis.meta.id
        // Resolver siempre el estado vivo de la meta para validar contra el apartado real actual.
        val vigente = analisisMetaActual(metaId) ?: actual.analisis
        val maximo = if (actual.tipo == TipoMovimientoMeta.RETIRAR) vigente.meta.apartado else null
        val apartadoParaAporte = if (actual.tipo == TipoMovimientoMeta.APORTAR) vigente.meta.apartado else null
        val error = servicio.validarMovimientoMeta(
            montoTexto = actual.montoTexto,
            maximoPermitido = maximo,
            apartadoActualParaAporte = apartadoParaAporte,
        )
        if (error != null) {
            _movimientoMeta.value = actual.copy(analisis = vigente, error = error)
            return
        }
        val monto = Dinero.parsear(actual.montoTexto)!!
        _movimientoMeta.value = actual.copy(analisis = vigente, guardando = true, error = null, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                when (actual.tipo) {
                    TipoMovimientoMeta.APORTAR -> servicio.aportarAMeta(vigente.meta, monto)
                    TipoMovimientoMeta.RETIRAR -> servicio.retirarDeMeta(
                        meta = vigente.meta,
                        monto = monto,
                        archivarSiQuedaEnCero = !vigente.meta.archivada && actual.archivarAlVaciar,
                    )
                }
                _movimientoMeta.update { dialog ->
                    if (dialog?.guardando == true && dialog.analisis.meta.id == metaId) null else dialog
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _movimientoMeta.update { dialog ->
                    if (dialog?.guardando == true && dialog.analisis.meta.id == metaId) {
                        dialog.copy(guardando = false, errorAlGuardar = true)
                    } else {
                        dialog
                    }
                }
            }
        }
    }

    fun abrirDetalleMeta(id: MetaAhorroId) {
        _accionDetalleMeta.value = AccionDetalleMeta()
        _detalleMetaId.value = id
    }

    fun cerrarDetalleMeta() {
        if (_accionDetalleMeta.value.procesando) return
        _detalleMetaId.value = null
        _accionDetalleMeta.value = AccionDetalleMeta()
    }

    fun alternarArchivarMeta(meta: MetaAhorro) {
        if (_accionDetalleMeta.value.procesando) return
        val id = meta.id ?: return
        val vigente = analisisMetaActual(id)?.meta ?: meta
        _accionDetalleMeta.value = AccionDetalleMeta(procesando = true, error = false)
        viewModelScope.launch {
            try {
                servicio.actualizarMeta(vigente.copy(archivada = !vigente.archivada))
                _accionDetalleMeta.value = AccionDetalleMeta()
                _detalleMetaId.update { actual -> if (actual == id) null else actual }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _accionDetalleMeta.value = AccionDetalleMeta(procesando = false, error = true)
            }
        }
    }

    fun pedirEliminarMeta(meta: MetaAhorro) {
        if (_metaPorEliminar.value?.eliminando == true || _accionDetalleMeta.value.procesando) return
        _metaPorEliminar.value = EliminacionMeta(meta)
    }

    fun cancelarEliminarMeta() {
        if (_metaPorEliminar.value?.eliminando == true) return
        _metaPorEliminar.value = null
    }

    fun confirmarEliminarMeta() {
        val actual = _metaPorEliminar.value ?: return
        val id = actual.meta.id ?: return
        if (actual.eliminando) return
        _metaPorEliminar.value = actual.copy(eliminando = true, errorAlEliminar = false)
        viewModelScope.launch {
            try {
                servicio.eliminarMeta(id)
                _metaPorEliminar.update { dialog ->
                    if (dialog?.eliminando == true && dialog.meta.id == id) null else dialog
                }
                _detalleMetaId.update { abierto -> if (abierto == id) null else abierto }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _metaPorEliminar.update { dialog ->
                    if (dialog?.eliminando == true && dialog.meta.id == id) {
                        dialog.copy(eliminando = false, errorAlEliminar = true)
                    } else {
                        dialog
                    }
                }
            }
        }
    }

    companion object {
        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val contenedor = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp).contenedor
                MetasViewModel(
                    libro = contenedor.repositorioLibro,
                    tarjetas = contenedor.repositorioTarjetas,
                    recurrentes = contenedor.repositorioRecurrentes,
                    repositorioMetas = contenedor.repositorioMetas,
                    servicio = contenedor.servicioMetas,
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
