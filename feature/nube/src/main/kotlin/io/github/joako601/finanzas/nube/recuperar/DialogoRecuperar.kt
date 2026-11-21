package io.github.joako601.finanzas.nube.recuperar

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion

/**
 * Placeholder del diálogo de recuperación de contraseña (se abre desde Login).
 *
 * Contrato reservado para el subagente de Recuperar: puede agregar su ViewModel y auxiliares
 * dentro de `recuperar/` sin modificar el resto de `:feature:nube`.
 */
@Composable
fun DialogoRecuperar(
    @Suppress("UNUSED_PARAMETER") repositorioSesion: RepositorioSesion,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.nube_recuperar_titulo)) },
        text = { Text(stringResource(R.string.nube_recuperar_placeholder)) },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.nube_cerrar))
            }
        },
    )
}
