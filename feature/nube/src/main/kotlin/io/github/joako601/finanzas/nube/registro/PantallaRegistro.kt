package io.github.joako601.finanzas.nube.registro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion

/**
 * Placeholder de registro de cuenta.
 *
 * Contrato reservado para el subagente de Registro: puede agregar su ViewModel y auxiliares
 * dentro de `registro/` sin modificar el resto de `:feature:nube`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRegistro(
    @Suppress("UNUSED_PARAMETER") repositorioSesion: RepositorioSesion,
    onIrALogin: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nube_registro_titulo)) },
                navigationIcon = {
                    TextButton(onClick = onIrALogin) {
                        Text(stringResource(R.string.nube_volver))
                    }
                },
            )
        },
    ) { margenes ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.nube_registro_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onIrALogin) {
                Text(stringResource(R.string.nube_ir_a_login))
            }
        }
    }
}
