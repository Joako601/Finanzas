package io.github.joako601.finanzas.ui.metas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.metas.AporteMeta
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.metas.AnalisisMetaAhorro
import io.github.joako601.finanzas.core.domain.metas.ErrorMeta
import io.github.joako601.finanzas.core.domain.metas.ErrorMovimientoMeta
import io.github.joako601.finanzas.core.domain.metas.EstadoMetaAhorro
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.ErrorAlGuardar
import io.github.joako601.finanzas.ui.componentes.SelectorFecha
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.movimientos.textoEditable
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.launch

/** Diálogo para crear o editar una meta / apartado de ahorro. */
@Composable
fun DialogoMeta(
    editor: EditorMeta,
    onEditar: (EditorMeta.() -> EditorMeta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val esEdicion = editor.editando != null
    AlertDialog(
        onDismissRequest = { if (!editor.guardando) onCerrar() },
        title = {
            Text(stringResource(if (esEdicion) R.string.metas_ahorro_editar else R.string.metas_ahorro_nueva))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val errorNombre = ErrorMeta.NOMBRE_VACIO in editor.errores
                OutlinedTextField(
                    value = editor.nombre,
                    onValueChange = { v -> onEditar { copy(nombre = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.guardando,
                    label = { Text(stringResource(R.string.metas_ahorro_nombre_label)) },
                    singleLine = true,
                    isError = errorNombre,
                    supportingText = if (errorNombre) {
                        {
                            Text(
                                text = stringResource(R.string.error_nombre_vacio),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )

                val errorObjetivo = ErrorMeta.OBJETIVO_INVALIDO in editor.errores
                OutlinedTextField(
                    value = editor.objetivoTexto,
                    onValueChange = { v -> onEditar { copy(objetivoTexto = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.guardando,
                    label = { Text(stringResource(R.string.metas_ahorro_objetivo_label)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    isError = errorObjetivo,
                    supportingText = if (errorObjetivo) {
                        {
                            Text(
                                text = stringResource(R.string.metas_ahorro_error_objetivo),
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (esEdicion) ImeAction.Done else ImeAction.Next,
                    ),
                )

                if (!esEdicion) {
                    val errorApartado = ErrorMeta.APARTADO_INVALIDO in editor.errores
                    OutlinedTextField(
                        value = editor.apartadoInicialTexto,
                        onValueChange = { v -> onEditar { copy(apartadoInicialTexto = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !editor.guardando,
                        label = { Text(stringResource(R.string.metas_ahorro_apartado_inicial_label)) },
                        prefix = { Text("$") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        isError = errorApartado,
                        supportingText = if (errorApartado) {
                            {
                                Text(
                                    text = stringResource(R.string.metas_ahorro_error_apartado),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    )
                }

                // Fila toggleable accesible (>= 48 dp + TalkBack enlaza etiqueta y estado)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = editor.tieneFechaLimite,
                            enabled = !editor.guardando,
                            role = Role.Switch,
                            onValueChange = { activo -> onEditar { copy(tieneFechaLimite = activo) } },
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.metas_ahorro_tiene_fecha),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(
                        checked = editor.tieneFechaLimite,
                        enabled = !editor.guardando,
                        onCheckedChange = null,
                    )
                }
                if (editor.tieneFechaLimite) {
                    SelectorFecha(
                        fecha = editor.fechaLimite,
                        onCambiar = { f -> onEditar { copy(fechaLimite = f) } },
                        etiqueta = stringResource(R.string.metas_ahorro_fecha_limite),
                        habilitado = !editor.guardando,
                    )
                }

                // Toggle: Reservar de mi disponible (fila toggleable accesible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = editor.reservarDeDisponible,
                            enabled = !editor.guardando,
                            role = Role.Switch,
                            onValueChange = { activo -> onEditar { copy(reservarDeDisponible = activo) } },
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.metas_ahorro_reservar_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.metas_ahorro_reservar_ayuda),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = editor.reservarDeDisponible,
                        enabled = !editor.guardando,
                        onCheckedChange = null,
                    )
                }

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

/** Diálogo rápido para aportar o retirar/usar dinero de una meta con chips acumulativos de 1 toque. */
@Composable
fun DialogoMovimientoMeta(
    dialogo: MovimientoMetaDialogo,
    analisisVivo: AnalisisMetaAhorro,
    onCambiarMonto: (String) -> Unit,
    onSumarMonto: (Dinero) -> Unit,
    onCambiarArchivarAlVaciar: (Boolean) -> Unit,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
    onCambiarRegistrarComoGasto: (Boolean) -> Unit = {},
) {
    val esAporte = dialogo.tipo == TipoMovimientoMeta.APORTAR
    val meta = analisisVivo.meta

    AlertDialog(
        onDismissRequest = { if (!dialogo.guardando) onCerrar() },
        title = {
            Text(
                stringResource(
                    if (esAporte) R.string.metas_ahorro_aportar_titulo else R.string.metas_ahorro_retirar_titulo,
                    meta.nombre,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (esAporte) {
                        listOf(Dinero.pesos(100), Dinero.pesos(500), Dinero.pesos(1_000)).forEach { montoRapido ->
                            AssistChip(
                                onClick = { onSumarMonto(montoRapido) },
                                enabled = !dialogo.guardando,
                                label = {
                                    Text(
                                        text = "+" + montoRapido.formatear(),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    )
                                },
                            )
                        }
                        if (analisisVivo.faltante.esPositivo) {
                            AssistChip(
                                onClick = { onCambiarMonto(analisisVivo.faltante.textoEditable()) },
                                enabled = !dialogo.guardando,
                                label = {
                                    Text(
                                        text = stringResource(
                                            R.string.metas_ahorro_completar_chip,
                                            analisisVivo.faltante.formatear(),
                                        ),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    )
                                },
                            )
                        }
                    } else {
                        AssistChip(
                            onClick = { onCambiarMonto(meta.apartado.textoEditable()) },
                            enabled = !dialogo.guardando,
                            label = {
                                Text(
                                    text = stringResource(R.string.metas_ahorro_todo_chip, meta.apartado.formatear()),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                )
                            },
                        )
                    }
                }

                val mensajeError = when (dialogo.error) {
                    ErrorMovimientoMeta.MONTO_INVALIDO -> stringResource(R.string.error_monto_cero)
                    ErrorMovimientoMeta.SUPERA_APARTADO -> stringResource(
                        R.string.metas_ahorro_error_supera_apartado,
                        meta.apartado.formatear(),
                    )
                    null -> null
                }

                OutlinedTextField(
                    value = dialogo.montoTexto,
                    onValueChange = onCambiarMonto,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !dialogo.guardando,
                    label = { Text(stringResource(R.string.monto)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    isError = mensajeError != null,
                    supportingText = mensajeError?.let { texto ->
                        {
                            Text(
                                text = texto,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )

                if (!esAporte && !meta.archivada) {
                    val montoParseado = Dinero.parsear(dialogo.montoTexto)
                    if (montoParseado == meta.apartado) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = dialogo.archivarAlVaciar,
                                    enabled = !dialogo.guardando,
                                    role = Role.Checkbox,
                                    onValueChange = onCambiarArchivarAlVaciar,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(
                                checked = dialogo.archivarAlVaciar,
                                enabled = !dialogo.guardando,
                                onCheckedChange = null,
                            )
                            Text(
                                text = stringResource(R.string.metas_ahorro_archivar_al_vaciar),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // "Retirar y registrar gasto": al guardar abre la captura de Inicio con el monto.
                if (!esAporte) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = dialogo.registrarComoGasto,
                                enabled = !dialogo.guardando,
                                role = Role.Checkbox,
                                onValueChange = onCambiarRegistrarComoGasto,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = dialogo.registrarComoGasto, enabled = !dialogo.guardando, onCheckedChange = null)
                        Text(text = stringResource(R.string.metas_ahorro_registrar_gasto), style = MaterialTheme.typography.bodySmall)
                    }
                }

                ErrorAlGuardar(dialogo.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar, enabled = !dialogo.guardando) {
                Text(stringResource(if (esAporte) R.string.metas_ahorro_aportar else R.string.metas_ahorro_retirar_usar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !dialogo.guardando) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

/** Aportes (+) y retiros (−) de la meta, los más recientes primero. Solo existen desde la v7. */
@Composable
private fun HistorialAportes(aportes: List<AporteMeta>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.metas_ahorro_historial),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (aportes.isEmpty()) {
            Text(
                stringResource(R.string.metas_ahorro_historial_vacio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aportes.take(MAXIMO_HISTORIAL).forEach { aporte ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(aporte.fecha.formatoCorto(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = if (aporte.monto.esNegativo) "−" + (-aporte.monto).formatear() else "+" + aporte.monto.formatear(),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (aporte.monto.esNegativo) MaterialTheme.colorScheme.onSurfaceVariant else TemaFinanzas.colores.ingreso,
                )
            }
        }
    }
}

/** Los últimos movimientos que caben sin volver larga la hoja. */
private const val MAXIMO_HISTORIAL = 8

/** Hoja de detalle de una meta de ahorro: progreso, cuotas sugeridas, acciones rápidas y edición. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDetalleMeta(
    analisis: AnalisisMetaAhorro,
    accionDetalle: AccionDetalleMeta,
    onAportar: () -> Unit,
    onRetirar: () -> Unit,
    onEditar: () -> Unit,
    onAlternarArchivar: () -> Unit,
    onEliminar: () -> Unit,
    onCerrar: () -> Unit,
    aportes: List<AporteMeta> = emptyList(),
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    val meta = analisis.meta
    val habilitado = !accionDetalle.procesando

    ModalBottomSheet(
        onDismissRequest = { if (habilitado) onCerrar() },
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
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = meta.nombre,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(
                            if (meta.reservarDeDisponible) {
                                R.string.metas_ahorro_reserva_activa
                            } else {
                                R.string.metas_ahorro_reserva_inactiva
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InsigniaEstadoMeta(estado = analisis.estado, archivada = meta.archivada)
            }

            TarjetaFinanzas(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = meta.apartado.formatear(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            color = if (!meta.archivada && analisis.estado == EstadoMetaAhorro.COMPLETADA) {
                                TemaFinanzas.colores.ingreso
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = stringResource(
                                R.string.metas_ahorro_de_objetivo_pct,
                                meta.objetivo.formatear(),
                                analisis.porcentajeEntero,
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    BarraProgresoMeta(
                        fraccion = analisis.fraccionAvance,
                        estado = analisis.estado,
                        porcentajeEntero = analisis.porcentajeEntero,
                    )

                    Text(
                        text = textoSubtituloMeta(analisis),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (!meta.archivada && analisis.estado == EstadoMetaAhorro.FECHA_ALCANZADA) {
                            TemaFinanzas.colores.aviso
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )

                    analisis.cuotaMensual?.let { mensual ->
                        Text(
                            text = stringResource(R.string.metas_ahorro_falta_y_mes, mensual.formatear()),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    meta.fechaLimite?.let { limite ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "${stringResource(R.string.metas_ahorro_fecha_limite)}: ${limite.formatoCorto()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (accionDetalle.error) {
                TarjetaFinanzas(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        ErrorAlGuardar(true)
                    }
                }
            }

            HistorialAportes(aportes)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (meta.archivada) {
                    BotonPrincipal(
                        texto = stringResource(R.string.metas_ahorro_reactivar),
                        onClick = onAlternarArchivar,
                        habilitado = habilitado,
                    )
                } else {
                    BotonPrincipal(
                        texto = stringResource(R.string.metas_ahorro_aportar),
                        onClick = {
                            alcance.launch {
                                try {
                                    estadoHoja.hide()
                                } finally {
                                    onCerrar()
                                    onAportar()
                                }
                            }
                        },
                        habilitado = habilitado,
                    )
                }
                if (meta.apartado.esPositivo) {
                    BotonSecundario(
                        texto = stringResource(R.string.metas_ahorro_retirar_usar),
                        onClick = {
                            alcance.launch {
                                try {
                                    estadoHoja.hide()
                                } finally {
                                    onCerrar()
                                    onRetirar()
                                }
                            }
                        },
                        habilitado = habilitado,
                    )
                }
                BotonSecundario(
                    texto = stringResource(R.string.metas_ahorro_editar),
                    onClick = {
                        alcance.launch {
                            try {
                                estadoHoja.hide()
                            } finally {
                                onCerrar()
                                onEditar()
                            }
                        }
                    },
                    habilitado = habilitado,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (!meta.archivada) {
                        TextButton(onClick = onAlternarArchivar, enabled = habilitado) {
                            Text(stringResource(R.string.metas_ahorro_archivar))
                        }
                    }
                    // onErrorContainer garantiza contraste WCAG AA > 10:1 sobre background en claro y oscuro.
                    TextButton(
                        onClick = onEliminar,
                        enabled = habilitado,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Text(stringResource(R.string.metas_ahorro_eliminar))
                    }
                }
            }
        }
    }
}

@Composable
fun DialogoConfirmarEliminarMeta(
    eliminacion: EliminacionMeta,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!eliminacion.eliminando) onCancelar() },
        title = { Text(stringResource(R.string.metas_ahorro_eliminar_pregunta)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.metas_ahorro_eliminar_texto, eliminacion.meta.nombre),
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
