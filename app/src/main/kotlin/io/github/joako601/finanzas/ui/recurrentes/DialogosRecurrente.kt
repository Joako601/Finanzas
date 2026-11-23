package io.github.joako601.finanzas.ui.recurrentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.core.domain.recurrentes.ErrorRecurrente
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.ui.componentes.ErrorAlGuardar
import io.github.joako601.finanzas.ui.componentes.SelectorFecha
import io.github.joako601.finanzas.ui.componentes.TECLADO_MONTO
import io.github.joako601.finanzas.ui.componentes.limpiarMonto
import io.github.joako601.finanzas.ui.movimientos.textoError

/** Alta o edición de un pago o ingreso fijo. */
@Composable
fun DialogoNuevoRecurrente(
    nuevo: NuevoRecurrente,
    categorias: List<Cuenta>,
    onEditar: (NuevoRecurrente.() -> NuevoRecurrente) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val esEdicion = nuevo.editando != null
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(if (esEdicion) R.string.editar_recurrente else R.string.nuevo_recurrente)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ClaseRegistro.entries.forEachIndexed { indice, clase ->
                        SegmentedButton(
                            selected = nuevo.clase == clase,
                            onClick = { onEditar { copy(clase = clase) } },
                            shape = SegmentedButtonDefaults.itemShape(indice, ClaseRegistro.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (clase) {
                                        ClaseRegistro.GASTO -> R.string.pago
                                        ClaseRegistro.INGRESO -> R.string.ingreso
                                    },
                                ),
                            )
                        }
                    }
                }
                Campo(
                    valor = nuevo.nombre,
                    onCambio = { v -> onEditar { copy(nombre = v.take(Cuenta.LARGO_MAXIMO_NOMBRE)) } },
                    etiqueta = stringResource(R.string.nombre_recurrente),
                    error = if (ErrorRecurrente.NOMBRE_VACIO in nuevo.errores) stringResource(R.string.error_nombre_vacio) else null,
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Campo(
                    valor = nuevo.monto,
                    onCambio = { v -> onEditar { copy(monto = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.monto_aproximado),
                    prefijo = "$",
                    error = if (ErrorRecurrente.MONTO_INVALIDO in nuevo.errores) stringResource(R.string.error_monto_pago) else null,
                    teclado = TECLADO_MONTO.copy(imeAction = ImeAction.Done),
                )
                Grupo(
                    titulo = stringResource(R.string.categoria),
                    error = if (ErrorRecurrente.SIN_CATEGORIA in nuevo.errores) stringResource(R.string.error_sin_categoria) else null,
                ) {
                    categorias.forEach { categoria ->
                        FilterChip(
                            selected = nuevo.categoriaId == categoria.id,
                            onClick = { onEditar { copy(categoriaId = categoria.id) } },
                            label = { Text(categoria.nombre) },
                        )
                    }
                }
                Grupo(titulo = stringResource(R.string.se_repite), error = null) {
                    Frecuencia.entries.forEach { frecuencia ->
                        FilterChip(
                            selected = nuevo.periodicidadPersonalizada == null && nuevo.frecuencia == frecuencia,
                            onClick = { onEditar { copy(frecuencia = frecuencia, periodicidadPersonalizada = null) } },
                            label = { Text(stringResource(frecuencia.etiqueta)) },
                        )
                    }
                }
                SelectorFecha(
                    fecha = nuevo.inicio,
                    onCambiar = { f -> onEditar { copy(inicio = f) } },
                    etiqueta = stringResource(R.string.proxima_fecha),
                )
                Text(stringResource(R.string.ayuda_recurrente), style = MaterialTheme.typography.bodySmall)
                ErrorAlGuardar(nuevo.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !nuevo.guardando) {
                Text(stringResource(if (esEdicion) R.string.guardar else R.string.crear))
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Acciones del diálogo de registro, para no pasar lambdas sueltas. */
class AccionesRegistroRecurrente(
    val editar: (RegistroRecurrente.() -> RegistroRecurrente) -> Unit,
    val registrar: () -> Unit,
    val omitir: () -> Unit,
    val eliminar: () -> Unit,
    val cerrar: () -> Unit,
    val editarSerie: () -> Unit = {},
    val pedirEliminar: () -> Unit = eliminar,
    val cancelarEliminar: () -> Unit = {},
    val agregarCuenta: (() -> Unit)? = null,
)

/** Registra el pago o cobro pendiente con el monto, la cuenta y la fecha reales. */
@Composable
fun DialogoRegistroRecurrente(
    registro: RegistroRecurrente,
    cuentas: List<Cuenta>,
    acciones: AccionesRegistroRecurrente,
) {
    val recurrente = registro.resumen.recurrente
    val erroresMonto = setOf(ErrorRegistro.MONTO_VACIO, ErrorRegistro.MONTO_INVALIDO, ErrorRegistro.MONTO_CERO)

    if (registro.confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = acciones.cancelarEliminar,
            title = { Text(stringResource(R.string.eliminar)) },
            text = { Text(stringResource(R.string.eliminar_recurrente_pregunta, recurrente.nombre)) },
            confirmButton = {
                TextButton(onClick = acciones.eliminar, enabled = !registro.guardando) {
                    Text(stringResource(R.string.eliminar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = acciones.cancelarEliminar, enabled = !registro.guardando) {
                    Text(stringResource(R.string.cancelar))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = acciones.cerrar,
        title = { Text(stringResource(R.string.registrar_recurrente, recurrente.nombre)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Campo(
                    valor = registro.monto,
                    onCambio = { v -> acciones.editar { copy(monto = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.monto),
                    prefijo = "$",
                    error = registro.errores.firstOrNull { it in erroresMonto }?.let { textoError(it) },
                    teclado = TECLADO_MONTO.copy(imeAction = ImeAction.Done),
                )
                Grupo(
                    titulo = stringResource(
                        when (recurrente.clase) {
                            ClaseRegistro.GASTO -> R.string.desde
                            ClaseRegistro.INGRESO -> R.string.llega_a
                        },
                    ),
                    error = if (ErrorRegistro.SIN_CUENTA in registro.errores) textoError(ErrorRegistro.SIN_CUENTA) else null,
                ) {
                    cuentas.forEach { cuenta ->
                        FilterChip(
                            selected = registro.cuentaId == cuenta.id,
                            onClick = { acciones.editar { copy(cuentaId = cuenta.id) } },
                            label = { Text(cuenta.nombre) },
                        )
                    }
                    if (acciones.agregarCuenta != null) {
                        FilterChip(
                            selected = false,
                            onClick = acciones.agregarCuenta,
                            label = { Text(stringResource(R.string.chip_agregar_cuenta)) },
                        )
                    }
                }
                SelectorFecha(
                    fecha = registro.fecha,
                    onCambiar = { f -> acciones.editar { copy(fecha = f) } },
                    etiqueta = stringResource(R.string.fecha),
                )
                // Errores que no son de un campo visible (por ejemplo, la categoría se archivó).
                registro.errores
                    .filterNot { it in erroresMonto || it == ErrorRegistro.SIN_CUENTA }
                    .forEach { Text(textoError(it), color = MaterialTheme.colorScheme.error) }
                ErrorAlGuardar(registro.errorAlGuardar)
                Text(stringResource(R.string.ayuda_omitir), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = acciones.editarSerie, enabled = !registro.guardando) {
                        Text(stringResource(R.string.accion_editar_serie))
                    }
                    TextButton(onClick = acciones.omitir, enabled = !registro.guardando) {
                        Text(stringResource(R.string.omitir))
                    }
                    TextButton(onClick = acciones.pedirEliminar, enabled = !registro.guardando) {
                        Text(stringResource(R.string.eliminar), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.registrar, enabled = !registro.guardando) {
                Text(stringResource(R.string.registrar))
            }
        },
        dismissButton = { TextButton(onClick = acciones.cerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun Grupo(titulo: String, error: String?, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
