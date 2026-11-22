package io.github.joako601.finanzas.nube.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Transformación compartida para ocultar contraseñas sin reasignar en cada recomposición. */
internal val TransformacionContrasena = PasswordVisualTransformation()

/**
 * Colores semánticos compartidos por las pantallas de `feature:nube`.
 *
 * Centraliza el verde de éxito claro/oscuro, el color de contraste sobre verde y el tono
 * claro del hocico en modo oscuro para evitar hex duplicados entre Login y MascotaLogin.
 */
internal object ColoresSemanticosNube {
    private val ExitoClaro = Color(0xFF0F9D58)
    private val ExitoOscuro = Color(0xFF3DDC97)
    private val SobreExitoClaro = Color.White
    private val SobreExitoOscuro = Color(0xFF1E1E1E)
    private val HocicoOscuro = Color(0xFFFFF1B8)

    @Composable
    @ReadOnlyComposable
    fun esTemaOscuro(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable
    @ReadOnlyComposable
    fun exito(): Color = if (esTemaOscuro()) ExitoOscuro else ExitoClaro

    @Composable
    @ReadOnlyComposable
    fun sobreExito(): Color = if (esTemaOscuro()) SobreExitoOscuro else SobreExitoClaro

    @Composable
    @ReadOnlyComposable
    fun hocicoMascota(): Color =
        if (esTemaOscuro()) HocicoOscuro else MaterialTheme.colorScheme.secondaryContainer
}

/**
 * Banner de aviso global unificado entre inicio de sesión y registro.
 *
 * Agrupa sus descendientes y usa región viva cortés para que TalkBack anuncie el mensaje
 * automáticamente sin interrumpir la lectura en curso.
 */
@Composable
internal fun BannerAvisoNube(
    mensaje: String,
    modifier: Modifier = Modifier,
) {
    val colorError = MaterialTheme.colorScheme.error
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = colorError.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colorError.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconoAlerta(tint = colorError)
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Ícono de alerta minimalista con `drawWithCache` (sin librerías de íconos ni alocaciones por frame). */
@Composable
internal fun IconoAlerta(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .size(20.dp)
            .drawWithCache {
                val centro = Offset(size.width / 2f, size.height / 2f)
                val radio = size.minDimension * 0.46f
                val grosorPx = 2.dp.toPx()
                val trazoCirculo = Stroke(width = grosorPx)
                val inicioLinea = Offset(centro.x, centro.y - radio * 0.45f)
                val finLinea = Offset(centro.x, centro.y + radio * 0.12f)
                val centroPunto = Offset(centro.x, centro.y + radio * 0.45f)
                val radioPunto = 1.3.dp.toPx()

                onDrawBehind {
                    drawCircle(
                        color = tint,
                        radius = radio,
                        center = centro,
                        style = trazoCirculo,
                    )
                    drawLine(
                        color = tint,
                        start = inicioLinea,
                        end = finLinea,
                        strokeWidth = grosorPx,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = tint,
                        radius = radioPunto,
                        center = centroPunto,
                    )
                }
            },
    )
}

/** Ícono de ojo / ojo tachado con `drawWithCache` para alternar visibilidad de contraseña. */
@Composable
internal fun IconoVisibilidad(
    visible: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .size(22.dp)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val grosorPx = 1.8.dp.toPx()
                val trazo = Stroke(width = grosorPx, cap = StrokeCap.Round)
                val centro = Offset(w * 0.5f, h * 0.5f)
                val radioPupila = size.minDimension * 0.14f
                val contorno = Path().apply {
                    moveTo(w * 0.1f, h * 0.5f)
                    quadraticTo(w * 0.5f, h * 0.12f, w * 0.9f, h * 0.5f)
                    quadraticTo(w * 0.5f, h * 0.88f, w * 0.1f, h * 0.5f)
                }
                val inicioTachado = Offset(w * 0.18f, h * 0.82f)
                val finTachado = Offset(w * 0.82f, h * 0.18f)

                onDrawBehind {
                    drawPath(contorno, color = tint, style = trazo)
                    drawCircle(
                        color = tint,
                        radius = radioPupila,
                        center = centro,
                        style = trazo,
                    )
                    if (visible) {
                        drawLine(
                            color = tint,
                            start = inicioTachado,
                            end = finTachado,
                            strokeWidth = grosorPx,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            },
    )
}
