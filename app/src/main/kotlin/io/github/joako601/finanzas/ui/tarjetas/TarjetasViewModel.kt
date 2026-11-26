package io.github.joako601.finanzas.ui.tarjetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalisisTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorEstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import io.github.joako601.finanzas.core.domain.tarjetas.CompraAMeses
import io.github.joako601.finanzas.core.domain.tarjetas.comprasAMeses
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
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
import kotlinx.datetime.LocalDate

/** Una tarjeta con lo que la pantalla necesita mostrar de ella. */
data class ResumenTarjeta(
    val tarjeta: Tarjeta,
    val deuda: Dinero,
    val disponible: Dinero,
    val situacion: SituacionTarjeta,
    val analisis: AnalisisTarjeta,
    /** Compras a meses con mensualidades por facturar (D-022). */
    val compras: List<CompraAMeses> = emptyList(),
)

data class EstadoTarjetas(
    val tarjetas: List<ResumenTarjeta> = emptyList(),
    /** Cuentas desde las que se puede pagar una tarjeta (efectivo y débito). */
    val cuentasParaPagar: List<Cuenta> = emptyList(),
    /** Saldo disponible actual en cada cuenta de efectivo o débito. */
    val saldosCuentasParaPagar: Map<CuentaId, Dinero> = emptyMap(),
)

