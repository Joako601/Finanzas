package io.github.joako601.finanzas.ui.componentes

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
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
internal object EstadoSplashKren {
    @Volatile
    var yaMostrado: Boolean = false
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
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val vista = LocalView.current

    // Evita que pulsar Atrás durante los ~960 ms cierre la app o afecte la pantalla inferior.
    BackHandler(enabled = true) {}

    // Mantiene barras del sistema claras sobre #111111 y restaura al terminar.
    DisposableEffect(vista) {
        val ventana = vista.context.encontrarActivity()?.window
        val controlador = ventana?.let { WindowInsetsControllerCompat(it, vista) }
        val previoStatus = controlador?.isAppearanceLightStatusBars
        val previoNav = controlador?.isAppearanceLightNavigationBars
        if (controlador != null) {
            controlador.isAppearanceLightStatusBars = false
            controlador.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (controlador != null) {
                if (previoStatus != null) controlador.isAppearanceLightStatusBars = previoStatus
                if (previoNav != null) controlador.isAppearanceLightNavigationBars = previoNav
            }
        }
    }

    val progresoBarras = remember { Animatable(0f) }
    val progresoPunto = remember { Animatable(0f) }
    val progresoHalo = remember { Animatable(0f) }
    val progresoNombre = remember { Animatable(0f) }
    val opacidadSalida = remember { Animatable(1f) }
    val escalaSalida = remember { Animatable(1f) }

    LaunchedEffect(sinAnimaciones) {
        if (sinAnimaciones) {
            EstadoSplashKren.yaMostrado = true
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
        EstadoSplashKren.yaMostrado = true
        onTerminar()
    }

    val desplazamientoNombrePx = with(LocalDensity.current) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = opacidadSalida.value
                scaleX = escalaSalida.value
                scaleY = escalaSalida.value
            }
            .background(FondoSplashKren)
            // Bloquea toques accidentales hacia la pantalla inferior durante el desvanecimiento.
            .pointerInput(Unit) {}
            .clearAndSetSemantics {},
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
                modifier = Modifier.size(84.dp),
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
                    alpha = p
                    translationY = (1f - p) * desplazamientoNombrePx
                },
            )
        }
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

                val colorBarra = Color.White.copy(alpha = alphaBarras)
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
                        color = VerdePuntoKren.copy(alpha = pPunto.coerceIn(0f, 1f)),
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
