package io.github.joako601.finanzas.ui.tarjetas

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.recordatorios.abrirAjustesDeAvisos
import io.github.joako601.finanzas.recordatorios.dispararProgramadorAvisos
import io.github.joako601.finanzas.recordatorios.marcarPermisoAvisosPedido
import io.github.joako601.finanzas.recordatorios.notificacionesActivas
import io.github.joako601.finanzas.recordatorios.puedePedirDialogoPermisoAvisos
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas

/**
 * Invita a activar las notificaciones si están apagadas o su canal está silenciado. Se vuelve
 * a revisar cada vez que la app regresa al frente (el usuario pudo activarlas en Ajustes) y
 * dispara el programador en cuanto pasan de inactivas a activas.
 */
@Composable
fun TarjetaActivarAvisos(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var activas by remember { mutableStateOf(notificacionesActivas(context)) }

    LifecycleResumeEffect(Unit) {
        val antes = activas
        val ahora = notificacionesActivas(context)
        activas = ahora
        if (!antes && ahora) {
            dispararProgramadorAvisos(context)
        }
        onPauseOrDispose { }
    }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        marcarPermisoAvisosPedido(context)
        val ahora = notificacionesActivas(context)
        activas = ahora
        if (ahora) {
            // Muestra de inmediato cualquier aviso del día que estaba esperando el permiso.
            dispararProgramadorAvisos(context)
        }
    }

    if (activas) return

    TarjetaFinanzas(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.activar_avisos_titulo), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.activar_avisos_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BotonPrincipal(
                texto = stringResource(R.string.activar_avisos_boton),
                onClick = {
                    if (puedePedirDialogoPermisoAvisos(context)) {
                        pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        abrirAjustesDeAvisos(context)
                    }
                },
            )
        }
    }
}
