package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R

/** Diálogo para dar de alta una cuenta de efectivo o débito con su saldo actual. */
@Composable
fun DialogoNuevaCuenta(
    nuevaCuenta: NuevaCuenta,
    onCambiarNombre: (String) -> Unit,
    onCambiarSaldo: (String) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.nueva_cuenta)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nuevaCuenta.nombre,
                    onValueChange = onCambiarNombre,
                    label = { Text(stringResource(R.string.nombre_cuenta)) },
                    singleLine = true,
                    isError = nuevaCuenta.errorNombre,
                    supportingText = if (nuevaCuenta.errorNombre) {
                        { Text(stringResource(R.string.error_nombre_vacio)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = nuevaCuenta.saldoTexto,
                    onValueChange = onCambiarSaldo,
                    label = { Text(stringResource(R.string.saldo_actual)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = nuevaCuenta.errorSaldo,
                    supportingText = if (nuevaCuenta.errorSaldo) {
                        { Text(stringResource(R.string.error_saldo_invalido)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !nuevaCuenta.guardando) {
                Text(stringResource(R.string.crear))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) }
        },
    )
}
