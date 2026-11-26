package io.github.joako601.finanzas.nube.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.ResultadoLogin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estados de reacción de la mascota/guardián en el inicio de sesión. */
enum class EstadoMascotaLogin {
    Reposo,
    EscribiendoCorreo,
    EscribiendoContrasena,
    Cargando,
    Exito,
    Error,
}

/** El login va en dos pasos: primero el correo y luego la contraseña. */
enum class PasoLogin {
    Correo,
    Contrasena,
}

/**
 * Avisos globales del inicio de sesión.
 *
 * Siguiendo OWASP A07 / WSTG-IDNT-04, nunca se muestran errores por campo ni se revela si
 * el correo existe o si solo falló la contraseña.
 */
enum class AvisoLogin {
    CorreoVacio,
    ContrasenaVacia,
    CredencialesInvalidas,
    SinConexion,
    DemasiadosIntentos,
    ErrorInesperado,
    GoogleNoDisponible,
}

/** Estado inmutable de la pantalla de inicio de sesión. */
data class EstadoUiLogin(
    val paso: PasoLogin = PasoLogin.Correo,
    val correo: String = "",
    val contrasena: String = "",
    val contrasenaVisible: Boolean = false,
    val cargando: Boolean = false,
    val avisoGlobal: AvisoLogin? = null,
    val estadoMascota: EstadoMascotaLogin = EstadoMascotaLogin.Reposo,
    val loginCompletado: Boolean = false,
    val intentoError: Int = 0,
) {
    /** Redacta la contraseña para impedir filtraciones accidentales en logs o volcados. */
    override fun toString(): String =
        "EstadoUiLogin(paso=$paso, correoLen=${correo.length}, contrasena=[REDACTADA], " +
            "contrasenaVisible=$contrasenaVisible, cargando=$cargando, " +
            "avisoGlobal=$avisoGlobal, estadoMascota=$estadoMascota, " +
            "loginCompletado=$loginCompletado, intentoError=$intentoError)"
}

/**
 * Controla el flujo seguro de inicio de sesión contra [RepositorioSesion].
 *
 * No valida complejidad de contraseña ni formato de correo con Regex en el cliente;
 * solo comprueba que ambos campos contengan texto antes de consultar Firebase.
 */
