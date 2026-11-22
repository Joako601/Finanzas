package io.github.joako601.finanzas.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Identidad visual propia (D-014): fondo claro con tarjetas blancas, amarillo como
 * color de acción con texto negro encima, montos en verde y rojo, y un modo oscuro en
 * gris carbón. Inspirada en referencias que el autor eligió, sin logos ni elementos de
 * marca de ningún banco.
 *
 * Regla de contraste: el amarillo NUNCA es color de texto sobre fondo claro (no se lee).
 * En modo claro, `primary` es casi negro (texto de botones y encabezados) y el amarillo
 * vive en `primaryContainer` (botón principal, botón flotante) y `secondaryContainer`
 * (chips seleccionados). En modo oscuro el amarillo sí puede ser texto.
 */

private val Amarillo = Color(0xFFFDD835)
private val AmarilloSuave = Color(0xFFFFF1B8)
private val CasiNegro = Color(0xFF242424)
private val Morado = Color(0xFF6B3FD4)

private val EsquemaClaro = lightColorScheme(
    primary = CasiNegro,
    onPrimary = Color.White,
    primaryContainer = Amarillo,
    onPrimaryContainer = CasiNegro,
    secondary = Color(0xFF5C5C5C),
    onSecondary = Color.White,
    secondaryContainer = AmarilloSuave,
    onSecondaryContainer = CasiNegro,
    tertiary = Morado,
    onTertiary = Color.White,
    background = Color(0xFFF5F5F7),
    onBackground = CasiNegro,
    surface = Color(0xFFF5F5F7),
    onSurface = CasiNegro,
    surfaceVariant = Color(0xFFEEEEF0),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEEEF0),
    outline = CasiNegro,
    outlineVariant = Color(0xFFE2E2E6),
    error = Color(0xFFD93A3A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF690005),
)

private val EsquemaOscuro = darkColorScheme(
    primary = Amarillo,
    onPrimary = CasiNegro,
    primaryContainer = Amarillo,
    onPrimaryContainer = CasiNegro,
    secondary = Color(0xFFBDBDBD),
    onSecondary = CasiNegro,
    secondaryContainer = Color(0xFF524814),
    onSecondaryContainer = Color(0xFFFFF1B8),
    tertiary = Color(0xFFFF8A4C),
    onTertiary = CasiNegro,
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceContainerLowest = Color(0xFF2B2B2B),
    surfaceContainerLow = Color(0xFF2E2E2E),
    surfaceContainer = Color(0xFF323232),
    surfaceContainerHigh = Color(0xFF363636),
    surfaceContainerHighest = Color(0xFF3D3D3D),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF444444),
    error = Color(0xFFFF6B6B),
    onError = CasiNegro,
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colores que Material no trae: sentido del dinero y fondo de los íconos de categoría. */
@Immutable
data class ColoresFinanzas(
    val ingreso: Color,
    val gasto: Color,
    val fondoIcono: Color,
    val icono: Color,
)

private val ColoresClaros = ColoresFinanzas(
    ingreso = Color(0xFF0F9D58),
    gasto = Color(0xFFD93A3A),
    fondoIcono = Color(0xFFF0F0F2),
    icono = CasiNegro,
)

private val ColoresOscuros = ColoresFinanzas(
    ingreso = Color(0xFF3DDC97),
    gasto = Color(0xFFFF7A7A),
    fondoIcono = Color(0xFF3A3A3A),
    icono = Color(0xFFFF8A4C),
)

private val LocalColoresFinanzas = staticCompositionLocalOf { ColoresClaros }

/** Acceso a los colores propios: `TemaFinanzas.colores.ingreso`. */
object TemaFinanzas {
    val colores: ColoresFinanzas
        @Composable @ReadOnlyComposable
        get() = LocalColoresFinanzas.current
}

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** La tipografía del sistema con más peso en títulos y cifras, como en las referencias. */
private val Tipografia = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Tema de la app. Ya no usa el color dinámico de Android 12+ (colores tomados del fondo
 * de pantalla): la app tiene identidad propia (D-014).
 */
@Composable
fun TemaFinanzas(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalColoresFinanzas provides if (oscuro) ColoresOscuros else ColoresClaros) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            shapes = Formas,
            typography = Tipografia,
            content = contenido,
        )
    }
}
