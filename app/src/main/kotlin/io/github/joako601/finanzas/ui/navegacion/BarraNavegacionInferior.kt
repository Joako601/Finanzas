package io.github.joako601.finanzas.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin
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
 * - [ANALISIS]: liquidez, flujo y gastos por categoría.
 * - [METAS]: topes mensuales y apartados de ahorro.
 * - [PERFIL]: cuenta (si la hay), respaldo, exportación y recordatorios.
 */
enum class PestanaPrincipal(
    @param:StringRes val etiquetaRes: Int,
) {
    INICIO(
        etiquetaRes = R.string.nav_inicio,
    ),
    ANALISIS(
        etiquetaRes = R.string.nav_analisis,
    ),
    METAS(
        etiquetaRes = R.string.nav_presupuesto,
    ),
    PERFIL(
        etiquetaRes = R.string.nav_perfil,
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
                        // También al tocar la pestaña ya abierta: así Perfil vuelve a su raíz.
                        onClick = { onSeleccionar(pestana) },
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

    val progresoIcono = remember { Animatable(if (activo) 1f else 0f) }
    var eraActivo by remember { mutableStateOf(activo) }

    LaunchedEffect(activo, sinAnimaciones) {
        if (activo) {
            if (sinAnimaciones || eraActivo) {
                progresoIcono.snapTo(1f)
            } else {
                progresoIcono.snapTo(0f)
                progresoIcono.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = CurvaMicroIconoNavbar,
                    ),
                )
            }
        } else {
            progresoIcono.snapTo(0f)
        }
        eraActivo = activo
    }

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
                color = { colorContenido },
                activo = activo,
                progresoAnim = { progresoIcono.value },
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

private val CurvaMicroIconoNavbar = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Librería propia de íconos de navegación de Kren — **Familia H (Uber Precision)**
 * con micro-animaciones *Seda iOS* al seleccionar cada pestaña:
 * - **Inicio:** expansión suave del umbral inferior y relleno translúcido.
 * - **Análisis:** trazado progresivo de la curva Stocks + aparición del punto pico.
 * - **Metas:** impulso vertical sutil del trofeo + dos micro-destellos pasajeros.
 * - **Perfil:** la cabeza de la silueta sube y vuelve, como un saludo breve.
 *
 * Recibe [color] y [progresoAnim] como lambdas para no invalidar `drawWithCache`
 * durante las animaciones (0 alocaciones de `Path`/`Stroke` por frame).
 */