class LoginViewModel(
    private val repositorioSesion: RepositorioSesion,
    private val demoraExitoMs: Long = DEMORA_EXITO_MS,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiLogin())
    val estado: StateFlow<EstadoUiLogin> = _estado.asStateFlow()

    private var jobInicioSesion: Job? = null

    fun onCorreoCambio(valor: String) {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            actual.copy(
                correo = valor,
                avisoGlobal = null,
                estadoMascota = EstadoMascotaLogin.EscribiendoCorreo,
            )
        }
    }

    fun onContrasenaCambio(valor: String) {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            actual.copy(
                contrasena = valor,
                avisoGlobal = null,
                estadoMascota = EstadoMascotaLogin.EscribiendoContrasena,
            )
        }
    }

    fun onFocoCorreo() {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            actual.copy(estadoMascota = EstadoMascotaLogin.EscribiendoCorreo)
        }
    }

    fun onFocoContrasena() {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            actual.copy(estadoMascota = EstadoMascotaLogin.EscribiendoContrasena)
        }
    }

    fun onSinFoco() {
        _estado.update { actual ->
            val escribiendo = actual.estadoMascota == EstadoMascotaLogin.EscribiendoCorreo ||
                actual.estadoMascota == EstadoMascotaLogin.EscribiendoContrasena
            if (!escribiendo) return@update actual
            actual.copy(estadoMascota = EstadoMascotaLogin.Reposo)
        }
    }

    fun onAlternarVisibilidadContrasena() {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            actual.copy(contrasenaVisible = !actual.contrasenaVisible)
        }
    }

    /**
     * Pasa a la contraseña con cualquier correo no vacío. No consulta a Firebase: si el primer
     * paso dijera "ese correo no tiene cuenta", revelaría qué correos existen (WSTG-IDNT-04).
     */
    fun continuar() {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            if (actual.correo.isBlank()) {
                actual.copy(
                    avisoGlobal = AvisoLogin.CorreoVacio,
                    estadoMascota = EstadoMascotaLogin.Error,
                    intentoError = actual.intentoError + 1,
                )
            } else {
                actual.copy(paso = PasoLogin.Contrasena, avisoGlobal = null, estadoMascota = EstadoMascotaLogin.Reposo)
            }
        }
    }

    /** Regresa al correo; la contraseña escrita se borra para no dejarla en memoria. */
    fun cambiarCorreo() {
        _estado.update { actual ->
            if (actual.cargando || actual.estadoMascota == EstadoMascotaLogin.Exito) return@update actual
            actual.copy(
                paso = PasoLogin.Correo,
                contrasena = "",
                contrasenaVisible = false,
                avisoGlobal = null,
                estadoMascota = EstadoMascotaLogin.Reposo,
            )
        }
    }

    /** El teléfono no tiene cuentas de Google o Google no respondió al pedir la cuenta. */
    fun googleNoDisponible() {
        registrarError(AvisoLogin.GoogleNoDisponible)
    }

    /** Entra con el ID token que entregó Google en el teléfono; Firebase lo verifica. */
    fun iniciarSesionConGoogle(idToken: String) {
        val actual = _estado.value
        if (actual.cargando || actual.loginCompletado || actual.estadoMascota == EstadoMascotaLogin.Exito) return
        empezarCarga()
        jobInicioSesion = viewModelScope.launch {
            procesarResultado(repositorioSesion.iniciarSesionConGoogle(idToken))
        }
    }

    fun iniciarSesion() {
        val actual = _estado.value
        if (actual.cargando || actual.loginCompletado || actual.estadoMascota == EstadoMascotaLogin.Exito) {
            return
        }

        if (actual.correo.isBlank() || actual.contrasena.isBlank()) {
            // Validación local: decir cuál campo falta no revela nada de las cuentas (OWASP).
            val aviso = if (actual.correo.isBlank()) AvisoLogin.CorreoVacio else AvisoLogin.ContrasenaVacia
            _estado.update {
                it.copy(
                    avisoGlobal = aviso,
                    estadoMascota = EstadoMascotaLogin.Error,
                    intentoError = it.intentoError + 1,
                )
            }
            return
        }

        val correoNormalizado = actual.correo.trim()
        val contrasenaIngresada = actual.contrasena

        empezarCarga()
        jobInicioSesion = viewModelScope.launch {
            procesarResultado(repositorioSesion.iniciarSesion(correo = correoNormalizado, contrasena = contrasenaIngresada))
        }
    }

    private fun empezarCarga() {
        _estado.update {
            it.copy(
                cargando = true,
                avisoGlobal = null,
                estadoMascota = EstadoMascotaLogin.Cargando,
            )
        }
    }

    private suspend fun procesarResultado(resultado: ResultadoLogin) {
            when (resultado) {
                ResultadoLogin.Exito -> {
                    _estado.update {
                        it.copy(
                            cargando = false,
                            avisoGlobal = null,
                            estadoMascota = EstadoMascotaLogin.Exito,
                        )
                    }
                    delay(demoraExitoMs)
                    _estado.update {
                        it.copy(
                            contrasena = "",
                            loginCompletado = true,
                        )
                    }
                }

                ResultadoLogin.CredencialesInvalidas -> registrarError(AvisoLogin.CredencialesInvalidas)
                ResultadoLogin.SinConexion -> registrarError(AvisoLogin.SinConexion)
                ResultadoLogin.DemasiadosIntentos -> registrarError(AvisoLogin.DemasiadosIntentos)
                ResultadoLogin.ErrorInesperado -> registrarError(AvisoLogin.ErrorInesperado)
            }
    }

    /**
     * Cancela cualquier intento o celebración en curso y limpia el estado para evitar
     * re-disparar el éxito al cerrar sesión o al salir de la pantalla.
     */
    fun reiniciar() {
        jobInicioSesion?.cancel()
        jobInicioSesion = null
        _estado.value = EstadoUiLogin()
    }

    private fun registrarError(aviso: AvisoLogin) {
        _estado.update {
            it.copy(
                cargando = false,
                avisoGlobal = aviso,
                estadoMascota = EstadoMascotaLogin.Error,
                intentoError = it.intentoError + 1,
            )
        }
    }

    companion object {
        const val DEMORA_EXITO_MS: Long = 750L

        fun fabrica(repositorioSesion: RepositorioSesion): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                        "ViewModel desconocido: ${modelClass.name}"
                    }
                    return LoginViewModel(repositorioSesion) as T
                }
            }
    }
}
