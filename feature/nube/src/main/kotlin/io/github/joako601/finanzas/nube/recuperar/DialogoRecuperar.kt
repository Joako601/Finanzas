package io.github.joako601.finanzas.nube.recuperar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.AnimacionLottie
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion

/**
 * Diálogo de recuperación de contraseña (se abre desde Login).
 *
 * Respeta la regla OWASP de no enumeración: al recibir confirmación de envío muestra siempre
 * el mismo mensaje neutral sin revelar si el correo tiene cuenta registrada.
 */
@Composable
fun DialogoRecuperar(
    repositorioSesion: RepositorioSesion,
    onCerrar: () -> Unit,
) {
    val factory = remember(repositorioSesion) {
        RecuperarViewModel.factory(repositorioSesion)
    }
    val viewModel: RecuperarViewModel = viewModel(
        key = "recuperar-${System.identityHashCode(repositorioSesion)}",
        factory = factory,
    )
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val cerrar = {
        if (!estado.enviando) {
            viewModel.reiniciar()
            onCerrar()
        }
    }

    if (estado.enviado) {
        AlertDialog(
            onDismissRequest = cerrar,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            icon = {
                AnimacionLottie(
                    resId = R.raw.anim_correo_enviado,
                    modifier = Modifier.size(88.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.nube_recuperar_enviado_titulo),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.nube_recuperar_enviado_mensaje),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            confirmButton = {
                Button(
                    onClick = cerrar,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.nube_recuperar_entendido))
                }
            },
        )
    } else {
        val mensajeError = estado.error?.let { stringResource(it.aRecursoTexto()) }
        val descripcionEnviando = stringResource(R.string.nube_recuperar_enviando)

        AlertDialog(
            onDismissRequest = cerrar,
            properties = DialogProperties(
                dismissOnBackPress = !estado.enviando,
                dismissOnClickOutside = !estado.enviando,
            ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    text = stringResource(R.string.nube_recuperar_titulo),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.nube_recuperar_descripcion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = estado.correo,
                        onValueChange = viewModel::alCambiarCorreo,
                        label = { Text(stringResource(R.string.nube_recuperar_correo_etiqueta)) },
                        placeholder = { Text(stringResource(R.string.nube_recuperar_correo_placeholder)) },
                        singleLine = true,
                        enabled = !estado.enviando,
                        isError = estado.error == ErrorRecuperar.CORREO_INVALIDO,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                focusManager.clearFocus()
                                viewModel.enviar()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mensajeError != null) {
                        Text(
                            text = mensajeError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.enviar()
                    },
                    enabled = !estado.enviando,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    if (estado.enviando) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = descripcionEnviando },
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                    } else {
                        Text(stringResource(R.string.nube_recuperar_enviar))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = cerrar,
                    enabled = !estado.enviando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.nube_recuperar_cancelar))
                }
            },
        )
    }
}

private fun ErrorRecuperar.aRecursoTexto(): Int = when (this) {
    ErrorRecuperar.CORREO_INVALIDO -> R.string.nube_recuperar_error_correo_invalido
    ErrorRecuperar.DEMASIADOS_INTENTOS -> R.string.nube_recuperar_error_demasiados_intentos
    ErrorRecuperar.SIN_CONEXION -> R.string.nube_recuperar_error_sin_conexion
    ErrorRecuperar.ERROR_INESPERADO -> R.string.nube_recuperar_error_inesperado
}
