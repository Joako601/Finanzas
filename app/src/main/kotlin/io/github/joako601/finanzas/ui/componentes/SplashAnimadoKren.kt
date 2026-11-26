package io.github.joako601.finanzas.ui.componentes

import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.detectTapGestures
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.joako601.finanzas.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FondoSplashKren = Color(0xFF111111)
private val TextoSplashKren = Color(0xFFF5F5F7)
private val VerdePuntoKren = Color(0xFF30D158)
private val CurvaSedaSplash = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Recuerda durante la vida del proceso si el splash animado de apertura ya se mostró,
 * evitando repetirlo al rotar la pantalla o recrear la Activity.
 */
/** Dónde aterriza el logo del splash y con qué colores llega (los de la pantalla de destino). */
internal class DestinoLogoSplash(val caja: Rect, val colorBarras: Color, val colorPunto: Color)

internal object EstadoSplashKren {
    @Volatile
    var yaMostrado: Boolean = false

    /**
     * Dónde está el logo de la pantalla que queda debajo (la portada de la nube). Si existe,
     * el splash no se desvanece entero: su logo viaja hasta ahí.
     */
    var destinoLogo: DestinoLogoSplash? by mutableStateOf(null)

    /** El splash ya se fue: la pantalla de abajo puede empezar su entrada. */
    var terminado: Boolean by mutableStateOf(false)
}

/**
 * Entrada animada de marca **Kren** (~960 ms, estilo Apple / Linear):
 * 1. Las dos barras blancas del Logo 8 se deslizan simétricamente hasta alinearse.
 * 2. El punto semántico `#30D158` brota al centro con un halo discreto.
 * 3. Aparece el nombre **Kren** y toda la capa se funde suavemente hacia la app.
 *
 * Si el usuario tiene desactivadas las animaciones del sistema, termina de inmediato.
 */
@Composable
fun SplashAnimadoKren(
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
    bloqueada: () -> Boolean = { false },
    onPedirDesbloqueo: () -> Unit = {},
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val vista = LocalView.current
    // Con el logo armado, esperando la huella: tocarlo la vuelve a pedir.
    var esperando by remember { mutableStateOf(false) }

    // Evita que pulsar Atrás durante los ~960 ms cierre la app o afecte la pantalla inferior.
    // Esperando la huella, Atrás manda Kren al fondo (como cualquier app bloqueada).
    BackHandler(enabled = true) {
        if (esperando) vista.context.encontrarActivity()?.moveTaskToBack(true)
    }

    val progresoBarras = remember { Animatable(0f) }
    val progresoPunto = remember { Animatable(0f) }
    val progresoHalo = remember { Animatable(0f) }
    val progresoNombre = remember { Animatable(0f) }
    val opacidadSalida = remember { Animatable(1f) }
    val escalaSalida = remember { Animatable(1f) }
    val opacidadFondo = remember { Animatable(1f) }
    val viaje = remember { Animatable(0f) }
    val progresoPista = remember { Animatable(0f) }

    /** Pide la huella y espera a que se confirme; mientras, el logo queda armado. */
    suspend fun esperarDesbloqueo() {
        if (!bloqueada()) return
        esperando = true
        onPedirDesbloqueo()
        coroutineScope {
            val pista = launch { progresoPista.animateTo(1f, tween(durationMillis = 320, easing = CurvaSedaSplash)) }
            snapshotFlow { bloqueada() }.first { !it }
            pista.cancel()
        }
        progresoPista.snapTo(0f)
        esperando = false
    }
    // Dónde quedó el logo del splash (centrado, 84 dp), para calcular el viaje al destino.
    var origenLogo by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(sinAnimaciones) {
        if (sinAnimaciones) {
            // Sin animaciones: el logo ya armado espera la huella, si hace falta, y se va.
            if (bloqueada()) {
                progresoBarras.snapTo(1f)
                progresoPunto.snapTo(1f)
                progresoNombre.snapTo(1f)
                esperarDesbloqueo()
            }
            EstadoSplashKren.yaMostrado = true
            EstadoSplashKren.terminado = true
            onTerminar()
            return@LaunchedEffect
        }
        launch {
            progresoBarras.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
            )
        }
        launch {
            delay(180)
            progresoPunto.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
            )
        }
        launch {
            delay(200)
            progresoHalo.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = CurvaSedaSplash),
            )
        }
        launch {
            delay(280)
            progresoNombre.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = CurvaSedaSplash),
            )
        }

        delay(720)
        esperarDesbloqueo()
        if (EstadoSplashKren.destinoLogo != null) {
            // Con destino: el fondo y el nombre se van mientras el logo viaja a su lugar.
            val fondo = launch { opacidadFondo.animateTo(0f, tween(durationMillis = 300, easing = CurvaSedaSplash)) }
            viaje.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow))
            fondo.join()
        } else {
            val salidaAlpha = launch {
                opacidadSalida.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 240, easing = CurvaSedaSplash),
                )
            }
            val salidaEscala = launch {
                escalaSalida.animateTo(
                    targetValue = 1.035f,
                    animationSpec = tween(durationMillis = 240, easing = CurvaSedaSplash),
                )
            }
            salidaAlpha.join()
            salidaEscala.join()
        }
        EstadoSplashKren.yaMostrado = true
        EstadoSplashKren.terminado = true
        onTerminar()
    }

    val desplazamientoNombrePx = with(LocalDensity.current) { 8.dp.toPx() }
    val descripcionBloqueo = stringResource(R.string.app_name)
    val accionBloqueo = stringResource(R.string.bloqueo_tocar)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = opacidadSalida.value
                scaleX = escalaSalida.value
                scaleY = escalaSalida.value
            }
            .drawBehind { drawRect(FondoSplashKren, alpha = opacidadFondo.value) }
            // Bloquea toques accidentales hacia la pantalla inferior; esperando, un toque pide la huella.
            .pointerInput(esperando) { detectTapGestures { if (esperando) onPedirDesbloqueo() } }
            .clearAndSetSemantics {
                if (esperando) {
                    contentDescription = descripcionBloqueo
                    onClick(label = accionBloqueo) {
                        onPedirDesbloqueo()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LogoKrenAnimado(
                progresoBarras = { progresoBarras.value },
                progresoPunto = { progresoPunto.value },
                progresoHalo = { progresoHalo.value },
                // Durante el viaje toma los colores de la pantalla de destino (tinta y verde del
                // tema): al aterrizar es idéntico al símbolo que queda debajo.
                colorBarras = {
                    val destino = EstadoSplashKren.destinoLogo
                    if (destino == null) Color.White else lerpColor(Color.White, destino.colorBarras, viaje.value.coerceIn(0f, 1f))
                },
                colorPunto = {
                    val destino = EstadoSplashKren.destinoLogo
                    if (destino == null) VerdePuntoKren else lerpColor(VerdePuntoKren, destino.colorPunto, viaje.value.coerceIn(0f, 1f))
                },
                modifier = Modifier
                    .size(84.dp)
                    .onGloballyPositioned { origenLogo = it.boundsInRoot() }
                    .graphicsLayer {
                        val desde = origenLogo
                        val hasta = EstadoSplashKren.destinoLogo?.caja
                        val p = viaje.value
                        if (desde != null && hasta != null && p > 0f) {
                            // Escala desde la esquina para que el viaje sea de esquina a esquina.
                            transformOrigin = TransformOrigin(0f, 0f)
                            val escala = lerp(1f, hasta.width / desde.width, p)
                            scaleX = escala
                            scaleY = escala
                            translationX = (hasta.left - desde.left) * p
                            translationY = (hasta.top - desde.top) * p
                        }
                    },
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                ),
                color = TextoSplashKren,
                modifier = Modifier.graphicsLayer {
                    val p = progresoNombre.value.coerceIn(0f, 1f)
                    alpha = p * opacidadFondo.value
                    translationY = (1f - p) * desplazamientoNombrePx
                },
            )
        }

        // Pista discreta mientras espera la huella, debajo del nombre y fuera de la columna para
        // que el logo no cambie de lugar; no es una pantalla aparte.
        Text(
            text = stringResource(R.string.bloqueo_tocar),
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSplashKren,
            modifier = Modifier
                .offset(y = 112.dp)
                .graphicsLayer { alpha = 0.7f * progresoPista.value },
        )
    }
}

