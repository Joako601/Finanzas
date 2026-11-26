package io.github.joako601.finanzas.ui.perfil

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.core.content.edit
import io.github.joako601.finanzas.FinanzasApp

/**
 * Ajustes de "Privacidad y seguridad" y la marca de la bienvenida, en las preferencias
 * privadas de la app. Solo interruptores: ningún monto ni nombre.
 */
class PreferenciasPrivacidad(context: Context) {
    private val preferencias = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    /** Modo discreto: abrir la app con los montos de Inicio ocultos. */
    var iniciarOculto: Boolean
        get() = preferencias.getBoolean("iniciar_oculto", false)
        set(valor) = preferencias.edit { putBoolean("iniciar_oculto", valor) }

    /** FLAG_SECURE: la vista previa de apps recientes sale en blanco y no se permiten capturas. */
    var ocultarEnRecientes: Boolean
        get() = preferencias.getBoolean("ocultar_recientes", false)
        set(valor) = preferencias.edit { putBoolean("ocultar_recientes", valor) }

    /** Pedir el PIN, patrón o huella del teléfono al abrir la app. */
    var bloqueo: Boolean
        get() = preferencias.getBoolean("bloqueo", false)
        set(valor) = preferencias.edit { putBoolean("bloqueo", valor) }

    /** La bienvenida del Día 1 ya se mostró (o se saltó). */
    var bienvenidaVista: Boolean
        get() = preferencias.getBoolean("bienvenida_vista", false)
        set(valor) = preferencias.edit { putBoolean("bienvenida_vista", valor) }

    companion object {
        private const val ARCHIVO = "privacidad"

        /**
         * Preferencias que se limpian al borrar los datos locales: estas, la última cuenta usada
         * y las de avisos. La fecha del último respaldo se conserva: el archivo sigue existiendo
         * y es justo lo que sirve para recuperar lo borrado.
         */
        private val AL_BORRAR = listOf(ARCHIVO, "captura", "finanzas_avisos")
    }

    /** Borra la base local (queda como recién instalada), estas preferencias y los avisos. */
    suspend fun borrarDatosLocales(app: FinanzasApp) {
        app.contenedor.respaldo.borrarTodo()
        AL_BORRAR.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit(commit = true) { clear() } }
        app.getSystemService(NotificationManager::class.java).cancelAll()
        // Sin datos no queda nada por avisar: cancela la alarma pendiente.
        app.contenedor.programador.ejecutar()
    }
}

/** Solo se puede bloquear si el teléfono tiene PIN, patrón, contraseña o huella. */
fun dispositivoSeguro(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java).isDeviceSecure

fun aplicarPantallaSegura(window: Window, activa: Boolean) {
    if (activa) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
