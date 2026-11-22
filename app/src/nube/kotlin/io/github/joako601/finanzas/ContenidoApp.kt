package io.github.joako601.finanzas

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.joako601.finanzas.nube.SeccionNube
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.componentes.toqueTactil
import io.github.joako601.finanzas.ui.movimientos.PantallaMovimientos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ModoInicioNube {
    SELECCION,
    NUBE,
    PRIVADO,
}

private val FondoTintaCubierta = Color(0xFF000000)
private val SuperficieTarjetaTrasera = Color(0xFF141416)
private val SuperficieTarjetaFrontal = Color(0xFF1C1C1E)
private val BordeTarjetaCubierta = Color(0xFF2E2E32)
private val TextoSobreTinta = Color(0xFFF5F5F7)
private val EtiquetaSobreTinta = Color(0xFF8E8E93)
private val VerdeIngresoCubierta = Color(0xFF30D158)
private val FormaHojaEntrada = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val FormaTarjetaEscena = RoundedCornerShape(20.dp)
private val CurvaEntradaSeda = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Contenido raíz de la distribución nube (D-020): al abrir la app presenta la portada
 * editorial "Contraste Tinta" para elegir entre iniciar sesión en la nube o entrar en
 * modo privado, sin acoplar `app/src/main` a Firebase.
 */
@Composable
fun ContenidoApp() {
    var modo by rememberSaveable { mutableStateOf(ModoInicioNube.SELECCION) }

    when (modo) {
        ModoInicioNube.SELECCION -> PantallaBienvenidaNube(
            onConectarNube = { modo = ModoInicioNube.NUBE },
            onEntrarPrivado = { modo = ModoInicioNube.PRIVADO },
        )

        ModoInicioNube.NUBE -> SeccionNube(
            onSalir = { modo = ModoInicioNube.SELECCION },
        )

        // En modo privado la interfaz principal sigue limpia sin botones de nube a la vista,
        // pero desde el menú ⋮ se puede volver a elegir modo o conectarse sin cerrar la app.
        ModoInicioNube.PRIVADO -> PantallaMovimientos(
            onAbrirNube = { modo = ModoInicioNube.SELECCION },
        )
    }
}

/**
 * Portada editorial "Contraste Tinta": plano superior negro OLED (#000000) con marca,
 * composición minimalista de tarjetas en el centro y titular; hoja inferior con borde
 * superior sutil y acciones en la zona natural del pulgar.
 */
