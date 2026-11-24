package io.github.joako601.finanzas.nube

import android.content.Context
import io.github.joako601.finanzas.nube.catalogo.RepositorioCatalogo
import io.github.joako601.finanzas.nube.catalogo.RepositorioCatalogoFalso
import io.github.joako601.finanzas.nube.catalogo.RepositorioCatalogoRemoto
import io.github.joako601.finanzas.nube.catalogo.ResultadoCatalogo
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisas
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisasFalso
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisasRemoto
import io.github.joako601.finanzas.nube.divisas.ResultadoConversion
import io.github.joako601.finanzas.nube.divisas.ResultadoDivisasActuales
import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.sesion.InicializadorFirebase
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFirebase
import java.math.BigDecimal

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

    private val clienteHttpBackend: ClienteHttpBackend? =
        repositorioSesion?.let { sesion ->
            ClienteHttpBackend(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                repositorioSesion = sesion,
                permitirHttpLocal = BuildConfig.DEBUG,
            ).takeIf { it.configurado }
        }

    val backendConfigurado: Boolean = clienteHttpBackend != null

    val repositorioCatalogo: RepositorioCatalogo = when {
        clienteHttpBackend != null -> RepositorioCatalogoRemoto(clienteHttpBackend)
        BuildConfig.DEBUG -> RepositorioCatalogoFalso()
        // En release nunca servimos datos ficticios silenciosamente si falta backend.baseUrl.release.
        else -> RepositorioCatalogoNoConfigurado
    }

    val repositorioDivisas: RepositorioDivisas = when {
        clienteHttpBackend != null -> RepositorioDivisasRemoto(clienteHttpBackend)
        BuildConfig.DEBUG -> RepositorioDivisasFalso()
        else -> RepositorioDivisasNoConfigurado
    }

    companion object {
        @Volatile
        private var instancia: ContenedorNube? = null

        fun obtener(context: Context): ContenedorNube =
            instancia ?: synchronized(this) {
                instancia ?: ContenedorNube(context.applicationContext).also { instancia = it }
            }
    }
}

private object RepositorioCatalogoNoConfigurado : RepositorioCatalogo {
    override suspend fun obtenerProductos(): ResultadoCatalogo = ResultadoCatalogo.ErrorServidor
}

private object RepositorioDivisasNoConfigurado : RepositorioDivisas {
    override suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales =
        ResultadoDivisasActuales.ErrorServidor

    override suspend fun convertir(monto: BigDecimal, de: String, a: String): ResultadoConversion =
        ResultadoConversion.ErrorServidor
}
