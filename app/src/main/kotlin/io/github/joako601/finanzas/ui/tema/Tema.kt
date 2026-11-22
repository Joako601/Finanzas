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
import androidx.compose.ui.unit.sp

/*
 * Identidad visual "Tinta & Papel" (D-014): estética monocromática minimalista con
 * cero color decorativo. El color aparece únicamente en datos semánticos (ingresos,
 * gastos y avisos), garantizando contraste WCAG AA >= 4.5:1 en modo claro y oscuro.
 */

private val Tinta = Color(0xFF111111)
private val Papel = Color(0xFFF5F5F7)
private val GrisHumo = Color(0xFFE8E8ED)
private val GrisGrafito = Color(0xFF6E6E73)
private val GrisLineaClaro = Color(0xFFE5E5EA)

private val NegroOled = Color(0xFF000000)
private val CarbonIos = Color(0xFF1C1C1E)
private val GrisElevadoOscuro = Color(0xFF2C2C2E)
private val GrisPlata = Color(0xFF8E8E93)

private val EsquemaClaro = lightColorScheme(
    primary = Tinta,
    onPrimary = Color.White,
    primaryContainer = Tinta,
    onPrimaryContainer = Color.White,
    secondary = GrisGrafito,
    onSecondary = Color.White,
    secondaryContainer = GrisHumo,
    onSecondaryContainer = Tinta,
    tertiary = GrisGrafito,
    onTertiary = Color.White,
    background = Papel,
    onBackground = Tinta,
    surface = Papel,
    onSurface = Tinta,
    surfaceVariant = GrisHumo,
    onSurfaceVariant = GrisGrafito,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = GrisHumo,
    outline = Tinta,
    outlineVariant = GrisLineaClaro,
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEB),
    onErrorContainer = Color(0xFF7A140E),
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = GrisPlata,
    onSecondary = Color.Black,
    secondaryContainer = GrisElevadoOscuro,
    onSecondaryContainer = Papel,
    tertiary = GrisPlata,
    onTertiary = Color.Black,
    background = NegroOled,
    onBackground = Papel,
    surface = NegroOled,
    onSurface = Papel,
    surfaceVariant = GrisElevadoOscuro,
    onSurfaceVariant = GrisPlata,
    surfaceContainerLowest = CarbonIos,
    surfaceContainerLow = CarbonIos,
    surfaceContainer = CarbonIos,
    surfaceContainerHigh = CarbonIos,
    surfaceContainerHighest = GrisElevadoOscuro,
    outline = Papel,
    outlineVariant = GrisElevadoOscuro,
    error = Color(0xFFFF453A),
    onError = Color.Black,
    errorContainer = Color(0xFF4A1614),
    onErrorContainer = Color(0xFFFFD6D3),
)

/** Colores semánticos propios que Material no trae: sentido del dinero, avisos y fondo de íconos. */
@Immutable
data class ColoresFinanzas(
    val ingreso: Color,
    val gasto: Color,
    val aviso: Color,
    val fondoIcono: Color,
    val icono: Color,
)

private val ColoresClaros = ColoresFinanzas(
    ingreso = Color(0xFF16803C),
    gasto = Color(0xFFD92D20),
    aviso = Color(0xFFB54708),
    fondoIcono = GrisHumo,
    icono = Tinta,
)

private val ColoresOscuros = ColoresFinanzas(
    ingreso = Color(0xFF30D158),
    gasto = Color(0xFFFF453A),
    aviso = Color(0xFFFF9F0A),
    fondoIcono = GrisElevadoOscuro,
    icono = Papel,
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
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Escala tipográfica "Aireada Apple" con números tabulares (`tnum`) en cifras y saldos. */
private val Tipografia = Typography().run {
    val estiloDisplay = displayMedium.copy(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    )
    val estiloCuerpo = bodyLarge.copy(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val estiloCaption = bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    )
    copy(
        displayLarge = estiloDisplay,
        displayMedium = estiloDisplay,
        headlineLarge = estiloDisplay,
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum",
        ),
        titleMedium = titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
        ),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = estiloCuerpo,
        bodyMedium = estiloCuerpo,
        bodySmall = estiloCaption,
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = estiloCaption,
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
