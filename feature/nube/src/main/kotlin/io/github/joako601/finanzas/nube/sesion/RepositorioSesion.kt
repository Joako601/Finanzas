package io.github.joako601.finanzas.nube.sesion

import kotlinx.coroutines.flow.StateFlow

/** Datos mínimos de la cuenta con sesión iniciada. */
data class UsuarioSesion(
    val uid: String,
    val correo: String,
)

/** Estado observable de la sesión en la sección nube. */
sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Autenticado(val usuario: UsuarioSesion) : EstadoSesion
}

/**
 * Resultado de iniciar sesión.
 *
 * Siguiendo OWASP (Authentication Cheat Sheet), nunca distingue entre correo inexistente
 * y contraseña incorrecta: ambos se devuelven como [CredencialesInvalidas] para evitar
 * enumeración de cuentas.
 */
sealed interface ResultadoLogin {
    data object Exito : ResultadoLogin
    data object CredencialesInvalidas : ResultadoLogin
    data object DemasiadosIntentos : ResultadoLogin
    data object SinConexion : ResultadoLogin
    data object ErrorInesperado : ResultadoLogin
}

/**
 * Resultado de registrar una cuenta nueva. Aquí sí se informan motivos concretos para
 * guiar al usuario (correo ya registrado, contraseña débil o formato inválido).
 */
sealed interface ResultadoRegistro {
    data object Exito : ResultadoRegistro
    data object CorreoEnUso : ResultadoRegistro
    data object ContrasenaDebil : ResultadoRegistro
    data object CorreoInvalido : ResultadoRegistro
    data object SinConexion : ResultadoRegistro
    data object ErrorInesperado : ResultadoRegistro
}

/**
 * Resultado de enviar el correo de recuperación de contraseña.
 *
 * Siguiendo OWASP, si el correo no está registrado se devuelve [Enviado] igual que si lo
 * estuviera, para no revelar qué correos tienen cuenta.
 */
sealed interface ResultadoRecuperacion {
    data object Enviado : ResultadoRecuperacion
    data object CorreoInvalido : ResultadoRecuperacion
    data object DemasiadosIntentos : ResultadoRecuperacion
    data object SinConexion : ResultadoRecuperacion
    data object ErrorInesperado : ResultadoRecuperacion
}

/** Contrato de autenticación que consumen todas las pantallas de `:feature:nube`. */
interface RepositorioSesion {
    val estado: StateFlow<EstadoSesion>

    suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin

    suspend fun registrar(correo: String, contrasena: String): ResultadoRegistro

    suspend fun enviarRecuperacion(correo: String): ResultadoRecuperacion

    suspend fun cerrarSesion()
}
