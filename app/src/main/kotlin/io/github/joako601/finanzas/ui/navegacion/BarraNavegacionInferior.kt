package io.github.joako601.finanzas.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.componentes.toqueTactil

/**
 * Los 4 apartados principales de la barra de navegación inferior.
 *
 * - [INICIO]: pantalla principal de movimientos, cuentas, tarjetas y pagos fijos.
 * - [ANALISIS], [METAS], [BOVEDA]: apartados preparados en la arquitectura de navegación
 *   con vistas temporales minimalistas ("Próximamente").
 */
enum class PestanaPrincipal(
    @param:StringRes val etiquetaRes: Int,
    @param:StringRes val tituloPlaceholderRes: Int,
    @param:StringRes val descripcionPlaceholderRes: Int,
) {
    INICIO(
        etiquetaRes = R.string.nav_inicio,
        tituloPlaceholderRes = R.string.nav_inicio,
        descripcionPlaceholderRes = R.string.nav_inicio,
    ),
    ANALISIS(
        etiquetaRes = R.string.nav_analisis,
        tituloPlaceholderRes = R.string.nav_analisis_titulo,
        descripcionPlaceholderRes = R.string.nav_analisis_desc,
    ),
    METAS(
        etiquetaRes = R.string.nav_presupuesto,
        tituloPlaceholderRes = R.string.nav_presupuesto_titulo,
        descripcionPlaceholderRes = R.string.nav_presupuesto_desc,
    ),
    BOVEDA(
        etiquetaRes = R.string.nav_boveda,
        tituloPlaceholderRes = R.string.nav_boveda_titulo,
        descripcionPlaceholderRes = R.string.nav_boveda_desc,
    ),
}

/** Altura del cuerpo interactivo de la barra de pestañas (sin inset del sistema). */
val AlturaBarraNavegacion: Dp = 70.dp

/** Altura de la franja superior de difuminado progresivo. */
val AlturaDifuminadoBarra: Dp = 24.dp

/** Altura total ocupada por la barra + su difuminado superior para despejar FAB y listas. */
val AlturaTotalBarraInferior: Dp = AlturaBarraNavegacion + AlturaDifuminadoBarra

/**
 * Barra de navegación inferior con 4 apartados y difuminado superior progresivo
 * (*Tinta & Papel · Aireada Apple · Seda iOS*).
 *
 * El borde superior proyecta un velo degradado semitransparente de 24 dp hacia el mismo
 * color de superficie de la barra (`surfaceContainerLowest`) para que el contenido que hace
 * scroll detrás se funda sin salto cromático tanto en modo claro como en `#000000` OLED.
 */
