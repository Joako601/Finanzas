package io.github.joako601.finanzas.recordatorios

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.joako601.finanzas.ETIQUETA_LOG
import io.github.joako601.finanzas.FinanzasApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val PREFS_AVISOS = "finanzas_avisos"
private const val CLAVE_PERMISO_AVISOS_PEDIDO = "permiso_notificaciones_pedido"

/** Si ya se mostró al menos una vez el diálogo de permiso `POST_NOTIFICATIONS` en esta instalación. */
fun yaSePidioPermisoAvisos(context: Context): Boolean =
    context.getSharedPreferences(PREFS_AVISOS, Context.MODE_PRIVATE)
        .getBoolean(CLAVE_PERMISO_AVISOS_PEDIDO, false)

/** Marca de forma persistente y compartida (Inicio y Bóveda) que ya se pidió el permiso. */
fun marcarPermisoAvisosPedido(context: Context) {
    context.getSharedPreferences(PREFS_AVISOS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(CLAVE_PERMISO_AVISOS_PEDIDO, true)
        .apply()
}

/**
 * Decide si conviene lanzar el diálogo del sistema para `POST_NOTIFICATIONS` o mandar directo
 * a Ajustes.
 *
 * - Antes de Android 13 no existe permiso en tiempo de ejecución: siempre se abre Ajustes.
 * - Si el usuario ya concedió el permiso de la app pero silenció el canal `recordatorios_pagos`,
 *   el diálogo no sirve: se abre directamente la configuración de ese canal.
 * - En Android 13+, si nunca se pidió o el sistema aún permite mostrarlo
 *   (`shouldShowRequestPermissionRationale`), se solicita el permiso en la app.
 */
fun puedePedirDialogoPermisoAvisos(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    if (canalAvisosSilenciado(context)) return false
    if (!yaSePidioPermisoAvisos(context)) return true
    val activity = context.encontrarActivity() ?: return false
    return activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
}

/** Abre los ajustes del canal si solo el canal está silenciado, o los ajustes de notificaciones de la app. */
fun abrirAjustesDeAvisos(context: Context) {
    val intencion = if (canalAvisosSilenciado(context)) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Avisos.CANAL)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intencion)
}

/** Dispara el programador en segundo plano al activarse las notificaciones (desde diálogo o al volver de Ajustes). */
fun dispararProgramadorAvisos(context: Context) {
    val contenedor = (context.applicationContext as FinanzasApp).contenedor
    contenedor.alcance.launch {
        try {
            contenedor.programador.ejecutar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(ETIQUETA_LOG, "Falló el programador de avisos", e)
            // Un fallo transitorio al reactivar notificaciones no debe cerrar la app.
        }
    }
}

private tailrec fun Context.encontrarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarActivity()
    else -> null
}
