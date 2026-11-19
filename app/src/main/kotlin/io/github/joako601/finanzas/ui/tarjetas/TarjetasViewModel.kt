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
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorEstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.deudaDe
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.situacionTarjeta
import io.github.joako601.finanzas.ui.hoy
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
)

data class EstadoTarjetas(
    val tarjetas: List<ResumenTarjeta> = emptyList(),
    /** Cuentas desde las que se puede pagar una tarjeta (efectivo y débito). */
    val cuentasParaPagar: List<Cuenta> = emptyList(),
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

enum class ErrorPago { MONTO_INVALIDO, SIN_CUENTA }

/** Diálogo de pago de tarjeta. */
data class PagoTarjeta(
    val tarjeta: Tarjeta,
    val montoTexto: String,
    val desdeId: CuentaId?,
    val fecha: LocalDate,
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

    val estado: StateFlow<EstadoTarjetas> = combine(
        libro.observarCuentas(),
        libro.observarSaldos(),
        libro.observarMovimientos(),
        repositorioTarjetas.observarTarjetas(),
        repositorioTarjetas.observarEstadosDeCuenta(),
    ) { cuentas, saldos, movimientos, tarjetas, estadosDeCuenta ->
        val hoy = fechaDeHoy()
        val pagos = pagosATarjetas(movimientos, cuentas.associateBy { it.id })
        EstadoTarjetas(
            tarjetas = tarjetas.filterNot { it.cuenta.archivada }.map { tarjeta ->
                val deuda = deudaDe(tarjeta, saldos)
                ResumenTarjeta(
                    tarjeta = tarjeta,
                    deuda = deuda,
                    disponible = tarjeta.lineaDeCredito - deuda,
                    situacion = situacionTarjeta(tarjeta, estadosDeCuenta, pagos, hoy),
                )
            },
            cuentasParaPagar = cuentas.filter { it.tipo == TipoCuenta.ACTIVO && !it.archivada },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoTarjetas())

    private val _nuevaTarjeta = MutableStateFlow<NuevaTarjeta?>(null)
    val nuevaTarjeta: StateFlow<NuevaTarjeta?> = _nuevaTarjeta.asStateFlow()

    private val _pago = MutableStateFlow<PagoTarjeta?>(null)
    val pago: StateFlow<PagoTarjeta?> = _pago.asStateFlow()

    private val _captura = MutableStateFlow<CapturaEstadoDeCuenta?>(null)
    val captura: StateFlow<CapturaEstadoDeCuenta?> = _captura.asStateFlow()

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

    fun abrirPago(resumen: ResumenTarjeta) {
        // Sugiere lo que falta para no generar intereses, si hay un estado de cuenta vigente.
        val sugerido = (resumen.situacion as? SituacionTarjeta.PagoPendiente)?.restante
        _pago.value = PagoTarjeta(
            tarjeta = resumen.tarjeta,
            montoTexto = sugerido?.formatear()?.removePrefix("$").orEmpty(),
            desdeId = estado.value.cuentasParaPagar.firstOrNull()?.id,
            fecha = fechaDeHoy(),
        )
    }

    fun cerrarPago() {
        _pago.value = null
    }

    fun editarPago(cambio: PagoTarjeta.() -> PagoTarjeta) =
        _pago.update { it?.cambio()?.copy(errores = emptySet(), errorAlGuardar = false) }

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
        // Propone las fechas del corte que falta capturar o, si no, del último corte.
        val (corte, limite) = when (val s = resumen.situacion) {
            is SituacionTarjeta.CapturaPendiente -> s.cortePredicho to s.limitePredicho
            else -> {
                val ultimo = CalendarioTarjeta.ultimoCorteHasta(resumen.tarjeta, hoy)
                ultimo to CalendarioTarjeta.limiteParaCorte(resumen.tarjeta, ultimo)
            }
        }
        _captura.value = CapturaEstadoDeCuenta(resumen.tarjeta, corte, limite)
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
