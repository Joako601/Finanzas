package io.github.joako601.finanzas.nube.catalogo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieConstants
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.AnimacionLottie
import io.github.joako601.finanzas.nube.componentes.ChipEstadoServidor
import io.github.joako601.finanzas.nube.componentes.EstadoConexionServidor
import io.github.joako601.finanzas.nube.componentes.toqueTactil
import io.github.joako601.finanzas.nube.divisas.DivisasViewModel
import io.github.joako601.finanzas.nube.divisas.EstadoUiDivisas
import io.github.joako601.finanzas.nube.divisas.ParDivisas
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisas
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisasFalso
import io.github.joako601.finanzas.nube.divisas.SeccionDivisas
import io.github.joako601.finanzas.nube.recuperar.DialogoCerrarSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion

/**
 * Pantalla principal autenticada de `:feature:nube`: catálogo de productos financieros
 * y cotizaciones/conversor de divisas conectados al backend Spring Boot.
 */
@Composable
fun PantallaCatalogo(
    usuario: UsuarioSesion,
    repositorioSesion: RepositorioSesion,
    onSalir: () -> Unit,
    onIrAFinanzas: (() -> Unit)? = null,
    repositorioCatalogo: RepositorioCatalogo = remember { RepositorioCatalogoFalso() },
    repositorioDivisas: RepositorioDivisas = remember { RepositorioDivisasFalso() },
    conectadoAlServidor: Boolean = false,
    claveSesion: String = usuario.uid,
) {
    val factoryCatalogo = remember(repositorioCatalogo, repositorioSesion) {
        CatalogoViewModel.factory(
            repositorioCatalogo = repositorioCatalogo,
            repositorioSesion = repositorioSesion,
        )
    }
    val viewModelCatalogo: CatalogoViewModel = viewModel(
        key = "catalogo-$claveSesion",
        factory = factoryCatalogo,
    )
    val estadoCatalogo by viewModelCatalogo.estado.collectAsStateWithLifecycle()

    val factoryDivisas = remember(repositorioDivisas) {
        DivisasViewModel.factory(repositorioDivisas = repositorioDivisas)
    }
    val viewModelDivisas: DivisasViewModel = viewModel(
        key = "divisas-$claveSesion",
        factory = factoryDivisas,
    )
    val estadoDivisas by viewModelDivisas.estado.collectAsStateWithLifecycle()

    ContenidoPantallaCatalogo(
        usuario = usuario,
        estado = estadoCatalogo,
        estadoDivisas = estadoDivisas,
        conectadoAlServidor = conectadoAlServidor,
        onSalir = onSalir,
        onCerrarSesion = viewModelCatalogo::cerrarSesion,
        onReintentar = viewModelCatalogo::reintentar,
        onRefrescar = viewModelCatalogo::refrescar,
        onReintentarDivisas = viewModelDivisas::reintentar,
        onRefrescarDivisas = viewModelDivisas::refrescar,
        onCambiarMontoDivisa = viewModelDivisas::cambiarMonto,
        onSeleccionarParDivisa = viewModelDivisas::seleccionarPar,
        onConvertirDivisa = viewModelDivisas::convertir,
        onIrAFinanzas = onIrAFinanzas,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContenidoPantallaCatalogo(
    usuario: UsuarioSesion,
    estado: EstadoUiCatalogo,
    onSalir: () -> Unit,
    onCerrarSesion: () -> Unit,
    onReintentar: () -> Unit,
    onIrAFinanzas: (() -> Unit)? = null,
    onRefrescar: () -> Unit = onReintentar,
    estadoDivisas: EstadoUiDivisas = EstadoUiDivisas(),
    conectadoAlServidor: Boolean = false,
    onReintentarDivisas: () -> Unit = {},
    onRefrescarDivisas: () -> Unit = onReintentarDivisas,
    onCambiarMontoDivisa: (String) -> Unit = {},
    onSeleccionarParDivisa: (ParDivisas) -> Unit = {},
    onConvertirDivisa: () -> Unit = {},
) {
    var mostrarConfirmarCierre by rememberSaveable { mutableStateOf(false) }
    var pestanaActiva by rememberSaveable { mutableIntStateOf(0) }
    var primerResume by remember { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (primerResume) {
            primerResume = false
        } else if (pestanaActiva == 0) {
            onRefrescar()
        } else {
            onRefrescarDivisas()
        }
    }

    if (mostrarConfirmarCierre) {
        DialogoCerrarSesion(
            enCurso = estado.cerrandoSesion,
            onConfirmar = {
                onCerrarSesion()
            },
            onCancelar = {
                mostrarConfirmarCierre = false
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Text(
                        text = stringResource(R.string.nube_catalogo_titulo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onSalir,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.nube_volver),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { mostrarConfirmarCierre = true },
                        enabled = !estado.cerrandoSesion,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text(
                            text = if (estado.cerrandoSesion) {
                                stringResource(R.string.catalogo_cerrando_sesion)
                            } else {
                                stringResource(R.string.nube_cerrar_sesion)
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { margenes ->
        val layoutDirection = LocalLayoutDirection.current
        val paddingInicio = margenes.calculateStartPadding(layoutDirection)
        val paddingFin = margenes.calculateEndPadding(layoutDirection)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = margenes.calculateTopPadding()),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pestanaActiva,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(start = paddingInicio, end = paddingFin),
            ) {
                Tab(
                    selected = pestanaActiva == 0,
                    onClick = {
                        val cambio = pestanaActiva != 0
                        pestanaActiva = 0
                        scrollBehavior.state.contentOffset = 0f
                        if (cambio) onRefrescar()
                    },
                    text = { Text(stringResource(R.string.catalogo_tab_productos)) },
                )
                Tab(
                    selected = pestanaActiva == 1,
                    onClick = {
                        val cambio = pestanaActiva != 1
                        pestanaActiva = 1
                        scrollBehavior.state.contentOffset = 0f
                        if (cambio) onRefrescarDivisas()
                    },
                    text = { Text(stringResource(R.string.catalogo_tab_divisas)) },
                )
            }

            val margenesContenido = PaddingValues(
                start = paddingInicio,
                top = 0.dp,
                end = paddingFin,
                bottom = margenes.calculateBottomPadding(),
            )

            if (pestanaActiva == 1) {
                SeccionDivisas(
                    estado = estadoDivisas,
                    conectadoAlServidor = conectadoAlServidor,
                    margenes = margenesContenido,
                    onReintentar = onReintentarDivisas,
                    onCerrarSesion = { mostrarConfirmarCierre = true },
                    onCambiarMonto = onCambiarMontoDivisa,
                    onSeleccionarPar = onSeleccionarParDivisa,
                    onConvertir = onConvertirDivisa,
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(margenes),
                )
            } else {
                when (val lista = estado.lista) {
                    EstadoListaCatalogo.Cargando -> EstadoCargandoCatalogo(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(margenesContenido),
                    )

                    EstadoListaCatalogo.Vacio -> EstadoMensajeCatalogo(
                        titulo = stringResource(R.string.catalogo_vacio_titulo),
                        texto = stringResource(R.string.catalogo_vacio_texto),
                        textoAccionPrimaria = stringResource(R.string.catalogo_reintentar),
                        onAccionPrimaria = onReintentar,
                        onIrAFinanzas = onIrAFinanzas,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(margenesContenido),
                    )

                    is EstadoListaCatalogo.Error -> {
                        val (tituloRes, textoRes) = when (lista.tipo) {
                            ErrorCatalogo.SIN_CONEXION ->
                                R.string.catalogo_error_sin_conexion_titulo to R.string.catalogo_error_sin_conexion_texto
                            ErrorCatalogo.SESION_EXPIRADA ->
                                R.string.catalogo_error_sesion_titulo to R.string.catalogo_error_sesion_texto
                            ErrorCatalogo.ERROR_SERVIDOR ->
                                R.string.catalogo_error_servidor_titulo to R.string.catalogo_error_servidor_texto
                        }
                        val esSesionExpirada = lista.tipo == ErrorCatalogo.SESION_EXPIRADA
                        EstadoMensajeCatalogo(
                            titulo = stringResource(tituloRes),
                            texto = stringResource(textoRes),
                            textoAccionPrimaria = stringResource(
                                if (esSesionExpirada) R.string.nube_cerrar_sesion else R.string.catalogo_reintentar,
                            ),
                            onAccionPrimaria = if (esSesionExpirada) {
                                { mostrarConfirmarCierre = true }
                            } else {
                                onReintentar
                            },
                            onIrAFinanzas = onIrAFinanzas,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(margenesContenido),
                        )
                    }

                    is EstadoListaCatalogo.Contenido -> ListaProductosCatalogo(
                        correoUsuario = usuario.correo,
                        productos = lista.productos,
                        estadoConexion = when {
                            !conectadoAlServidor -> EstadoConexionServidor.DEMO
                            estado.refrescando -> EstadoConexionServidor.CONECTANDO
                            else -> EstadoConexionServidor.CONECTADO
                        },
                        refrescando = estado.refrescando,
                        margenes = margenesContenido,
                        onActualizar = onReintentar,
                        onIrAFinanzas = onIrAFinanzas,
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(margenes),
                    )
                }
            }
        }
    }
}

@Composable
private fun EstadoCargandoCatalogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(24.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimacionLottie(
                resId = R.raw.anim_catalogo_carga,
                modifier = Modifier.size(88.dp),
                iteraciones = LottieConstants.IterateForever,
            )
            Text(
                text = stringResource(R.string.catalogo_cargando),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EstadoMensajeCatalogo(
    titulo: String,
    texto: String,
    textoAccionPrimaria: String,
    onAccionPrimaria: () -> Unit,
    modifier: Modifier = Modifier,
    onIrAFinanzas: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimacionLottie(
                    resId = R.raw.anim_catalogo_vacio,
                    modifier = Modifier
                        .size(104.dp)
                        .align(Alignment.CenterHorizontally),
                    iteraciones = LottieConstants.IterateForever,
                )
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val interaccionReintentar = remember { MutableInteractionSource() }
                Button(
                    onClick = onAccionPrimaria,
                    interactionSource = interaccionReintentar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toqueTactil(interaccionReintentar),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        text = textoAccionPrimaria,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (onIrAFinanzas != null) {
                    val interaccionFinanzas = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onIrAFinanzas,
                        interactionSource = interaccionFinanzas,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .toqueTactil(interaccionFinanzas),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.nube_ir_a_finanzas),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListaProductosCatalogo(
    correoUsuario: String,
    productos: List<ProductoFinanciero>,
    estadoConexion: EstadoConexionServidor,
    refrescando: Boolean,
    margenes: PaddingValues,
    onActualizar: () -> Unit,
    modifier: Modifier = Modifier,
    onIrAFinanzas: (() -> Unit)? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = margenes.calculateStartPadding(layoutDirection) + 20.dp,
            top = margenes.calculateTopPadding() + 12.dp,
            end = margenes.calculateEndPadding(layoutDirection) + 20.dp,
            bottom = margenes.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(
            key = "encabezado-catalogo",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "encabezado",
        ) {
            EncabezadoCatalogo(
                correoUsuario = correoUsuario,
                totalProductos = productos.size,
                estadoConexion = estadoConexion,
                refrescando = refrescando,
                onActualizar = onActualizar,
                onIrAFinanzas = onIrAFinanzas,
            )
        }
        items(
            items = productos,
            key = { it.id },
            contentType = { "producto" },
        ) { producto ->
            TarjetaProductoFinanciero(producto = producto)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncabezadoCatalogo(
    correoUsuario: String,
    totalProductos: Int,
    estadoConexion: EstadoConexionServidor,
    refrescando: Boolean,
    onActualizar: () -> Unit,
    onIrAFinanzas: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Solo los textos informativos se agrupan para TalkBack; los botones quedan fuera.
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.catalogo_sesion_activa, correoUsuario),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ChipEstadoServidor(estado = estadoConexion)
                }
                Text(
                    // Solo el modo demostración (sin servidor) usa cifras de ejemplo sin verificar.
                    text = stringResource(
                        if (estadoConexion == EstadoConexionServidor.DEMO) R.string.catalogo_aviso_ejemplo else R.string.catalogo_aviso_referencia,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.catalogo_total_productos, totalProductos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onActualizar,
                    enabled = !refrescando,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(R.string.catalogo_actualizar),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (onIrAFinanzas != null) {
                    val interaccionFinanzas = remember { MutableInteractionSource() }
                    Button(
                        onClick = onIrAFinanzas,
                        interactionSource = interaccionFinanzas,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .toqueTactil(interaccionFinanzas),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.nube_ir_a_finanzas),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TarjetaProductoFinanciero(producto: ProductoFinanciero) {
    val costoFormateado = remember(producto.costoReferenciaPesos, producto.periodicidad) {
        formatearCostoReferencia(producto.costoReferenciaPesos, producto.periodicidad)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = producto.categoria,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = producto.entidad,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = producto.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = costoFormateado,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            producto.tasaAnualReferencia?.let { tasa ->
                Text(
                    text = stringResource(R.string.catalogo_tasa_referencia, tasa),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = producto.detalleReferencia,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = producto.fuenteReferencia,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
