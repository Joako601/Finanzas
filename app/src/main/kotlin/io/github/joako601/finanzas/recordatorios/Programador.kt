package io.github.joako601.finanzas.recordatorios

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.recordatorios.PlanRecordatorios
import io.github.joako601.finanzas.core.domain.recordatorios.RepositorioRecordatorios
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.ZoneId

/**
 * Corazón de los recordatorios. Cada ejecución:
 * 1. recalcula el plan con los datos actuales (si ya pagaste, el aviso de pago no existe);
 * 2. muestra los avisos vencidos que no se han entregado y registra la entrega con su
 *    hora programada y su hora real (para medir el retraso);
 * 3. programa UNA alarma para el siguiente aviso.
 *
 * Se ejecuta al abrir la app, cuando cambian los datos, cuando suena la alarma, al
 * reiniciar el teléfono, al cambiar la hora o la zona horaria y al actualizar la app.
 * Es idempotente: ejecutarla de más no duplica avisos (la clave lo impide).
 *
 * Por qué AlarmManager y no WorkManager (D-015): WorkManager trae su propia base de datos
 * y agrega permisos al manifiesto (estado de red, servicio en primer plano) que la guardia
 * rechaza; para "despertar a tal hora y mostrar un aviso" basta la alarma del sistema.
 *
 * Alarma INEXACTA (setAndAllowWhileIdle): el sistema puede retrasarla unos minutos para
 * ahorrar batería, incluso en reposo profundo. Las alarmas exactas exigen un permiso
 * especial que Android 14+ niega por defecto; solo se pedirá si la medición lo justifica.
 */
class Programador(
    private val context: Context,
    private val libro: RepositorioLibro,
    private val tarjetas: RepositorioTarjetas,
    private val recordatorios: RepositorioRecordatorios,
    private val avisos: Avisos,
) {
    private val candado = Mutex()

    suspend fun ejecutar() = candado.withLock {
        val zona = ZoneId.systemDefault()
        val ahora: LocalDateTime = java.time.LocalDateTime.now(zona).toKotlinLocalDateTime()

        val cuentas = libro.observarCuentas().first().associateBy { it.id }
        val plan = PlanRecordatorios.calcular(
            tarjetas = tarjetas.observarTarjetas().first(),
            estados = tarjetas.observarEstadosDeCuenta().first(),
            pagos = pagosATarjetas(libro.observarMovimientos().first(), cuentas),
            hoy = ahora.date,
        )
        val seleccion = PlanRecordatorios.seleccionar(plan, ahora, recordatorios.clavesEntregadas())

        // Sin permiso de notificaciones no se marca nada como entregado: cuando el
        // usuario lo conceda, los avisos del día todavía se mostrarán.
        if (avisos.puedeMostrar()) {
            for (recordatorio in seleccion.mostrarAhora) {
                avisos.mostrar(recordatorio)
                recordatorios.registrarEntrega(
                    clave = recordatorio.clave,
                    tipo = recordatorio.tipo,
                    programadoMillis = recordatorio.momento.enMillis(zona),
                    entregadoMillis = System.currentTimeMillis(),
                )
            }
        }

        val alarmas = context.getSystemService(AlarmManager::class.java)
        val disparo = intencionDeAlarma(context)
        val siguiente = seleccion.siguiente
        if (siguiente == null) {
            alarmas.cancel(disparo)
        } else {
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, siguiente.momento.enMillis(zona), disparo)
        }
    }

    companion object {
        /** Una sola alarma para toda la app: siempre apunta al siguiente aviso. */
        fun intencionDeAlarma(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReceptorAlarma::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

private fun LocalDateTime.enMillis(zona: ZoneId): Long = toJavaLocalDateTime().atZone(zona).toInstant().toEpochMilli()

/** Atajo usado por la pantalla para saber si los avisos están activos. */
fun notificacionesActivas(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
