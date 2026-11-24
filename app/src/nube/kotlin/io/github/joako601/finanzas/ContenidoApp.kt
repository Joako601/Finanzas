package io.github.joako601.finanzas

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import io.github.joako601.finanzas.ui.componentes.LogoKrenAnimado
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.componentes.toqueTactil
import io.github.joako601.finanzas.ui.navegacion.ContenedorPrincipal
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

private const val PREFS_INICIO_NUBE = "finanzas_modo_inicio"
private const val CLAVE_MODO_GUARDADO = "modo_guardado"
private const val CLAVE_YA_ENTRO_FINANZAS = "ya_entro_finanzas"

/**
 * Contenido raíz de la distribución nube (D-020): al abrir la app presenta la portada
 * editorial "Contraste Tinta" la primera vez para elegir entre iniciar sesión en la nube o
 * entrar en modo privado, recordando la elección para no bloquear al usuario en cada apertura.
 */
@Composable
fun ContenidoApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_INICIO_NUBE, Context.MODE_PRIVATE)
    }
    var yaEntroAFinanzas by rememberSaveable {
        mutableStateOf(prefs.getBoolean(CLAVE_YA_ENTRO_FINANZAS, false))
    }
    var modo by rememberSaveable {
        val guardado = prefs.getString(CLAVE_MODO_GUARDADO, null)
        val inicial = when (guardado) {
            ModoInicioNube.PRIVADO.name -> ModoInicioNube.PRIVADO
            ModoInicioNube.NUBE.name -> ModoInicioNube.NUBE
            else -> ModoInicioNube.SELECCION
        }
        mutableStateOf(inicial)
    }

    fun cambiarModo(nuevo: ModoInicioNube) {
        modo = nuevo
        val editor = prefs.edit().putString(CLAVE_MODO_GUARDADO, nuevo.name)
        if (nuevo == ModoInicioNube.PRIVADO) {
            yaEntroAFinanzas = true
            editor.putBoolean(CLAVE_YA_ENTRO_FINANZAS, true)
        }
        editor.apply()
    }

    when (modo) {
        ModoInicioNube.SELECCION -> PantallaBienvenidaNube(
            onConectarNube = { cambiarModo(ModoInicioNube.NUBE) },
            onEntrarPrivado = { cambiarModo(ModoInicioNube.PRIVADO) },
        )

        ModoInicioNube.NUBE -> SeccionNube(
            onSalir = {
                if (yaEntroAFinanzas) {
                    cambiarModo(ModoInicioNube.PRIVADO)
                } else {
                    cambiarModo(ModoInicioNube.SELECCION)
                }
            },
            onIrAFinanzas = { cambiarModo(ModoInicioNube.PRIVADO) },
        )

        // En modo privado la interfaz principal sigue limpia sin botones de nube a la vista,
        // pero desde el menú ⋮ o Bóveda ("Cuenta y catálogo") se puede abrir la sección nube
        // sin sobrescribir la preferencia guardada: al reabrir la app volverá directo a finanzas.
        ModoInicioNube.PRIVADO -> ContenedorPrincipal(
            onAbrirNube = { modo = ModoInicioNube.NUBE },
        )
    }
}

/**
 * Portada editorial "Contraste Tinta": plano superior negro OLED (#000000) con el símbolo
 * "Flujo", composición minimalista de tarjetas en el centro y titular; hoja inferior con
 * borde superior sutil y acciones en la zona natural del pulgar.
 *
 * En pantallas bajas (orientación horizontal o fuente al 150–200%), todo el contenedor hace
 * scroll vertical continuo para que la hoja inferior nunca colapse el plano superior.
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoTintaCubierta),
    ) {
        val esAlturaCompacta = maxHeight < 580.dp

        Column(
            modifier = if (esAlturaCompacta) {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            // Plano superior negro OLED: marca arriba, escena de tarjetas al centro, titular abajo.
            Box(
                modifier = if (esAlturaCompacta) {
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                } else {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = if (esAlturaCompacta) {
                        Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    } else {
                        Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    },
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Lockup limpio de marca (Logo 8 animado de Kren + nombre).
                    Row(
                        modifier = Modifier.graphicsLayer {
                            alpha = marcaProgreso.value
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LogoKrenAnimado(
                            progresoBarras = { marcaProgreso.value },
                            progresoPunto = { marcaProgreso.value },
                            modifier = Modifier.size(28.dp),
                        )
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
}

/**
 * Composición visual decorativa de dos tarjetas monocromáticas escalonadas (estilo Apple Wallet).
 * Se marca como decorativa para TalkBack para no competir con el titular ni los accesos.
 */
@Composable
private fun EscenaTarjetasEntrada(modifier: Modifier = Modifier) {
    val etiquetaDisponible = stringResource(R.string.bienvenida_escena_disponible)
    val etiquetaMovimiento = stringResource(R.string.bienvenida_escena_movimiento)

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
                        text = etiquetaDisponible,
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
                        text = etiquetaMovimiento,
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
