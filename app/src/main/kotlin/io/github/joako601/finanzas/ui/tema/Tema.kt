package io.github.joako601.finanzas.ui.tema

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val VerdeFinanzas = Color(0xFF1B6B4E)

private val EsquemaClaro = lightColorScheme(primary = VerdeFinanzas)
private val EsquemaOscuro = darkColorScheme(primary = Color(0xFF8BD6B4))

/**
 * Tema de Material 3. En Android 12+ usa el color dinámico (los colores salen del fondo
 * de pantalla del usuario); en versiones anteriores, un verde fijo.
 */
@Composable
fun TemaFinanzas(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    val esquema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (oscuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        oscuro -> EsquemaOscuro
        else -> EsquemaClaro
    }
    MaterialTheme(colorScheme = esquema, content = contenido)
}
