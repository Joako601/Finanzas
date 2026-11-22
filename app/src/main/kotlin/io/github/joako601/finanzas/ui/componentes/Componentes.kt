package io.github.joako601.finanzas.ui.componentes

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Tarjeta blanca (gris carbón en modo oscuro) con esquinas redondeadas y una sombra
 * ligera: el bloque básico de la interfaz.
 */
@Composable
fun TarjetaFinanzas(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = contenido,
    )
}

/** Botón de la acción principal: amarillo con texto negro, de ancho completo. */
@Composable
fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
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
    val context = LocalContext.current
    val animacionesDesactivadas = remember(context) {
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
