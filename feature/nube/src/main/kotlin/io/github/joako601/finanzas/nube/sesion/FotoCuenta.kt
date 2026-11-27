package io.github.joako601.finanzas.nube.sesion

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga la foto de la cuenta (la que trae Google) sin agregar una librería de imágenes.
 * Solo acepta `https`; ante cualquier falla devuelve `null` y el Perfil usa la inicial.
 */
suspend fun descargarFotoCuenta(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    if (!url.startsWith("https://")) return@withContext null
    try {
        val conexion = URL(url).openConnection() as HttpURLConnection
        conexion.connectTimeout = 10_000
        conexion.readTimeout = 10_000
        try {
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            conexion.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } finally {
            conexion.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
