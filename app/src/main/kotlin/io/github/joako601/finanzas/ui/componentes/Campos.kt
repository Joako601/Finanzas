package io.github.joako601.finanzas.ui.componentes

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.joako601.finanzas.R

/**
 * Solo caracteres que pueden formar un monto; el teclado numérico a veces deja pasar
 * otros. La validación real es `Dinero.parsear`.
 */
fun limpiarMonto(texto: String): String = texto.filter { it.isDigit() || it == '.' || it == ',' }.take(20)

val TECLADO_MONTO = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

/** Campo de texto de una línea con etiqueta y, si hay [error], el mensaje debajo. */
@Composable
fun Campo(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    error: String?,
    teclado: KeyboardOptions,
    prefijo: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        prefix = prefijo?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = teclado,
    )
}

/** "No se pudo guardar" debajo de un formulario, cuando falla la base. */
@Composable
fun ErrorAlGuardar(hayError: Boolean) {
    if (hayError) Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
}
