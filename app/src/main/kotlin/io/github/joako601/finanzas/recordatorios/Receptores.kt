package io.github.joako601.finanzas.recordatorios

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.joako601.finanzas.FinanzasApp
import kotlinx.coroutines.launch

/**
 * Corre el programador fuera del hilo principal. `goAsync` mantiene vivo el receptor
 * mientras termina la corrutina (el sistema da unos segundos; consultar la base y
 * publicar un aviso tarda mucho menos).
 */
private fun BroadcastReceiver.ejecutarProgramador(context: Context) {
    val pendiente = goAsync()
    val contenedor = (context.applicationContext as FinanzasApp).contenedor
    contenedor.alcance.launch {
        try {
            contenedor.programador.ejecutar()
        } finally {
            pendiente.finish()
        }
    }
}

/** Recibe la alarma programada. No exportado: solo nuestra PendingIntent lo invoca. */
class ReceptorAlarma : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ejecutarProgramador(context)
}

/**
 * Reprograma cuando el sistema pierde o mueve las alarmas: reinicio del teléfono,
 * cambio de hora o de zona horaria, y actualización de la app (Android borra las
 * alarmas de una app al actualizarla).
 *
 * Está exportado porque así lo exige recibir estos avisos del sistema, pero todos son
 * "broadcasts protegidos": ninguna otra app puede enviarlos. Aun así se valida la
 * acción, y reprogramar es inofensivo e idempotente.
 */
class ReceptorSistema : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACCIONES) ejecutarProgramador(context)
    }

    private companion object {
        val ACCIONES = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
