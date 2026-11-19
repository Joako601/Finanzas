package io.github.joako601.finanzas.ui.respaldo

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.FileNotFoundException
import java.io.IOException

/** Lee y escribe por medio del selector de archivos (Storage Access Framework). */
class ArchivosDelSistema(private val resolver: ContentResolver) : Archivos {

    override suspend fun leer(uri: String): ByteArray = withContext(Dispatchers.IO) {
        val entrada = resolver.openInputStream(uri.toUri()) ?: throw IOException("Sin acceso a $uri")
        entrada.use {
            // Un respaldo pesa kilobytes; un archivo enorme (un video elegido por error) no se carga entero.
            val datos = it.readNBytesCompat(MAXIMO_BYTES + 1)
            if (datos.size > MAXIMO_BYTES) throw IOException("Archivo demasiado grande")
            datos
        }
    }

    override suspend fun escribir(uri: String, datos: ByteArray): Unit = withContext(Dispatchers.IO) {
        val destino = uri.toUri()
        // "wt" vacía el archivo antes de escribir; algunos proveedores solo aceptan "w".
        val salida = try {
            resolver.openOutputStream(destino, "wt")
        } catch (e: FileNotFoundException) {
            resolver.openOutputStream(destino, "w")
        } catch (e: IllegalArgumentException) {
            resolver.openOutputStream(destino, "w")
        } ?: throw IOException("Sin acceso a $uri")
        salida.use { it.write(datos) }
    }

    override suspend fun borrar(uri: String): Unit = withContext(Dispatchers.IO) {
        DocumentsContract.deleteDocument(resolver, uri.toUri())
    }

    private companion object {
        const val MAXIMO_BYTES = 50 * 1024 * 1024
    }
}

/** InputStream.readNBytes llega hasta Android 13; el mínimo de la app es Android 8. */
private fun java.io.InputStream.readNBytesCompat(limite: Int): ByteArray {
    val salida = java.io.ByteArrayOutputStream()
    val bufer = ByteArray(64 * 1024)
    while (salida.size() < limite) {
        val leidos = read(bufer, 0, minOf(bufer.size, limite - salida.size()))
        if (leidos < 0) break
        salida.write(bufer, 0, leidos)
    }
    return salida.toByteArray()
}

/** La fecha del último respaldo, en las preferencias privadas de la app (no es un dato financiero). */
class PreferenciasRespaldo(context: Context) : UltimoRespaldo {
    private val preferencias = context.getSharedPreferences("respaldo", Context.MODE_PRIVATE)

    override fun leer(): LocalDate? =
        preferencias.getLong(CLAVE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { LocalDate.fromEpochDays(it.toInt()) }

    override fun guardar(fecha: LocalDate) {
        preferencias.edit { putLong(CLAVE, fecha.toEpochDays().toLong()) }
    }

    private companion object {
        const val CLAVE = "ultimo_dia"
    }
}
