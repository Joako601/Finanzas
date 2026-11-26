package io.github.joako601.finanzas.nube.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Lo que pasó al pedirle al teléfono una cuenta de Google. */
internal sealed interface CuentaGoogle {
    /** El ID token que Firebase verifica. Nunca va a logs. */
    class Token(val idToken: String) : CuentaGoogle {
        override fun toString(): String = "CuentaGoogle.Token(len=${idToken.length})"
    }

    /** El usuario cerró la hoja de cuentas: no es un error. */
    data object Cancelada : CuentaGoogle

    /** No hay cuentas de Google en el teléfono o Google no respondió. */
    data object NoDisponible : CuentaGoogle
}

/**
 * Muestra la hoja de cuentas de Google del sistema (Credential Manager) y devuelve el ID
 * token de la elegida. [contexto] debe ser la Activity: la hoja se dibuja encima de ella.
 * Acepta cualquier cuenta de Google; la regla del dominio institucional es solo del registro
 * con correo.
 */
internal suspend fun pedirCuentaGoogle(contexto: Context, webClientId: String): CuentaGoogle {
    val solicitud = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
        .build()
    return try {
        val credencial = CredentialManager.create(contexto).getCredential(contexto, solicitud).credential
        if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            CuentaGoogle.Token(GoogleIdTokenCredential.createFrom(credencial.data).idToken)
        } else {
            CuentaGoogle.NoDisponible
        }
    } catch (e: GetCredentialCancellationException) {
        CuentaGoogle.Cancelada
    } catch (e: GetCredentialException) {
        CuentaGoogle.NoDisponible
    } catch (e: GoogleIdTokenParsingException) {
        CuentaGoogle.NoDisponible
    }
}
