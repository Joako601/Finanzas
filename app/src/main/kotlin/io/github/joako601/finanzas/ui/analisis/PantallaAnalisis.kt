package io.github.joako601.finanzas.ui.analisis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.disponible.EventoFuturo
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.disponible.TipoEvento
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.SelectorSegmentadoSeda
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.TextoMontoAnimado
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.disponible.AccionesLineaDeTiempo
import io.github.joako601.finanzas.ui.disponible.HojaLineaDeTiempo
import io.github.joako601.finanzas.ui.formatoDiaCorto
import io.github.joako601.finanzas.ui.recurrentes.AccionesRegistroRecurrente
import io.github.joako601.finanzas.ui.recurrentes.DialogoNuevoRecurrente
import io.github.joako601.finanzas.ui.recurrentes.DialogoRegistroRecurrente
import io.github.joako601.finanzas.ui.recurrentes.RecurrentesViewModel
import io.github.joako601.finanzas.ui.tarjetas.DialogoPagoTarjeta
import io.github.joako601.finanzas.ui.tarjetas.TarjetasViewModel
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Apartado `Análisis` ([io.github.joako601.finanzas.ui.navegacion.PestanaPrincipal.ANALISIS]):
 * presenta la Curva de Liquidez Unificada (Histórico + Proyección a 30 Días), el Flujo del
 * Periodo en barra 1D monocromática y el Ranking de Gastos por Categoría.
 */
@Composable
fun PantallaAnalisis(
    viewModel: AnalisisViewModel = viewModel(factory = AnalisisViewModel.Fabrica),
    tarjetasViewModel: TarjetasViewModel = viewModel(factory = TarjetasViewModel.Fabrica),
    recurrentesViewModel: RecurrentesViewModel = viewModel(factory = RecurrentesViewModel.Fabrica),
    margenInferiorExtra: Dp = 0.dp,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val estadoTarjetas by tarjetasViewModel.estado.collectAsStateWithLifecycle()
    val pagoTarjeta by tarjetasViewModel.pago.collectAsStateWithLifecycle()
    val estadoRecurrentes by recurrentesViewModel.estado.collectAsStateWithLifecycle()
    val nuevoRecurrente by recurrentesViewModel.nuevo.collectAsStateWithLifecycle()
    val registroRecurrente by recurrentesViewModel.registro.collectAsStateWithLifecycle()

    var verLineaDeTiempo by rememberSaveable { mutableStateOf(false) }

    ContenidoAnalisis(
        estado = estado,
        onVerLineaDeTiempo = { verLineaDeTiempo = true },
        margenInferiorExtra = margenInferiorExtra,
    )

    if (verLineaDeTiempo) {
        HojaLineaDeTiempo(
            estado = estado.estadoLinea,
            acciones = AccionesLineaDeTiempo(
                tocarFijo = { id ->
                    estadoRecurrentes.recurrentes.firstOrNull { it.recurrente.id == id }?.let {
                        verLineaDeTiempo = false
                        recurrentesViewModel.abrirRegistro(it)
                    }
                },
                tocarTarjeta = { id, monto ->
                    estadoTarjetas.tarjetas.firstOrNull { it.tarjeta.id == id }?.let {
                        verLineaDeTiempo = false
                        tarjetasViewModel.abrirPago(it, monto)
                    }
                },
                agregarFijo = {
                    verLineaDeTiempo = false
                    recurrentesViewModel.abrirNuevo()
                },
                cerrar = { verLineaDeTiempo = false },
            ),
        )
    }

    nuevoRecurrente?.let { actual ->
        DialogoNuevoRecurrente(
            nuevo = actual,
            categorias = estadoRecurrentes.categoriasDe(actual.clase),
            onEditar = recurrentesViewModel::editarNuevo,
            onGuardar = recurrentesViewModel::guardarNuevo,
            onCerrar = recurrentesViewModel::cerrarNuevo,
        )
    }

    registroRecurrente?.let { actual ->
        DialogoRegistroRecurrente(
            registro = actual,
            cuentas = estadoRecurrentes.cuentasPara(actual.resumen.recurrente.clase),
            acciones = AccionesRegistroRecurrente(
                editar = recurrentesViewModel::editarRegistro,
                registrar = recurrentesViewModel::guardarRegistro,
                omitir = recurrentesViewModel::omitir,
                eliminar = recurrentesViewModel::eliminar,
                cerrar = recurrentesViewModel::cerrarRegistro,
                editarSerie = recurrentesViewModel::abrirEdicionDesdeRegistro,
                pedirEliminar = recurrentesViewModel::pedirConfirmarEliminar,
                cancelarEliminar = recurrentesViewModel::cancelarConfirmarEliminar,
            ),
        )
    }

    pagoTarjeta?.let { actual ->
        DialogoPagoTarjeta(
            pago = actual,
            cuentas = estadoTarjetas.cuentasParaPagar,
            saldosCuentas = estadoTarjetas.saldosCuentasParaPagar,
            onEditar = tarjetasViewModel::editarPago,
            onGuardar = tarjetasViewModel::guardarPago,
            onCerrar = tarjetasViewModel::cerrarPago,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoAnalisis(
    estado: EstadoAnalisis,
    onVerLineaDeTiempo: () -> Unit,
    margenInferiorExtra: Dp = 0.dp,
) {
    var periodoSeleccionado by rememberSaveable { mutableStateOf(PeriodoAnalisis.QUINCE_DIAS) }
    var indiceInspeccionado by remember(periodoSeleccionado) { mutableStateOf<Int?>(null) }

    val linea = estado.estadoLinea.linea
    val corte = estado.cortes[periodoSeleccionado]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_analisis),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { margenes ->
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
                        bottom = margenes.calculateBottomPadding() + margenInferiorExtra + 32.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!estado.cargando && (!estado.tieneCuentasDeDinero || linea == null || corte == null)) {
                    TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.sin_cuentas_para_disponible),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else if (linea != null && corte != null) {
                    SelectorPeriodoAnalisis(
                        seleccionado = periodoSeleccionado,
                        onSeleccionar = { nuevo ->
                            if (nuevo != periodoSeleccionado) {
                                indiceInspeccionado = null
                                periodoSeleccionado = nuevo
                            }
                        },
                    )

                    TarjetaPulsoLiquidez(
                        corte = corte,
                        linea = linea,
                        indiceInspeccionado = indiceInspeccionado,
                        onInspeccionarIndice = { indiceInspeccionado = it },
                        onVerLineaDeTiempo = onVerLineaDeTiempo,
                    )

                    TarjetaFlujoPeriodo(
                        periodo = periodoSeleccionado,
                        flujo = corte.flujo,
                    )

                    TarjetaGastosPorCategoria(
                        periodo = periodoSeleccionado,
                        categorias = corte.categorias,
                    )
                }
            }
        }
    }
}

