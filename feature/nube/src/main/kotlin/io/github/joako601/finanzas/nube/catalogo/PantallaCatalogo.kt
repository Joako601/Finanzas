package io.github.joako601.finanzas.nube.catalogo

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.launch

/**
 * Placeholder del catálogo (visible cuando hay sesión iniciada).
 *
 * Contrato reservado para el subagente de Catálogo: puede agregar su ViewModel y datos
 * falsos dentro de `catalogo/` sin modificar el resto de `:feature:nube`. La barra superior
 * ya deja el espacio para el botón Cerrar sesión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCatalogo(
    usuario: UsuarioSesion,
    repositorioSesion: RepositorioSesion,
    onSalir: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nube_catalogo_titulo)) },
                navigationIcon = {
                    TextButton(onClick = onSalir) {
                        Text(stringResource(R.string.nube_volver))
                    }
                },
                actions = {
                    TextButton(onClick = { alcance.launch { repositorioSesion.cerrarSesion() } }) {
                        Text(stringResource(R.string.nube_cerrar_sesion))
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
                text = usuario.correo,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.nube_catalogo_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
