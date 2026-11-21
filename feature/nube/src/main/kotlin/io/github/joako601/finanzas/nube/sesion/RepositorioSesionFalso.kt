package io.github.joako601.finanzas.nube.sesion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación en memoria de [RepositorioSesion] para pruebas en la JVM y vistas previas
 * de Compose, sin tocar red ni Firebase.
 */
class RepositorioSesionFalso(
    estadoInicial: EstadoSesion = EstadoSesion.SinSesion,
    cuentasIniciales: Map<String, String> = emptyMap(),
) : RepositorioSesion {

    private val cuentas = cuentasIniciales.toMutableMap()
    private val _estado = MutableStateFlow(estadoInicial)
    override val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    /** Si no es nulo, [iniciarSesion] devuelve este resultado sin mirar [cuentas]. */
    var resultadoLoginForzado: ResultadoLogin? = null

    /** Si no es nulo, [registrar] devuelve este resultado sin modificar [cuentas]. */
    var resultadoRegistroForzado: ResultadoRegistro? = null

    /** Si no es nulo, [enviarRecuperacion] devuelve este resultado. */
    var resultadoRecuperacionForzado: ResultadoRecuperacion? = null

    private val _correosRecuperacion = mutableListOf<String>()
    val correosRecuperacionEnviados: List<String> get() = _correosRecuperacion.toList()

    var vecesCerrarSesion: Int = 0
        private set

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin {
        resultadoLoginForzado?.let { return it }
        val normalizado = correo.trim()
        val guardada = cuentas[normalizado] ?: return ResultadoLogin.CredencialesInvalidas
        if (guardada != contrasena) return ResultadoLogin.CredencialesInvalidas
        _estado.value = EstadoSesion.Autenticado(UsuarioSesion(uid = "falso-$normalizado", correo = normalizado))
        return ResultadoLogin.Exito
    }

    override suspend fun registrar(correo: String, contrasena: String): ResultadoRegistro {
        resultadoRegistroForzado?.let { return it }
        val normalizado = correo.trim()
        if (!normalizado.contains("@") || !normalizado.contains(".")) return ResultadoRegistro.CorreoInvalido
        if (contrasena.length < 8) return ResultadoRegistro.ContrasenaDebil
        if (normalizado in cuentas) return ResultadoRegistro.CorreoEnUso
        cuentas[normalizado] = contrasena
        _estado.value = EstadoSesion.Autenticado(UsuarioSesion(uid = "falso-$normalizado", correo = normalizado))
        return ResultadoRegistro.Exito
    }

    override suspend fun enviarRecuperacion(correo: String): ResultadoRecuperacion {
        resultadoRecuperacionForzado?.let { return it }
        val normalizado = correo.trim()
        if (!normalizado.contains("@") || !normalizado.contains(".")) return ResultadoRecuperacion.CorreoInvalido
        _correosRecuperacion += normalizado
        // OWASP: aunque el correo no esté en `cuentas`, respondemos Enviado.
        return ResultadoRecuperacion.Enviado
    }

    override suspend fun cerrarSesion() {
        vecesCerrarSesion += 1
        _estado.value = EstadoSesion.SinSesion
    }
}
