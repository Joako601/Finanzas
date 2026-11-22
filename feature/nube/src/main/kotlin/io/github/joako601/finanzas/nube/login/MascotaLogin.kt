package io.github.joako601.finanzas.nube.login

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube

/**
 * Mascota animada (alcancía guardián) coherente con la paleta de Finanzas.
 *
 * Reacciona a cada estado del inicio de sesión: reposo, atención al correo, privacidad
 * cubriéndose los ojos al escribir la contraseña, carga activa, celebración con salto y
 * check verde en éxito, y sacudida horizontal con alerta roja en error.
 */
@Composable
internal fun MascotaLogin(
    estado: EstadoMascotaLogin,
    contrasenaVisible: Boolean,
    intentoError: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Accesibilidad: respeta animación reducida del sistema.
    val reducirMovimiento = remember(context) {
        val escala = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        escala == 0f
    }

    val colorScheme = MaterialTheme.colorScheme
    val verdeExito = ColoresSemanticosNube.exito()
    val sobreVerde = ColoresSemanticosNube.sobreExito()

    val colorHaloObjetivo = when (estado) {
        EstadoMascotaLogin.Exito -> verdeExito.copy(alpha = 0.18f)
        EstadoMascotaLogin.Error -> colorScheme.error.copy(alpha = 0.16f)
        EstadoMascotaLogin.EscribiendoCorreo,
        EstadoMascotaLogin.EscribiendoContrasena,
        -> colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> colorScheme.surfaceVariant
    }
    val colorHaloState = animateColorAsState(
        targetValue = colorHaloObjetivo,
        animationSpec = if (reducirMovimiento) snap() else tween(durationMillis = 220),
        label = "colorHaloMascota",
    )

    val coberturaIzquierdaState = animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.EscribiendoContrasena) 1f else 0f,
        animationSpec = if (reducirMovimiento) {
            snap()
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "coberturaOjoIzquierdo",
    )
    // Si el usuario activa "mostrar contraseña", la patita derecha se asoma ligeramente.
    val coberturaDerechaState = animateFloatAsState(
        targetValue = when {
            estado != EstadoMascotaLogin.EscribiendoContrasena -> 0f
            contrasenaVisible -> 0.42f
            else -> 1f
        },
        animationSpec = if (reducirMovimiento) {
            snap()
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "coberturaOjoDerecho",
    )

    val miradaAbajoState = animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.EscribiendoCorreo) 1f else 0f,
        animationSpec = if (reducirMovimiento) snap() else tween(durationMillis = 180),
        label = "miradaAbajo",
    )

    val saltoExitoDpState = animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.Exito && !reducirMovimiento) -10f else 0f,
        animationSpec = if (reducirMovimiento) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "saltoExito",
    )

    val escalaMascotaState = animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.Exito && !reducirMovimiento) 1.07f else 1f,
        animationSpec = if (reducirMovimiento) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "escalaExito",
    )

    val progresoInsigniaState = animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.Exito || estado == EstadoMascotaLogin.Error) 1f else 0f,
        animationSpec = if (reducirMovimiento) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "insigniaEstado",
    )

    val sacudidaX = remember { Animatable(0f) }
    LaunchedEffect(estado, intentoError, reducirMovimiento) {
        if (estado == EstadoMascotaLogin.Error && !reducirMovimiento) {
            val secuencia = floatArrayOf(-12f, 12f, -8f, 8f, -4f, 0f)
            for (objetivo in secuencia) {
                sacudidaX.animateTo(
                    targetValue = objetivo,
                    animationSpec = tween(durationMillis = 48, easing = FastOutSlowInEasing),
                )
            }
        } else {
            sacudidaX.snapTo(0f)
        }
    }

    // Se mantiene como State<Float>? sin delegado `by` para leerlo solo en fase de dibujo (0 recomposiciones).
    val anguloCargaState: State<Float>? = if (estado == EstadoMascotaLogin.Cargando && !reducirMovimiento) {
        val transicion = rememberInfiniteTransition(label = "cargaMascota")
        transicion.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "anguloCarga",
        )
    } else {
        null
    }

    val descripcionEstado = stringResource(
        when (estado) {
            EstadoMascotaLogin.Reposo -> R.string.login_mascota_reposo
            EstadoMascotaLogin.EscribiendoCorreo -> R.string.login_mascota_correo
            EstadoMascotaLogin.EscribiendoContrasena -> R.string.login_mascota_contrasena
            EstadoMascotaLogin.Cargando -> R.string.login_mascota_cargando
            EstadoMascotaLogin.Exito -> R.string.login_mascota_exito
            EstadoMascotaLogin.Error -> R.string.login_mascota_error
        },
    )

    val colorPrimario = colorScheme.primary
    val colorCuerpo = colorScheme.primaryContainer
    val colorTrazo = colorScheme.onPrimaryContainer
    val colorHocico = ColoresSemanticosNube.hocicoMascota()
    val colorBordeHalo = when (estado) {
        EstadoMascotaLogin.Error -> colorScheme.error
        EstadoMascotaLogin.Exito -> verdeExito
        else -> colorScheme.outlineVariant
    }
    val alphaBordeHalo = if (estado == EstadoMascotaLogin.Error || estado == EstadoMascotaLogin.Exito) 0.7f else 0.5f
    val colorError = colorScheme.error
    val colorSobreError = colorScheme.onError

    Spacer(
        modifier = modifier
            .size(124.dp)
            .semantics { contentDescription = descripcionEstado }
            .offset {
                IntOffset(
                    x = sacudidaX.value.dp.roundToPx(),
                    y = saltoExitoDpState.value.dp.roundToPx(),
                )
            }
            .graphicsLayer {
                val escala = escalaMascotaState.value
                scaleX = escala
                scaleY = escala
            }
            .padding(4.dp)
            .drawWithCache {
                val centro = Offset(size.width / 2f, size.height / 2f)
                val radioHalo = size.minDimension * 0.46f
                val margenArco = (size.minDimension - radioHalo * 2f) / 2f
                val tamanoArco = Size(radioHalo * 2f, radioHalo * 2f)
                val topLeftArco = Offset(margenArco, margenArco)

                val radioCabeza = radioHalo * 0.68f
                val centroCabeza = Offset(centro.x, centro.y + radioHalo * 0.04f)
                val grosorTrazo = 2.2.dp.toPx()

                val trazoHalo = Stroke(width = 2.dp.toPx())
                val trazoArcoCarga = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                val trazoPrincipal = Stroke(width = grosorTrazo)
                val trazoRedondeado = Stroke(width = grosorTrazo, cap = StrokeCap.Round)
                val trazoHocico = Stroke(width = grosorTrazo * 0.85f)
                val trazoBocaSuave = Stroke(width = grosorTrazo * 0.9f, cap = StrokeCap.Round)
                val trazoCheck = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val grosorExclamacion = 2.6.dp.toPx()
                val radioPuntoExclamacionBase = 1.6.dp.toPx()
                val radioInsigniaBase = 14.dp.toPx()
                val centroInsignia = Offset(centro.x + radioHalo * 0.68f, centro.y + radioHalo * 0.64f)

                val radioOreja = radioCabeza * 0.28f
                val orejaIzq = Offset(centroCabeza.x - radioCabeza * 0.62f, centroCabeza.y - radioCabeza * 0.62f)
                val orejaDer = Offset(centroCabeza.x + radioCabeza * 0.62f, centroCabeza.y - radioCabeza * 0.62f)

                val anchoRanura = radioCabeza * 0.44f
                val altoRanura = 3.5.dp.toPx()
                val ranuraTopLeft = Offset(centroCabeza.x - anchoRanura / 2f, centroCabeza.y - radioCabeza * 0.72f)
                val ranuraSize = Size(anchoRanura, altoRanura)
                val ranuraCorner = CornerRadius(altoRanura, altoRanura)

                val ojoY = centroCabeza.y - radioCabeza * 0.14f
                val sepOjos = radioCabeza * 0.36f
                val ojoIzq = Offset(centroCabeza.x - sepOjos, ojoY)
                val ojoDer = Offset(centroCabeza.x + sepOjos, ojoY)
                val radioOjo = radioCabeza * 0.11f

                val cejaY = ojoY - radioOjo * 2.0f
                val desplazamientoCeja = 2.dp.toPx()
                val cejaIzqInicio = Offset(ojoIzq.x - radioOjo * 1.3f, cejaY + desplazamientoCeja)
                val cejaIzqFin = Offset(ojoIzq.x + radioOjo * 1.1f, cejaY - desplazamientoCeja)
                val cejaDerInicio = Offset(ojoDer.x - radioOjo * 1.1f, cejaY - desplazamientoCeja)
                val cejaDerFin = Offset(ojoDer.x + radioOjo * 1.3f, cejaY + desplazamientoCeja)

                val anchoHocico = radioCabeza * 0.58f
                val altoHocico = radioCabeza * 0.34f
                val hocicoTopLeft = Offset(
                    centroCabeza.x - anchoHocico / 2f,
                    centroCabeza.y + radioCabeza * 0.08f,
                )
                val hocicoSize = Size(anchoHocico, altoHocico)
                val orificioY = hocicoTopLeft.y + altoHocico * 0.5f
                val radioOrificio = radioCabeza * 0.055f
                val orificioIzq = Offset(centroCabeza.x - anchoHocico * 0.18f, orificioY)
                val orificioDer = Offset(centroCabeza.x + anchoHocico * 0.18f, orificioY)

                val bocaY = hocicoTopLeft.y + altoHocico + radioCabeza * 0.12f
                val pathOjoFelizIzq = crearPathOjoFeliz(ojoIzq, radioOjo * 1.4f)
                val pathOjoFelizDer = crearPathOjoFeliz(ojoDer, radioOjo * 1.4f)

                val pathBocaError = Path().apply {
                    moveTo(centroCabeza.x - radioCabeza * 0.16f, bocaY + 3.dp.toPx())
                    quadraticTo(centroCabeza.x, bocaY - 4.dp.toPx(), centroCabeza.x + radioCabeza * 0.16f, bocaY + 3.dp.toPx())
                }
                val pathBocaExito = Path().apply {
                    moveTo(centroCabeza.x - radioCabeza * 0.22f, bocaY - 2.dp.toPx())
                    quadraticTo(centroCabeza.x, bocaY + 8.dp.toPx(), centroCabeza.x + radioCabeza * 0.22f, bocaY - 2.dp.toPx())
                }
                val pathBocaNormal = Path().apply {
                    moveTo(centroCabeza.x - radioCabeza * 0.15f, bocaY - 1.dp.toPx())
                    quadraticTo(centroCabeza.x, bocaY + 4.dp.toPx(), centroCabeza.x + radioCabeza * 0.15f, bocaY - 1.dp.toPx())
                }
                // Path reutilizable con rewind() para la insignia animada de check.
                val pathCheck = Path()

                val pataBaseY = centroCabeza.y + radioCabeza * 0.84f
                val anchoPata = radioCabeza * 0.42f
                val altoPata = radioCabeza * 0.34f
                val tamanoPata = Size(anchoPata, altoPata)
                val desplazamientoMaximo = pataBaseY - ojoY

                onDrawBehind {
                    val colorHalo = colorHaloState.value
                    val coberturaIzquierda = coberturaIzquierdaState.value
                    val coberturaDerecha = coberturaDerechaState.value
                    val miradaAbajo = miradaAbajoState.value
                    val progresoInsignia = progresoInsigniaState.value
                    val anguloCarga = anguloCargaState?.value ?: 0f

                    // 1. Halo circular de fondo
                    drawCircle(color = colorHalo, radius = radioHalo, center = centro)
                    drawCircle(
                        color = colorBordeHalo.copy(alpha = alphaBordeHalo),
                        radius = radioHalo,
                        center = centro,
                        style = trazoHalo,
                    )

                    // 2. Arco giratorio cuando está cargando
                    if (estado == EstadoMascotaLogin.Cargando) {
                        drawArc(
                            color = colorPrimario,
                            startAngle = anguloCarga,
                            sweepAngle = 105f,
                            useCenter = false,
                            topLeft = topLeftArco,
                            size = tamanoArco,
                            style = trazoArcoCarga,
                        )
                    }

                    // 3. Orejas redondeadas de la alcancía guardián
                    dibujarOreja(orejaIzq, radioOreja, colorCuerpo, colorTrazo, trazoPrincipal)
                    dibujarOreja(orejaDer, radioOreja, colorCuerpo, colorTrazo, trazoPrincipal)

                    // 4. Cabeza principal
                    drawCircle(color = colorCuerpo, radius = radioCabeza, center = centroCabeza)
                    drawCircle(color = colorTrazo, radius = radioCabeza, center = centroCabeza, style = trazoPrincipal)

                    // 5. Ranura de moneda en la parte superior (sello de alcancía Finanzas)
                    drawRoundRect(
                        color = colorTrazo,
                        topLeft = ranuraTopLeft,
                        size = ranuraSize,
                        cornerRadius = ranuraCorner,
                    )

                    // 6. Ojos según el estado
                    if (estado == EstadoMascotaLogin.Exito) {
                        drawPath(pathOjoFelizIzq, color = colorTrazo, style = trazoRedondeado)
                        drawPath(pathOjoFelizDer, color = colorTrazo, style = trazoRedondeado)
                    } else {
                        val desplazamientoPupilaY = miradaAbajo * (radioOjo * 0.55f)
                        dibujarOjo(ojoIzq, radioOjo, desplazamientoPupilaY, colorTrazo)
                        dibujarOjo(ojoDer, radioOjo, desplazamientoPupilaY, colorTrazo)

                        // Cejas de alerta cuando hay error
                        if (estado == EstadoMascotaLogin.Error) {
                            drawLine(
                                color = colorTrazo,
                                start = cejaIzqInicio,
                                end = cejaIzqFin,
                                strokeWidth = grosorTrazo,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = colorTrazo,
                                start = cejaDerInicio,
                                end = cejaDerFin,
                                strokeWidth = grosorTrazo,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    // 7. Hocico de alcancía
                    drawOval(color = colorHocico, topLeft = hocicoTopLeft, size = hocicoSize)
                    drawOval(
                        color = colorTrazo,
                        topLeft = hocicoTopLeft,
                        size = hocicoSize,
                        style = trazoHocico,
                    )
                    drawCircle(color = colorTrazo, radius = radioOrificio, center = orificioIzq)
                    drawCircle(color = colorTrazo, radius = radioOrificio, center = orificioDer)

                    // 8. Boca expresiva
                    when (estado) {
                        EstadoMascotaLogin.Error -> drawPath(pathBocaError, color = colorTrazo, style = trazoRedondeado)
                        EstadoMascotaLogin.Exito -> drawPath(pathBocaExito, color = colorTrazo, style = trazoRedondeado)
                        else -> drawPath(pathBocaNormal, color = colorTrazo, style = trazoBocaSuave)
                    }

                    // 9. Patitas animadas que suben a cubrir los ojos al escribir contraseña
                    dibujarPata(
                        centro = Offset(ojoIzq.x, pataBaseY - desplazamientoMaximo * coberturaIzquierda),
                        tamano = tamanoPata,
                        relleno = colorCuerpo,
                        trazo = colorTrazo,
                        estiloTrazo = trazoPrincipal,
                    )
                    dibujarPata(
                        centro = Offset(ojoDer.x, pataBaseY - desplazamientoMaximo * coberturaDerecha),
                        tamano = tamanoPata,
                        relleno = colorCuerpo,
                        trazo = colorTrazo,
                        estiloTrazo = trazoPrincipal,
                    )

                    // 10. Insignia flotante de éxito (check verde) o error (alerta roja)
                    if (progresoInsignia > 0.05f) {
                        val radioInsignia = radioInsigniaBase * progresoInsignia
                        if (estado == EstadoMascotaLogin.Exito) {
                            drawCircle(color = verdeExito, radius = radioInsignia, center = centroInsignia)
                            pathCheck.rewind()
                            pathCheck.moveTo(
                                centroInsignia.x - radioInsignia * 0.42f,
                                centroInsignia.y + radioInsignia * 0.02f,
                            )
                            pathCheck.lineTo(
                                centroInsignia.x - radioInsignia * 0.1f,
                                centroInsignia.y + radioInsignia * 0.34f,
                            )
                            pathCheck.lineTo(
                                centroInsignia.x + radioInsignia * 0.44f,
                                centroInsignia.y - radioInsignia * 0.3f,
                            )
                            drawPath(
                                path = pathCheck,
                                color = sobreVerde,
                                style = trazoCheck,
                            )
                        } else if (estado == EstadoMascotaLogin.Error) {
                            drawCircle(color = colorError, radius = radioInsignia, center = centroInsignia)
                            drawLine(
                                color = colorSobreError,
                                start = Offset(centroInsignia.x, centroInsignia.y - radioInsignia * 0.42f),
                                end = Offset(centroInsignia.x, centroInsignia.y + radioInsignia * 0.1f),
                                strokeWidth = grosorExclamacion,
                                cap = StrokeCap.Round,
                            )
                            drawCircle(
                                color = colorSobreError,
                                radius = radioPuntoExclamacionBase * progresoInsignia,
                                center = Offset(centroInsignia.x, centroInsignia.y + radioInsignia * 0.42f),
                            )
                        }
                    }
                }
            },
    )
}

private fun crearPathOjoFeliz(centro: Offset, radio: Float): Path = Path().apply {
    moveTo(centro.x - radio, centro.y + radio * 0.25f)
    quadraticTo(centro.x, centro.y - radio * 0.9f, centro.x + radio, centro.y + radio * 0.25f)
}

private fun DrawScope.dibujarOreja(
    centro: Offset,
    radio: Float,
    relleno: Color,
    trazo: Color,
    estiloTrazo: Stroke,
) {
    drawCircle(color = relleno, radius = radio, center = centro)
    drawCircle(color = trazo, radius = radio, center = centro, style = estiloTrazo)
}

private fun DrawScope.dibujarOjo(
    centro: Offset,
    radio: Float,
    desplazamientoPupilaY: Float,
    colorTrazo: Color,
) {
    drawCircle(
        color = colorTrazo,
        radius = radio,
        center = Offset(centro.x, centro.y + desplazamientoPupilaY),
    )
    // Brillo sutil del ojo
    drawCircle(
        color = Color.White,
        radius = radio * 0.34f,
        center = Offset(centro.x - radio * 0.28f, centro.y + desplazamientoPupilaY - radio * 0.28f),
    )
}

private fun DrawScope.dibujarPata(
    centro: Offset,
    tamano: Size,
    relleno: Color,
    trazo: Color,
    estiloTrazo: Stroke,
) {
    val esquina = Offset(centro.x - tamano.width / 2f, centro.y - tamano.height / 2f)
    drawOval(color = relleno, topLeft = esquina, size = tamano)
    drawOval(color = trazo, topLeft = esquina, size = tamano, style = estiloTrazo)
}
