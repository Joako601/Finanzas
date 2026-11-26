package io.github.joako601.finanzas.ui.componentes

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.composed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.joako601.finanzas.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** Modo discreto: con `true`, los montos de Inicio se muestran como [MONTO_OCULTO]. */
val LocalMontosOcultos = staticCompositionLocalOf { false }

const val MONTO_OCULTO = "$ ••••"

/** El monto formateado, o [MONTO_OCULTO] en modo discreto. */
@Composable
fun Dinero.enPantalla(): String = if (LocalMontosOcultos.current) MONTO_OCULTO else formatear()

/**
 * Muestra un monto en formato tabular (`tnum`) con curva "Seda iOS":
 * - la primera vez que aparece cuenta desde $0 hasta el monto (600 ms); después, cada cambio
 *   rueda del valor anterior al nuevo (360 ms);
 * - en modo discreto el número corre y sus dígitos caen en puntos de izquierda a derecha
 *   (450 ms); al mostrarlo, los puntos se van y vuelve a contar desde $0.
 * Con *Quitar animaciones* todo cambia sin interpolar. TalkBack solo oye el monto final o
 * "Monto oculto".
 */
@Composable
fun TextoMontoAnimado(
    monto: Dinero,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val oculto = LocalMontosOcultos.current
    val estiloTabular = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val textoFinal = remember(monto) { monto.formatear() }
    // Saveable: no vuelve a contar desde $0 al regresar a la pestaña ni al volver una fila a la vista.
    var yaContado by rememberSaveable { mutableStateOf(false) }
    val inicial = if (yaContado || sinAnimaciones || oculto) monto.centavos else 0L
    var origenCentavos by remember { mutableLongStateOf(inicial) }
    var destinoCentavos by remember { mutableLongStateOf(inicial) }
    val progreso = remember { Animatable(1f) }
    // 0 = monto visible, 1 = "$ ••••". Arranca donde está: abrir ya oculto no anima nada.
    val pOculto = remember { Animatable(if (oculto) 1f else 0f) }

    suspend fun rodarHasta(centavos: Long, duracionMs: Int) {
        origenCentavos = interpolarCentavos(origenCentavos, destinoCentavos, progreso.value)
        destinoCentavos = centavos
        progreso.snapTo(0f)
        progreso.animateTo(1f, tween(durationMillis = duracionMs, easing = CurvaSaldoRodante))
    }

    LaunchedEffect(monto.centavos, sinAnimaciones) {
        val primeraVez = !yaContado
        yaContado = true
        if (sinAnimaciones || monto.centavos == destinoCentavos) {
            origenCentavos = monto.centavos
            destinoCentavos = monto.centavos
            progreso.snapTo(1f)
        } else {
            rodarHasta(monto.centavos, if (primeraVez) 600 else 360)
        }
    }

    LaunchedEffect(oculto, sinAnimaciones) {
        when {
            sinAnimaciones -> pOculto.snapTo(if (oculto) 1f else 0f)
            oculto -> pOculto.animateTo(1f, tween(durationMillis = 450, easing = CurvaSaldoRodante))
            pOculto.value > 0f -> {
                // Al mostrar: los puntos se van mientras el número vuelve a contar desde $0.
                origenCentavos = 0L
                destinoCentavos = 0L
                progreso.snapTo(1f)
                launch { rodarHasta(monto.centavos, 600) }
                pOculto.animateTo(0f, tween(durationMillis = 300, easing = CurvaSaldoRodante))
            }
        }
    }

    val centavosVisibles = when {
        sinAnimaciones -> monto.centavos
        progreso.value >= 1f -> destinoCentavos
        else -> interpolarCentavos(origenCentavos, destinoCentavos, progreso.value)
    }

    val hablado = if (oculto) stringResource(R.string.monto_oculto) else textoFinal
    Text(
        text = textoConPuntos(Dinero(centavosVisibles).formatear(), pOculto.value),
        style = estiloTabular,
        color = color,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(hablado) },
    )
}

/**
 * "Corre y cae en puntos": con [progreso] 0 es [formateado] tal cual y con 1 es [MONTO_OCULTO].
 * En medio, `$`, comas y punto se quedan; cada dígito corre (sube de valor) hasta su turno y,
 * de izquierda a derecha, se vuelve `•`. El largo no cambia, así el texto no brinca.
 */
