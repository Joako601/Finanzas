package io.github.joako601.finanzas.ui.componentes

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Tarjeta blanca (gris carbón en modo oscuro) sin elevación y con borde sutil de 1 dp:
 * el bloque básico de la interfaz ("Aireada Apple").
 */
@Composable
fun TarjetaFinanzas(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = contenido,
    )
}

private val CurvaSaldoRodante = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Indica si el usuario desactivó las animaciones del sistema (`ANIMATOR_DURATION_SCALE == 0f`). */
@Composable
internal fun rememberAnimacionesDesactivadas(): Boolean {
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
    val sinAnimaciones = rememberAnimacionesDesactivadas()
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
 * Muestra un monto en formato tabular (`tnum`) animando suavemente sus cambios en 360 ms
 * con curva "Seda iOS". Si las animaciones están desactivadas, actualiza sin interpolación.
 */
@Composable
fun TextoMontoAnimado(
    monto: Dinero,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val estiloTabular = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val textoFinal = remember(monto) { monto.formatear() }
    var origenCentavos by remember { mutableLongStateOf(monto.centavos) }
    var destinoCentavos by remember { mutableLongStateOf(monto.centavos) }
    val progreso = remember { Animatable(1f) }

    LaunchedEffect(monto.centavos, sinAnimaciones) {
        if (sinAnimaciones || monto.centavos == destinoCentavos) {
            origenCentavos = monto.centavos
            destinoCentavos = monto.centavos
            progreso.snapTo(1f)
        } else {
            origenCentavos = interpolarCentavos(origenCentavos, destinoCentavos, progreso.value)
            destinoCentavos = monto.centavos
            progreso.snapTo(0f)
            progreso.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = CurvaSaldoRodante),
            )
        }
    }

    val centavosVisibles = when {
        sinAnimaciones -> monto.centavos
        progreso.value >= 1f -> destinoCentavos
        else -> interpolarCentavos(origenCentavos, destinoCentavos, progreso.value)
    }

    Text(
        text = Dinero(centavosVisibles).formatear(),
        style = estiloTabular,
        color = color,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(textoFinal) },
    )
}

private fun interpolarCentavos(origen: Long, destino: Long, progreso: Float): Long {
    if (progreso <= 0f) return origen
    if (progreso >= 1f) return destino
    val delta = (destino.toDouble() - origen.toDouble()) * progreso.toDouble()
    return (origen.toDouble() + delta).roundToLong().coerceIn(-Long.MAX_VALUE, Long.MAX_VALUE)
}

/** Botón de la acción principal: tinta con texto blanco (blanco con texto negro en oscuro), ancho completo. */
@Composable
fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = habilitado,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toqueTactil(interactionSource),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Botón de una acción secundaria: borde y texto del color del texto (negro en claro, blanco
 * en oscuro), del mismo tamaño que [BotonPrincipal]. El estilo de Material 3 por omisión
 * los pone grises y parecen deshabilitados; es el mismo trato que "Estado de cuenta".
 */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toqueTactil(interactionSource),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ícono dentro de un círculo de color suave, como encabezado de una fila. */
@Composable
fun IconoEnCirculo(@DrawableRes icono: Int, tamano: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .background(TemaFinanzas.colores.fondoIcono),
        contentAlignment = Alignment.Center,
    ) {
        // Decorativo: el nombre de la categoría ya está escrito al lado.
        Icon(painterResource(icono), contentDescription = null, tint = TemaFinanzas.colores.icono, modifier = Modifier.size(tamano * 0.55f))
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
fun AnimacionLottie(
    @RawRes resId: Int,
    modifier: Modifier = Modifier,
    iteraciones: Int = 1,
    descripcionContenido: String? = null,
) {
    val animacionesDesactivadas = rememberAnimacionesDesactivadas()
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
