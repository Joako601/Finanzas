package io.github.joako601.finanzas.registrador

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Escribe el registro como JSON Lines: un objeto JSON por línea, agregado al final del
 * archivo. Si el proceso muere a la mitad, como mucho se pierde la última línea, y el
 * archivo se puede leer línea por línea sin cargarlo entero.
 *
 * Se usa org.json, que viene con Android, para no agregar dependencias.
 *
 * Las escrituras van a un único hilo propio: el sistema llama al servicio en el hilo
 * principal y no conviene escribir a disco ahí.
 *
 * Hay una sola instancia por proceso ([de]). El servicio y la pantalla corren en el
 * mismo proceso y comparten ese hilo, así que "borrar" nunca se cruza con una escritura.
 */
class Bitacora private constructor(directorio: File, private val preferencias: Preferencias) {

    val archivo: File = File(directorio, NOMBRE_ARCHIVO)
    private val escritor = Executors.newSingleThreadExecutor()

    fun registrar(evento: JSONObject) {
        val ahora = System.currentTimeMillis()
        evento.put("v", VERSION_FORMATO)
        evento.put("registradoEn", ahora)
        evento.put("zona", TimeZone.getDefault().id)
        val linea = evento.toString()
        escritor.execute {
            archivo.appendText(linea + "\n")
            preferencias.contarEvento(ahora)
        }
    }

    fun borrar() {
        escritor.execute {
            archivo.delete()
            preferencias.reiniciarContadores()
        }
    }

    companion object {
        const val NOMBRE_ARCHIVO = "registro.jsonl"

        @Volatile
        private var instancia: Bitacora? = null

        fun de(context: Context): Bitacora = instancia ?: synchronized(this) {
            instancia ?: Bitacora(context.applicationContext.filesDir, Preferencias(context)).also { instancia = it }
        }

        /** Se incrementa si cambia la forma de las líneas, para poder leer registros viejos. */
        const val VERSION_FORMATO = 1

        /** Límite de profundidad al recorrer Bundles anidados en los extras. */
        private const val PROFUNDIDAD_MAXIMA = 4

        /** Describe una notificación con todo lo que el sistema nos deja ver. */
        fun describir(
            sbn: StatusBarNotification,
            ranking: NotificationListenerService.Ranking?,
        ): JSONObject {
            val n = sbn.notification
            return JSONObject().apply {
                put("paquete", sbn.packageName)
                put("clave", sbn.key)
                put("id", sbn.id)
                put("etiqueta", sbn.tag ?: JSONObject.NULL)
                put("postTime", sbn.postTime)
                put("usuario", sbn.user.toString())
                put("esGrupo", sbn.isGroup)
                put("claveGrupo", sbn.groupKey)
                put("claveGrupoForzada", sbn.overrideGroupKey ?: JSONObject.NULL)
                put("enCurso", sbn.isOngoing)
                put("descartable", sbn.isClearable)
                put("notificacion", describirNotificacion(n))
                if (ranking != null) put("ranking", describirRanking(ranking))
            }
        }

        private fun describirNotificacion(n: Notification): JSONObject = JSONObject().apply {
            put("when", n.`when`)
            put("canal", n.channelId ?: JSONObject.NULL)
            put("categoria", n.category ?: JSONObject.NULL)
            put("flags", n.flags)
            put("esResumenDeGrupo", n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
            put("grupo", n.group ?: JSONObject.NULL)
            put("sortKey", n.sortKey ?: JSONObject.NULL)
            put("visibilidad", n.visibility)
            put("numero", n.number)
            put("ticker", n.tickerText?.toString() ?: JSONObject.NULL)
            // Versión que se muestra en la pantalla de bloqueo cuando el contenido se oculta.
            val publica = n.publicVersion
            put("tieneVersionPublica", publica != null)
            if (publica != null) put("versionPublicaExtras", bundleAJson(publica.extras, 0))
            // Todos los extras, no solo los conocidos: así aparecen campos inesperados.
            put("extras", bundleAJson(n.extras, 0))
        }

        private fun describirRanking(r: NotificationListenerService.Ranking): JSONObject = JSONObject().apply {
            put("importancia", r.importance)
            put("canalNombre", r.channel?.name?.toString() ?: JSONObject.NULL)
            put("suspendida", r.isSuspended)
            put("sentimientoUsuario", r.userSentiment)
        }

        /**
         * Convierte un Bundle a JSON con el tipo de cada valor. Textos, números y
         * booleanos se guardan tal cual; imágenes, íconos y objetos complejos solo como
         * nombre de tipo, para no llenar el archivo de binarios.
         */
        @Suppress("DEPRECATION") // Bundle.get(String) sin tipo es justo lo que necesitamos aquí.
        private fun bundleAJson(bundle: Bundle?, profundidad: Int): JSONObject {
            val json = JSONObject()
            if (bundle == null) return json
            for (llave in bundle.keySet().sorted()) {
                try {
                    val valor = bundle.get(llave)
                    json.put(
                        llave,
                        JSONObject()
                            .put("tipo", valor?.javaClass?.name ?: "null")
                            .put("valor", valorAJson(valor, profundidad)),
                    )
                } catch (e: Exception) {
                    // Un valor que no se puede leer no debe tirar el registro completo.
                    json.put(llave, JSONObject().put("error", e.javaClass.name))
                }
            }
            return json
        }

        private fun valorAJson(valor: Any?, profundidad: Int): Any = when (valor) {
            null -> JSONObject.NULL
            is CharSequence -> valor.toString()
            is Boolean, is Int, is Long -> valor
            is Float -> if (valor.isFinite()) valor.toDouble() else valor.toString()
            is Double -> if (valor.isFinite()) valor else valor.toString()
            is Bundle -> if (profundidad < PROFUNDIDAD_MAXIMA) bundleAJson(valor, profundidad + 1) else "Bundle"
            is Array<*> -> JSONArray().apply { valor.forEach { put(valorAJson(it, profundidad + 1)) } }
            is IntArray -> JSONArray(valor.toList())
            is LongArray -> JSONArray(valor.toList())
            else -> JSONObject.NULL // El tipo ya quedó en el campo "tipo".
        }
    }
}
