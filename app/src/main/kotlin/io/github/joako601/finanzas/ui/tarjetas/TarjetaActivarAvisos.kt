package io.github.joako601.finanzas.ui.tarjetas

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.recordatorios.notificacionesActivas
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import kotlinx.coroutines.launch

/**
 * Invita a activar las notificaciones si están apagadas. Se vuelve a revisar cada vez
 * que la app regresa al frente (el usuario pudo activarlas en Ajustes).
 *
 * Android 13+ permite pedir el permiso desde la app; si el usuario ya lo negó, el
 * sistema deja de mostrar el diálogo, así que el botón lleva a los ajustes de
 * notificaciones de la app.
 */
@Composable
fun TarjetaActivarAvisos(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var activas by remember { mutableStateOf(notificacionesActivas(context)) }
    var yaSePidio by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        activas = notificacionesActivas(context)
        onPauseOrDispose { }
    }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        yaSePidio = true
        activas = concedido || notificacionesActivas(context)
        if (activas) {
            // Muestra de inmediato cualquier aviso del día que estaba esperando el permiso.
            val contenedor = (context.applicationContext as FinanzasApp).contenedor
            contenedor.alcance.launch { contenedor.programador.ejecutar() }
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !yaSePidio) {
                        pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            )
        }
    }
}
