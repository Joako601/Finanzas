package io.github.joako601.finanzas.ui.metas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.metas.AnalisisTope
import io.github.joako601.finanzas.core.domain.metas.EstadoRitmoTope
import io.github.joako601.finanzas.core.domain.metas.ResumenTopesMes
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.SelectorSegmentadoSeda
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.TextoMontoAnimado
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.formatoMes
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Pestaña 3 ("Metas"): Topes mensuales tranquilos por categoría (Tanda 1) y Apartados de ahorro
 * (Tanda 2), alineados al sistema *Tinta & Papel · Aireada Apple · Seda iOS*.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMetas(
    viewModel: MetasViewModel = viewModel(factory = MetasViewModel.Fabrica),
    margenInferiorExtra: Dp = 0.dp,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val editorTope by viewModel.editorTope.collectAsStateWithLifecycle()
    val detalleTopeId by viewModel.detalleTopeId.collectAsStateWithLifecycle()
    val topePorEliminar by viewModel.topePorEliminar.collectAsStateWithLifecycle()
    val editorMeta by viewModel.editorMeta.collectAsStateWithLifecycle()
    val movimientoMeta by viewModel.movimientoMeta.collectAsStateWithLifecycle()
    val detalleMetaId by viewModel.detalleMetaId.collectAsStateWithLifecycle()
    val accionDetalleMeta by viewModel.accionDetalleMeta.collectAsStateWithLifecycle()
    val metaPorEliminar by viewModel.metaPorEliminar.collectAsStateWithLifecycle()
    var subPestana by rememberSaveable { mutableStateOf(SubPestanaMetas.TOPES) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_presupuesto),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { margenes ->
        if (estado.cargando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(margenes),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = margenes.calculateTopPadding())
                .consumeWindowInsets(margenes),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = margenes.calculateBottomPadding() + margenInferiorExtra + 28.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SelectorSegmentadoSeda(
                    opciones = SubPestanaMetas.entries,
                    seleccionada = subPestana,
                    etiqueta = { opcion ->
                        stringResource(
                            when (opcion) {
                                SubPestanaMetas.TOPES -> R.string.metas_tab_topes
                                SubPestanaMetas.AHORRO -> R.string.metas_tab_ahorro
                            },
                        )
                    },
                    onSeleccionar = { subPestana = it },
                )

                when (subPestana) {
                    SubPestanaMetas.TOPES -> {
                        val resumen = estado.resumenTopes
                        if (resumen == null || resumen.topes.isEmpty()) {
                            EstadoVacioTopes(
                                categoriasSugeridas = estado.categoriasSinTope.take(3),
                                puedeAgregar = estado.categoriasSinTope.isNotEmpty(),
                                onElegirCategoria = { viewModel.abrirNuevoTope(it) },
                                onAgregarTope = { viewModel.abrirNuevoTope() },
                            )
                        } else {
                            TarjetaResumenTopes(
                                resumen = resumen,
                                puedeAgregarMas = estado.categoriasSinTope.isNotEmpty(),
                                onAgregarTope = { viewModel.abrirNuevoTope() },
                            )

                            resumen.topes.forEach { analisis ->
                                TarjetaTopeCategoria(
                                    analisis = analisis,
                                    resumen = resumen,
                                    onClick = { viewModel.abrirDetalleTope(analisis.categoria.id) },
                                )
                            }
                        }
                    }

                    SubPestanaMetas.AHORRO -> {
                        ContenidoAhorro(
                            resumen = estado.resumenAhorro,
                            onNuevaMeta = viewModel::abrirNuevaMeta,
                            onAportar = { viewModel.abrirMovimientoMeta(it, TipoMovimientoMeta.APORTAR) },
                            onRetirar = { viewModel.abrirMovimientoMeta(it, TipoMovimientoMeta.RETIRAR) },
                            onVerDetalle = viewModel::abrirDetalleMeta,
                        )
                    }
                }
            }
        }
    }

    editorTope?.let { actual ->
        val sugerencia = actual.categoriaId?.let { estado.sugerenciasPorCategoria[it] }
        DialogoTope(
            editor = actual,
            categoriasDisponibles = estado.categoriasSinTope,
            sugerencia = sugerencia,
            onElegirCategoria = viewModel::elegirCategoriaTope,
            onCambiarMonto = viewModel::cambiarMontoTope,
            onUsarSugerencia = viewModel::aplicarSugerenciaTope,
            onGuardar = viewModel::guardarTope,
            onCerrar = viewModel::cerrarEditorTope,
        )
    }

    detalleTopeId?.let { id ->
        val resumen = estado.resumenTopes
        val analisis = resumen?.topes?.firstOrNull { it.categoria.id == id }
        if (resumen != null && analisis != null) {
            HojaDetalleTope(
                analisis = analisis,
                resumen = resumen,
                onEditar = { viewModel.abrirEditarTope(analisis) },
                onEliminar = { viewModel.pedirEliminarTope(analisis) },
                onCerrar = viewModel::cerrarDetalleTope,
            )
        }
    }

    topePorEliminar?.let { actual ->
        DialogoConfirmarEliminarTope(
            eliminacion = actual,
            onConfirmar = viewModel::confirmarEliminarTope,
            onCancelar = viewModel::cancelarEliminarTope,
        )
    }

    editorMeta?.let { actual ->
        DialogoMeta(
            editor = actual,
            onEditar = viewModel::editarMeta,
            onGuardar = viewModel::guardarMeta,
            onCerrar = viewModel::cerrarEditorMeta,
        )
    }

    movimientoMeta?.let { actual ->
        val resumen = estado.resumenAhorro
        val analisisVivo = (resumen?.metasActivas.orEmpty() + resumen?.metasArchivadas.orEmpty())
            .firstOrNull { it.meta.id == actual.analisis.meta.id }
            ?: actual.analisis
        DialogoMovimientoMeta(
            dialogo = actual,
            analisisVivo = analisisVivo,
            onCambiarMonto = viewModel::cambiarMontoMovimientoMeta,
            onSumarMonto = viewModel::sumarMontoMovimientoMeta,
            onCambiarArchivarAlVaciar = viewModel::cambiarArchivarAlVaciar,
            onConfirmar = viewModel::confirmarMovimientoMeta,
            onCerrar = viewModel::cerrarMovimientoMeta,
        )
    }

    detalleMetaId?.let { id ->
        val resumen = estado.resumenAhorro
        val analisis = (resumen?.metasActivas.orEmpty() + resumen?.metasArchivadas.orEmpty())
            .firstOrNull { it.meta.id == id }
        if (analisis != null) {
            key(id) {
                HojaDetalleMeta(
                    analisis = analisis,
                    accionDetalle = accionDetalleMeta,
                    onAportar = { viewModel.abrirMovimientoMeta(analisis, TipoMovimientoMeta.APORTAR) },
                    onRetirar = { viewModel.abrirMovimientoMeta(analisis, TipoMovimientoMeta.RETIRAR) },
                    onEditar = { viewModel.abrirEditarMeta(analisis.meta) },
                    onAlternarArchivar = { viewModel.alternarArchivarMeta(analisis.meta) },
                    onEliminar = { viewModel.pedirEliminarMeta(analisis.meta) },
                    onCerrar = viewModel::cerrarDetalleMeta,
                )
            }
        }
    }

    metaPorEliminar?.let { actual ->
        DialogoConfirmarEliminarMeta(
            eliminacion = actual,
            onConfirmar = viewModel::confirmarEliminarMeta,
            onCancelar = viewModel::cancelarEliminarMeta,
        )
    }
}

