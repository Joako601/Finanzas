package io.github.joako601.finanzas.registrador

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Foto del estado del registrador que muestra la pantalla. */
private data class Estado(
    val acceso: Boolean,
    val conectado: Boolean,
    val cambioDeConexionMillis: Long,
    val eventos: Long,
    val ultimoEventoMillis: Long,
    val tamanoKb: Long,
    val permitidos: Set<String>,
    /** Paquete → nombre visible de la app, o null si no está instalada o no es visible. */
    val paquetes: List<Pair<String, String?>>,
)

/**
 * Pantalla de control del registrador: estado del acceso, qué apps se registran y
 * cuántos eventos lleva. Se refresca cada 2 segundos mientras está visible.
 */
class MainActivity : ComponentActivity() {

    private lateinit var preferencias: Preferencias
    private lateinit var bitacora: Bitacora

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferencias = Preferencias(this)
        bitacora = Bitacora.de(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Pantalla()
                }
            }
        }
    }

    private fun leerEstado(): Estado {
        val permitidos = preferencias.paquetesPermitidos
        return Estado(
            acceso = getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(this, RegistradorService::class.java)),
            conectado = preferencias.conectado,
            cambioDeConexionMillis = preferencias.cambioDeConexionMillis,
            eventos = preferencias.eventosRegistrados,
            ultimoEventoMillis = preferencias.ultimoEventoMillis,
            tamanoKb = bitacora.archivo.length() / 1024,
            permitidos = permitidos,
            paquetes = (permitidos + preferencias.paquetesVistos).sorted().map { it to nombreDeApp(it) },
        )
    }

    /** Abre directo el interruptor de acceso a notificaciones de esta app. */
    private fun abrirAjustesDeAcceso() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(this, RegistradorService::class.java).flattenToString(),
        )
        // Algunos fabricantes no implementan la pantalla de detalle; se cae a la lista general.
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    /** Información de la app, donde Samsung pone "Batería" → "Sin restricciones". */
    private fun abrirAjustesDeBateria() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun nombreDeApp(paquete: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    @Composable
    private fun Pantalla() {
        // Avanza cada 2 s y en cada acción; `remember(refresco)` vuelve a leer el estado.
        var refresco by remember { mutableIntStateOf(0) }
        var confirmarBorrado by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(2_000)
                refresco++
            }
        }
        val estado = remember(refresco) { leerEstado() }
        val formato = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Registrador de notificaciones", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Herramienta de la fase 0. Guarda el contenido de las notificaciones de las apps " +
                    "activadas abajo, solo en este teléfono. No tiene internet.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()
            Text("Acceso a notificaciones: " + if (estado.acceso) "concedido" else "NO concedido")
            if (estado.cambioDeConexionMillis > 0) {
                Text(
                    "Servicio " + (if (estado.conectado) "conectado" else "DESCONECTADO") +
                        " desde " + formato.format(Date(estado.cambioDeConexionMillis)),
                )
            }
            Button(onClick = ::abrirAjustesDeAcceso) {
                Text(if (estado.acceso) "Revisar acceso" else "Conceder acceso")
            }
            OutlinedButton(onClick = ::abrirAjustesDeBateria) { Text("Batería: poner \"Sin restricciones\"") }

            HorizontalDivider()
            Text("Eventos registrados: ${estado.eventos}")
            if (estado.ultimoEventoMillis > 0) {
                Text("Último: " + formato.format(Date(estado.ultimoEventoMillis)))
            }
            Text("Tamaño del archivo: ${estado.tamanoKb} KB")

            HorizontalDivider()
            Text("Apps a registrar", style = MaterialTheme.typography.titleMedium)
            Text(
                "Aparecen las sugeridas y cualquier app que haya publicado una notificación " +
                    "(solo su nombre). Si un banco no aparece, espera a que mande una.",
                style = MaterialTheme.typography.bodySmall,
            )
            estado.paquetes.forEach { (paquete, nombre) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nombre ?: "(no instalada o nombre desconocido)")
                        Text(paquete, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = paquete in estado.permitidos,
                        onCheckedChange = { activar ->
                            preferencias.paquetesPermitidos =
                                if (activar) estado.permitidos + paquete else estado.permitidos - paquete
                            refresco++
                        },
                    )
                }
            }

            HorizontalDivider()
            OutlinedButton(onClick = { confirmarBorrado = true }) { Text("Borrar registro") }
        }

        if (confirmarBorrado) {
            AlertDialog(
                onDismissRequest = { confirmarBorrado = false },
                title = { Text("¿Borrar el registro?") },
                text = { Text("Se pierde todo lo capturado. Exporta antes con exportar.sh.") },
                confirmButton = {
                    TextButton(onClick = {
                        bitacora.borrar()
                        confirmarBorrado = false
                        refresco++
                    }) { Text("Borrar") }
                },
                dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
            )
        }
    }
}
