package io.github.joako601.finanzas.nube.red

import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.ResultadoTokenId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets

/** Resultado de bajo nivel del transporte HTTP. */
internal sealed interface ResultadoTransporte {
    data class Respuesta(val codigo: Int, val cuerpo: String) : ResultadoTransporte
    data object RespuestaInvalida : ResultadoTransporte
    data object ErrorRed : ResultadoTransporte
}

/** Abstracción mínima del transporte HTTP para permitir pruebas unitarias sin abrir sockets. */
internal fun interface TransporteHttp {
    fun ejecutarGet(urlCompleta: String, tokenBearer: String): ResultadoTransporte
}

/** Resultado tipado de una llamada autenticada hacia el backend Spring Boot. */
internal sealed interface RespuestaHttp {
    data class Exito(val codigo: Int, val cuerpo: String) : RespuestaHttp
    data object NoAutorizado : RespuestaHttp
    data object SinConexion : RespuestaHttp
    data class ErrorServidor(val codigo: Int) : RespuestaHttp
}

private class RespuestaDemasiadoGrandeException : IOException("Respuesta HTTP excede el límite permitido")

/**
 * Transporte estándar sobre [HttpURLConnection] con tiempos límite acotados,
 * sin redirecciones automáticas (para no filtrar el encabezado `Authorization`)
 * y con tope de memoria de respuesta.
 */
internal object TransporteHttpUrlConnection : TransporteHttp {
    private const val TIMEOUT_CONEXION_MS = 5_000
    private const val TIMEOUT_LECTURA_MS = 8_000
    private const val MAX_BYTES_RESPUESTA = 2 * 1024 * 1024 // 2 MB

    override fun ejecutarGet(urlCompleta: String, tokenBearer: String): ResultadoTransporte {
        var conexion: HttpURLConnection? = null
        return try {
            val url = URI(urlCompleta).toURL()
            conexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_CONEXION_MS
                readTimeout = TIMEOUT_LECTURA_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $tokenBearer")
            }
            val codigo = conexion.responseCode
            val cuerpo = if (codigo in 200..299) {
                conexion.inputStream?.use { leerAcotado(it, MAX_BYTES_RESPUESTA) }.orEmpty()
            } else {
                // No leemos cuerpos de error extensos porque el cliente solo usa el código HTTP.
                ""
            }
            ResultadoTransporte.Respuesta(codigo = codigo, cuerpo = cuerpo)
        } catch (_: RespuestaDemasiadoGrandeException) {
            ResultadoTransporte.RespuestaInvalida
        } catch (_: URISyntaxException) {
            ResultadoTransporte.RespuestaInvalida
        } catch (_: IllegalArgumentException) {
            ResultadoTransporte.RespuestaInvalida
        } catch (_: IOException) {
            ResultadoTransporte.ErrorRed
        } finally {
            conexion?.disconnect()
        }
    }

    private fun leerAcotado(entrada: java.io.InputStream, maxBytes: Int): String {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val leidos = entrada.read(buffer)
            if (leidos <= 0) break
            total += leidos
            if (total > maxBytes) {
                throw RespuestaDemasiadoGrandeException()
            }
            salida.write(buffer, 0, leidos)
        }
        return salida.toString(StandardCharsets.UTF_8.name())
    }
}

/**
 * Cliente HTTP autenticado con Firebase ID Token hacia el backend Spring Boot.
 *
 * - Nunca registra tokens ni encabezados sensibles.
 * - Exige `https://` fuera de modo debug; en debug permite `http://` únicamente hacia
 *   `localhost` o `10.0.2.2`, alineado con `app/src/nubeDebug/res/xml/network_security_config.xml`.
 * - Ante un `401 Unauthorized`, coordina un único refresco forzado del token de Firebase
 *   con [Mutex] para evitar tormentas de refrescos en peticiones concurrentes.
 */
