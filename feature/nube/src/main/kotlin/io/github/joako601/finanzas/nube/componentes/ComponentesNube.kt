package io.github.joako601.finanzas.nube.componentes

import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.launch

/** Transformación compartida para ocultar contraseñas sin reasignar en cada recomposición. */
internal val TransformacionContrasena = PasswordVisualTransformation()

/**
 * Colores semánticos compartidos por las pantallas de `feature:nube` (paleta "Tinta & Papel").
 *
 * Centraliza el verde de éxito claro/oscuro, el color de contraste sobre verde y los tonos
 * monocromáticos de la mascota para evitar hex duplicados entre Login y MascotaLogin.
 */
internal object ColoresSemanticosNube {
    private val ExitoClaro = Color(0xFF16803C)
    private val ExitoOscuro = Color(0xFF30D158)

    @Composable
    @ReadOnlyComposable
    fun esTemaOscuro(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable
    @ReadOnlyComposable
    fun exito(): Color = if (esTemaOscuro()) ExitoOscuro else ExitoClaro


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
    icono: (@Composable () -> Unit)? = null,
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
            if (icono != null) {
                icono()
            } else {
                IconoAlerta(tint = colorError)
            }
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Indica si el usuario desactivó las animaciones del sistema (`ANIMATOR_DURATION_SCALE == 0f`). */
@Composable
internal fun rememberAnimacionesDesactivadasNube(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Retroalimentación táctil "Seda iOS" (`scale 0.97` con resorte amortiguado + háptica sutil).
 * Respeta la preferencia del sistema de reducir/quitar animaciones.
 */
@Composable
internal fun Modifier.toqueTactil(interactionSource: MutableInteractionSource): Modifier {
    val sinAnimaciones = rememberAnimacionesDesactivadasNube()
    val escala = remember { Animatable(1f) }
    val haptica = LocalHapticFeedback.current
    LaunchedEffect(interactionSource, sinAnimaciones) {
        if (sinAnimaciones) escala.snapTo(1f)
        interactionSource.interactions.collect { interaccion ->
            when (interaccion) {
                is PressInteraction.Press -> {
                    haptica.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!sinAnimaciones) {
                        launch {
                            escala.animateTo(
                                targetValue = 0.97f,
                                animationSpec = spring(dampingRatio = 0.90f, stiffness = 500f),
                            )
                        }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (!sinAnimaciones) {
                        launch {
                            escala.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.90f, stiffness = 500f),
                            )
                        }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = escala.value
        scaleY = escala.value
    }
}

/**
 * Animación Lottie 100% local desde `res/raw/`, respetuosa con movimiento reducido.
 *
 * Si el usuario tiene desactivadas las animaciones del sistema (`ANIMATOR_DURATION_SCALE == 0f`),
 * congela la composición en el último frame (`1f`) para mostrar el ícono completo sin movimiento.
 * Si [descripcionContenido] es `null`, se marca como decorativa para TalkBack.
 */
@Composable
internal fun AnimacionLottie(
    @RawRes resId: Int,
    modifier: Modifier = Modifier,
    iteraciones: Int = 1,
    descripcionContenido: String? = null,
) {
    val animacionesDesactivadas = rememberAnimacionesDesactivadasNube()
    val composicion by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progreso by animateLottieCompositionAsState(
        composition = composicion,
        iterations = iteraciones,
        isPlaying = !animacionesDesactivadas,
    )
    val modificadorSemantica = if (descripcionContenido != null) {
        modifier.semantics { contentDescription = descripcionContenido }
    } else {
        modifier.clearAndSetSemantics {}
    }

    LottieAnimation(
        composition = composicion,
        progress = { if (animacionesDesactivadas) 1f else progreso },
        modifier = modificadorSemantica,
    )
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