/**
 * Control segmentado global de 2 opciones ("15 días" | "30 días") con área táctil >= 48 dp.
 */
@Composable
private fun SelectorPeriodoAnalisis(
    seleccionado: PeriodoAnalisis,
    onSeleccionar: (PeriodoAnalisis) -> Unit,
) {
    SelectorSegmentadoSeda(
        opciones = PeriodoAnalisis.entries,
        seleccionada = seleccionado,
        etiqueta = { opcion ->
            stringResource(
                when (opcion) {
                    PeriodoAnalisis.QUINCE_DIAS -> R.string.analisis_periodo_15_dias
                    PeriodoAnalisis.TREINTA_DIAS -> R.string.analisis_periodo_30_dias
                },
            )
        },
        onSeleccionar = onSeleccionar,
    )
}

/**
 * Tarjeta 1: Encabezado Vivo (0 layout shift, 0 ms en scrubbing) + Lienzo de Curva Unificada
 * + Eje X nativo + Acción para abrir [HojaLineaDeTiempo].
 */
@Composable
private fun TarjetaPulsoLiquidez(
    corte: CortePeriodoAnalisis,
    linea: LineaDeTiempo,
    indiceInspeccionado: Int?,
    onInspeccionarIndice: (Int?) -> Unit,
    onVerLineaDeTiempo: () -> Unit,
) {
    val puntoActivo = indiceInspeccionado?.let { corte.puntosCurva.getOrNull(it) }

    TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EncabezadoVivoLiquidez(
                linea = linea,
                puntoInspeccionado = puntoActivo,
            )

            GraficoLiquidez(
                corte = corte,
                linea = linea,
                indiceInspeccionado = indiceInspeccionado,
                onInspeccionarIndice = onInspeccionarIndice,
            )

            TextButton(
                onClick = onVerLineaDeTiempo,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = stringResource(R.string.ver_proximos_30_dias),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EncabezadoVivoLiquidez(
    linea: LineaDeTiempo,
    puntoInspeccionado: PuntoLiquidez?,
) {
    val estiloHeadline = MaterialTheme.typography.headlineLarge
    val estiloBody = MaterialTheme.typography.bodyMedium
    val estiloMontoTabular = remember(estiloHeadline) {
        estiloHeadline.copy(fontFeatureSettings = "tnum")
    }
    val estiloDetalleTabular = remember(estiloBody) {
        estiloBody.copy(fontFeatureSettings = "tnum")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (puntoInspeccionado == null) {
            // Estado en reposo: Disponible real + síntesis de liquidez a 30 días
            Text(
                text = stringResource(R.string.analisis_disponible_subtitulo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextoMontoAnimado(
                monto = linea.disponible,
                style = MaterialTheme.typography.headlineLarge,
                color = if (linea.disponible.esNegativo) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            SubtituloReposoDisponible(linea = linea, estilo = estiloDetalleTabular)
        } else {
            // Estado en scrubbing activo: actualización 1:1 en 0 ms sin interpolación de 360 ms
            val etiquetaFase = when (puntoInspeccionado.fase) {
                FasePuntoLiquidez.HISTORICO -> stringResource(
                    R.string.analisis_fase_historico,
                    puntoInspeccionado.fecha.formatoDiaCorto(linea.hoy),
                )
                FasePuntoLiquidez.HOY -> stringResource(R.string.analisis_fase_hoy)
                FasePuntoLiquidez.PROYECCION -> stringResource(
                    R.string.analisis_fase_proyeccion,
                    puntoInspeccionado.fecha.formatoDiaCorto(linea.hoy),
                )
            }

            Text(
                text = etiquetaFase,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = puntoInspeccionado.saldo.formatear(),
                style = estiloMontoTabular,
                color = if (puntoInspeccionado.saldo.esNegativo) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            SubtituloPuntoInspeccionado(
                punto = puntoInspeccionado,
                linea = linea,
                estilo = estiloDetalleTabular,
            )
        }
    }
}

@Composable
private fun SubtituloReposoDisponible(
    linea: LineaDeTiempo,
    estilo: androidx.compose.ui.text.TextStyle,
) {
    val secundario = MaterialTheme.colorScheme.onSurfaceVariant
    val bajo = linea.puntoMasBajo
    val faltante = linea.primerFaltante

    when {
        faltante != null -> {
            val textoFaltante = when {
                faltante.fecha != linea.hoy ->
                    stringResource(R.string.faltante, faltante.fecha.formatoDiaCorto(linea.hoy), faltante.falta.formatear())
                linea.enCuentas.esNegativo ->
                    stringResource(R.string.faltante_hoy, faltante.falta.formatear())
                else ->
                    stringResource(R.string.faltante_hoy_vencidos, faltante.falta.formatear())
            }
            Text(
                text = textoFaltante,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.error,
            )
        }
        bajo != null -> {
            Text(
                text = stringResource(
                    R.string.en_cuentas_para_pagos,
                    linea.enCuentas.formatear(),
                    linea.comprometido.formatear(),
                ) + " " + stringResource(
                    R.string.saldo_mas_bajo,
                    (linea.diaMasBajo ?: bajo.fecha).formatoDiaCorto(linea.hoy),
                ),
                style = estilo,
                color = secundario,
            )
        }
        else -> {
            Text(
                text = stringResource(
                    if (linea.eventos.any { it.esSalida }) {
                        R.string.ingresos_cubren_pagos
                    } else {
                        R.string.sin_pagos_30_dias
                    },
                ),
                style = estilo,
                color = secundario,
            )
        }
    }
}

@Composable
private fun SubtituloPuntoInspeccionado(
    punto: PuntoLiquidez,
    linea: LineaDeTiempo,
    estilo: androidx.compose.ui.text.TextStyle,
) {
    val secundario = MaterialTheme.colorScheme.onSurfaceVariant
    when (punto.fase) {
        FasePuntoLiquidez.HISTORICO -> {
            val conteo = punto.cantidadMovimientosActivos
            if (conteo == 0) {
                Text(
                    text = stringResource(R.string.analisis_sin_actividad_dia),
                    style = estilo,
                    color = secundario,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val deltaFormateado = formatearConSigno(punto.deltaDia)
                val texto = if (conteo == 1) {
                    stringResource(R.string.analisis_un_movimiento_dia, deltaFormateado)
                } else {
                    stringResource(R.string.analisis_varios_movimientos_dia, conteo, deltaFormateado)
                }
                Text(
                    text = texto,
                    style = estilo,
                    color = secundario,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        FasePuntoLiquidez.HOY,
        FasePuntoLiquidez.PROYECCION,
        -> {
            val eventosConEfecto = punto.eventosFuturos.filter { it.tipo != TipoEvento.CORTE }
            val listaMostrar = if (eventosConEfecto.isNotEmpty()) eventosConEfecto else punto.eventosFuturos
            if (listaMostrar.isNotEmpty()) {
                val principal = describirEventoCorto(listaMostrar.first())
                val texto = if (listaMostrar.size > 1) {
                    stringResource(R.string.analisis_mas_eventos, principal, listaMostrar.size - 1)
                } else {
                    principal
                }
                Text(
                    text = texto,
                    style = estilo,
                    color = if (punto.saldo.esNegativo) MaterialTheme.colorScheme.error else secundario,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (punto.fase == FasePuntoLiquidez.HOY) {
                SubtituloReposoDisponible(linea = linea, estilo = estilo)
            } else {
                Text(
                    text = stringResource(R.string.analisis_sin_eventos_proyeccion),
                    style = estilo,
                    color = secundario,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun describirEventoCorto(evento: EventoFuturo): String = when (evento.tipo) {
    TipoEvento.PAGO_TARJETA ->
        stringResource(R.string.evento_pago_tarjeta, evento.nombre) + " · −" + evento.monto.formatear()
    TipoEvento.PAGO_FIJO ->
        evento.nombre + " · −" + evento.monto.formatear()
    TipoEvento.INGRESO_ESPERADO ->
        evento.nombre + " · +" + evento.monto.formatear()
    TipoEvento.CORTE ->
        stringResource(R.string.evento_corte, evento.nombre)
}

private fun formatearConSigno(dinero: Dinero): String = when {
    dinero.esPositivo -> "+" + dinero.formatear()
    dinero.esNegativo -> "−" + dinero.absoluto().formatear()
    else -> dinero.formatear()
}

/**
 * Tarjeta 2: Flujo del Periodo (Entradas vs. Salidas) con barra 1D 100% monocromática
 * *Tinta & Papel* (contraste > 8:1 apto para deuteranopía y protanopía).
 */
@Composable
private fun TarjetaFlujoPeriodo(
    periodo: PeriodoAnalisis,
    flujo: FlujoPeriodo,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val titulo = stringResource(
        when (periodo) {
            PeriodoAnalisis.QUINCE_DIAS -> R.string.analisis_flujo_titulo_15
            PeriodoAnalisis.TREINTA_DIAS -> R.string.analisis_flujo_titulo_30
        },
    )
    val subtitulo = if (flujo.salidas.esPositivo) {
        stringResource(R.string.analisis_flujo_subtitulo, flujo.ritmoDiarioSalidas.formatear())
    } else {
        stringResource(R.string.analisis_flujo_sin_salidas)
    }

    val proporcionSalidasAnimada by animateFloatAsState(
        targetValue = flujo.proporcionSalidas,
        animationSpec = if (sinAnimaciones) {
            tween(0)
        } else {
            spring(dampingRatio = 0.90f, stiffness = 420f)
        },
        label = "proporcionSalidas",
    )

    TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BarraFlujoMonocromatica(
                tieneSalidas = flujo.salidas.esPositivo,
                tieneEntradas = flujo.entradas.esPositivo,
                proporcionSalidas = proporcionSalidasAnimada,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Salidas (Indicador sólido onSurface + monto semántico en gasto)
                BloqueMontoFlujo(
                    etiqueta = stringResource(R.string.analisis_salidas),
                    montoTexto = if (flujo.salidas.esPositivo) "−" + flujo.salidas.formatear() else flujo.salidas.formatear(),
                    colorMonto = if (flujo.salidas.esPositivo) {
                        TemaFinanzas.colores.gasto
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    esIndicadorSolido = true,
                )

                // Entradas (Indicador tonal con borde + monto semántico en ingreso)
                BloqueMontoFlujo(
                    etiqueta = stringResource(R.string.analisis_entradas),
                    montoTexto = if (flujo.entradas.esPositivo) "+" + flujo.entradas.formatear() else flujo.entradas.formatear(),
                    colorMonto = if (flujo.entradas.esPositivo) {
                        TemaFinanzas.colores.ingreso
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    esIndicadorSolido = false,
                )
            }
        }
    }
}

@Composable
private fun BarraFlujoMonocromatica(
    tieneSalidas: Boolean,
    tieneEntradas: Boolean,
    proporcionSalidas: Float,
) {
    val colorSalidas = MaterialTheme.colorScheme.onSurface
    val colorEntradas = MaterialTheme.colorScheme.secondaryContainer
    // Borde con contraste gráfico WCAG 1.4.11 >= 3.0:1 sobre tarjeta clara y oscura.
    val colorBordeEntradas = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !tieneSalidas && !tieneEntradas -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorEntradas, CircleShape),
                )
            }
            tieneSalidas && !tieneEntradas -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorSalidas, CircleShape),
                )
            }
            !tieneSalidas && tieneEntradas -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorEntradas, CircleShape)
                        .border(BorderStroke(1.dp, colorBordeEntradas), CircleShape),
                )
            }
            else -> {
                val pesoSalidas = proporcionSalidas.coerceIn(0.08f, 0.92f)
                val pesoEntradas = (1f - pesoSalidas).coerceAtLeast(0.08f)

                Box(
                    modifier = Modifier
                        .weight(pesoSalidas)
                        .fillMaxHeight()
                        .background(colorSalidas, CircleShape),
                )
                Spacer(Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .weight(pesoEntradas)
                        .fillMaxHeight()
                        .background(colorEntradas, CircleShape)
                        .border(BorderStroke(1.dp, colorBordeEntradas), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun BloqueMontoFlujo(
    etiqueta: String,
    montoTexto: String,
    colorMonto: androidx.compose.ui.graphics.Color,
    esIndicadorSolido: Boolean,
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (esIndicadorSolido) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = CircleShape,
                    )
                    .then(
                        if (!esIndicadorSolido) {
                            Modifier.border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)),
                                CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = montoTexto,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = colorMonto,
        )
    }
}

/**
 * Tarjeta 3: Ranking de Gastos por Categoría en Barras Horizontales 1D (Top 5 + fila 6 "Otras").
 * Muestra montos limpios sin signo `−` en `onSurface` `tnum` para evitar fatiga visual.
 */
@Composable
private fun TarjetaGastosPorCategoria(
    periodo: PeriodoAnalisis,
    categorias: List<GastoCategoria>,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()

    TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.analisis_categorias_titulo),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.analisis_categorias_subtitulo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (categorias.isEmpty()) {
                Text(
                    text = stringResource(R.string.analisis_categorias_vacio, periodo.diasHistoricos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    categorias.forEachIndexed { indice, item ->
                        FilaGastoCategoria(
                            item = item,
                            esPrincipal = indice == 0,
                            sinAnimaciones = sinAnimaciones,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaGastoCategoria(
    item: GastoCategoria,
    esPrincipal: Boolean,
    sinAnimaciones: Boolean,
) {
    val nombreVisible = if (item.esAgrupacionOtras) {
        stringResource(R.string.analisis_otras_categorias, item.cantidadAgrupadas)
    } else {
        item.nombre
    }
    val iconoRes = if (item.esAgrupacionOtras) {
        R.drawable.ic_cat_otros
    } else {
        iconoDeCategoria(item.nombre)
    }

    val proporcionAnimada by animateFloatAsState(
        targetValue = item.proporcionDelTotal.coerceIn(0.03f, 1f),
        animationSpec = if (sinAnimaciones) {
            tween(0)
        } else {
            spring(dampingRatio = 0.90f, stiffness = 420f)
        },
        label = "barraCategoria",
    )

    val colorBarra = when {
        item.esAgrupacionOtras -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        esPrincipal -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconoEnCirculo(icono = iconoRes, tamano = 36.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = nombreVisible,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.analisis_porcentaje, item.porcentajeRedondeado),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = item.monto.formatear(),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Barra horizontal 1D de 6 dp sobre pista secondaryContainer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = proporcionAnimada)
                        .fillMaxHeight()
                        .background(colorBarra, CircleShape),
                )
            }
        }
    }
}
