package io.github.joako601.finanzas.registrador

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Servicio que el sistema conecta cuando el usuario concede el acceso a notificaciones.
 *
 * Qué registra:
 * - "publicada": cada notificación nueva O ACTUALIZADA de un paquete permitido. Android
 *   no avisa si es una actualización; se deduce porque la misma "clave" llega dos veces.
 *   "vecesEnSesion" ayuda a verlo sin procesar el archivo.
 * - "retirada": cuando una notificación desaparece, con la razón (el usuario la
 *   descartó, la app la canceló, se tocó...).
 * - "activa_al_conectar": las que ya estaban en la barra al conectarse el servicio,
 *   para no perder lo que llegó mientras estuvo desconectado.
 * - "conectado" / "desconectado": huecos en el registro. Si el ahorro de batería de
 *   Samsung mata el servicio, aquí se ve cuánto tiempo estuvo ciego.
 *
 * De los paquetes NO permitidos solo se guarda el nombre (para poder activarlos),
 * nunca el contenido.
 */
class RegistradorService : NotificationListenerService() {

    private lateinit var preferencias: Preferencias
    private lateinit var bitacora: Bitacora
    private val vecesPorClave = mutableMapOf<String, Int>()

    override fun onCreate() {
        super.onCreate()
        preferencias = Preferencias(this)
        bitacora = Bitacora.de(this)
    }

    override fun onListenerConnected() {
        preferencias.marcarConexion(conectado = true, ahora = System.currentTimeMillis())
        bitacora.registrar(JSONObject().put("evento", "conectado"))
        // getActiveNotifications puede fallar si el sistema aún no termina de enlazar.
        val activas = runCatching { activeNotifications }.getOrNull().orEmpty()
        for (sbn in activas) procesar("activa_al_conectar", sbn)
    }

    override fun onListenerDisconnected() {
        preferencias.marcarConexion(conectado = false, ahora = System.currentTimeMillis())
        bitacora.registrar(JSONObject().put("evento", "desconectado"))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        procesar("publicada", sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (!esPermitido(sbn)) return
        vecesPorClave.remove(sbn.key)
        bitacora.registrar(
            JSONObject()
                .put("evento", "retirada")
                .put("paquete", sbn.packageName)
                .put("clave", sbn.key)
                .put("postTime", sbn.postTime)
                .put("razon", reason)
                .put("razonNombre", nombreRazon(reason)),
        )
    }

    private fun procesar(evento: String, sbn: StatusBarNotification, rankingMap: RankingMap? = null) {
        preferencias.marcarVisto(sbn.packageName)
        if (!esPermitido(sbn)) return

        val veces = (vecesPorClave[sbn.key] ?: 0) + 1
        vecesPorClave[sbn.key] = veces

        val ranking = rankingMap?.let { mapa ->
            Ranking().takeIf { mapa.getRanking(sbn.key, it) }
        }
        bitacora.registrar(
            Bitacora.describir(sbn, ranking)
                .put("evento", evento)
                .put("vecesEnSesion", veces),
        )
    }

    private fun esPermitido(sbn: StatusBarNotification): Boolean =
        sbn.packageName in preferencias.paquetesPermitidos

    private fun nombreRazon(razon: Int): String = when (razon) {
        REASON_CLICK -> "click"
        REASON_CANCEL -> "descartada_por_usuario"
        REASON_CANCEL_ALL -> "descartar_todas"
        REASON_APP_CANCEL -> "cancelada_por_app"
        REASON_APP_CANCEL_ALL -> "app_cancelo_todas"
        REASON_GROUP_SUMMARY_CANCELED -> "resumen_de_grupo_cancelado"
        REASON_GROUP_OPTIMIZATION -> "optimizacion_de_grupo"
        REASON_TIMEOUT -> "expiro"
        REASON_SNOOZED -> "pospuesta"
        REASON_CHANNEL_BANNED -> "canal_bloqueado"
        REASON_PACKAGE_BANNED -> "app_bloqueada"
        REASON_LISTENER_CANCEL -> "cancelada_por_lector"
        REASON_ASSISTANT_CANCEL -> "cancelada_por_asistente"
        REASON_LOCKDOWN -> "bloqueo"
        else -> "otra_$razon"
    }
}
