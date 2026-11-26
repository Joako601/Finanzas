package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.ui.componentes.TECLADO_MONTO

/**
 * Diálogo para dar de alta o editar una cuenta de efectivo o débito con su saldo actual. Al
 * editar ofrece "Archivar cuenta", que se confirma en el mismo diálogo.
 */
@Composable
fun DialogoNuevaCuenta(
    nuevaCuenta: NuevaCuenta,
    onCambiarNombre: (String) -> Unit,
    onCambiarSaldo: (String) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
    onPedirArchivar: () -> Unit = {},
    onCancelarArchivar: () -> Unit = {},
    onArchivar: () -> Unit = {},
    puedeArchivar: Boolean = true,
    /** Al editar: cierra el diálogo y abre la búsqueda filtrada por esta cuenta. `null` = no se ofrece. */
    onVerMovimientos: (() -> Unit)? = null,
) {
    val editando = nuevaCuenta.editando
    if (editando != null && nuevaCuenta.confirmandoArchivar) {
        ConfirmarArchivar(
            nombre = editando.cuenta.nombre,
            ayuda = stringResource(R.string.archivar_cuenta_ayuda),
            trabajando = nuevaCuenta.guardando,
            error = nuevaCuenta.errorAlGuardar,
            onArchivar = onArchivar,
            onCancelar = onCancelarArchivar,
        )
        return
    }
    val esEdicion = editando != null
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(if (esEdicion) R.string.editar_cuenta else R.string.nueva_cuenta)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(
                    valor = nuevaCuenta.nombre,
                    onCambio = onCambiarNombre,
                    etiqueta = stringResource(R.string.nombre_cuenta),
                    error = if (nuevaCuenta.errorNombre) stringResource(R.string.error_nombre_vacio) else null,
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Campo(
                    valor = nuevaCuenta.saldoTexto,
                    onCambio = onCambiarSaldo,
                    etiqueta = stringResource(R.string.saldo_actual),
                    prefijo = "$",
                    error = if (nuevaCuenta.errorSaldo) stringResource(R.string.error_saldo_invalido) else null,
                    teclado = TECLADO_MONTO.copy(imeAction = ImeAction.Done),
                )
                if (esEdicion) {
                    Text(
                        text = stringResource(R.string.editar_cuenta_ayuda),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onVerMovimientos != null) {
                        TextButton(
                            onClick = onVerMovimientos,
                            enabled = !nuevaCuenta.guardando,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.ver_movimientos)) }
                    }
                    // La última cuenta de efectivo o débito no se archiva (D-022).
                    if (puedeArchivar) {
                        TextButton(
                            onClick = onPedirArchivar,
                            enabled = !nuevaCuenta.guardando,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.archivar_cuenta)) }
                    }
                }
                if (nuevaCuenta.errorAlGuardar) {
                    Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !nuevaCuenta.guardando) {
                Text(stringResource(if (esEdicion) R.string.guardar else R.string.crear))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) }
        },
    )
}

/**
 * Confirmación de archivar una cuenta o tarjeta. No es borrar: los movimientos se conservan;
 * por eso el botón no va en rojo.
 */
@Composable
internal fun ConfirmarArchivar(
    nombre: String,
    ayuda: String,
    trabajando: Boolean,
    error: Boolean,
    onArchivar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!trabajando) onCancelar() },
        title = { Text(stringResource(R.string.archivar_pregunta, nombre)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ayuda, style = MaterialTheme.typography.bodyMedium)
                if (error) {
                    Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onArchivar, enabled = !trabajando) { Text(stringResource(R.string.archivar)) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !trabajando) { Text(stringResource(R.string.cancelar)) }
        },
    )
}
