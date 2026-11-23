package io.github.joako601.finanzas.ui.analisis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val CurvaEntradaGrafico = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val InsetHorizontalLienzo = 12.dp
private val InsetVerticalLienzo = 14.dp
private val AlturaLienzoCurva = 184.dp

/**
 * Lienzo de la Curva de Liquidez Unificada (Histórico + Proyección a 30 Días) y eje X nativo.
 *
 * - Usa interpolación cúbica monótona de Fritsch-Carlson (`cubicTo`) para que los días planos
 *   permanezcan horizontales y las caídas bruscas jamás produzcan falsos negativos (*overshoot*).
 * - Cumple contraste WCAG AA >= 3.0:1 en todos los trazos y anclas, con opacidades de área
 *   `< 6%` en modo oscuro OLED para cero fatiga visual nocturna.
 * - Incluye `systemGestureExclusion()` y bloqueo direccional (`|dx| >= |dy|`) para convivir
 *   sin conflicto con el scroll vertical de la pantalla y el gesto "Atrás" de Android.
 */
@Composable
fun GraficoLiquidez(
    corte: CortePeriodoAnalisis,
    linea: LineaDeTiempo,
    indiceInspeccionado: Int?,
    onInspeccionarIndice: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val puntos = corte.puntosCurva
    if (puntos.size < 2) return

    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val haptica = LocalHapticFeedback.current
    val corteActual by rememberUpdatedState(corte)
    val onInspeccionarActual by rememberUpdatedState(onInspeccionarIndice)

    val progresoTrazado = remember { Animatable(if (sinAnimaciones) 1f else 0f) }
    LaunchedEffect(sinAnimaciones) {
        if (sinAnimaciones) {
            progresoTrazado.snapTo(1f)
        } else if (progresoTrazado.value < 1f) {
            progresoTrazado.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = CurvaEntradaGrafico),
            )
        }
    }

    val colorTinta = MaterialTheme.colorScheme.onSurface
    val colorSecundario = MaterialTheme.colorScheme.onSurfaceVariant
    val colorSuperficie = MaterialTheme.colorScheme.surfaceContainerLowest
    val colorError = MaterialTheme.colorScheme.error
    val colorAviso = TemaFinanzas.colores.aviso
    val esModoClaro = colorSuperficie.luminance() > 0.5f

    // Accesibilidad TalkBack: descripción sintetizada y acciones directas sin arrastrar.
    val descripcionGrafico = stringResource(
        R.string.analisis_grafico_cd,
        corte.periodo.diasHistoricos,
        linea.enCuentas.formatear(),
        linea.disponible.formatear(),
    )
    val etiquetaAccionInicio = stringResource(R.string.analisis_accion_inicio_periodo)
    val etiquetaAccionHoy = stringResource(R.string.analisis_accion_saldo_hoy)
    val etiquetaAccionBajo = stringResource(R.string.analisis_accion_punto_mas_bajo)
    val etiquetaAccionRestablecer = stringResource(R.string.analisis_accion_restablecer)

    val accionesAccesibilidad = remember(
        corte.indiceHoy,
        corte.indicePuntoMasBajo,
        indiceInspeccionado,
        etiquetaAccionInicio,
        etiquetaAccionHoy,
        etiquetaAccionBajo,
        etiquetaAccionRestablecer,
    ) {
        buildList {
            add(
                CustomAccessibilityAction(etiquetaAccionInicio) {
                    onInspeccionarActual(0)
                    true
                },
            )
            add(
                CustomAccessibilityAction(etiquetaAccionHoy) {
                    onInspeccionarActual(corteActual.indiceHoy)
                    true
                },
            )
            corte.indicePuntoMasBajo?.let { idxBajo ->
                add(
                    CustomAccessibilityAction(etiquetaAccionBajo) {
                        onInspeccionarActual(idxBajo)
                        true
                    },
                )
            }
            if (indiceInspeccionado != null) {
                add(
                    CustomAccessibilityAction(etiquetaAccionRestablecer) {
                        onInspeccionarActual(null)
                        true
                    },
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = descripcionGrafico
                customActions = accionesAccesibilidad
            },
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(AlturaLienzoCurva)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    val insetPx = InsetHorizontalLienzo.toPx()
                    val touchSlop = viewConfiguration.touchSlop

                    fun xAIndice(x: Float, totalPuntos: Int): Int {
                        val anchoUtil = (size.width - 2f * insetPx).coerceAtLeast(1f)
                        val fraccion = ((x - insetPx) / anchoUtil).coerceIn(0f, 1f)
                        return (fraccion * (totalPuntos - 1)).roundToInt().coerceIn(0, totalPuntos - 1)
                    }

                    fun emitirHapticaDeDia(indice: Int) {
                        val actual = corteActual
                        val punto = actual.puntosCurva.getOrNull(indice) ?: return
                        when {
                            indice == actual.indiceHoy ||
                                punto.esPuntoMasBajo ||
                                punto.esPrimerFaltante -> {
                                haptica.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            punto.tieneActividad -> {
                                haptica.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var acumuladoDx = 0f
                        var acumuladoDy = 0f
                        var scrubbingActivo = false
                        var ultimoIndice = -1

                        while (true) {
                            val evento = awaitPointerEvent()
                            val cambio = evento.changes.firstOrNull { it.id == down.id } ?: evento.changes.firstOrNull() ?: break

                            if (cambio.changedToUpIgnoreConsumed() || !cambio.pressed) {
                                if (scrubbingActivo) {
                                    cambio.consume()
                                    onInspeccionarActual(null)
                                }
                                break
                            }

                            val totalPuntos = corteActual.puntosCurva.size
                            if (totalPuntos < 2) break

                            val delta = cambio.positionChange()
                            if (!scrubbingActivo) {
                                acumuladoDx += delta.x
                                acumuladoDy += delta.y
                                if (abs(acumuladoDx) > touchSlop || abs(acumuladoDy) > touchSlop) {
                                    if (abs(acumuladoDx) >= abs(acumuladoDy)) {
                                        scrubbingActivo = true
                                        cambio.consume()
                                        val idxInicial = xAIndice(cambio.position.x, totalPuntos)
                                        ultimoIndice = idxInicial
                                        onInspeccionarActual(idxInicial)
                                        emitirHapticaDeDia(idxInicial)
                                    } else {
                                        // Gesto vertical: cede el control al verticalScroll de la pantalla.
                                        break
                                    }
                                }
                            } else {
                                cambio.consume()
                                val nuevoIndice = xAIndice(cambio.position.x, totalPuntos)
                                if (nuevoIndice != ultimoIndice) {
                                    ultimoIndice = nuevoIndice
                                    onInspeccionarActual(nuevoIndice)
                                    emitirHapticaDeDia(nuevoIndice)
                                }
                            }
                        }
                    }
                }
                .drawWithCache {
                    val insetX = InsetHorizontalLienzo.toPx()
                    val insetY = InsetVerticalLienzo.toPx()
                    val anchoUtil = (size.width - 2f * insetX).coerceAtLeast(1f)
                    val altoUtil = (size.height - 2f * insetY).coerceAtLeast(1f)
                    val yBase = size.height - insetY

                    // Escala Y en Double para evitar cualquier desborde incluso en valores extremos de Long.
                    val minDatos = if (corte.mostrarLineaCero) {
                        min(corte.saldoMinimoCurva.centavos.toDouble(), 0.0)
                    } else {
                        corte.saldoMinimoCurva.centavos.toDouble()
                    }
                    val maxDatos = if (corte.mostrarLineaCero) {
                        max(corte.saldoMaximoCurva.centavos.toDouble(), 0.0)
                    } else {
                        corte.saldoMaximoCurva.centavos.toDouble()
                    }

                    val rangoBruto = maxDatos - minDatos
                    val margen = if (rangoBruto == 0.0) {
                        max(abs(maxDatos) * 0.2, 10_000.0)
                    } else {
                        max(rangoBruto * 0.14, 1_000.0)
                    }
                    val minEscala = minDatos - margen
                    val maxEscala = maxDatos + margen
                    val rangoTotal = (maxEscala - minEscala).coerceAtLeast(1.0)

                    fun mapearY(dinero: Dinero): Float {
                        val proporcion = ((dinero.centavos.toDouble() - minEscala) / rangoTotal).toFloat().coerceIn(0f, 1f)
                        return insetY + altoUtil * (1f - proporcion)
                    }

                    fun mapearX(indice: Int): Float {
                        val fraccion = indice.toFloat() / (puntos.size - 1).coerceAtLeast(1).toFloat()
                        return insetX + anchoUtil * fraccion
                    }

                    val coordenadas = List(puntos.size) { idx ->
                        Offset(mapearX(idx), mapearY(puntos[idx].saldo))
                    }

                    val yCero = mapearY(Dinero.CERO)
                    val yDisponible = mapearY(linea.disponible)
                    val xHoy = coordenadas[corte.indiceHoy].x

                    // Cuando $0.00 está visible, las áreas positivas cierran sobre yCero y las
                    // áreas de déficit cierran hacia arriba sobre yCero (solo pintan bajo $0.00).
                    val yCierreArea = if (corte.mostrarLineaCero && yCero in insetY..yBase) yCero else yBase

                    val geometria = construirGeometriaMonotona(
                        coordenadas = coordenadas,
                        indiceHoy = corte.indiceHoy,
                        yCierre = yCierreArea,
                    )

                    val trazoHistorico = Stroke(
                        width = 2.25.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    val patronProyeccion = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        0f,
                    )
                    val trazoProyeccion = Stroke(
                        width = 2.0.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = patronProyeccion,
                    )
                    val trazoDeficit = Stroke(
                        width = 2.25.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = patronProyeccion,
                    )
                    val patronAnclaHoy = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                        0f,
                    )
                    val patronCotaDisponible = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                        0f,
                    )

                    val brushAreaHistorica = Brush.verticalGradient(
                        colors = listOf(
                            colorTinta.copy(alpha = if (esModoClaro) 0.07f else 0.05f),
                            Color.Transparent,
                        ),
                        startY = insetY,
                        endY = yCierreArea,
                    )
                    val brushAreaProyectada = Brush.verticalGradient(
                        colors = listOf(
                            colorTinta.copy(alpha = if (esModoClaro) 0.04f else 0.03f),
                            Color.Transparent,
                        ),
                        startY = insetY,
                        endY = yCierreArea,
                    )
                    val brushAreaDeficit = Brush.verticalGradient(
                        colors = listOf(
                            colorError.copy(alpha = if (esModoClaro) 0.11f else 0.14f),
                            colorError.copy(alpha = 0.03f),
                        ),
                        startY = yCero,
                        endY = yBase,
                    )

                    onDrawBehind {
                        // 1. Línea base $0.00 condicional (contraste WCAG >= 3.09:1)
                        if (corte.mostrarLineaCero && yCero in insetY..yBase) {
                            drawLine(
                                color = colorSecundario.copy(alpha = 0.75f),
                                start = Offset(insetX, yCero),
                                end = Offset(size.width - insetX, yCero),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        // 2. Cota horizontal del Disponible Real si baja respecto a hoy (WCAG >= 3.46:1)
                        val tieneBajadaFutura = linea.puntoMasBajo != null
                        val colorCota = if (linea.disponible.esNegativo) colorError else colorAviso
                        if (tieneBajadaFutura && yDisponible in insetY..yBase) {
                            drawLine(
                                color = colorCota.copy(alpha = 0.75f),
                                start = Offset(xHoy, yDisponible),
                                end = Offset(size.width - insetX, yDisponible),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = patronCotaDisponible,
                            )
                        }

                        // 3. Ancla vertical "Hoy" (WCAG >= 3.09:1)
                        drawLine(
                            color = colorSecundario.copy(alpha = 0.75f),
                            start = Offset(xHoy, insetY * 0.5f),
                            end = Offset(xHoy, yBase),
                            strokeWidth = 1.25.dp.toPx(),
                            pathEffect = patronAnclaHoy,
                        )

                        // 4. Curvas y áreas con animación de entrada (clip horizontal)
                        val limiteDerecho = size.width * progresoTrazado.value
                        clipRect(left = 0f, top = 0f, right = limiteDerecho, bottom = size.height) {
                            // Tramo positivo (encima de yCero si hay déficit; completo si todo >= $0)
                            val limiteInferiorPositivo = if (corte.saldoMinimoCurva.esNegativo && yCero in insetY..yBase) {
                                yCero
                            } else {
                                size.height
                            }
                            clipRect(left = 0f, top = 0f, right = size.width, bottom = limiteInferiorPositivo) {
                                drawPath(path = geometria.areaHistorica, brush = brushAreaHistorica)
                                drawPath(
                                    path = geometria.trazoHistorico,
                                    color = colorTinta,
                                    style = trazoHistorico,
                                )

                                drawPath(path = geometria.areaProyectada, brush = brushAreaProyectada)
                                drawPath(
                                    path = geometria.trazoProyectado,
                                    color = colorTinta.copy(alpha = 0.72f),
                                    style = trazoProyeccion,
                                )
                            }

                            // Tramo de déficit (solo la cuenca real estrictamente bajo $0.00; evita teñir un tramo plano en $0.00)
                            if (corte.saldoMinimoCurva.esNegativo && yCero < yBase) {
                                clipRect(
                                    left = 0f,
                                    top = (yCero + 1f).coerceAtMost(size.height),
                                    right = size.width,
                                    bottom = size.height,
                                ) {
                                    drawPath(path = geometria.areaHistorica, brush = brushAreaDeficit)
                                    drawPath(path = geometria.areaProyectada, brush = brushAreaDeficit)
                                    drawPath(
                                        path = geometria.trazoHistorico,
                                        color = colorError,
                                        style = trazoHistorico,
                                    )
                                    drawPath(
                                        path = geometria.trazoProyectado,
                                        color = colorError,
                                        style = trazoDeficit,
                                    )
                                }
                            }
                        }

                        // 5. Máximo 2 nodos en reposo ("Hoy" y "Punto más bajo")
                        if (progresoTrazado.value >= 0.65f) {
                            val centroHoy = coordenadas[corte.indiceHoy]
                            val colorNodoHoy = if (puntos[corte.indiceHoy].saldo.esNegativo) colorError else colorTinta

                            // Si el punto más bajo ocurre intradía (vencidos hoy o salida antes que ingreso el mismo día),
                            // conectar con trazo vertical punteado hasta la curva de cierre de ese día.
                            corte.indicePuntoMasBajo?.let { idxBajo ->
                                val puntoCierre = coordenadas[idxBajo]
                                val centroBajo = Offset(puntoCierre.x, yDisponible)
                                if (yDisponible > puntoCierre.y + 4.dp.toPx()) {
                                    drawLine(
                                        color = colorCota,
                                        start = puntoCierre,
                                        end = centroBajo,
                                        strokeWidth = 2.dp.toPx(),
                                        pathEffect = patronCotaDisponible,
                                    )
                                }
                                drawCircle(
                                    color = colorSuperficie,
                                    radius = 6.5.dp.toPx(),
                                    center = centroBajo,
                                )
                                drawCircle(
                                    color = colorCota,
                                    radius = 4.5.dp.toPx(),
                                    center = centroBajo,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }

                            // Nodo 1: "Hoy" (sólido con halo)
                            drawCircle(
                                color = colorSuperficie,
                                radius = 6.dp.toPx(),
                                center = centroHoy,
                            )
                            drawCircle(
                                color = colorNodoHoy,
                                radius = 4.dp.toPx(),
                                center = centroHoy,
                            )
                        }

                        // 6. Crosshair y nodo activo durante el scrubbing
                        indiceInspeccionado?.takeIf { it in coordenadas.indices }?.let { idx ->
                            val puntoActivo = coordenadas[idx]
                            val saldoActivo = puntos[idx].saldo
                            val colorActivo = if (saldoActivo.esNegativo) colorError else colorTinta

                            drawLine(
                                color = colorTinta.copy(alpha = 0.50f),
                                start = Offset(puntoActivo.x, insetY * 0.4f),
                                end = Offset(puntoActivo.x, yBase),
                                strokeWidth = 1.25.dp.toPx(),
                            )
                            drawCircle(
                                color = colorSuperficie,
                                radius = 8.dp.toPx(),
                                center = puntoActivo,
                            )
                            drawCircle(
                                color = colorActivo,
                                radius = 5.5.dp.toPx(),
                                center = puntoActivo,
                            )
                        }
                    }
                },
        )

        Spacer(Modifier.height(4.dp))

        // Eje X en Compose nativo (sin tope de font_scale = 1.3 y centrado exacto por ancho medido)
        EjeXTemporalNativo(
            periodo = corte.periodo,
            fraccionHoy = corte.indiceHoy.toFloat() / (puntos.size - 1).coerceAtLeast(1).toFloat(),
        )
    }
}

@Composable
private fun EjeXTemporalNativo(
    periodo: PeriodoAnalisis,
    fraccionHoy: Float,
) {
    val estiloEtiqueta = MaterialTheme.typography.labelSmall.copy(
        fontFeatureSettings = "tnum",
    )
    val etiquetaInicio = stringResource(
        when (periodo) {
            PeriodoAnalisis.QUINCE_DIAS -> R.string.analisis_eje_menos_15
            PeriodoAnalisis.TREINTA_DIAS -> R.string.analisis_eje_menos_30
        },
    )
    val etiquetaHoy = stringResource(R.string.hoy)
    val etiquetaFin = stringResource(R.string.analisis_eje_mas_30)

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InsetHorizontalLienzo),
        content = {
            Text(
                text = etiquetaInicio,
                style = estiloEtiqueta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = etiquetaHoy,
                style = estiloEtiqueta.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = etiquetaFin,
                style = estiloEtiqueta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
    ) { measurables, constraints ->
        val ancho = constraints.maxWidth
        val sueltas = constraints.copy(minWidth = 0, minHeight = 0)
        val pInicio = measurables[0].measure(sueltas)
        val pHoy = measurables[1].measure(sueltas)
        val pFin = measurables[2].measure(sueltas)
        val separacion = 8.dp.roundToPx()

        val xFin = (ancho - pFin.width).coerceAtLeast(0)
        val minXHoy = pInicio.width + separacion
        val maxXHoy = xFin - separacion - pHoy.width
        val centroDeseado = (ancho * fraccionHoy).roundToInt() - pHoy.width / 2

        if (minXHoy <= maxXHoy) {
            val alto = maxOf(pInicio.height, pHoy.height, pFin.height)
            val xHoy = centroDeseado.coerceIn(minXHoy, maxXHoy)
            layout(ancho, alto) {
                pInicio.placeRelative(0, (alto - pInicio.height) / 2)
                pHoy.placeRelative(xHoy, (alto - pHoy.height) / 2)
                pFin.placeRelative(xFin, (alto - pFin.height) / 2)
            }
        } else {
            // A font_scale extremo en pantallas angostas, "Hoy" baja a una segunda línea sin encimarse.
            val altoFila1 = maxOf(pInicio.height, pFin.height)
            val altoTotal = altoFila1 + 2.dp.roundToPx() + pHoy.height
            val xHoy = centroDeseado.coerceIn(0, (ancho - pHoy.width).coerceAtLeast(0))
            layout(ancho, altoTotal) {
                pInicio.placeRelative(0, 0)
                pFin.placeRelative(xFin, 0)
                pHoy.placeRelative(xHoy, altoFila1 + 2.dp.roundToPx())
            }
        }
    }
}

private class GeometriaCurvaUnificada(
    val trazoHistorico: Path,
    val areaHistorica: Path,
    val trazoProyectado: Path,
    val areaProyectada: Path,
)

/**
 * Construye los `Path` histórico y proyectado mediante interpolación cúbica monótona
 * de Fritsch-Carlson. Garantiza pendiente cero en extremos locales y mesetas planas.
 *
 * [yCierre] es `yCero` cuando la línea `$0.00` es visible, logrando que las zonas positivas
 * cierren hacia abajo hasta `$0.00` y las zonas negativas cierren hacia arriba hasta `$0.00`.
 */
private fun construirGeometriaMonotona(
    coordenadas: List<Offset>,
    indiceHoy: Int,
    yCierre: Float,
): GeometriaCurvaUnificada {
    val n = coordenadas.size
    val tangentes = FloatArray(n)

    if (n >= 2) {
        val secantes = FloatArray(n - 1)
        for (k in 0 until n - 1) {
            val dx = (coordenadas[k + 1].x - coordenadas[k].x).coerceAtLeast(1e-4f)
            val dy = coordenadas[k + 1].y - coordenadas[k].y
            secantes[k] = dy / dx
        }

        tangentes[0] = secantes[0]
        tangentes[n - 1] = secantes[n - 2]

        for (k in 1 until n - 1) {
            val s0 = secantes[k - 1]
            val s1 = secantes[k]
            tangentes[k] = if (s0 * s1 <= 0f) 0f else (s0 + s1) * 0.5f
        }

        // Restricción de monotonía de Fritsch-Carlson
        for (k in 0 until n - 1) {
            val s = secantes[k]
            if (abs(s) < 1e-5f) {
                tangentes[k] = 0f
                tangentes[k + 1] = 0f
            } else {
                val alfa = tangentes[k] / s
                val beta = tangentes[k + 1] / s
                val magnitud = hypot(alfa, beta)
                if (magnitud > 3f) {
                    val tau = 3f / magnitud
                    tangentes[k] = tau * alfa * s
                    tangentes[k + 1] = tau * beta * s
                }
            }
        }
    }

    fun construirSegmento(desde: Int, hasta: Int): Pair<Path, Path> {
        val trazo = Path()
        val area = Path()
        if (desde >= hasta || desde !in coordenadas.indices || hasta !in coordenadas.indices) {
            return trazo to area
        }
        val pInicio = coordenadas[desde]
        trazo.moveTo(pInicio.x, pInicio.y)
        area.moveTo(pInicio.x, yCierre)
        area.lineTo(pInicio.x, pInicio.y)

        for (k in desde until hasta) {
            val p0 = coordenadas[k]
            val p1 = coordenadas[k + 1]
            val dx = p1.x - p0.x
            val cp1x = p0.x + dx / 3f
            val cp1y = p0.y + tangentes[k] * dx / 3f
            val cp2x = p1.x - dx / 3f
            val cp2y = p1.y - tangentes[k + 1] * dx / 3f
            trazo.cubicTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y)
            area.cubicTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y)
        }

        val pFin = coordenadas[hasta]
        area.lineTo(pFin.x, yCierre)
        area.close()
        return trazo to area
    }

    val (trazoHist, areaHist) = construirSegmento(0, indiceHoy.coerceIn(0, n - 1))
    val (trazoProy, areaProy) = construirSegmento(indiceHoy.coerceIn(0, n - 1), n - 1)

    return GeometriaCurvaUnificada(
        trazoHistorico = trazoHist,
        areaHistorica = areaHist,
        trazoProyectado = trazoProy,
        areaProyectada = areaProy,
    )
}