@Composable
private fun EstadoVacioTopes(
    categoriasSugeridas: List<Cuenta>,
    puedeAgregar: Boolean,
    onElegirCategoria: (CuentaId) -> Unit,
    onAgregarTope: () -> Unit,
) {
    TarjetaFinanzas(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.metas_topes_vacio_titulo),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.metas_topes_vacio_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (categoriasSugeridas.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.metas_topes_sugeridas_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        categoriasSugeridas.forEach { categoria ->
                            AssistChip(
                                onClick = { onElegirCategoria(categoria.id) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(iconoDeCategoria(categoria.nombre)),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = { Text(categoria.nombre) },
                            )
                        }
                    }
                }
            }

            if (puedeAgregar) {
                Spacer(Modifier.height(2.dp))

                BotonPrincipal(
                    texto = stringResource(R.string.metas_agregar_tope),
                    onClick = onAgregarTope,
                )
            }
        }
    }
}

@Composable
private fun TarjetaResumenTopes(
    resumen: ResumenTopesMes,
    puedeAgregarMas: Boolean,
    onAgregarTope: () -> Unit,
) {
    TarjetaFinanzas(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.metas_topes_mes_dia,
                        resumen.hoy.formatoMes(),
                        resumen.diaDelMes,
                        resumen.diasDelMes,
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InsigniaRitmoTope(resumen.ritmoGlobal)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.metas_topes_gastado_titulo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.Bottom,
                ) {
                    TextoMontoAnimado(
                        monto = resumen.totalGastado,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (resumen.ritmoGlobal == EstadoRitmoTope.EXCEDIDO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = stringResource(R.string.metas_topes_de_limite, resumen.totalLimite.formatear()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }

            val porcentajeGlobal = (resumen.fraccionGlobal * 100f + 0.5f).toInt().coerceAtLeast(0)
            BarraTopeConRitmo(
                fraccionConsumida = resumen.fraccionGlobal,
                fraccionMes = resumen.fraccionMesTranscurrido,
                ritmo = resumen.ritmoGlobal,
                porcentajeEntero = porcentajeGlobal,
                diaDelMes = resumen.diaDelMes,
                diasDelMes = resumen.diasDelMes,
            )

            val textoResumen = when {
                resumen.totalExcedente.esPositivo -> stringResource(
                    R.string.metas_topes_excedido_por,
                    resumen.totalExcedente.formatear(),
                )
                !resumen.totalRestante.esPositivo -> stringResource(R.string.metas_topes_sin_margen)
                resumen.diasRestantes == 0 -> stringResource(
                    R.string.metas_topes_restante_ultimo_dia,
                    resumen.totalRestante.formatear(),
                )
                else -> pluralStringResource(
                    R.plurals.metas_topes_restante_dias,
                    resumen.diasRestantes,
                    resumen.totalRestante.formatear(),
                    resumen.diasRestantes,
                )
            }
            Text(
                text = textoResumen,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = if (resumen.totalExcedente.esPositivo) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            val conteos = listOfNotNull(
                if (resumen.cantidadExcedidos > 0) {
                    pluralStringResource(
                        R.plurals.metas_topes_resumen_conteos_excedidos,
                        resumen.cantidadExcedidos,
                        resumen.cantidadExcedidos,
                    )
                } else {
                    null
                },
                if (resumen.cantidadRitmoAlto > 0) stringResource(R.string.metas_topes_resumen_conteos_alto, resumen.cantidadRitmoAlto) else null,
                if (resumen.cantidadEnRitmo > 0) stringResource(R.string.metas_topes_resumen_conteos_bien, resumen.cantidadEnRitmo) else null,
            ).joinToString(" · ")
            if (conteos.isNotEmpty()) {
                Text(
                    text = conteos,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (puedeAgregarMas) {
                BotonSecundario(
                    texto = stringResource(R.string.metas_agregar_tope),
                    onClick = onAgregarTope,
                )
            }
        }
    }
}

@Composable
private fun TarjetaTopeCategoria(
    analisis: AnalisisTope,
    resumen: ResumenTopesMes,
    onClick: () -> Unit,
) {
    val etiquetaAccion = stringResource(R.string.metas_topes_ver_detalle)
    TarjetaFinanzas(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(
                role = Role.Button,
                onClickLabel = etiquetaAccion,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    IconoEnCirculo(iconoDeCategoria(analisis.categoria.nombre), tamano = 36.dp)
                    Text(
                        text = analisis.categoria.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                InsigniaRitmoTope(analisis.ritmo)
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = analisis.gastadoMesActual.formatear(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (analisis.ritmo == EstadoRitmoTope.EXCEDIDO) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = stringResource(R.string.metas_topes_de_limite, analisis.tope.limiteMensual.formatear()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BarraTopeConRitmo(
                fraccionConsumida = analisis.fraccionConsumida,
                fraccionMes = resumen.fraccionMesTranscurrido,
                ritmo = analisis.ritmo,
                porcentajeEntero = analisis.porcentajeEntero,
                diaDelMes = resumen.diaDelMes,
                diasDelMes = resumen.diasDelMes,
            )

            Text(
                text = textoEstadoTope(analisis),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = if (analisis.ritmo == EstadoRitmoTope.EXCEDIDO) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Barra horizontal 1D monocromática con marca vertical del día del mes ("pacing marker").
 * Muestra en menos de 1 segundo si el gasto real va detrás o delante del ritmo esperado.
 */
@Composable
internal fun BarraTopeConRitmo(
    fraccionConsumida: Float,
    fraccionMes: Float,
    ritmo: EstadoRitmoTope,
    porcentajeEntero: Int,
    diaDelMes: Int,
    diasDelMes: Int,
    modifier: Modifier = Modifier,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val objetivo = fraccionConsumida.coerceIn(0f, 1f)
    val avance by animateFloatAsState(
        targetValue = objetivo,
        animationSpec = if (sinAnimaciones) snap() else spring(dampingRatio = 0.90f, stiffness = 420f),
        label = "barraTopeRitmo",
    )

    val colorPista = MaterialTheme.colorScheme.secondaryContainer
    val colorRelleno = when (ritmo) {
        EstadoRitmoTope.EN_RITMO -> MaterialTheme.colorScheme.onSurface
        EstadoRitmoTope.RITMO_ALTO -> TemaFinanzas.colores.aviso
        EstadoRitmoTope.EXCEDIDO -> MaterialTheme.colorScheme.error
    }
    val colorMarca = MaterialTheme.colorScheme.onSurface
    val colorHalo = MaterialTheme.colorScheme.surfaceContainerLowest
    val descripcion = stringResource(R.string.metas_barra_cd, porcentajeEntero, diaDelMes, diasDelMes)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics { contentDescription = descripcion },
    ) {
        if (size.width <= 0f) return@Canvas
        val altoBarra = 8.dp.toPx()
        val yBarra = (size.height - altoBarra) / 2f
        val radio = altoBarra / 2f
        val esquina = CornerRadius(radio, radio)

        // Pista
        drawRoundRect(
            color = colorPista,
            topLeft = Offset(0f, yBarra),
            size = Size(size.width, altoBarra),
            cornerRadius = esquina,
        )

        // Relleno de progreso
        if (avance > 0f) {
            val anchoRelleno = (size.width * avance).coerceAtLeast(altoBarra)
            drawRoundRect(
                color = colorRelleno,
                topLeft = Offset(0f, yBarra),
                size = Size(anchoRelleno.coerceAtMost(size.width), altoBarra),
                cornerRadius = esquina,
            )
        }

        // Marca vertical del ritmo del día actual del mes (con halo de superficie para legibilidad total)
        val margen = 3.dp.toPx()
        val maxX = (size.width - margen).coerceAtLeast(margen)
        val xMarca = (size.width * fraccionMes.coerceIn(0.02f, 0.98f))
            .coerceIn(margen.coerceAtMost(maxX), maxX)
        drawLine(
            color = colorHalo,
            start = Offset(xMarca, 1.dp.toPx()),
            end = Offset(xMarca, size.height - 1.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colorMarca,
            start = Offset(xMarca, 1.dp.toPx()),
            end = Offset(xMarca, size.height - 1.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Insignia de estado sobre `surfaceContainerLowest` con borde de 1 dp (contraste WCAG AA >= 4.5:1). */
@Composable
internal fun InsigniaRitmoTope(ritmo: EstadoRitmoTope) {
    val (textoRes, colorTexto) = when (ritmo) {
        EstadoRitmoTope.EN_RITMO -> R.string.metas_ritmo_en_ritmo to TemaFinanzas.colores.ingreso
        EstadoRitmoTope.RITMO_ALTO -> R.string.metas_ritmo_alto to TemaFinanzas.colores.aviso
        EstadoRitmoTope.EXCEDIDO -> R.string.metas_ritmo_excedido to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = stringResource(textoRes),
            style = MaterialTheme.typography.labelSmall,
            color = colorTexto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun textoEstadoTope(analisis: AnalisisTope): String = when {
    analisis.excedente.esPositivo -> stringResource(
        R.string.metas_topes_excedido_por,
        analisis.excedente.formatear(),
    )
    !analisis.restante.esPositivo -> stringResource(R.string.metas_topes_sin_margen)
    else -> stringResource(
        R.string.metas_topes_restante_por_dia,
        analisis.restante.formatear(),
        analisis.disponiblePorDiaRestante.formatear(),
    )
}
