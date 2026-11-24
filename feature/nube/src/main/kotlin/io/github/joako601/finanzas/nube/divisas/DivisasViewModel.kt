package io.github.joako601.finanzas.nube.divisas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Error recuperable al cargar el tablero de divisas. */
enum class ErrorDivisas {
    SIN_CONEXION,
    SESION_EXPIRADA,
    ERROR_SERVIDOR,
}

/** Estado del listado principal de cotizaciones (USD, EUR, CAD -> MXN). */
sealed interface EstadoListaDivisas {
    data object Cargando : EstadoListaDivisas
    data class Contenido(val cotizaciones: List<TipoCambio>) : EstadoListaDivisas
    data class Error(val tipo: ErrorDivisas) : EstadoListaDivisas
}

/** Error del conversor de divisas. */
enum class ErrorConversion {
    MONTO_INVALIDO,
    SIN_CONEXION,
    SESION_EXPIRADA,
    ERROR_SERVIDOR,
}

/** Estado del resultado del conversor rápido. */
sealed interface EstadoConversion {
    data object Inactivo : EstadoConversion
    data object Convirtiendo : EstadoConversion
    data class Resultado(val conversion: ConversionDivisa) : EstadoConversion
    data class Error(val tipo: ErrorConversion) : EstadoConversion
}

/** Estado observable completo de la pestaña de Divisas. */
data class EstadoUiDivisas(
    val lista: EstadoListaDivisas = EstadoListaDivisas.Cargando,
    val refrescando: Boolean = false,
    val montoTexto: String = "100",
    val parSeleccionado: ParDivisas = PARES_CONVERSOR_DISPONIBLES.first(),
    val conversion: EstadoConversion = EstadoConversion.Inactivo,
)

/** ViewModel de la pestaña de divisas: cotizaciones vigentes + conversor en vivo contra el backend. */
class DivisasViewModel(
    private val repositorioDivisas: RepositorioDivisas = RepositorioDivisasFalso(),
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiDivisas())
    val estado: StateFlow<EstadoUiDivisas> = _estado.asStateFlow()

    private var trabajoCarga: Job? = null
    private var trabajoConversion: Job? = null

    init {
        cargarCotizaciones(silencioso = false)
    }

    fun reintentar() {
        cargarCotizaciones(silencioso = false)
    }

    /**
     * Verifica el estado del servidor y refresca cotizaciones sin ocultar las actuales mientras responde.
     */
    fun refrescar() {
        val hayContenido = _estado.value.lista is EstadoListaDivisas.Contenido
        cargarCotizaciones(silencioso = hayContenido)
    }

    fun cambiarMonto(nuevoTexto: String) {
        val sinComasMiles = if ('.' in nuevoTexto) {
            nuevoTexto.replace(",", "")
        } else {
            nuevoTexto.replace(',', '.')
        }
        val filtrado = sinComasMiles.filter { it.isDigit() || it == '.' }
        if (filtrado.count { it == '.' } > 1 || filtrado.length > 13) return
        val partes = filtrado.split('.')
        if (partes.size == 2 && partes[1].length > 2) return
        // Cancelar cualquier conversión en curso para evitar resultados de un monto obsoleto.
        trabajoConversion?.cancel()
        _estado.update {
            it.copy(
                montoTexto = filtrado,
                conversion = EstadoConversion.Inactivo,
            )
        }
    }

    fun seleccionarPar(par: ParDivisas) {
        trabajoConversion?.cancel()
        _estado.update {
            it.copy(
                parSeleccionado = par,
                conversion = EstadoConversion.Inactivo,
            )
        }
    }

    fun convertir() {
        trabajoConversion?.cancel()
        val actual = _estado.value
        val monto = actual.montoTexto.trim().toBigDecimalOrNull()
        val escalaValida = monto != null && monto.stripTrailingZeros().scale() <= 2
        if (monto == null || !escalaValida || monto < MONTO_MINIMO || monto > MONTO_MAXIMO) {
            _estado.update { it.copy(conversion = EstadoConversion.Error(ErrorConversion.MONTO_INVALIDO)) }
            return
        }

        _estado.update { it.copy(conversion = EstadoConversion.Convirtiendo) }
        trabajoConversion = viewModelScope.launch {
            val nuevoResultado = try {
                when (
                    val res = repositorioDivisas.convertir(
                        monto = monto,
                        de = actual.parSeleccionado.origen,
                        a = actual.parSeleccionado.destino,
                    )
                ) {
                    is ResultadoConversion.Exito -> EstadoConversion.Resultado(res.conversion)
                    ResultadoConversion.MontoInvalido -> EstadoConversion.Error(ErrorConversion.MONTO_INVALIDO)
                    ResultadoConversion.SinConexion -> EstadoConversion.Error(ErrorConversion.SIN_CONEXION)
                    ResultadoConversion.SesionExpirada -> EstadoConversion.Error(ErrorConversion.SESION_EXPIRADA)
                    ResultadoConversion.ErrorServidor -> EstadoConversion.Error(ErrorConversion.ERROR_SERVIDOR)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoConversion.Error(ErrorConversion.ERROR_SERVIDOR)
            }
            _estado.update { it.copy(conversion = nuevoResultado) }
        }
    }

    private fun cargarCotizaciones(silencioso: Boolean) {
        trabajoCarga?.cancel()
        if (silencioso) {
            _estado.update { it.copy(refrescando = true) }
        } else {
            _estado.update { it.copy(lista = EstadoListaDivisas.Cargando, refrescando = false) }
        }
        trabajoCarga = viewModelScope.launch {
            val nuevoEstado = try {
                when (val resultado = repositorioDivisas.obtenerCotizacionesPrincipales()) {
                    is ResultadoDivisasActuales.Exito -> EstadoListaDivisas.Contenido(resultado.cotizaciones)
                    ResultadoDivisasActuales.SinConexion -> EstadoListaDivisas.Error(ErrorDivisas.SIN_CONEXION)
                    ResultadoDivisasActuales.SesionExpirada -> EstadoListaDivisas.Error(ErrorDivisas.SESION_EXPIRADA)
                    ResultadoDivisasActuales.ErrorServidor -> EstadoListaDivisas.Error(ErrorDivisas.ERROR_SERVIDOR)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoListaDivisas.Error(ErrorDivisas.ERROR_SERVIDOR)
            }
            _estado.update { it.copy(lista = nuevoEstado, refrescando = false) }
        }
    }

    companion object {
        private val MONTO_MINIMO = BigDecimal("0.01")
        private val MONTO_MAXIMO = BigDecimal("100000000.00")

        fun factory(
            repositorioDivisas: RepositorioDivisas = RepositorioDivisasFalso(),
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DivisasViewModel::class.java)) {
                    "ViewModel desconocido: ${modelClass.name}"
                }
                return DivisasViewModel(repositorioDivisas = repositorioDivisas) as T
            }
        }
    }
}
