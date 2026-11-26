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
import io.github.joako601.finanzas.nube.mercado.AlertasLocales
import io.github.joako601.finanzas.nube.mercado.CacheMercado
import io.github.joako601.finanzas.nube.mercado.EstadoMercado
import io.github.joako601.finanzas.nube.mercado.FuenteMercado
import io.github.joako601.finanzas.nube.mercado.FuenteMercadoFalsa
import io.github.joako601.finanzas.nube.mercado.FuenteMercadoRemota
import io.github.joako601.finanzas.nube.mercado.MonitorConexion
import io.github.joako601.finanzas.nube.mercado.RepositorioMercado
import io.github.joako601.finanzas.nube.mercado.ResultadoFuente
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal

/**
 * Contenedor manual de dependencias de `:feature:nube` (D-007, D-020).
 *
 * Vive separado del `Contenedor` principal de `:app` para que la distribución privada no
 * conozca Firebase y `:feature:nube` no tenga acceso a la base de Room.
 */
class ContenedorNube(context: Context) {

    val firebaseConfigurado: Boolean = InicializadorFirebase.estaConfigurado()

    private val sesionFirebase: RepositorioSesion? =
        InicializadorFirebase.obtenerAuth(context)?.let(::RepositorioSesionFirebase)

    private val clienteHttpBackend: ClienteHttpBackend? =
        sesionFirebase?.let { sesion ->
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

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val monitorConexion = MonitorConexion(context)

    /** Datos de la pestaña Mercado. Sin Firebase configurado queda en [EstadoMercado.Oculto]. */
    val repositorioMercado: RepositorioMercado = RepositorioMercado(
        fuente = when {
            clienteHttpBackend != null -> FuenteMercadoRemota(clienteHttpBackend, repositorioDivisas, repositorioCatalogo)
            BuildConfig.DEBUG -> FuenteMercadoFalsa(repositorioDivisas, repositorioCatalogo)
            else -> FuenteMercado { ResultadoFuente.ErrorServidor }
        },
        cache = CacheMercado(File(context.noBackupFilesDir, "mercado")),
        alertas = AlertasLocales(File(context.noBackupFilesDir, "mercado")),
        conectado = monitorConexion.conectado,
        sesion = sesionFirebase?.estado ?: MutableStateFlow(EstadoSesion.SinSesion),
        alcance = alcance,
    )

    /**
     * La sesión de Firebase con el cierre centralizado: cerrar sesión a propósito, desde
     * cualquier pantalla, borra también los datos y las alertas de Mercado. Una sesión que se cae por fuera
     * (token revocado) no pasa por aquí y conserva los datos.
     */
    val repositorioSesion: RepositorioSesion? = sesionFirebase?.let { base ->
        SesionConLimpieza(base) {
            repositorioMercado.borrar()
            withContext(Dispatchers.IO) { repositorioMercado.alertas?.borrar() }
        }
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

private class SesionConLimpieza(
    private val base: RepositorioSesion,
    private val limpiar: suspend () -> Unit,
) : RepositorioSesion by base {
    override suspend fun cerrarSesion() {
        base.cerrarSesion()
        limpiar()
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
