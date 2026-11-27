package io.github.joako601.finanzas.nube.red

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.joako601.finanzas.nube.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Imágenes ya descargadas en esta sesión de la app: el catálogo no las vuelve a pedir al regresar. */
private val cache = LruCache<String, ImageBitmap>(32)

/**
 * Descarga una imagen (foto de la cuenta, imágenes del catálogo en Supabase) sin agregar una
 * librería de imágenes. Solo acepta `https`; ante cualquier falla devuelve `null`.
 */
suspend fun descargarImagen(url: String): ImageBitmap? {
    cache.get(url)?.let { return it }
    if (!url.startsWith("https://")) return null
    return withContext(Dispatchers.IO) {
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
    }?.also { cache.put(url, it) }
}

/**
 * Imagen remota con respaldo: mientras carga queda el fondo suave y, si falla, un ícono de
 * "imagen no disponible". Es decorativa: el texto de al lado ya dice qué es.
 */
@Composable
internal fun ImagenRemota(url: String, modifier: Modifier = Modifier) {
    val estado by produceState<Resultado>(Resultado.Cargando, url) {
        value = descargarImagen(url)?.let { Resultado.Lista(it) } ?: Resultado.Fallo
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val e = estado) {
            is Resultado.Lista -> Image(
                bitmap = e.imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Resultado.Fallo -> Icon(
                painter = painterResource(R.drawable.ic_imagen_no_disponible),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Resultado.Cargando -> Unit
        }
    }
}

private sealed interface Resultado {
    data object Cargando : Resultado
    data object Fallo : Resultado
    class Lista(val imagen: ImageBitmap) : Resultado
}
