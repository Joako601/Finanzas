package io.github.joako601.finanzas.ui.tarjetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.AnalisisTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorEstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.movimientos.textoEditable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
)

/** Diálogo de captura de estado de cuenta. */
data class CapturaEstadoDeCuenta(
    val tarjeta: Tarjeta,
    val fechaCorte: LocalDate,
    val fechaLimite: LocalDate,
    val pagoSinIntereses: String = "",
    val pagoMinimo: String = "",
    val errores: Set<ErrorEstadoDeCuenta> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/**
 * ViewModel de la sección de tarjetas. Convive en la misma pantalla con
 * MovimientosViewModel; cada uno se encarga de su parte y ambos leen la misma base,
 * así que un pago registrado aquí aparece solo en la lista de movimientos.
 */
class TarjetasViewModel(
    private val libro: RepositorioLibro,
    repositorioTarjetas: RepositorioTarjetas,
    private val servicio: ServicioTarjetas,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    /** La fecha de hoy, revisada cada minuto para recalcular si la app queda abierta tras la medianoche. */
    private val dias = flow {
        while (true) {
            emit(fechaDeHoy())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val estado: StateFlow<EstadoTarjetas> = combine(
        combine(libro.observarCuentas(), libro.observarSaldos(), libro.observarMovimientos(), ::Triple),
        combine(repositorioTarjetas.observarTarjetas(), repositorioTarjetas.observarEstadosDeCuenta(), dias, ::Triple),
    ) { (cuentas, saldos, movimientos), (tarjetas, estadosDeCuenta, hoy) ->
        val porId = cuentas.associateBy { it.id }
        val pagos = pagosATarjetas(movimientos, porId)
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

    /** Tasas anuales personalizadas en sesión por tarjeta (por defecto 60%). */
    private val tasasPorTarjeta = mutableMapOf<CuentaId, Int>()

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
        val tasa = tasasPorTarjeta[resumen.tarjeta.id] ?: AnalizadorTarjeta.TASA_ANUAL_REFERENCIA_MEXICO

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

    fun cerrarPago() {
        _pago.value = null
    }

    /** Selecciona explícitamente una cuenta recién creada desde "+ Agregar cuenta" mientras el diálogo de pago está abierto. */
    fun seleccionarCuentaPago(id: CuentaId) {
        _pago.update { actual ->
            actual?.copy(desdeId = id, errores = actual.errores - ErrorPago.SIN_CUENTA)
        }
    }

    fun editarPago(cambio: PagoTarjeta.() -> PagoTarjeta) =
        _pago.update { actual ->
            actual?.cambio()?.also { actualizado ->
                tasasPorTarjeta[actualizado.tarjeta.id] = actualizado.tasaAnualPorcentaje
            }?.copy(errores = emptySet(), errorAlGuardar = false)
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
                libro.registrar(Registro.pagoDeTarjeta(actual.fecha, monto!!, desde!!, actual.tarjeta.cuenta))
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
            pagoSinIntereses = estadoEditable?.pagoSinIntereses?.textoEditable().orEmpty(),
            pagoMinimo = estadoEditable?.pagoMinimo?.textoEditable().orEmpty(),
        )
    }

    fun cerrarCaptura() {
        _captura.value = null
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
                    actual.tarjeta, actual.fechaCorte, actual.fechaLimite, actual.pagoSinIntereses, actual.pagoMinimo,
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