/**
 * Dibuja el isotipo oficial de **Kren** (Logo 8, proporción 1:1 con `ic_launcher_frente.xml`)
 * animado sin alocaciones por frame en `drawWithCache`.
 */
@Composable
internal fun LogoKrenAnimado(
    progresoBarras: () -> Float,
    progresoPunto: () -> Float,
    progresoHalo: () -> Float = { 0f },
    colorBarras: () -> Color = { Color.White },
    colorPunto: () -> Color = { VerdePuntoKren },
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val u = size.minDimension / 66f
            val grosor = 8.5f * u
            val trazoHalo = Stroke(width = 1.6f * u)
            val ySup = 17f * u
            val yInf = 49f * u
            val centro = Offset(33f * u, 33f * u)
            val radioPunto = 5.5f * u
            val recorridoX = 18f * u

            onDrawBehind {
                val pBarras = progresoBarras()
                val alphaBarras = pBarras.coerceIn(0f, 1f)
                val offsetX = (1f - pBarras) * recorridoX

                val colorBarra = colorBarras().copy(alpha = alphaBarras)
                drawLine(
                    color = colorBarra,
                    start = Offset(13f * u - offsetX, ySup),
                    end = Offset(41f * u - offsetX, ySup),
                    strokeWidth = grosor,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = colorBarra,
                    start = Offset(25f * u + offsetX, yInf),
                    end = Offset(53f * u + offsetX, yInf),
                    strokeWidth = grosor,
                    cap = StrokeCap.Round,
                )

                val pHalo = progresoHalo().coerceIn(0f, 1f)
                if (pHalo > 0f && pHalo < 1f) {
                    val radioHalo = radioPunto + (15f * u * pHalo)
                    val alphaHalo = (1f - pHalo) * 0.34f
                    drawCircle(
                        color = VerdePuntoKren.copy(alpha = alphaHalo),
                        radius = radioHalo,
                        center = centro,
                        style = trazoHalo,
                    )
                }

                val pPunto = progresoPunto().coerceAtLeast(0f)
                if (pPunto > 0.01f) {
                    drawCircle(
                        color = colorPunto().copy(alpha = pPunto.coerceIn(0f, 1f)),
                        radius = radioPunto * pPunto,
                        center = centro,
                    )
                }
            }
        },
    )
}

private tailrec fun Context.encontrarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarActivity()
    else -> null
}
