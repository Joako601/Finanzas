package io.github.joako601.finanzas.nube.recuperar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.ResultadoRecuperacion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Errores visibles en el diálogo de recuperación de contraseña. */
enum class ErrorRecuperar {
    CORREO_INVALIDO,
    DEMASIADOS_INTENTOS,
    SIN_CONEXION,
    ERROR_INESPERADO,
}

/** Estado de UI del diálogo de recuperación de contraseña. */
data class EstadoRecuperar(
    val correo: String = "",
    val enviando: Boolean = false,
    val enviado: Boolean = false,
    val error: ErrorRecuperar? = null,
) {
    /** Nunca expone el correo en claro al volcar el estado. */
    override fun toString(): String =
        "EstadoRecuperar(correoLen=${correo.length}, enviando=$enviando, enviado=$enviado, error=$error)"
}

/**
 * ViewModel del diálogo de recuperación de contraseña.
 *
 * Valida localmente solo formato básico no vacío antes de enviar y respeta la regla OWASP
 * de no enumeración: cuando [RepositorioSesion.enviarRecuperacion] responde
 * [ResultadoRecuperacion.Enviado], la UI muestra siempre el mismo mensaje neutral.
 */
class RecuperarViewModel(
    private val repositorioSesion: RepositorioSesion,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoRecuperar())
    val estado: StateFlow<EstadoRecuperar> = _estado.asStateFlow()

    private var trabajoEnvio: Job? = null

    fun alCambiarCorreo(nuevoCorreo: String) {
        _estado.update { actual ->
            if (actual.enviando || actual.enviado) {
                actual
            } else {
                actual.copy(correo = nuevoCorreo, error = null)
            }
        }
    }

    fun enviar() {
        val actual = _estado.value
        if (actual.enviando || actual.enviado) return

        val correoLimpio = actual.correo.trim()
        if (!esCorreoBasicoValido(correoLimpio)) {
            _estado.update { it.copy(error = ErrorRecuperar.CORREO_INVALIDO) }
            return
        }

        _estado.update { it.copy(enviando = true, error = null) }
        trabajoEnvio = viewModelScope.launch {
            val resultado = repositorioSesion.enviarRecuperacion(correoLimpio)
            _estado.update { estadoActual ->
                when (resultado) {
                    ResultadoRecuperacion.Enviado -> estadoActual.copy(
                        enviando = false,
                        enviado = true,
                        error = null,
                    )

                    ResultadoRecuperacion.CorreoInvalido -> estadoActual.copy(
                        enviando = false,
                        error = ErrorRecuperar.CORREO_INVALIDO,
                    )

                    ResultadoRecuperacion.DemasiadosIntentos -> estadoActual.copy(
                        enviando = false,
                        error = ErrorRecuperar.DEMASIADOS_INTENTOS,
                    )

                    ResultadoRecuperacion.SinConexion -> estadoActual.copy(
                        enviando = false,
                        error = ErrorRecuperar.SIN_CONEXION,
                    )

                    ResultadoRecuperacion.ErrorInesperado -> estadoActual.copy(
                        enviando = false,
                        error = ErrorRecuperar.ERROR_INESPERADO,
                    )
                }
            }
        }
    }

    /** Limpia el estado y cancela cualquier envío activo al cerrar el diálogo. */
    fun reiniciar() {
        trabajoEnvio?.cancel()
        trabajoEnvio = null
        _estado.value = EstadoRecuperar()
    }

    companion object {
        fun factory(repositorioSesion: RepositorioSesion): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(RecuperarViewModel::class.java)) {
                        "ViewModel desconocido: ${modelClass.name}"
                    }
                    return RecuperarViewModel(repositorioSesion) as T
                }
            }
    }
}

/** Validación básica local para evitar peticiones vacías o claramente mal formadas. */
internal fun esCorreoBasicoValido(correo: String): Boolean {
    if (correo.isEmpty() || correo.any { it.isWhitespace() }) return false
    val partes = correo.split("@")
    if (partes.size != 2) return false
    val (local, dominio) = partes
    if (local.isEmpty() || dominio.isEmpty()) return false
    if (dominio.startsWith(".") || dominio.endsWith(".") || dominio.contains("..")) return false
    val etiquetas = dominio.split(".")
    return etiquetas.size >= 2 && etiquetas.all { it.isNotEmpty() }
}
