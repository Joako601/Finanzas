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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieConstants
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.AnimacionLottie
import io.github.joako601.finanzas.nube.componentes.toqueTactil
import io.github.joako601.finanzas.nube.recuperar.DialogoCerrarSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion

/**
 * Pantalla del catálogo financiero público (visible cuando hay sesión iniciada).
 *
 * Muestra productos financieros de referencia en México con categoría, entidad, costo
 * formateado y detalle, además del botón para cerrar sesión en la barra superior.
 */
@Composable
fun PantallaCatalogo(
    usuario: UsuarioSesion,
    repositorioSesion: RepositorioSesion,
    onSalir: () -> Unit,
) {
    val factory = remember(repositorioSesion) {
        CatalogoViewModel.factory(repositorioSesion = repositorioSesion)
    }
    val viewModel: CatalogoViewModel = viewModel(
        key = "catalogo-${usuario.uid}",
        factory = factory,
    )
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    ContenidoPantallaCatalogo(
        usuario = usuario,
        estado = estado,
        onSalir = onSalir,
        onCerrarSesion = viewModel::cerrarSesion,
        onReintentar = viewModel::reintentar,
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
) {
    var mostrarConfirmarCierre by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
        when (val lista = estado.lista) {
            EstadoListaCatalogo.Cargando -> EstadoCargandoCatalogo(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(margenes),
            )

            EstadoListaCatalogo.Vacio -> EstadoMensajeCatalogo(
                titulo = stringResource(R.string.catalogo_vacio_titulo),
                texto = stringResource(R.string.catalogo_vacio_texto),
                onReintentar = onReintentar,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(margenes),
            )

            is EstadoListaCatalogo.Error -> {
                val (tituloRes, textoRes) = when (lista.tipo) {
                    ErrorCatalogo.SIN_CONEXION ->
                        R.string.catalogo_error_sin_conexion_titulo to R.string.catalogo_error_sin_conexion_texto
                    ErrorCatalogo.ERROR_SERVIDOR ->
                        R.string.catalogo_error_servidor_titulo to R.string.catalogo_error_servidor_texto
                }
                EstadoMensajeCatalogo(
                    titulo = stringResource(tituloRes),
                    texto = stringResource(textoRes),
                    onReintentar = onReintentar,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(margenes),
                )
            }

            is EstadoListaCatalogo.Contenido -> ListaProductosCatalogo(
                correoUsuario = usuario.correo,
                productos = lista.productos,
                margenes = margenes,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(margenes),
            )
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
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
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
                    onClick = onReintentar,
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
                        text = stringResource(R.string.catalogo_reintentar),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListaProductosCatalogo(
    correoUsuario: String,
    productos: List<ProductoFinanciero>,
    margenes: PaddingValues,
    modifier: Modifier = Modifier,
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

@Composable
private fun EncabezadoCatalogo(
    correoUsuario: String,
    totalProductos: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.catalogo_sesion_activa, correoUsuario),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.catalogo_aviso_referencia),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.catalogo_total_productos, totalProductos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaProductoFinanciero(producto: ProductoFinanciero) {
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
