package io.github.joako601.finanzas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.joako601.finanzas.ui.componentes.EstadoSplashKren
import io.github.joako601.finanzas.ui.componentes.SplashAnimadoKren
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Única Activity de la app. Con Compose, las pantallas son funciones y no Activities;
 * esta solo pone el contenido en la ventana y coordina el splash animado de apertura.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dibuja detrás de las barras del sistema; Scaffold aplica los márgenes necesarios.
        enableEdgeToEdge()
        setContent {
            TemaFinanzas {
                val sinAnimaciones = rememberAnimacionesDesactivadas()
                // `remember` (no `rememberSaveable`) + `EstadoSplashKren.yaMostrado` garantiza:
                // 1) 0 frames de parpadeo si las animaciones del sistema están desactivadas.
                // 2) No reinicia el splash si el usuario rota a mitad o después de la apertura.
                var mostrarSplash by remember {
                    val debeMostrar = !sinAnimaciones && !EstadoSplashKren.yaMostrado
                    EstadoSplashKren.yaMostrado = true
                    mutableStateOf(debeMostrar)
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    ContenidoApp()
                    if (mostrarSplash) {
                        SplashAnimadoKren(
                            onTerminar = { mostrarSplash = false },
                        )
                    }
                }
            }
        }
    }
}
