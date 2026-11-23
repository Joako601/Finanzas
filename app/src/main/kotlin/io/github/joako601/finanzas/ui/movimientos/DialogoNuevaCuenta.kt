package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.ui.componentes.TECLADO_MONTO

/** Diálogo para dar de alta o editar una cuenta de efectivo o débito con su saldo actual. */
@Composable
fun DialogoNuevaCuenta(
    nuevaCuenta: NuevaCuenta,
    onCambiarNombre: (String) -> Unit,
    onCambiarSaldo: (String) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val esEdicion = nuevaCuenta.editando != null
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
