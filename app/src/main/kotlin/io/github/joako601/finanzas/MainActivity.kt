package io.github.joako601.finanzas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.joako601.finanzas.ui.movimientos.PantallaMovimientos
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Única Activity de la app. Con Compose, las pantallas son funciones y no Activities;
 * esta solo pone el contenido en la ventana.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dibuja detrás de las barras del sistema; Scaffold aplica los márgenes necesarios.
        enableEdgeToEdge()
        setContent {
            TemaFinanzas {
                PantallaMovimientos()
            }
        }
    }
}
