package io.github.joako601.finanzas.recordatorios

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.joako601.finanzas.MainActivity
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.recordatorios.Recordatorio
import io.github.joako601.finanzas.core.domain.recordatorios.TipoRecordatorio
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.hoy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Construye y publica las notificaciones de recordatorio.
 *
 * Privacidad: los montos y nombres de tarjeta solo se ven con el teléfono desbloqueado.
 * En la pantalla de bloqueo aparece una versión pública sin datos ("Tienes un
 * recordatorio de pago"), porque un teléfono prestado o a la vista de otros está en
 * el alcance del modelo de amenazas.
 */
class Avisos(private val context: Context) {

    private val notificaciones = context.getSystemService(NotificationManager::class.java)

    /** Crea el canal; Android lo ignora si ya existe, así que se puede llamar siempre. */
    fun crearCanal() {
        notificaciones.createNotificationChannel(
            NotificationChannel(
                CANAL,
                context.getString(R.string.canal_recordatorios),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.canal_recordatorios_descripcion) },
        )
    }

    fun puedeMostrar(): Boolean = notificacionesActivas(context)

    fun mostrar(recordatorio: Recordatorio) {
        crearCanal()
        val nombre = recordatorio.nombre
        val referencia = recordatorio.fechaReferencia
        // Se calcula contra la fecha real de entrega (no la programada) para no decir "Mañana" si llegó al día siguiente.
        val diasHastaReferencia = hoy().daysUntil(referencia)
        // Llegó después de su fecha (p. ej. pasó la medianoche): "Hoy pagas" sería falso y ya no sirve.
        if (diasHastaReferencia < 0 && recordatorio.tipo != TipoRecordatorio.CAPTURAR_ESTADO) return
        val (titulo, texto) = when (recordatorio.tipo) {
            TipoRecordatorio.ANTES_DEL_CORTE -> Pair(
                if (diasHastaReferencia <= 0) {
                    context.getString(R.string.aviso_corte_titulo_hoy, nombre)
                } else {
                    context.getString(R.string.aviso_corte_titulo, nombre)
                },
                context.getString(R.string.aviso_corte_texto, referencia.plus(1, DateTimeUnit.DAY).formatoCorto()),
            )
            TipoRecordatorio.CAPTURAR_ESTADO -> Pair(
                context.getString(R.string.aviso_capturar_titulo, nombre),
                context.getString(R.string.aviso_capturar_texto, referencia.formatoCorto()),
            )
            TipoRecordatorio.PAGO_PROXIMO -> Pair(
                context.getString(
                    R.string.aviso_pago_titulo,
                    recordatorio.monto?.formatear().orEmpty(),
                    nombre,
                    referencia.formatoCorto(),
                ),
                if (diasHastaReferencia <= 0) {
                    context.getString(R.string.aviso_pago_texto_hoy)
                } else {
                    context.resources.getQuantityString(
                        R.plurals.aviso_pago_texto,
                        diasHastaReferencia,
                        diasHastaReferencia,
                    )
                },
            )
            TipoRecordatorio.PAGO_RECURRENTE -> Pair(
                if (diasHastaReferencia <= 0) {
                    context.getString(R.string.aviso_recurrente_titulo_hoy, nombre)
                } else {
                    context.getString(R.string.aviso_recurrente_titulo, nombre)
                },
                context.getString(R.string.aviso_recurrente_texto, recordatorio.monto?.formatear().orEmpty()),
            )
        }

        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val publica = Notification.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_cat_tarjeta)
            .setContentTitle(context.getString(R.string.aviso_publico))
            .build()

        val notificacion = Notification.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_cat_tarjeta)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .build()

        // El id sale de la clave: si por alguna razón se publica dos veces, se reemplaza.
        notificaciones.notify(recordatorio.clave.hashCode(), notificacion)
    }

    /**
     * Alerta de tipo de cambio de Mercado, en su propio canal para que se pueda silenciar aparte.
     * Mismo criterio de privacidad: en la pantalla de bloqueo no aparecen moneda ni tasa.
     */
    fun mostrarAlertaCambio(id: String, titulo: String, texto: String) {
        notificaciones.createNotificationChannel(
            NotificationChannel(
                CANAL_MERCADO,
                context.getString(R.string.canal_alertas_cambio),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.canal_alertas_cambio_descripcion) },
        )
        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val publica = Notification.Builder(context, CANAL_MERCADO)
            .setSmallIcon(R.drawable.ic_cat_tarjeta)
            .setContentTitle(context.getString(R.string.aviso_publico_mercado))
            .build()
        val notificacion = Notification.Builder(context, CANAL_MERCADO)
            .setSmallIcon(R.drawable.ic_cat_tarjeta)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .build()
        notificaciones.notify("alerta_cambio:$id".hashCode(), notificacion)
    }

    companion object {
        const val CANAL = "recordatorios_pagos"
        const val CANAL_MERCADO = "alertas_tipo_cambio"
    }
}
