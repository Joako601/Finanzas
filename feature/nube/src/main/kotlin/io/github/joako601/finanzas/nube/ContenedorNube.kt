package io.github.joako601.finanzas.nube

import android.content.Context
import io.github.joako601.finanzas.nube.sesion.InicializadorFirebase
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFirebase

/**
 * Contenedor manual de dependencias de `:feature:nube` (D-007, D-020).
 *
 * Vive separado del `Contenedor` principal de `:app` para que la distribución privada no
 * conozca Firebase y `:feature:nube` no tenga acceso a la base de Room.
 */
class ContenedorNube(context: Context) {

    val firebaseConfigurado: Boolean = InicializadorFirebase.estaConfigurado()

    val repositorioSesion: RepositorioSesion? =
        InicializadorFirebase.obtenerAuth(context)?.let(::RepositorioSesionFirebase)

    companion object {
        @Volatile
        private var instancia: ContenedorNube? = null

        fun obtener(context: Context): ContenedorNube =
            instancia ?: synchronized(this) {
                instancia ?: ContenedorNube(context.applicationContext).also { instancia = it }
            }
    }
}
