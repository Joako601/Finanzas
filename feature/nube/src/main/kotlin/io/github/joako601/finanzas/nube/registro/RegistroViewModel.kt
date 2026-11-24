package io.github.joako601.finanzas.nube.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.ResultadoRegistro
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Avisos globales no ligados a un campo específico del formulario de registro. */
enum class ErrorGlobalRegistro {
    SinConexion,
    DemasiadosIntentos,
    ErrorInesperado,
}

/** Estado inmutable de la pantalla de registro. */
data class EstadoRegistroUi(
    val correo: String = "",
    val contrasena: String = "",
    val confirmarContrasena: String = "",
    val mostrarContrasena: Boolean = false,
    val mostrarConfirmarContrasena: Boolean = false,
    val requisitosContrasena: RequisitosContrasena = RequisitosContrasena(),
    val errorCorreo: ErrorCorreoRegistro? = null,
    val errorContrasena: ErrorContrasenaRegistro? = null,
    val errorConfirmacion: ErrorConfirmacionRegistro? = null,
    val errorGlobal: ErrorGlobalRegistro? = null,
    val cargando: Boolean = false,
    val registroExitoso: Boolean = false,
) {
    /** Nunca expone correo ni contraseñas en texto plano al volcar el estado. */
    override fun toString(): String =
        "EstadoRegistroUi(correoLen=${correo.length}, contrasena=***, confirmarContrasena=***, " +
            "cargando=$cargando, registroExitoso=$registroExitoso, " +
            "errorCorreo=$errorCorreo, errorContrasena=$errorContrasena, " +
            "errorConfirmacion=$errorConfirmacion, errorGlobal=$errorGlobal)"
}

/**
 * Coordina validaciones locales, progreso en tiempo real de los requisitos de contraseña
 * y el alta de cuenta en [RepositorioSesion].
 */
class RegistroViewModel(
    private val repositorioSesion: RepositorioSesion,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoRegistroUi())
    val estado: StateFlow<EstadoRegistroUi> = _estado.asStateFlow()

    private var jobRegistro: Job? = null

    fun alCambiarCorreo(valor: String) {
        _estado.update { actual ->
            actual.copy(
                correo = valor,
                errorCorreo = null,
                errorGlobal = null,
            )
        }
    }

    fun alCambiarContrasena(valor: String) {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena(valor)
        _estado.update { actual ->
            actual.copy(
                contrasena = valor,
                requisitosContrasena = requisitos,
                errorContrasena = null,
                errorConfirmacion = if (actual.confirmarContrasena == valor) null else actual.errorConfirmacion,
                errorGlobal = null,
            )
        }
    }

    fun alCambiarConfirmarContrasena(valor: String) {
        _estado.update { actual ->
            actual.copy(
                confirmarContrasena = valor,
                errorConfirmacion = null,
                errorGlobal = null,
            )
        }
    }

    fun alAlternarMostrarContrasena() {
        _estado.update { actual ->
            actual.copy(mostrarContrasena = !actual.mostrarContrasena)
        }
    }

    fun alAlternarMostrarConfirmarContrasena() {
        _estado.update { actual ->
            actual.copy(mostrarConfirmarContrasena = !actual.mostrarConfirmarContrasena)
        }
    }

    fun alEnviar() {
        val actual = _estado.value
        if (actual.cargando || actual.registroExitoso) return

        val errorCorreo = ValidadorRegistro.validarCorreo(actual.correo)
        val errorContrasena = ValidadorRegistro.validarContrasena(actual.contrasena)
        val errorConfirmacion = ValidadorRegistro.validarConfirmacion(
            contrasena = actual.contrasena,
            confirmacion = actual.confirmarContrasena,
        )

        if (errorCorreo != null || errorContrasena != null || errorConfirmacion != null) {
            _estado.update {
                it.copy(
                    errorCorreo = errorCorreo,
                    errorContrasena = errorContrasena,
                    errorConfirmacion = errorConfirmacion,
                    errorGlobal = null,
                )
            }
            return
        }

        val correoNormalizado = ValidadorRegistro.normalizarCorreo(actual.correo)
        val contrasena = actual.contrasena

        _estado.update {
            it.copy(
                cargando = true,
                errorCorreo = null,
                errorContrasena = null,
                errorConfirmacion = null,
                errorGlobal = null,
            )
        }

        jobRegistro = viewModelScope.launch {
            val resultado = repositorioSesion.registrar(correoNormalizado, contrasena)
            _estado.update { estadoActual ->
                when (resultado) {
                    ResultadoRegistro.Exito -> estadoActual.copy(
                        contrasena = "",
                        confirmarContrasena = "",
                        mostrarContrasena = false,
                        mostrarConfirmarContrasena = false,
                        cargando = false,
                        registroExitoso = true,
                    )

                    ResultadoRegistro.CorreoEnUso -> estadoActual.copy(
                        cargando = false,
                        errorCorreo = ErrorCorreoRegistro.YaRegistrado,
                    )

                    ResultadoRegistro.CorreoInvalido -> estadoActual.copy(
                        cargando = false,
                        errorCorreo = ErrorCorreoRegistro.FormatoInvalido,
                    )

                    ResultadoRegistro.ContrasenaDebil -> estadoActual.copy(
                        cargando = false,
                        errorContrasena = ErrorContrasenaRegistro.DebilEnServidor,
                    )

                    ResultadoRegistro.SinConexion -> estadoActual.copy(
                        cargando = false,
                        errorGlobal = ErrorGlobalRegistro.SinConexion,
                    )

                    ResultadoRegistro.DemasiadosIntentos -> estadoActual.copy(
                        cargando = false,
                        errorGlobal = ErrorGlobalRegistro.DemasiadosIntentos,
                    )

                    ResultadoRegistro.ErrorInesperado -> estadoActual.copy(
                        cargando = false,
                        errorGlobal = ErrorGlobalRegistro.ErrorInesperado,
                    )
                }
            }
        }
    }

    fun alConfirmarExito() {
        reiniciar()
    }

    /** Cancela cualquier registro en curso y limpia el estado del formulario al salir. */
    fun reiniciar() {
        jobRegistro?.cancel()
        jobRegistro = null
        _estado.value = EstadoRegistroUi()
    }

    companion object {
        fun factory(repositorioSesion: RepositorioSesion): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(RegistroViewModel::class.java)) {
                        "ViewModel desconocido: ${modelClass.name}"
                    }
                    return RegistroViewModel(repositorioSesion) as T
                }
            }
    }
}