@Composable
internal fun IconoPestanaPrincipal(
    pestana: PestanaPrincipal,
    color: () -> Color,
    activo: Boolean,
    progresoAnim: () -> Float = { if (activo) 1f else 0f },
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val u = size.minDimension / 24f
            val anchoTrazo = (if (activo) 1.90f else 1.65f) * u
            val trazo = Stroke(
                width = anchoTrazo,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            when (pestana) {
                PestanaPrincipal.INICIO -> {
                    // Casa arquitectónica con cúspide suavizada y umbral interior expansivo
                    val casaInicio = Path().apply {
                        moveTo(5f * u, 10.6f * u)
                        lineTo(10.6f * u, 5.6f * u)
                        quadraticTo(12f * u, 4.4f * u, 13.4f * u, 5.6f * u)
                        lineTo(19f * u, 10.6f * u)
                        lineTo(19f * u, 17.4f * u)
                        arcTo(
                            rect = Rect(15f * u, 15.4f * u, 19f * u, 19.4f * u),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(7f * u, 19.4f * u)
                        arcTo(
                            rect = Rect(5f * u, 15.4f * u, 9f * u, 19.4f * u),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        close()
                    }
                    val centroUmbralX = 12f * u
                    val umbralY = 15.8f * u
                    val medioUmbral = 2.2f * u

                    onDrawBehind {
                        val tinta = color()
                        val p = progresoAnim().coerceIn(0f, 1f)
                        if (activo) {
                            drawPath(path = casaInicio, color = tinta.copy(alpha = 0.14f * p))
                        }
                        drawPath(path = casaInicio, color = tinta, style = trazo)
                        val extension = if (activo) medioUmbral * (0.25f + 0.75f * p) else medioUmbral
                        drawLine(
                            color = tinta,
                            start = Offset(centroUmbralX - extension, umbralY),
                            end = Offset(centroUmbralX + extension, umbralY),
                            strokeWidth = anchoTrazo,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                PestanaPrincipal.ANALISIS -> {
                    // Eje L redondeado + trazado progresivo de la línea de tendencia Stocks
                    val ejeAnalisis = Path().apply {
                        moveTo(4.8f * u, 5.2f * u)
                        lineTo(4.8f * u, 17.2f * u)
                        arcTo(
                            rect = Rect(4.8f * u, 15.2f * u, 8.8f * u, 19.2f * u),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false,
                        )
                        lineTo(19.2f * u, 19.2f * u)
                    }
                    val tendenciaAnalisis = Path().apply {
                        moveTo(7.8f * u, 14.8f * u)
                        lineTo(11.2f * u, 11.2f * u)
                        lineTo(14.2f * u, 13.2f * u)
                        lineTo(18.2f * u, 8.2f * u)
                    }
                    val medidor = PathMeasure().apply { setPath(tendenciaAnalisis, false) }
                    val longitudTendencia = medidor.length
                    val trazoParcial = Path()
                    val puntoPico = Offset(18.2f * u, 8.2f * u)
                    val radioPico = (if (activo) 1.65f else 1.25f) * u

                    onDrawBehind {
                        val tinta = color()
                        val p = progresoAnim().coerceIn(0f, 1f)
                        drawPath(path = ejeAnalisis, color = tinta, style = trazo)
                        if (activo && p < 0.999f) {
                            trazoParcial.rewind()
                            medidor.getSegment(0f, longitudTendencia * p, trazoParcial, true)
                            drawPath(path = trazoParcial, color = tinta, style = trazo)
                            val escalaPico = ((p - 0.85f) / 0.15f).coerceIn(0f, 1f)
                            if (escalaPico > 0f) {
                                drawCircle(
                                    color = tinta,
                                    radius = radioPico * escalaPico,
                                    center = puntoPico,
                                )
                            }
                        } else {
                            drawPath(path = tendenciaAnalisis, color = tinta, style = trazo)
                            drawCircle(color = tinta, radius = radioPico, center = puntoPico)
                        }
                    }
                }

                PestanaPrincipal.METAS -> {
                    // Trofeo minimalista con micro-impulso vertical y 2 destellos pasajeros
                    val trazoAsa = Stroke(
                        width = (if (activo) 1.75f else 1.55f) * u,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    val copaMetas = Path().apply {
                        moveTo(8f * u, 5.2f * u)
                        lineTo(16f * u, 5.2f * u)
                        lineTo(16f * u, 9.8f * u)
                        arcTo(
                            rect = Rect(8f * u, 5.8f * u, 16f * u, 13.8f * u),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false,
                        )
                        close()
                    }
                    val asaIzqMetas = Path().apply {
                        moveTo(8f * u, 7.2f * u)
                        lineTo(5.6f * u, 7.2f * u)
                        arcTo(
                            rect = Rect(4.0f * u, 7.2f * u, 7.2f * u, 10.4f * u),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = -180f,
                            forceMoveTo = false,
                        )
                        lineTo(8.1f * u, 10.4f * u)
                    }
                    val asaDerMetas = Path().apply {
                        moveTo(16f * u, 7.2f * u)
                        lineTo(18.4f * u, 7.2f * u)
                        arcTo(
                            rect = Rect(16.8f * u, 7.2f * u, 20.0f * u, 10.4f * u),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false,
                        )
                        lineTo(15.9f * u, 10.4f * u)
                    }
                    val talloInicio = Offset(12f * u, 13.8f * u)
                    val talloFin = Offset(12f * u, 18.8f * u)
                    val baseInicio = Offset(9f * u, 18.8f * u)
                    val baseFin = Offset(15f * u, 18.8f * u)
                    val destelloIzqA = Offset(6.0f * u, 3.5f * u)
                    val destelloIzqB = Offset(4.9f * u, 2.4f * u)
                    val destelloDerA = Offset(18.0f * u, 3.5f * u)
                    val destelloDerB = Offset(19.1f * u, 2.4f * u)

                    onDrawBehind {
                        val tinta = color()
                        val p = progresoAnim().coerceIn(0f, 1f)
                        val elevacion = if (activo && p < 0.999f) {
                            -1.2f * u * sin(PI * p).toFloat()
                        } else {
                            0f
                        }

                        translate(top = elevacion) {
                            if (activo) {
                                drawPath(path = copaMetas, color = tinta.copy(alpha = 0.14f * p))
                            }
                            drawPath(path = copaMetas, color = tinta, style = trazo)
                            drawPath(path = asaIzqMetas, color = tinta, style = trazoAsa)
                            drawPath(path = asaDerMetas, color = tinta, style = trazoAsa)
                            drawLine(
                                color = tinta,
                                start = talloInicio,
                                end = talloFin,
                                strokeWidth = anchoTrazo,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = tinta,
                                start = baseInicio,
                                end = baseFin,
                                strokeWidth = anchoTrazo,
                                cap = StrokeCap.Round,
                            )
                        }

                        // Dos micro-destellos discretos durante la transición que desaparecen en reposo
                        if (activo && p in 0.12f..0.96f) {
                            val faseDestello = ((p - 0.12f) / 0.84f).coerceIn(0f, 1f)
                            val opacidadDestello = sin(PI * faseDestello).toFloat() * 0.85f
                            val tintaDestello = tinta.copy(alpha = opacidadDestello)
                            drawLine(
                                color = tintaDestello,
                                start = destelloIzqA,
                                end = destelloIzqB,
                                strokeWidth = 1.4f * u,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = tintaDestello,
                                start = destelloDerA,
                                end = destelloDerB,
                                strokeWidth = 1.4f * u,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }

                PestanaPrincipal.PERFIL -> {
                    // Persona dentro de un círculo; al activarse la cabeza sube y vuelve (saludo breve).
                    val centro = Offset(12f * u, 12f * u)
                    val radioAro = 8.2f * u
                    val radioCabeza = 2.7f * u
                    val centroCabezaY = 10.1f * u
                    val hombros = Path().apply {
                        moveTo(7.6f * u, 17.6f * u)
                        cubicTo(8.8f * u, 14.6f * u, 15.2f * u, 14.6f * u, 16.4f * u, 17.6f * u)
                    }
                    val salto = 1.2f * u

                    onDrawBehind {
                        val tinta = color()
                        val p = progresoAnim().coerceIn(0f, 1f)
                        if (activo) {
                            drawCircle(color = tinta.copy(alpha = 0.14f * p), radius = radioAro, center = centro)
                        }
                        drawCircle(color = tinta, radius = radioAro, center = centro, style = trazo)
                        val elevacion = if (activo) salto * sin(PI.toFloat() * p) else 0f
                        drawCircle(
                            color = tinta,
                            radius = radioCabeza,
                            center = Offset(centro.x, centroCabezaY - elevacion),
                            style = trazo,
                        )
                        drawPath(path = hombros, color = tinta, style = trazo)
                    }
                }
            }
        },
    )
}