@Composable
private fun PantallaBienvenidaNube(
    onConectarNube: () -> Unit,
    onEntrarPrivado: () -> Unit,
) {
    val vista = LocalView.current
    DisposableEffect(vista) {
        val ventana = (vista.context as? Activity)?.window
        val controlador = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        val previo = controlador?.isAppearanceLightStatusBars
        if (controlador != null) {
            controlador.isAppearanceLightStatusBars = false
        }
        onDispose {
            if (controlador != null && previo != null) {
                controlador.isAppearanceLightStatusBars = previo
            }
        }
    }

    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val densidad = LocalDensity.current
    val desplazamientoHojaPx = with(densidad) { 32.dp.toPx() }
    val desplazamientoEscenaPx = with(densidad) { 18.dp.toPx() }
    val desplazamientoTextoPx = with(densidad) { 12.dp.toPx() }

    val marcaProgreso = remember { Animatable(if (sinAnimaciones) 1f else 0f) }
    val escenaProgreso = remember { Animatable(if (sinAnimaciones) 1f else 0f) }
    val titularProgreso = remember { Animatable(if (sinAnimaciones) 1f else 0f) }
    val hojaProgreso = remember { Animatable(if (sinAnimaciones) 1f else 0f) }

    LaunchedEffect(sinAnimaciones) {
        if (sinAnimaciones) {
            marcaProgreso.snapTo(1f)
            escenaProgreso.snapTo(1f)
            titularProgreso.snapTo(1f)
            hojaProgreso.snapTo(1f)
            return@LaunchedEffect
        }
        launch {
            marcaProgreso.animateTo(1f, tween(durationMillis = 280, easing = CurvaEntradaSeda))
        }
        launch {
            delay(100)
            escenaProgreso.animateTo(1f, spring(dampingRatio = 0.90f, stiffness = 420f))
        }
        launch {
            delay(200)
            titularProgreso.animateTo(1f, tween(durationMillis = 360, easing = CurvaEntradaSeda))
        }
        launch {
            delay(220)
            hojaProgreso.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 420f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoTintaCubierta),
    ) {
        // Plano superior negro OLED: marca arriba, escena de tarjetas al centro, titular abajo.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Lockup limpio de marca (símbolo geométrico de barras + nombre).
                Row(
                    modifier = Modifier.graphicsLayer {
                        val p = marcaProgreso.value
                        alpha = p
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SimboloMarcaEntrada(modifier = Modifier.size(28.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextoSobreTinta,
                    )
                }

                // Escena central de tarjetas minimalistas (llena el espacio 20:9 con propósito).
                EscenaTarjetasEntrada(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .graphicsLayer {
                            val p = escenaProgreso.value
                            alpha = p.coerceIn(0f, 1f)
                            translationY = (1f - p) * desplazamientoEscenaPx
                        },
                )

                // Titular editorial.
                Column(
                    modifier = Modifier.graphicsLayer {
                        val p = titularProgreso.value
                        alpha = p
                        translationY = (1f - p) * desplazamientoTextoPx
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.bienvenida_nube_titular),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.6).sp,
                        ),
                        color = TextoSobreTinta,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }

        // Hoja inferior de acceso con borde superior sutil (separa planos en oscuro y claro).
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = hojaProgreso.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * desplazamientoHojaPx
                },
            shape = FormaHojaEntrada,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BotonPrincipal(
                        texto = stringResource(R.string.bienvenida_nube_iniciar),
                        onClick = onConectarNube,
                    )

                    val interaccionPrivado = remember { MutableInteractionSource() }
                    Button(
                        onClick = onEntrarPrivado,
                        interactionSource = interaccionPrivado,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .toqueTactil(interaccionPrivado),
                        shape = MaterialTheme.shapes.small,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.bienvenida_nube_privado),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Text(
                        text = stringResource(R.string.bienvenida_nube_nota),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Composición visual decorativa de dos tarjetas monocromáticas escalonadas (estilo Apple Wallet).
 * Se marca como decorativa para TalkBack para no competir con el titular ni los accesos.
 */
@Composable
private fun EscenaTarjetasEntrada(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // Tarjeta trasera ligeramente inclinada: aporta profundidad real sobre el plano OLED.
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .height(116.dp)
                .offset(y = (-26).dp)
                .graphicsLayer { rotationZ = -3f },
            shape = FormaTarjetaEscena,
            color = SuperficieTarjetaTrasera,
            border = BorderStroke(1.dp, BordeTarjetaCubierta),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 18.dp)
                        .background(BordeTarjetaCubierta, RoundedCornerShape(4.dp)),
                )
                Text(
                    text = "•••• 4821",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = EtiquetaSobreTinta,
                )
            }
        }

        // Tarjeta principal al frente: muestra disponible tabular y 1 movimiento limpio.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 14.dp),
            shape = FormaTarjetaEscena,
            color = SuperficieTarjetaFrontal,
            border = BorderStroke(1.dp, BordeTarjetaCubierta),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Disponible real",
                        style = MaterialTheme.typography.bodySmall,
                        color = EtiquetaSobreTinta,
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(VerdeIngresoCubierta, CircleShape),
                    )
                }

                Text(
                    text = "$14,820.50",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = TextoSobreTinta,
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BordeTarjetaCubierta),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Quincena · Débito",
                        style = MaterialTheme.typography.bodySmall,
                        color = EtiquetaSobreTinta,
                    )
                    Text(
                        text = "+$8,500.00",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = VerdeIngresoCubierta,
                    )
                }
            }
        }
    }
}

/** Símbolo geométrico de 3 barras escalonadas y punto de balance (insinúa una F limpia). */
@Composable
private fun SimboloMarcaEntrada(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 48f
        val radio = CornerRadius(3.5f * u, 3.5f * u)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(8f * u, 9f * u),
            size = Size(32f * u, 7f * u),
            cornerRadius = radio,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.78f),
            topLeft = Offset(8f * u, 20.5f * u),
            size = Size(24f * u, 7f * u),
            cornerRadius = radio,
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(8f * u, 32f * u),
            size = Size(14f * u, 7f * u),
            cornerRadius = radio,
        )
        drawCircle(
            color = Color.White,
            radius = 3.5f * u,
            center = Offset(36.5f * u, 35.5f * u),
        )
    }
}
