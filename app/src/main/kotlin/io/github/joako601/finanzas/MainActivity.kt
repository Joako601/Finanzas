package io.github.joako601.finanzas

import android.app.KeyguardManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.joako601.finanzas.ui.perfil.PreferenciasApp
import io.github.joako601.finanzas.ui.perfil.ModoTema
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.joako601.finanzas.ui.componentes.EstadoSplashKren
import io.github.joako601.finanzas.ui.componentes.SplashAnimadoKren
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.perfil.PreferenciasPrivacidad
import io.github.joako601.finanzas.ui.perfil.aplicarPantallaSegura
import io.github.joako601.finanzas.ui.perfil.dispositivoSeguro
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Única Activity de la app. Con Compose, las pantallas son funciones y no Activities;
 * esta solo pone el contenido en la ventana, coordina el splash animado de apertura y
 * aplica "Privacidad y seguridad": pantalla segura y bloqueo con la credencial del teléfono,
 * que se pide con el logo del splash detenido (no en una pantalla aparte).
 */
class MainActivity : ComponentActivity() {

    private val privacidad by lazy { PreferenciasPrivacidad(this) }
    private val bloqueada = mutableStateOf(false)
    private var autenticando = false

    private val pedirCredencial = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        autenticando = false
        if (it.resultCode == RESULT_OK) {
            desbloqueada = true
            bloqueada.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dibuja detrás de las barras del sistema; Scaffold aplica los márgenes necesarios.
        enableEdgeToEdge()
        // Con bloqueo pero sin PIN en el teléfono no hay nada que proteger: no se fuerza la pantalla segura.
        aplicarPantallaSegura(window, privacidad.ocultarEnRecientes || (privacidad.bloqueo && dispositivoSeguro(this)))
        bloqueada.value = debeBloquear()
        PreferenciasApp.cargarTema(this)
        setContent {
            val modoTema by PreferenciasApp.tema.collectAsState()
            val oscuro = when (modoTema) {
                ModoTema.SISTEMA -> isSystemInDarkTheme()
                ModoTema.CLARO -> false
                ModoTema.OSCURO -> true
            }
            TemaFinanzas(oscuro = oscuro) {
                val sinAnimaciones = rememberAnimacionesDesactivadas()
                // `remember` (no `rememberSaveable`) + `EstadoSplashKren.yaMostrado` garantiza:
                // 1) 0 frames de parpadeo si las animaciones del sistema están desactivadas.
                // 2) No reinicia el splash si el usuario rota a mitad o después de la apertura.
                var mostrarSplash by remember {
                    // Bloqueada, el splash sale siempre: es donde se pide la huella.
                    val debeMostrar = (!sinAnimaciones && !EstadoSplashKren.yaMostrado) || bloqueada.value
                    EstadoSplashKren.yaMostrado = true
                    // Sin splash, la pantalla de abajo no tiene que esperar a nadie.
                    if (!debeMostrar) EstadoSplashKren.terminado = true
                    mutableStateOf(debeMostrar)
                }
                val estaBloqueada by bloqueada
                // Al volver tras la gracia, el logo se vuelve a armar y se detiene para pedir la huella.
                LaunchedEffect(estaBloqueada) { if (estaBloqueada) mostrarSplash = true }
                // Bloqueada, el splash se ve siempre: sin él quedaría una pantalla vacía sin salida.
                val splashVisible = mostrarSplash || estaBloqueada
                // Un solo lugar decide las barras: claras sobre el fondo oscuro del splash, y luego
                // según el tema de la app (un tema fijo distinto al del sistema también cuenta).
                LaunchedEffect(oscuro, splashVisible) {
                    val estilo = if (oscuro || splashVisible) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                }
                // Si se vuelve a bloquear mientras el splash se iba, otra vuelta del splash pide la huella.
                var vueltaSplash by remember { mutableIntStateOf(0) }
                // Guarda el estado de la app (pestaña, modo de entrada…) mientras está fuera por el bloqueo.
                val guardadorApp = rememberSaveableStateHolder()
                Box(modifier = Modifier.fillMaxSize()) {
                    // Bloqueada no se compone la app: ni sus diálogos ni sus hojas (ventanas aparte)
                    // quedan encima del splash, y TalkBack no tiene nada que leer debajo.
                    if (!estaBloqueada) guardadorApp.SaveableStateProvider("app") { ContenidoApp() }
                    if (splashVisible) {
                        key(vueltaSplash) {
                            SplashAnimadoKren(
                                onTerminar = { if (bloqueada.value) vueltaSplash++ else mostrarSplash = false },
                                bloqueada = { bloqueada.value },
                                onPedirDesbloqueo = ::pedirDesbloqueo,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val desde = enSegundoPlanoDesde
        if (desde != 0L && SystemClock.elapsedRealtime() - desde > GRACIA_MS) desbloqueada = false
        enSegundoPlanoDesde = 0L
        bloqueada.value = debeBloquear()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) enSegundoPlanoDesde = SystemClock.elapsedRealtime()
    }

    // Si el teléfono ya no tiene PIN, no se puede confirmar nada: no se deja al usuario fuera.
    private fun debeBloquear(): Boolean = privacidad.bloqueo && !desbloqueada && dispositivoSeguro(this)

    private fun pedirDesbloqueo() {
        if (autenticando) return
        // Sin dependencias nuevas (D-020): la pantalla de credencial del propio sistema.
        @Suppress("DEPRECATION")
        val intento = getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.bloqueo_motivo))
        if (intento == null) {
            bloqueada.value = false
            return
        }
        autenticando = true
        pedirCredencial.launch(intento)
    }

    companion object {
        /**
         * Vive lo que el proceso: sobrevive a girar la pantalla, no a que Android cierre la app.
         * Perfil la pone en `true` al activar el bloqueo (quien lo activa ya está dentro).
         */
        internal var desbloqueada = false
        private var enSegundoPlanoDesde = 0L

        /** Elegir un archivo de respaldo o abrir ajustes no debe volver a pedir el PIN. */
        private const val GRACIA_MS = 30_000L
    }
}
