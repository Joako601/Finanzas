package io.github.joako601.finanzas.nube.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * Placeholder de inicio de sesión.
 *
 * Contrato reservado para el subagente de Login: puede agregar su ViewModel y auxiliares
 * dentro de `login/` sin modificar el resto de `:feature:nube`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLogin(
    @Suppress("UNUSED_PARAMETER") repositorioSesion: RepositorioSesion,
    onIrARegistro: () -> Unit,
    onAbrirRecuperar: () -> Unit,
    onSalir: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nube_login_titulo)) },
                navigationIcon = {
                    TextButton(onClick = onSalir) {
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
                text = stringResource(R.string.nube_login_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = onIrARegistro) {
                Text(stringResource(R.string.nube_ir_a_registro))
            }
            TextButton(onClick = onAbrirRecuperar) {
                Text(stringResource(R.string.nube_olvide_contrasena))
            }
        }
    }
}
