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

    /** Si no es nulo, [obtenerTokenId] devuelve este resultado sin mirar el estado de sesión. */
    var resultadoTokenIdForzado: ResultadoTokenId? = null

    /** Si no es nulo, [obtenerTokenId] en refresco forzado devuelve este resultado. */
    var resultadoTokenIdRefrescoForzado: ResultadoTokenId? = null

    var vecesCerrarSesion: Int = 0
        private set

    var vecesRefrescoForzadoToken: Int = 0
        private set

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin {
        resultadoLoginForzado?.let { return it }
        val normalizado = correo.trim()
        val guardada = cuentas[normalizado] ?: return ResultadoLogin.CredencialesInvalidas
        if (guardada != contrasena) return ResultadoLogin.CredencialesInvalidas
        _estado.value = EstadoSesion.Autenticado(UsuarioSesion(uid = "falso-$normalizado", correo = normalizado))
        return ResultadoLogin.Exito
    }

    /** Cualquier token no vacío entra como una cuenta de Google de prueba. */
    override suspend fun iniciarSesionConGoogle(idToken: String): ResultadoLogin {
        resultadoLoginForzado?.let { return it }
        if (idToken.isBlank()) return ResultadoLogin.CredencialesInvalidas
        _estado.value = EstadoSesion.Autenticado(UsuarioSesion(uid = "falso-google", correo = "demo@gmail.com", nombre = "Demo"))
        return ResultadoLogin.Exito
    }

    override suspend fun registrar(correo: String, contrasena: String): ResultadoRegistro {
        resultadoRegistroForzado?.let { return it }
        val normalizado = correo.trim()
        if (!normalizado.contains("@") || !normalizado.contains(".")) return ResultadoRegistro.CorreoInvalido
        if (contrasena.length < 8) return ResultadoRegistro.ContrasenaDebil
        if (normalizado in cuentas) return ResultadoRegistro.CorreoEnUso
        cuentas[normalizado] = contrasena
        // No inicia sesión automáticamente: el registro avisa y regresa al Login.
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

    override suspend fun obtenerTokenId(forzarRefresco: Boolean): ResultadoTokenId {
        if (forzarRefresco) {
            vecesRefrescoForzadoToken += 1
            resultadoTokenIdRefrescoForzado?.let { return it }
        }
        resultadoTokenIdForzado?.let { return it }
        val actual = _estado.value as? EstadoSesion.Autenticado ?: return ResultadoTokenId.SinSesion
        val token = if (forzarRefresco) {
            "token-falso-refrescado-${actual.usuario.uid}"
        } else {
            "token-falso-${actual.usuario.uid}"
        }
        return ResultadoTokenId.Exito(token)
    }
}