/** Diálogo de alta de tarjeta. Los campos son texto tal cual se escribe. */
data class NuevaTarjeta(
    val nombre: String = "",
    val linea: String = "",
    val diaCorte: String = "",
    val diaLimite: String = "",
    val deuda: String = "",
    val errores: Set<ErrorTarjeta> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/** Diálogo de edición de los parámetros de una tarjeta existente. */
data class EdicionTarjeta(
    val actual: Tarjeta,
    val nombre: String,
    val linea: String,
    val diaCorte: String,
    val diaLimite: String,
    val errores: Set<ErrorTarjeta> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
    /** El diálogo muestra la confirmación de archivar en lugar de los campos. */
    val confirmandoArchivar: Boolean = false,
)

enum class ErrorPago { MONTO_INVALIDO, SIN_CUENTA }

/** Diálogo de pago de tarjeta con análisis y simulación de intereses en vivo. */
data class PagoTarjeta(
    val tarjeta: Tarjeta,
    val montoTexto: String,
    val desdeId: CuentaId?,
    val fecha: LocalDate,
    val analisis: AnalisisTarjeta? = null,
    val tasaAnualPorcentaje: Int = AnalizadorTarjeta.TASA_ANUAL_REFERENCIA_MEXICO,
    val errores: Set<ErrorPago> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
    /** Pago ya guardado que se está corrigiendo; `null` si es un pago nuevo. */
    val editando: MovimientoId? = null,
    /** Nota del pago original, para no perderla al corregirlo. */
    val nota: String = "",
)

/** Diálogo de captura de estado de cuenta. */
data class CapturaEstadoDeCuenta(
    val tarjeta: Tarjeta,
    val fechaCorte: LocalDate,
    val fechaLimite: LocalDate,
    val fechaCorteOriginal: LocalDate? = null,
    val pagoSinIntereses: String = "",
    val pagoMinimo: String = "",
    val errores: Set<ErrorEstadoDeCuenta> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
    /** Al editar un estado guardado: el diálogo muestra la confirmación de eliminarlo. */
    val confirmandoEliminar: Boolean = false,
)

/**
 * ViewModel de la sección de tarjetas. Convive en la misma pantalla con
 * MovimientosViewModel; cada uno se encarga de su parte y ambos leen la misma base,
 * así que un pago registrado aquí aparece solo en la lista de movimientos.
 */
class TarjetasViewModel(
    private val libro: RepositorioLibro,
    private val repositorioTarjetas: RepositorioTarjetas,
    private val servicio: ServicioTarjetas,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    private val dias = diasDesde(fechaDeHoy)

    val estado: StateFlow<EstadoTarjetas> = combine(
        combine(libro.observarCuentas(), libro.observarSaldos(), libro.observarMovimientos(), ::Triple),
        combine(repositorioTarjetas.observarTarjetas(), repositorioTarjetas.observarEstadosDeCuenta(), dias, ::Triple),
    ) { (cuentas, saldos, movimientos), (tarjetas, estadosDeCuenta, hoy) ->
        val porId = cuentas.associateBy { it.id }
        val pagos = pagosATarjetas(movimientos, porId)
        val compras = comprasAMeses(movimientos, porId, tarjetas).filter { it.activaAl(hoy) }
        val cuentasActivas = cuentas.filter { it.tipo == TipoCuenta.ACTIVO && !it.archivada }
        val saldosActivas = cuentasActivas.associate { it.id to (saldos[it.id] ?: Dinero.CERO) }

        EstadoTarjetas(
            tarjetas = tarjetas.filterNot { it.cuenta.archivada }.map { tarjeta ->
                val deuda = deudaDe(tarjeta, saldos)
                val analisis = AnalizadorTarjeta.analizar(
                    tarjeta = tarjeta,
                    deuda = deuda,
                    estados = estadosDeCuenta,
                    pagos = pagos,
                    hoy = hoy,
                )
                ResumenTarjeta(
                    tarjeta = tarjeta,
                    deuda = deuda,
                    disponible = analisis.desglose.creditoDisponible,
                    situacion = analisis.situacion,
                    analisis = analisis,
                    compras = compras.filter { it.tarjeta.id == tarjeta.id },
                )
            },
            cuentasParaPagar = cuentasActivas,
            saldosCuentasParaPagar = saldosActivas,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoTarjetas())

    private val _nuevaTarjeta = MutableStateFlow<NuevaTarjeta?>(null)
    val nuevaTarjeta: StateFlow<NuevaTarjeta?> = _nuevaTarjeta.asStateFlow()

    private val _edicionTarjeta = MutableStateFlow<EdicionTarjeta?>(null)
    val edicionTarjeta: StateFlow<EdicionTarjeta?> = _edicionTarjeta.asStateFlow()

    private val _detalleTarjetaId = MutableStateFlow<CuentaId?>(null)
    val detalleTarjetaId: StateFlow<CuentaId?> = _detalleTarjetaId.asStateFlow()

    private val _pago = MutableStateFlow<PagoTarjeta?>(null)
    val pago: StateFlow<PagoTarjeta?> = _pago.asStateFlow()

    private val _captura = MutableStateFlow<CapturaEstadoDeCuenta?>(null)
    val captura: StateFlow<CapturaEstadoDeCuenta?> = _captura.asStateFlow()

    // ---- Detalle y Edición ----

    fun abrirDetalle(resumen: ResumenTarjeta) {
        _detalleTarjetaId.value = resumen.tarjeta.id
    }

    fun cerrarDetalle() {
        _detalleTarjetaId.value = null
    }

    fun abrirEdicionTarjeta(tarjeta: Tarjeta) {
        _edicionTarjeta.value = EdicionTarjeta(
            actual = tarjeta,
            nombre = tarjeta.cuenta.nombre,
            linea = tarjeta.lineaDeCredito.textoEditable(),
            diaCorte = tarjeta.diaDeCorte.toString(),
            diaLimite = tarjeta.diaLimite.toString(),
        )
    }

    fun cerrarEdicionTarjeta() {
        _edicionTarjeta.value = null
    }

    fun pedirArchivarTarjeta() = _edicionTarjeta.update {
        if (it == null || it.guardando) it else it.copy(confirmandoArchivar = true, errorAlGuardar = false)
    }

    fun cancelarArchivarTarjeta() = _edicionTarjeta.update { it?.copy(confirmandoArchivar = false) }

    /** Archiva la tarjeta en edición: sale de Inicio, del registro y de los avisos; su historial queda. */
    fun archivarTarjeta() {
        val actual = _edicionTarjeta.value ?: return
        if (actual.guardando || !actual.confirmandoArchivar) return
        _edicionTarjeta.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                libro.cambiarArchivada(actual.actual.cuenta.id, archivada = true)
                _edicionTarjeta.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _edicionTarjeta.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    fun editarTarjetaExistente(cambio: EdicionTarjeta.() -> EdicionTarjeta) =
        _edicionTarjeta.update { it?.cambio()?.copy(errores = emptySet(), errorAlGuardar = false) }

    fun guardarEdicionTarjeta() {
        val actual = _edicionTarjeta.value ?: return
        if (actual.guardando) return
        val errores = servicio.validarEdicion(actual.nombre, actual.linea, actual.diaCorte, actual.diaLimite)
        if (errores.isNotEmpty()) {
            _edicionTarjeta.value = actual.copy(errores = errores)
            return
        }
        _edicionTarjeta.value = actual.copy(guardando = true)
        viewModelScope.launch {
            try {
                servicio.actualizarTarjeta(
                    actual = actual.actual,
                    nombre = actual.nombre,
                    lineaDeCredito = Dinero.parsear(actual.linea)!!,
                    diaDeCorte = actual.diaCorte.trim().toInt(),
                    diaLimite = actual.diaLimite.trim().toInt(),
                )
                _edicionTarjeta.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _edicionTarjeta.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    // ---- Alta ----

    fun abrirNuevaTarjeta() {
        _nuevaTarjeta.value = NuevaTarjeta()
    }

    fun cerrarNuevaTarjeta() {
        _nuevaTarjeta.value = null
    }

    /** Cambia un campo; los errores se limpian para no regañar mientras se corrige. */
    fun editarNuevaTarjeta(cambio: NuevaTarjeta.() -> NuevaTarjeta) =
        _nuevaTarjeta.update { it?.cambio()?.copy(errores = emptySet(), errorAlGuardar = false) }

    fun guardarNuevaTarjeta() {
        val actual = _nuevaTarjeta.value ?: return
        if (actual.guardando) return
        val errores = servicio.validarAlta(actual.nombre, actual.linea, actual.diaCorte, actual.diaLimite, actual.deuda)
        if (errores.isNotEmpty()) {
            _nuevaTarjeta.value = actual.copy(errores = errores)
            return
        }
        _nuevaTarjeta.value = actual.copy(guardando = true)
        viewModelScope.launch {
            try {
                servicio.crearTarjeta(
                    nombre = actual.nombre,
                    lineaDeCredito = Dinero.parsear(actual.linea)!!,
                    diaDeCorte = actual.diaCorte.trim().toInt(),
                    diaLimite = actual.diaLimite.trim().toInt(),
                    deudaActual = if (actual.deuda.isBlank()) Dinero.CERO else Dinero.parsear(actual.deuda)!!,
                    hoy = fechaDeHoy(),
                )
                _nuevaTarjeta.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _nuevaTarjeta.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    // ---- Pago ----

    /**
     * Abre el pago. Sugiere [montoSugerido] si se indica (por ejemplo, desde la línea de
     * tiempo: un estado vencido o el estimado sin estado de cuenta); si no, lo que falta
     * para no generar intereses del estado vigente o vencido, o la deuda total si está en ciclo.
     */
    fun abrirPago(resumen: ResumenTarjeta, montoSugerido: Dinero? = null) {
        val exigibleCiclo = resumen.analisis.progresoCiclo?.restanteSinIntereses
        val sugerido = montoSugerido
            ?: exigibleCiclo?.takeIf { it.esPositivo }
            ?: resumen.analisis.desglose.deudaTotal.takeIf { it.esPositivo }
        val tasa = resumen.tarjeta.tasaAnual ?: AnalizadorTarjeta.TASA_ANUAL_REFERENCIA_MEXICO

        // Preseleccionar la primera cuenta cuyo saldo cubra el pago, o la primera cuenta disponible.
        val cuentas = estado.value.cuentasParaPagar
        val saldos = estado.value.saldosCuentasParaPagar
        val cuentaPreferida = if (sugerido != null) {
            cuentas.firstOrNull { (saldos[it.id] ?: Dinero.CERO) >= sugerido } ?: cuentas.firstOrNull()
        } else {
            cuentas.firstOrNull()
        }

        _pago.value = PagoTarjeta(
            tarjeta = resumen.tarjeta,
            montoTexto = sugerido?.textoEditable().orEmpty(),
            desdeId = cuentaPreferida?.id,
            fecha = fechaDeHoy(),
            analisis = resumen.analisis,
            tasaAnualPorcentaje = tasa,
        )
    }

    /**
     * Abre un pago a tarjeta ya registrado para corregir monto, cuenta o fecha. Sin el análisis
     * de la tarjeta: la deuda actual ya descuenta este pago y sus sugerencias confundirían.
     */
    fun abrirEdicionPago(resumen: MovimientoResumido) {
        val id = resumen.movimiento.id ?: return
        if (resumen.clase != ClaseMovimiento.PAGO_TARJETA) return
        val tarjeta = estado.value.tarjetas.firstOrNull { it.tarjeta.cuenta.id == resumen.contraparte?.id }?.tarjeta ?: return
        _pago.value = PagoTarjeta(
            tarjeta = tarjeta,
            montoTexto = resumen.monto.textoEditable(),
            desdeId = resumen.cuenta?.id,
            fecha = resumen.movimiento.fecha,
            editando = id,
            nota = resumen.movimiento.descripcion,
        )
    }

    fun cerrarPago() {
        _pago.value = null
    }

    /** Selecciona explícitamente una cuenta recién creada desde "+ Agregar cuenta" mientras el diálogo de pago está abierto. */
    fun seleccionarCuentaPago(id: CuentaId) {
        _pago.update { actual ->
            actual?.copy(desdeId = id, errores = actual.errores - ErrorPago.SIN_CUENTA)
        }
    }

    fun editarPago(cambio: PagoTarjeta.() -> PagoTarjeta) {
        val antes = _pago.value ?: return
        val despues = antes.cambio().copy(errores = emptySet(), errorAlGuardar = false)
        _pago.value = despues
        // La tasa elegida queda guardada en la tarjeta (v7) para la próxima simulación.
        if (despues.tasaAnualPorcentaje != antes.tasaAnualPorcentaje) {
            viewModelScope.launch {
                try {
                    repositorioTarjetas.guardarTasaAnual(despues.tarjeta.id, despues.tasaAnualPorcentaje)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin dinero de por medio: la simulación sigue con la tasa elegida; solo no se recuerda.
                }
            }
        }
    }

    fun guardarPago() {
        val actual = _pago.value ?: return
        if (actual.guardando) return
        val monto = Dinero.parsear(actual.montoTexto)?.takeIf { it.esPositivo }
        val desde = estado.value.cuentasParaPagar.firstOrNull { it.id == actual.desdeId }
        val errores = buildSet {
            if (monto == null) add(ErrorPago.MONTO_INVALIDO)
            if (desde == null) add(ErrorPago.SIN_CUENTA)
        }
        if (errores.isNotEmpty()) {
            _pago.value = actual.copy(errores = errores)
            return
        }
        _pago.value = actual.copy(guardando = true)
        viewModelScope.launch {
            try {
                val movimiento = Registro.pagoDeTarjeta(actual.fecha, monto!!, desde!!, actual.tarjeta.cuenta, actual.nota)
                if (actual.editando == null) libro.registrar(movimiento) else libro.actualizar(movimiento.copy(id = actual.editando))
                _pago.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pago.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    // ---- Estado de cuenta ----

    fun abrirCaptura(resumen: ResumenTarjeta) {
        val hoy = fechaDeHoy()
        val ultimoCorte = CalendarioTarjeta.ultimoCorteHasta(resumen.tarjeta, hoy)
        val estadoEditable = when (val s = resumen.situacion) {
            is SituacionTarjeta.CapturaPendiente -> null
            is SituacionTarjeta.PagoPendiente -> s.estado
            is SituacionTarjeta.Pagado -> s.estado
            is SituacionTarjeta.EnCiclo -> resumen.analisis.vencidoPendiente?.estado
                ?.takeIf { it.fechaCorte >= ultimoCorte }
        }
        val (corte, limite) = when (val s = resumen.situacion) {
            is SituacionTarjeta.CapturaPendiente -> s.cortePredicho to s.limitePredicho
            else -> if (estadoEditable != null) {
                estadoEditable.fechaCorte to estadoEditable.fechaLimite
            } else {
                ultimoCorte to CalendarioTarjeta.limiteParaCorte(resumen.tarjeta, ultimoCorte)
            }
        }
        _captura.value = CapturaEstadoDeCuenta(
            tarjeta = resumen.tarjeta,
            fechaCorte = corte,
            fechaLimite = limite,
            fechaCorteOriginal = estadoEditable?.fechaCorte,
            pagoSinIntereses = estadoEditable?.pagoSinIntereses?.textoEditable().orEmpty(),
            pagoMinimo = estadoEditable?.pagoMinimo?.textoEditable().orEmpty(),
        )
    }

    /** Corregir un estado de cuenta ya capturado, aunque no sea el último (desde el historial). */
    fun abrirEdicionEstado(tarjeta: Tarjeta, estado: EstadoDeCuenta) {
        _captura.value = CapturaEstadoDeCuenta(
            tarjeta = tarjeta,
            fechaCorte = estado.fechaCorte,
            fechaLimite = estado.fechaLimite,
            fechaCorteOriginal = estado.fechaCorte,
            pagoSinIntereses = estado.pagoSinIntereses.textoEditable(),
            pagoMinimo = estado.pagoMinimo.textoEditable(),
        )
    }

    fun cerrarCaptura() {
        _captura.value = null
    }

    fun pedirEliminarEstado() = _captura.update {
        if (it?.fechaCorteOriginal == null || it.guardando) it else it.copy(confirmandoEliminar = true, errorAlGuardar = false)
    }

    fun cancelarEliminarEstado() = _captura.update { it?.copy(confirmandoEliminar = false) }

    /** Borra un estado de cuenta capturado por error. Compras y pagos no cambian. */
    fun eliminarEstado() {
        val actual = _captura.value ?: return
        val corte = actual.fechaCorteOriginal ?: return
        if (actual.guardando || !actual.confirmandoEliminar) return
        _captura.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                repositorioTarjetas.eliminarEstadoDeCuenta(actual.tarjeta.id, corte)
                _captura.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _captura.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    fun editarCaptura(cambio: CapturaEstadoDeCuenta.() -> CapturaEstadoDeCuenta) =
        _captura.update { it?.cambio()?.copy(errores = emptySet(), errorAlGuardar = false) }

    fun guardarCaptura() {
        val actual = _captura.value ?: return
        if (actual.guardando) return
        _captura.value = actual.copy(guardando = true)
        viewModelScope.launch {
            try {
                val errores = servicio.capturarEstadoDeCuenta(
                    tarjeta = actual.tarjeta,
                    fechaCorte = actual.fechaCorte,
                    fechaLimite = actual.fechaLimite,
                    pagoSinInteresesTexto = actual.pagoSinIntereses,
                    pagoMinimoTexto = actual.pagoMinimo,
                    fechaCorteAnterior = actual.fechaCorteOriginal,
                )
                _captura.value = if (errores.isEmpty()) null else actual.copy(errores = errores)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _captura.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    companion object {
        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val contenedor = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp).contenedor
                TarjetasViewModel(
                    libro = contenedor.repositorioLibro,
                    repositorioTarjetas = contenedor.repositorioTarjetas,
                    servicio = contenedor.servicioTarjetas,
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
