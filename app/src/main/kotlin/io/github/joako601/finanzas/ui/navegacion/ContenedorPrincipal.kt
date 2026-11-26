package io.github.joako601.finanzas.ui.navegacion

import io.github.joako601.finanzas.ui.perfil.PreferenciasPrivacidad
import io.github.joako601.finanzas.ui.componentes.LocalMontosOcultos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.ui.metas.GastoDesdeRetiro
import io.github.joako601.finanzas.ui.analisis.PantallaAnalisis
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.metas.PantallaMetas
import io.github.joako601.finanzas.ui.movimientos.PantallaMovimientos
import io.github.joako601.finanzas.ui.perfil.CuentaPerfil
import io.github.joako601.finanzas.ui.perfil.PantallaPerfil
import io.github.joako601.finanzas.ui.perfil.SubpantallaPerfil

/**
 * Contenedor principal con la [BarraNavegacionInferior] de 4 apartados flotando sobre el
 * contenido con difuminado superior progresivo.
 *
 * - Tab 1 ([PestanaPrincipal.INICIO]): conecta con [PantallaMovimientos].
 * - Tab 2 ([PestanaPrincipal.ANALISIS]): conecta con [PantallaAnalisis] (curva de liquidez
 *   histórica + proyección a 30 días, flujo del periodo y gastos por categoría).
 * - Tab 3 ([PestanaPrincipal.METAS]): conecta con [PantallaMetas] (topes mensuales por categoría
 *   con ritmo del mes y apartados de ahorro).
 * - Tab 4 ([PestanaPrincipal.PERFIL]): conecta con [PantallaPerfil] (cuenta si la hay, respaldo
 *   cifrado, CSV y recordatorios).
 *
 * @param cuenta datos de la cuenta nube para el Perfil; `null` en la distribución privada.
 * @param mercado contenido de la 5.ª pestaña ([PestanaPrincipal.MERCADO]), recibe el margen
 *   inferior; `null` la oculta (distribución privada o nube sin sesión ni datos). Si
 *   desaparece estando abierta, se vuelve a Inicio.
 * - Conserva el estado de UI (scroll de listas) al cambiar de pestaña mediante [rememberSaveableStateHolder].
 */
@Composable
fun ContenedorPrincipal(
    cuenta: CuentaPerfil? = null,
    mercado: (@Composable (margenInferior: Dp) -> Unit)? = null,
) {
    var pestanaActual by rememberSaveable { mutableStateOf(PestanaPrincipal.INICIO) }
    val sinMercado = mercado == null
    LaunchedEffect(sinMercado) {
        if (sinMercado && pestanaActual == PestanaPrincipal.MERCADO) pestanaActual = PestanaPrincipal.INICIO
    }
    val pestanas = remember(sinMercado) {
        PestanaPrincipal.entries.filter { !sinMercado || it != PestanaPrincipal.MERCADO }
    }
    var subpantallaPerfil by rememberSaveable { mutableStateOf(SubpantallaPerfil.NINGUNA) }
    // Categoría tocada en Análisis que Metas debe abrir; se consume al atenderla.
    var categoriaParaMetas by remember { mutableStateOf<CuentaId?>(null) }
    // Retiro de un apartado que Inicio debe abrir como gasto; se consume al atenderlo.
    var gastoParaInicio by remember { mutableStateOf<GastoDesdeRetiro?>(null) }
    // Movimiento tocado en el detalle de un tope que Inicio debe abrir para editar.
    var movimientoParaEditar by remember { mutableStateOf<MovimientoId?>(null) }
    val guardadorEstado = rememberSaveableStateHolder()
    // Modo discreto: arranca como diga Perfil → Privacidad y el ojo de Inicio lo cambia en la sesión.
    val contexto = LocalContext.current
    var montosOcultos by rememberSaveable { mutableStateOf(PreferenciasPrivacidad(contexto).iniciarOculto) }

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
                    // El modo discreto es de Inicio: lo que se ve en público al abrir la app.
                    PestanaPrincipal.INICIO -> CompositionLocalProvider(LocalMontosOcultos provides montosOcultos) {
                        PantallaMovimientos(
                            margenInferiorExtra = AlturaTotalBarraInferior,
                            onAlternarMontos = { montosOcultos = !montosOcultos },
                            gastoPedido = gastoParaInicio,
                            onGastoAtendido = { gastoParaInicio = null },
                            movimientoPedido = movimientoParaEditar,
                            onMovimientoAtendido = { movimientoParaEditar = null },
                        )
                    }

                    PestanaPrincipal.ANALISIS -> PantallaAnalisis(
                        margenInferiorExtra = AlturaTotalBarraInferior,
                        onTocarCategoria = {
                            categoriaParaMetas = it
                            pestanaActual = PestanaPrincipal.METAS
                        },
                    )

                    PestanaPrincipal.METAS -> PantallaMetas(
                        margenInferiorExtra = AlturaTotalBarraInferior,
                        categoriaPedida = categoriaParaMetas,
                        onCategoriaAtendida = { categoriaParaMetas = null },
                        onRegistrarGasto = {
                            gastoParaInicio = it
                            pestanaActual = PestanaPrincipal.INICIO
                        },
                        onEditarMovimiento = {
                            movimientoParaEditar = it
                            pestanaActual = PestanaPrincipal.INICIO
                        },
                    )

                    PestanaPrincipal.MERCADO -> mercado?.invoke(AlturaTotalBarraInferior)

                    PestanaPrincipal.PERFIL -> PantallaPerfil(
                        cuenta = cuenta,
                        subpantalla = subpantallaPerfil,
                        onSubpantalla = { subpantallaPerfil = it },
                        margenInferiorExtra = AlturaTotalBarraInferior,
                    )
                }
            }
        }

        BarraNavegacionInferior(
            seleccionada = pestanaActual,
            pestanas = pestanas,
            onSeleccionar = {
                // Salir de Perfil, o volver a tocarlo, lo deja en su raíz (patrón de pestañas iOS).
                if (it != PestanaPrincipal.PERFIL || pestanaActual == PestanaPrincipal.PERFIL) {
                    subpantallaPerfil = SubpantallaPerfil.NINGUNA
                }
                pestanaActual = it
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
        )
    }
}
