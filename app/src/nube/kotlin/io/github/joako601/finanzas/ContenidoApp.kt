package io.github.joako601.finanzas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.joako601.finanzas.nube.SeccionNube
import io.github.joako601.finanzas.ui.movimientos.PantallaMovimientos

/**
 * Contenido raíz de la distribución nube (D-020): agrega en el menú principal el acceso
 * opcional "Cuenta y catálogo" hacia [SeccionNube], sin acoplar `app/src/main` a Firebase.
 */
@Composable
fun ContenidoApp() {
    var enNube by rememberSaveable { mutableStateOf(false) }
    if (enNube) {
        SeccionNube(onSalir = { enNube = false })
    } else {
        PantallaMovimientos(onAbrirNube = { enNube = true })
    }
}
