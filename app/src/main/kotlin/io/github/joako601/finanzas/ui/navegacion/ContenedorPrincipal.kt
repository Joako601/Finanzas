package io.github.joako601.finanzas.ui.navegacion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.ui.analisis.PantallaAnalisis
import io.github.joako601.finanzas.ui.boveda.PantallaBoveda
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.metas.PantallaMetas
import io.github.joako601.finanzas.ui.movimientos.PantallaMovimientos

/**
 * Contenedor principal con la [BarraNavegacionInferior] de 4 apartados flotando sobre el
 * contenido con difuminado superior progresivo.
 *
 * - Tab 1 ([PestanaPrincipal.INICIO]): conecta con [PantallaMovimientos].
 * - Tab 2 ([PestanaPrincipal.ANALISIS]): conecta con [PantallaAnalisis] (curva de liquidez
 *   histórica + proyección a 30 días, flujo del periodo y gastos por categoría).
 * - Tab 3 ([PestanaPrincipal.METAS]): conecta con [PantallaMetas] (topes mensuales por categoría
 *   con ritmo del mes y apartados de ahorro).
 * - Tab 4 ([PestanaPrincipal.BOVEDA]): conecta con [PantallaBoveda] (respaldo cifrado, CSV,
 *   estado de recordatorios y acceso a nube cuando aplica).
 * - Conserva el estado de UI (scroll de listas) al cambiar de pestaña mediante [rememberSaveableStateHolder].
 */
@Composable
fun ContenedorPrincipal(
    onAbrirNube: (() -> Unit)? = null,
) {
    var pestanaActual by rememberSaveable { mutableStateOf(PestanaPrincipal.INICIO) }
    val guardadorEstado = rememberSaveableStateHolder()

    // Si el usuario está en las pestañas 2, 3 o 4, el botón Atrás regresa primero a Inicio.
    BackHandler(enabled = pestanaActual != PestanaPrincipal.INICIO) {
        pestanaActual = PestanaPrincipal.INICIO
    }

    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val desplazamientoPx = with(LocalDensity.current) { 18.dp.roundToPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = pestanaActual,
            transitionSpec = {
                if (sinAnimaciones) {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                } else {
                    val direccion = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(260)) { direccion * desplazamientoPx } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(200)) { -direccion * desplazamientoPx } + fadeOut(tween(180)))
                }
            },
            label = "navegacionInferior",
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
        ) { pestana ->
            guardadorEstado.SaveableStateProvider(key = pestana) {
                when (pestana) {
                    PestanaPrincipal.INICIO -> PantallaMovimientos(
                        onAbrirNube = onAbrirNube,
                        margenInferiorExtra = AlturaTotalBarraInferior,
                    )

                    PestanaPrincipal.ANALISIS -> PantallaAnalisis(
                        margenInferiorExtra = AlturaTotalBarraInferior,
                    )

                    PestanaPrincipal.METAS -> PantallaMetas(
                        margenInferiorExtra = AlturaTotalBarraInferior,
                    )

                    PestanaPrincipal.BOVEDA -> PantallaBoveda(
                        onAbrirNube = onAbrirNube,
                        margenInferiorExtra = AlturaTotalBarraInferior,
                    )
                }
            }
        }

        BarraNavegacionInferior(
            seleccionada = pestanaActual,
            onSeleccionar = { pestanaActual = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
        )
    }
}
