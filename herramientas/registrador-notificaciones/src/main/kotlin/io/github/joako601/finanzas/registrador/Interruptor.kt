package io.github.joako601.finanzas.registrador

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import org.json.JSONObject

/**
 * Prende y apaga el registrador sin quitarle el acceso a notificaciones.
 *
 * Apagar = deshabilitar el componente [RegistradorService]: Android lo desconecta y no
 * lo vuelve a despertar, así que no gasta nada. El permiso concedido se conserva; al
 * prender, se pide al sistema que lo reconecte y vuelve a registrar.
 *
 * Cada cambio queda en la bitácora ("pausado_por_usuario" / "reanudado_por_usuario")
 * para que el corpus distinga "no llegó nada" de "estaba apagado".
 */
object Interruptor {

    private fun componente(context: Context) = ComponentName(context, RegistradorService::class.java)

    fun encendido(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(componente(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun cambiar(context: Context, encender: Boolean) {
        if (encender == encendido(context)) return
        val bitacora = Bitacora.de(context)
        if (!encender) bitacora.registrar(JSONObject().put("evento", "pausado_por_usuario"))
        context.packageManager.setComponentEnabledSetting(
            componente(context),
            if (encender) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (encender) {
            bitacora.registrar(JSONObject().put("evento", "reanudado_por_usuario"))
            // Sin esto, Android podría tardar hasta el siguiente reinicio en reconectarlo.
            NotificationListenerService.requestRebind(componente(context))
        } else {
            Preferencias(context).marcarConexion(conectado = false, ahora = System.currentTimeMillis())
        }
    }
}
