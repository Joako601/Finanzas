package io.github.joako601.finanzas.nube.sesion

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import io.github.joako601.finanzas.nube.BuildConfig

/**
 * Inicializa Firebase a mano con [FirebaseOptions] leídas de `firebase.properties` (D-020).
 *
 * Por qué manual y no con `google-services.json`: así la distribución privada nunca toca el
 * plugin de Google Services, ningún archivo de credenciales entra a git, y si falta
 * `firebase.properties` la app compila y muestra un aviso claro en lugar de cerrarse.
 *
 * Referencia oficial: https://firebase.google.com/docs/projects/multiprojects#configure_multiple_projects
 */
internal object InicializadorFirebase {

    private val candado = Any()

    fun estaConfigurado(): Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun obtenerAuth(context: Context): FirebaseAuth? {
        if (!estaConfigurado()) return null
        val app = synchronized(candado) {
            val appContext = context.applicationContext
            FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }
        return FirebaseAuth.getInstance(app)
    }
}
