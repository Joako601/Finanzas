package io.github.joako601.finanzas.nube.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tipo de error recuperable al cargar el catálogo. */
enum class ErrorCatalogo {
    SIN_CONEXION,
    ERROR_SERVIDOR,
}

/** Estado exclusivo del contenido principal del catálogo. */
sealed interface EstadoListaCatalogo {
    data object Cargando : EstadoListaCatalogo
    data class Contenido(val productos: List<ProductoFinanciero>) : EstadoListaCatalogo
    data object Vacio : EstadoListaCatalogo
    data class Error(val tipo: ErrorCatalogo) : EstadoListaCatalogo
}

/** Estado observable de la pantalla de catálogo. */
data class EstadoUiCatalogo(
    val lista: EstadoListaCatalogo = EstadoListaCatalogo.Cargando,
    val cerrandoSesion: Boolean = false,
)

/** ViewModel de [PantallaCatalogo]: carga productos, reintenta errores y coordina cierre de sesión. */
class CatalogoViewModel(
    private val repositorioCatalogo: RepositorioCatalogo = RepositorioCatalogoFalso(),
    private val repositorioSesion: RepositorioSesion,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiCatalogo())
    val estado: StateFlow<EstadoUiCatalogo> = _estado.asStateFlow()

    private var trabajoCarga: Job? = null

    init {
        cargarCatalogo()
    }

    /** Reintenta la carga del catálogo cuando hubo un error o está vacío. */
    fun reintentar() {
        cargarCatalogo()
    }

    /** Cierra la sesión activa evitando pulsaciones duplicadas mientras termina. */
    fun cerrarSesion() {
        if (_estado.value.cerrandoSesion) return
        _estado.update { it.copy(cerrandoSesion = true) }
        viewModelScope.launch {
            try {
                repositorioSesion.cerrarSesion()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _estado.update { it.copy(cerrandoSesion = false) }
            }
        }
    }

    private fun cargarCatalogo() {
        trabajoCarga?.cancel()
        _estado.update { it.copy(lista = EstadoListaCatalogo.Cargando) }
        trabajoCarga = viewModelScope.launch {
            val nuevoEstado = try {
                when (val resultado = repositorioCatalogo.obtenerProductos()) {
                    is ResultadoCatalogo.Exito -> {
                        if (resultado.productos.isEmpty()) {
                            EstadoListaCatalogo.Vacio
                        } else {
                            EstadoListaCatalogo.Contenido(resultado.productos)
                        }
                    }
                    ResultadoCatalogo.SinConexion -> EstadoListaCatalogo.Error(ErrorCatalogo.SIN_CONEXION)
                    ResultadoCatalogo.ErrorServidor -> EstadoListaCatalogo.Error(ErrorCatalogo.ERROR_SERVIDOR)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoListaCatalogo.Error(ErrorCatalogo.ERROR_SERVIDOR)
            }
            _estado.update { it.copy(lista = nuevoEstado) }
        }
    }

    companion object {
        fun factory(
            repositorioCatalogo: RepositorioCatalogo = RepositorioCatalogoFalso(),
            repositorioSesion: RepositorioSesion,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CatalogoViewModel::class.java)) {
                    "ViewModel desconocido: ${modelClass.name}"
                }
                return CatalogoViewModel(
                    repositorioCatalogo = repositorioCatalogo,
                    repositorioSesion = repositorioSesion,
                ) as T
            }
        }
    }
}