internal class ClienteHttpBackend(
    baseUrl: String,
    private val repositorioSesion: RepositorioSesion,
    permitirHttpLocal: Boolean = false,
    private val transporte: TransporteHttp = TransporteHttpUrlConnection,
    private val despachadorIo: CoroutineDispatcher = Dispatchers.IO,
) {
    val urlBaseValida: String? = validarUrlBaseBackend(baseUrl, permitirHttpLocal)

    val configurado: Boolean
        get() = urlBaseValida != null

    private val mutexRefresco = Mutex()

    // Último refresco como par (token que falló → token nuevo): solo se reusa si el 401 vino
    // de ese mismo token, así no se entrega un JWT viejo ni de otro usuario.
    private var ultimoTokenFallido: String? = null
    private var ultimoTokenRefrescado: String? = null

    suspend fun get(rutaConQuery: String): RespuestaHttp {
        val base = urlBaseValida ?: return RespuestaHttp.ErrorServidor(codigo = 0)
        if (!esRutaValida(rutaConQuery)) {
            return RespuestaHttp.ErrorServidor(codigo = 0)
        }

        val tokenInicial = when (val resToken = repositorioSesion.obtenerTokenId(forzarRefresco = false)) {
            is ResultadoTokenId.Exito -> resToken.token.takeIf(::esTokenBearerValido)
                ?: return RespuestaHttp.NoAutorizado
            ResultadoTokenId.SinConexion -> return RespuestaHttp.SinConexion
            ResultadoTokenId.SinSesion -> return RespuestaHttp.NoAutorizado
        }

        val urlCompleta = "$base$rutaConQuery"
        val primerIntento = ejecutarEnIo(urlCompleta, tokenInicial)
        if (primerIntento is ResultadoTransporte.Respuesta && primerIntento.codigo == 401) {
            val tokenNuevo = when (val resRefresco = refrescarTokenCoordinado(tokenInicial)) {
                is ResultadoTokenId.Exito -> resRefresco.token.takeIf(::esTokenBearerValido)
                    ?: return RespuestaHttp.NoAutorizado
                ResultadoTokenId.SinConexion -> return RespuestaHttp.SinConexion
                ResultadoTokenId.SinSesion -> return RespuestaHttp.NoAutorizado
            }
            if (tokenNuevo == tokenInicial) {
                return RespuestaHttp.NoAutorizado
            }
            val segundoIntento = ejecutarEnIo(urlCompleta, tokenNuevo)
            return mapearResultado(segundoIntento)
        }
        return mapearResultado(primerIntento)
    }

    private suspend fun refrescarTokenCoordinado(tokenFallido: String): ResultadoTokenId =
        mutexRefresco.withLock {
            val yaRefrescado = ultimoTokenRefrescado
            if (yaRefrescado != null && ultimoTokenFallido == tokenFallido) {
                return@withLock ResultadoTokenId.Exito(yaRefrescado)
            }
            val res = repositorioSesion.obtenerTokenId(forzarRefresco = true)
            val valido = res is ResultadoTokenId.Exito && esTokenBearerValido(res.token)
            ultimoTokenFallido = tokenFallido.takeIf { valido }
            ultimoTokenRefrescado = (res as? ResultadoTokenId.Exito)?.token?.takeIf { valido }
            res
        }

    private suspend fun ejecutarEnIo(
        urlCompleta: String,
        tokenBearer: String,
    ): ResultadoTransporte = try {
        withContext(despachadorIo) {
            transporte.ejecutarGet(urlCompleta, tokenBearer)
        }
    } catch (e: CancellationException) {
        throw e
    }

    private fun mapearResultado(resultado: ResultadoTransporte): RespuestaHttp = when (resultado) {
        ResultadoTransporte.ErrorRed -> RespuestaHttp.SinConexion
        ResultadoTransporte.RespuestaInvalida -> RespuestaHttp.ErrorServidor(codigo = 0)
        is ResultadoTransporte.Respuesta -> when {
            resultado.codigo in 200..299 -> RespuestaHttp.Exito(resultado.codigo, resultado.cuerpo)
            resultado.codigo == 401 -> RespuestaHttp.NoAutorizado
            else -> RespuestaHttp.ErrorServidor(resultado.codigo)
        }
    }
}

private fun esRutaValida(ruta: String): Boolean =
    ruta.startsWith("/") && ruta.none { it.isISOControl() || it.isWhitespace() }

private fun esTokenBearerValido(token: String): Boolean =
    token.isNotBlank() && token.none { it.isISOControl() || it.isWhitespace() }

/**
 * Valida y normaliza la URL base del backend (sin barra final).
 * En release únicamente acepta `https://` con host no vacío, puerto válido y sin credenciales.
 * En debug permite además `http://localhost` y `http://10.0.2.2` para coincidir con
 * `app/src/nubeDebug/res/xml/network_security_config.xml`.
 */
internal fun validarUrlBaseBackend(urlCruda: String, permitirHttpLocal: Boolean): String? {
    val limpia = urlCruda.trim().trimEnd('/')
    if (limpia.isEmpty() || limpia.any { it.isISOControl() || it.isWhitespace() }) return null
    val uri = try {
        URI(limpia)
    } catch (_: Exception) {
        return null
    }
    if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    if (uri.port != -1 && uri.port !in 1..65535) return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val esquema = uri.scheme?.lowercase() ?: return null
    val esquemaValido = when (esquema) {
        "https" -> true
        "http" -> permitirHttpLocal && esHostLocalDesarrollo(host)
        else -> false
    }
    if (!esquemaValido) return null
    return limpia
}

private fun esHostLocalDesarrollo(host: String): Boolean =
    host == "localhost" || host == "10.0.2.2"
