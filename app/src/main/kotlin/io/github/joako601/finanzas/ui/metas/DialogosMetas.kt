package io.github.joako601.finanzas.ui.metas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.metas.AnalisisTope
import io.github.joako601.finanzas.core.domain.metas.ErrorTope
import io.github.joako601.finanzas.core.domain.metas.EstadoRitmoTope
import io.github.joako601.finanzas.core.domain.metas.ResumenTopesMes
import io.github.joako601.finanzas.core.domain.metas.SugerenciaTope
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.ErrorAlGuardar
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.formatoMes
import io.github.joako601.finanzas.ui.formatoMesEnFrase
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.launch

/** Diálogo para crear o editar un tope mensual de categoría con sugerencia transparente de historial. */
@Composable
fun DialogoTope(
    editor: EditorTope,
    categoriasDisponibles: List<Cuenta>,
    sugerencia: SugerenciaTope?,
    onElegirCategoria: (CuentaId) -> Unit,
    onCambiarMonto: (String) -> Unit,
    onUsarSugerencia: (Dinero) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.guardando) onCerrar() },
        title = {
            Text(
                stringResource(if (editor.editando) R.string.metas_editar_tope else R.string.metas_nuevo_tope),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val categoriaFija = editor.categoriaEditada
                if (categoriaFija != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconoEnCirculo(iconoDeCategoria(categoriaFija.nombre), tamano = 36.dp)
                        Text(
                            text = categoriaFija.nombre,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.categoria),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        categoriasDisponibles.forEach { categoria ->
                            FilterChip(
                                selected = editor.categoriaId == categoria.id,
                                onClick = { onElegirCategoria(categoria.id) },
                                enabled = !editor.guardando,
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
                    if (ErrorTope.SIN_CATEGORIA in editor.errores || ErrorTope.CATEGORIA_NO_ES_GASTO in editor.errores) {
                        Text(
                            text = stringResource(R.string.metas_error_sin_categoria),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }

                if (sugerencia != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when {
                                sugerencia.basadaEnMesActual -> stringResource(
                                    R.string.metas_sugerencia_mes_actual,
                                    sugerencia.promedioMensual.formatear(),
                                )
                                sugerencia.esDelMesAnterior -> stringResource(
                                    R.string.metas_sugerencia_mes_pasado,
                                    sugerencia.promedioMensual.formatear(),
                                )
                                sugerencia.mesesConDatos == 1 -> stringResource(
                                    R.string.metas_sugerencia_mes_reciente,
                                    sugerencia.promedioMensual.formatear(),
                                )
                                else -> stringResource(
                                    R.string.metas_sugerencia_promedio,
                                    sugerencia.promedioMensual.formatear(),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AssistChip(
                            onClick = { onUsarSugerencia(sugerencia.montoSugeridoRedondeado) },
                            enabled = !editor.guardando,
                            label = {
                                Text(
                                    text = stringResource(
                                        R.string.metas_usar_sugerencia,
                                        sugerencia.montoSugeridoRedondeado.formatear(),
                                    ),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                )
                            },
                        )
                    }
                }

                val errorMonto = ErrorTope.MONTO_INVALIDO in editor.errores
                OutlinedTextField(
                    value = editor.montoTexto,
                    onValueChange = onCambiarMonto,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.guardando,
                    label = { Text(stringResource(R.string.metas_limite_mensual)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    isError = errorMonto,
                    supportingText = if (errorMonto) {
                        {
                            Text(
                                text = stringResource(R.string.metas_error_monto_tope),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )

                ErrorAlGuardar(editor.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !editor.guardando) {
                Text(stringResource(R.string.guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !editor.guardando) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

/** Hoja de detalle de un tope mensual: desglose, comparativa contra mes pasado y movimientos del mes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDetalleTope(
    analisis: AnalisisTope,
    resumen: ResumenTopesMes,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                ) {
                    IconoEnCirculo(iconoDeCategoria(analisis.categoria.nombre), tamano = 40.dp)
                    Column {
                        Text(
                            text = analisis.categoria.nombre,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
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
                    }
                }
                InsigniaRitmoTope(analisis.ritmo)
            }

            TarjetaFinanzas(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = analisis.gastadoMesActual.formatear(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            color = if (analisis.ritmo == EstadoRitmoTope.EXCEDIDO) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = stringResource(R.string.metas_topes_de_limite, analisis.tope.limiteMensual.formatear()),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
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
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (analisis.ritmo == EstadoRitmoTope.EXCEDIDO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = if (analisis.gastadoMesAnterior.esPositivo) {
                            stringResource(R.string.metas_detalle_mes_pasado, analisis.gastadoMesAnterior.formatear())
                        } else {
                            stringResource(R.string.metas_detalle_sin_mes_pasado)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.metas_detalle_movimientos_titulo, resumen.hoy.formatoMesEnFrase()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )

            TarjetaFinanzas(Modifier.fillMaxWidth()) {
                if (analisis.movimientosMesActual.isEmpty()) {
                    Text(
                        text = stringResource(R.string.metas_detalle_sin_movimientos),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    analisis.movimientosMesActual.forEachIndexed { indice, mov ->
                        if (indice > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        val subtitulo = listOfNotNull(
                            mov.movimiento.fecha.formatoCorto(),
                            mov.cuenta?.nombre,
                        ).joinToString(" · ")
                        // FlowRow evita que montos grandes aplasten la descripción y fecha a font_scale = 2.0.
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = mov.movimiento.descripcion.ifBlank { analisis.categoria.nombre },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = subtitulo,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "−" + mov.monto.formatear(),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = TemaFinanzas.colores.gasto,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPrincipal(
                    texto = stringResource(R.string.metas_editar_tope),
                    onClick = {
                        alcance.launch {
                            estadoHoja.hide()
                            onEditar()
                        }
                    },
                )
                // onErrorContainer (#7A140E / #FFD6D3) garantiza contraste WCAG AA > 10:1 sobre background.
                TextButton(
                    onClick = onEliminar,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.metas_eliminar_tope))
                }
            }
        }
    }
}

@Composable
fun DialogoConfirmarEliminarTope(
    eliminacion: EliminacionTope,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!eliminacion.eliminando) onCancelar() },
        title = { Text(stringResource(R.string.metas_eliminar_tope_pregunta)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.metas_eliminar_tope_texto, eliminacion.analisis.categoria.nombre),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ErrorAlGuardar(eliminacion.errorAlEliminar)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                enabled = !eliminacion.eliminando,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.eliminar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !eliminacion.eliminando) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}
