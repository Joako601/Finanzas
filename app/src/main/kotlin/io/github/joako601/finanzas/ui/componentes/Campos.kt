package io.github.joako601.finanzas.ui.componentes

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.joako601.finanzas.R

/**
 * Solo caracteres que pueden formar un monto; el teclado numérico a veces deja pasar
 * otros. La validación real es `Dinero.parsear`.
 */
fun limpiarMonto(texto: String): String = texto.filter { it.isDigit() || it == '.' || it == ',' }.take(20)

/** Sin autocorrección ni sugerencias: el teclado no debe aprender la contraseña. */
val TECLADO_CONTRASENA = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next)

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
    /** Contraseña: muestra puntos en lugar de las letras. */
    oculto: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        prefix = prefijo?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        // El error se anuncia solo: tras tocar Guardar, TalkBack sigue en el botón.
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        keyboardOptions = teclado,
        visualTransformation = if (oculto) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

/** "No se pudo guardar" debajo de un formulario, cuando falla la base. */
@Composable
fun ErrorAlGuardar(hayError: Boolean) {
    if (hayError) Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
}
