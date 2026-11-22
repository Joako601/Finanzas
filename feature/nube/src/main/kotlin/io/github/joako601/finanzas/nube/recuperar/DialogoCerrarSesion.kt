package io.github.joako601.finanzas.nube.recuperar

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.joako601.finanzas.nube.R

/**
 * Diálogo reutilizable para confirmar el cierre de sesión antes de invocar
 * `RepositorioSesion.cerrarSesion()`, evitando cierres accidentales.
 */
@Composable
fun DialogoCerrarSesion(
    enCurso: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val descripcionEnCurso = stringResource(R.string.nube_cerrar_sesion_en_curso)

    AlertDialog(
        onDismissRequest = {
            if (!enCurso) onCancelar()
        },
        properties = DialogProperties(
            dismissOnBackPress = !enCurso,
            dismissOnClickOutside = !enCurso,
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = stringResource(R.string.nube_cerrar_sesion_confirmar_titulo),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.nube_cerrar_sesion_confirmar_mensaje),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                enabled = !enCurso,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                if (enCurso) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = descripcionEnCurso },
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text(stringResource(R.string.nube_cerrar_sesion_confirmar))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                enabled = !enCurso,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.nube_cerrar_sesion_cancelar))
            }
        },
    )
}
