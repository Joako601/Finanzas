package io.github.joako601.finanzas.nube.sesion

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementación real de [RepositorioSesion] respaldada por [FirebaseAuth].
 *
 * Traduce todas las excepciones de Firebase a resultados tipados respetando las reglas
 * OWASP de no enumeración en login y recuperación, y nunca registra correos ni contraseñas.
 */
internal class RepositorioSesionFirebase(
    private val auth: FirebaseAuth,
) : RepositorioSesion {

    private val _estado = MutableStateFlow(auth.currentUser.aEstadoSesion())
    override val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    private val escucha = FirebaseAuth.AuthStateListener { instancia ->
        _estado.value = instancia.currentUser.aEstadoSesion()
    }

    init {
        auth.addAuthStateListener(escucha)
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin = try {
        auth.signInWithEmailAndPassword(correo.trim(), contrasena).esperar()
        ResultadoLogin.Exito
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        mapearErrorLogin(
            codigoError = (e as? FirebaseAuthException)?.errorCode,
            esRed = e is FirebaseNetworkException,
            demasiadosIntentos = e is FirebaseTooManyRequestsException,
        )
    }

    override suspend fun registrar(correo: String, contrasena: String): ResultadoRegistro = try {
        auth.createUserWithEmailAndPassword(correo.trim(), contrasena).esperar()
        ResultadoRegistro.Exito
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        mapearErrorRegistro(
            codigoError = (e as? FirebaseAuthException)?.errorCode,
            esRed = e is FirebaseNetworkException,
        )
    }

    override suspend fun enviarRecuperacion(correo: String): ResultadoRecuperacion = try {
        auth.sendPasswordResetEmail(correo.trim()).esperar()
        ResultadoRecuperacion.Enviado
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        mapearErrorRecuperacion(
            codigoError = (e as? FirebaseAuthException)?.errorCode,
            esRed = e is FirebaseNetworkException,
            demasiadosIntentos = e is FirebaseTooManyRequestsException,
        )
    }

    override suspend fun cerrarSesion() {
        auth.signOut()
    }
}

private fun FirebaseUser?.aEstadoSesion(): EstadoSesion =
    if (this == null) {
        EstadoSesion.SinSesion
    } else {
        EstadoSesion.Autenticado(UsuarioSesion(uid = uid, correo = email.orEmpty()))
    }

/** Espera un [Task] de Play Services sin agregar la dependencia `kotlinx-coroutines-play-services`. */
private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuacion ->
    addOnCompleteListener { tarea ->
        when {
            tarea.isCanceled -> continuacion.cancel()
            tarea.isSuccessful -> continuacion.resume(tarea.result)
            else -> continuacion.resumeWithException(
                tarea.exception ?: IllegalStateException("La tarea de Firebase falló sin excepción"),
            )
        }
    }
}

/**
 * Mapeo puro de errores de login (extraído para pruebas en la JVM sin Firebase).
 * Todos los errores de usuario/contraseña convergen en [ResultadoLogin.CredencialesInvalidas].
 */
internal fun mapearErrorLogin(
    codigoError: String?,
    esRed: Boolean,
    demasiadosIntentos: Boolean,
): ResultadoLogin = when {
    esRed -> ResultadoLogin.SinConexion
    demasiadosIntentos || codigoError == "ERROR_TOO_MANY_REQUESTS" -> ResultadoLogin.DemasiadosIntentos
    codigoError in CODIGOS_CREDENCIALES_INVALIDAS -> ResultadoLogin.CredencialesInvalidas
    else -> ResultadoLogin.ErrorInesperado
}

internal fun mapearErrorRegistro(
    codigoError: String?,
    esRed: Boolean,
): ResultadoRegistro = when {
    esRed -> ResultadoRegistro.SinConexion
    codigoError == "ERROR_EMAIL_ALREADY_IN_USE" -> ResultadoRegistro.CorreoEnUso
    codigoError == "ERROR_WEAK_PASSWORD" -> ResultadoRegistro.ContrasenaDebil
    codigoError == "ERROR_INVALID_EMAIL" -> ResultadoRegistro.CorreoInvalido
    else -> ResultadoRegistro.ErrorInesperado
}

/**
 * Mapeo puro de errores de recuperación. `ERROR_USER_NOT_FOUND` se reporta como
 * [ResultadoRecuperacion.Enviado] para no permitir descubrir qué correos están registrados.
 */
internal fun mapearErrorRecuperacion(
    codigoError: String?,
    esRed: Boolean,
    demasiadosIntentos: Boolean,
): ResultadoRecuperacion = when {
    esRed -> ResultadoRecuperacion.SinConexion
    demasiadosIntentos || codigoError == "ERROR_TOO_MANY_REQUESTS" -> ResultadoRecuperacion.DemasiadosIntentos
    codigoError == "ERROR_USER_NOT_FOUND" -> ResultadoRecuperacion.Enviado
    codigoError == "ERROR_INVALID_EMAIL" -> ResultadoRecuperacion.CorreoInvalido
    else -> ResultadoRecuperacion.ErrorInesperado
}

private val CODIGOS_CREDENCIALES_INVALIDAS = setOf(
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND",
    "ERROR_INVALID_EMAIL",
    "ERROR_USER_DISABLED",
)