internal fun textoConPuntos(formateado: String, progreso: Float): String {
    if (progreso <= 0f) return formateado
    if (progreso >= 1f) return MONTO_OCULTO
    val digitos = formateado.count { it.isDigit() }
    val giro = (progreso * 20).toInt()
    var indice = 0
    return buildString(formateado.length) {
        formateado.forEach { c ->
            if (!c.isDigit()) {
                append(c)
            } else {
                val turno = 0.3f + 0.7f * indice / digitos
                append(if (progreso >= turno) '•' else '0' + (c - '0' + giro) % 10)
                indice++
            }
        }
    }
}

/**
 * Para frases y filas con [enPantalla]: al ocultar o mostrar, el texto nuevo entra con un
 * fundido de 240 ms en lugar de cambiar de golpe. Nada con *Quitar animaciones*.
 */
fun Modifier.fundidoAlOcultar(): Modifier = composed {
    val oculto = LocalMontosOcultos.current
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val alfa = remember { Animatable(1f) }
    var previo by remember { mutableStateOf(oculto) }
    LaunchedEffect(oculto) {
        if (oculto != previo && !sinAnimaciones) {
            alfa.snapTo(0f)
            alfa.animateTo(1f, tween(durationMillis = 240, easing = CurvaSaldoRodante))
        }
        previo = oculto
    }
    graphicsLayer { alpha = alfa.value }
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

/**
 * Selector segmentado deslizante estilo "Seda iOS": pista `secondaryContainer` y píldora activa
 * blanca (carbón en oscuro) con borde de 1 dp que se desliza con resorte amortiguado.
 *
 * Cada segmento garantiza un área táctil mínima de 48 dp de alto y escala su altura con
 * [IntrinsicSize.Min] para que la píldora nunca se recorte con `font_scale` al 200%.
 */
@Composable
fun <T> SelectorSegmentadoSeda(
    opciones: List<T>,
    seleccionada: T,
    etiqueta: @Composable (T) -> String,
    onSeleccionar: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (opciones.isEmpty()) return
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val indiceObjetivo = opciones.indexOf(seleccionada).coerceAtLeast(0).toFloat()
    val fraccionDesplazamiento by animateFloatAsState(
        targetValue = indiceObjetivo,
        animationSpec = if (sinAnimaciones) {
            snap()
        } else {
            spring(dampingRatio = 0.86f, stiffness = 460f)
        },
        label = "selectorSegmentadoSeda",
    )

    val colorPastilla = MaterialTheme.colorScheme.surfaceContainerLowest
    val colorBordePastilla = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    ) {
        // Píldora activa deslizante dibujada sobre el tamaño exacto del contenedor (sin SubcomposeLayout,
        // compatible con IntrinsicSize.Min y escalado al 200%).
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val margen = 3.dp.toPx()
                    val anchoSegmento = size.width / opciones.size
                    val anchoPastilla = (anchoSegmento - margen * 2f).coerceAtLeast(0f)
                    val altoPastilla = (size.height - margen * 2f).coerceAtLeast(0f)
                    val xPastilla = anchoSegmento * fraccionDesplazamiento + margen
                    val radio = altoPastilla / 2f
                    val esquina = CornerRadius(radio, radio)
                    drawRoundRect(
                        color = colorPastilla,
                        topLeft = Offset(xPastilla, margen),
                        size = Size(anchoPastilla, altoPastilla),
                        cornerRadius = esquina,
                    )
                    drawRoundRect(
                        color = colorBordePastilla,
                        topLeft = Offset(xPastilla, margen),
                        size = Size(anchoPastilla, altoPastilla),
                        cornerRadius = esquina,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            opciones.forEach { opcion ->
                val activo = opcion == seleccionada
                val interaccion = remember { MutableInteractionSource() }
                val texto = etiqueta(opcion)
                val colorTexto by animateColorAsState(
                    targetValue = if (activo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
                    },
                    animationSpec = tween(durationMillis = if (sinAnimaciones) 0 else 160),
                    label = "textoSegmentoSeda",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .toqueTactil(interaccion)
                        .clickable(
                            interactionSource = interaccion,
                            indication = null,
                            onClick = { onSeleccionar(opcion) },
                        )
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            selected = activo
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = texto,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colorTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
