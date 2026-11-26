package io.github.joako601.finanzas.nube.mercado

import java.io.File
import java.io.IOException

/**
 * Los últimos datos de Mercado en un archivo JSON dentro de `noBackupFilesDir` (fuera de los
 * respaldos automáticos, igual que la base; D-006). No toca Room (D-020).
 *
 * Un solo archivo con el `uid` adentro: si se lee con otra cuenta, se borra y no se muestra.
 * La escritura es atómica (archivo temporal y `rename` en la misma carpeta), así una app
 * cerrada a la mitad deja el archivo anterior o el nuevo, nunca uno cortado.
 *
 * Las funciones hacen E/S de disco: se llaman fuera del hilo principal.
 */
internal class CacheMercado(private val carpeta: File) {

    private val archivo get() = File(carpeta, ARCHIVO)

    @Synchronized
    fun leer(uid: String): DatosMercado? {
        val datos = leerCualquiera() ?: return null
        if (datos.uid != uid) {
            borrar()
            return null
        }
        return datos
    }

    /** Lo guardado sin importar la cuenta: sirve para mostrar los datos con la sesión expirada. */
    @Synchronized
    fun leerCualquiera(): DatosMercado? {
        val texto = try {
            archivo.takeIf { it.isFile }?.readText()
        } catch (_: IOException) {
            null
        } ?: return null
        return datosMercadoDesdeJson(texto)
    }

    @Synchronized
    fun guardar(datos: DatosMercado) {
        carpeta.mkdirs()
        val temporal = File(carpeta, "$ARCHIVO.tmp")
        temporal.writeText(datos.aJson())
        if (!temporal.renameTo(archivo)) {
            temporal.delete()
            throw IOException("No se pudo reemplazar la caché de mercado")
        }
    }

    @Synchronized
    fun borrar() {
        archivo.delete()
        File(carpeta, "$ARCHIVO.tmp").delete()
    }

    private companion object {
        const val ARCHIVO = "mercado.json"
    }
}