@Composable
fun BarraNavegacionInferior(
    seleccionada: PestanaPrincipal,
    onSeleccionar: (PestanaPrincipal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorSuperficie = MaterialTheme.colorScheme.surfaceContainerLowest
    val colorBorde = MaterialTheme.colorScheme.outlineVariant

    // Gradiente multi-parada hacia `colorSuperficie` para evitar saltos entre fondo y barra.
    val difuminadoSuperior = remember(colorSuperficie) {
        Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.25f to colorSuperficie.copy(alpha = 0.16f),
            0.55f to colorSuperficie.copy(alpha = 0.48f),
            0.82f to colorSuperficie.copy(alpha = 0.80f),
            1.00f to colorSuperficie.copy(alpha = 0.95f),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Franja superior de difuminado progresivo (no intercepta toques de la lista).
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(AlturaDifuminadoBarra)
                .background(difuminadoSuperior),
        )

        // Cuerpo translúcido de la barra + Safe Area inferior.
        // `.pointerInput(Unit) {}` consume toques en los márgenes para que no atraviesen al LazyColumn.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorSuperficie.copy(alpha = 0.95f))
                .pointerInput(Unit) {}
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Línea cabello apoyada sobre la superficie base para no dejar fugas de opacidad.
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colorBorde.copy(alpha = 0.55f)),
            )

            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(min = AlturaBarraNavegacion)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PestanaPrincipal.entries.forEach { pestana ->
                    ItemBarraNavegacion(
                        pestana = pestana,
                        activo = pestana == seleccionada,
                        onClick = {
                            if (pestana != seleccionada) {
                                onSeleccionar(pestana)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemBarraNavegacion(
    pestana: PestanaPrincipal,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val interaccion = remember { MutableInteractionSource() }
    val etiqueta = stringResource(pestana.etiquetaRes)

    val colorContenido by animateColorAsState(
        targetValue = if (activo) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = if (sinAnimaciones) 0 else 180),
        label = "colorTab",
    )

    val progresoActivo by animateFloatAsState(
        targetValue = if (activo) 1f else 0f,
        animationSpec = if (sinAnimaciones) {
            tween(0)
        } else {
            spring(dampingRatio = 0.86f, stiffness = 460f)
        },
        label = "pildoraTab",
    )

    val colorPildora = MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .toqueTactil(interaccion)
            .clickable(
                interactionSource = interaccion,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = activo
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        // Contenedor del ícono con píldora activa animada (Seda iOS).
        Box(
            modifier = Modifier.size(width = 52.dp, height = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = progresoActivo
                        scaleX = 0.65f + 0.35f * progresoActivo
                        scaleY = 0.80f + 0.20f * progresoActivo
                    }
                    .background(colorPildora, CircleShape),
            )

            IconoPestanaPrincipal(
                pestana = pestana,
                color = colorContenido,
                activo = activo,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = colorContenido,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Librería de íconos de navegación en contorno fino uniforme (1.75–2.05 dp, puntas redondas,
 * estilo SF Symbols / Lucide) usando `drawWithCache` para cachear `Path` y `Stroke` por tamaño
 * (0 alocaciones por frame durante las transiciones).
 */
@Composable
internal fun IconoPestanaPrincipal(
    pestana: PestanaPrincipal,
    color: Color,
    activo: Boolean,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val u = size.minDimension / 24f
            val trazo = Stroke(
                width = (if (activo) 2.05f else 1.75f) * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            val radioBarra = CornerRadius(1.5f * u, 1.5f * u)

            val techoInicio = Path().apply {
                moveTo(3.5f * u, 10.5f * u)
                lineTo(12f * u, 3.5f * u)
                lineTo(20.5f * u, 10.5f * u)
            }
            val cuerpoInicio = Path().apply {
                moveTo(5.5f * u, 9.5f * u)
                lineTo(5.5f * u, 19.5f * u)
                lineTo(18.5f * u, 19.5f * u)
                lineTo(18.5f * u, 9.5f * u)
            }

            val escudoBoveda = Path().apply {
                moveTo(12f * u, 3.2f * u)
                lineTo(19f * u, 6.0f * u)
                lineTo(19f * u, 11.8f * u)
                cubicTo(
                    19f * u, 16.2f * u,
                    15.8f * u, 19.3f * u,
                    12f * u, 20.8f * u,
                )
                cubicTo(
                    8.2f * u, 19.3f * u,
                    5f * u, 16.2f * u,
                    5f * u, 11.8f * u,
                )
                lineTo(5f * u, 6.0f * u)
                close()
            }
            val checkBoveda = Path().apply {
                moveTo(9.3f * u, 12.2f * u)
                lineTo(11.2f * u, 14.1f * u)
                lineTo(14.9f * u, 10.2f * u)
            }

            onDrawBehind {
                when (pestana) {
                    PestanaPrincipal.INICIO -> {
                        drawPath(path = techoInicio, color = color, style = trazo)
                        drawPath(path = cuerpoInicio, color = color, style = trazo)
                        drawLine(
                            color = color,
                            start = Offset(9.5f * u, 19.5f * u),
                            end = Offset(14.5f * u, 19.5f * u),
                            strokeWidth = trazo.width,
                            cap = StrokeCap.Round,
                        )
                    }

                    PestanaPrincipal.ANALISIS -> {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(4.5f * u, 13f * u),
                            size = Size(3.5f * u, 7f * u),
                            cornerRadius = radioBarra,
                            style = trazo,
                        )
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(10.25f * u, 8.5f * u),
                            size = Size(3.5f * u, 11.5f * u),
                            cornerRadius = radioBarra,
                            style = trazo,
                        )
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(16f * u, 4.5f * u),
                            size = Size(3.5f * u, 15.5f * u),
                            cornerRadius = radioBarra,
                            style = trazo,
                        )
                    }

                    PestanaPrincipal.METAS -> {
                        drawCircle(
                            color = color,
                            radius = 8.2f * u,
                            center = Offset(12f * u, 12f * u),
                            style = trazo,
                        )
                        drawCircle(
                            color = color.copy(alpha = 0.88f),
                            radius = 4.4f * u,
                            center = Offset(12f * u, 12f * u),
                            style = trazo,
                        )
                        drawCircle(
                            color = color,
                            radius = 1.4f * u,
                            center = Offset(12f * u, 12f * u),
                        )
                    }

                    PestanaPrincipal.BOVEDA -> {
                        drawPath(path = escudoBoveda, color = color, style = trazo)
                        drawPath(path = checkBoveda, color = color, style = trazo)
                    }
                }
            }
        },
    )
}
