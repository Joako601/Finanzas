package io.github.joako601.finanzas.nube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.joako601.finanzas.nube.catalogo.PantallaCatalogo
import io.github.joako601.finanzas.nube.login.PantallaLogin
import io.github.joako601.finanzas.nube.navegacion.RutaNube
import io.github.joako601.finanzas.nube.recuperar.DialogoRecuperar
import io.github.joako601.finanzas.nube.registro.PantallaRegistro
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion

/**
 * Punto de entrada público de `:feature:nube` (D-020, D-021).
 *
 * Si `firebase.properties` no estaba configurado al compilar, muestra un aviso claro con
 * instrucciones y un botón para volver, sin cerrarse.
 */
@Composable
fun SeccionNube(onSalir: () -> Unit) {
    val context = LocalContext.current
    val contenedor = remember(context) { ContenedorNube.obtener(context) }
    val repositorio = contenedor.repositorioSesion
    if (repositorio == null) {
        PantallaSinConfigurar(onSalir = onSalir)
    } else {
        SeccionNube(repositorioSesion = repositorio, onSalir = onSalir)
    }
}

/**
 * Sobrecarga con [repositorioSesion] explícito para pruebas y vistas previas con
 * `RepositorioSesionFalso`.
 */
@Composable
fun SeccionNube(
    repositorioSesion: RepositorioSesion,
    onSalir: () -> Unit,
) {
    val estadoSesion by repositorioSesion.estado.collectAsStateWithLifecycle()
    var rutaSinSesion by rememberSaveable { mutableStateOf(RutaNube.LOGIN) }
    var mostrarRecuperar by rememberSaveable { mutableStateOf(false) }

    val rutaActual = when (estadoSesion) {
        EstadoSesion.Cargando -> null
        is EstadoSesion.Autenticado -> RutaNube.CATALOGO
        EstadoSesion.SinSesion -> if (rutaSinSesion == RutaNube.REGISTRO) RutaNube.REGISTRO else RutaNube.LOGIN
    }

    BackHandler {
        when {
            mostrarRecuperar -> mostrarRecuperar = false
            rutaActual == RutaNube.REGISTRO -> rutaSinSesion = RutaNube.LOGIN
            else -> onSalir()
        }
    }

    when (val actual = estadoSesion) {
        EstadoSesion.Cargando -> {
            Scaffold { margenes ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(margenes),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        is EstadoSesion.Autenticado -> {
            PantallaCatalogo(
                usuario = actual.usuario,
                repositorioSesion = repositorioSesion,
                onSalir = onSalir,
            )
        }

        EstadoSesion.SinSesion -> {
            when (rutaActual) {
                RutaNube.REGISTRO -> PantallaRegistro(
                    repositorioSesion = repositorioSesion,
                    onIrALogin = { rutaSinSesion = RutaNube.LOGIN },
                )

                RutaNube.LOGIN, RutaNube.CATALOGO, null -> PantallaLogin(
                    repositorioSesion = repositorioSesion,
                    onIrARegistro = { rutaSinSesion = RutaNube.REGISTRO },
                    onAbrirRecuperar = { mostrarRecuperar = true },
                    onSalir = onSalir,
                )
            }

            if (mostrarRecuperar) {
                DialogoRecuperar(
                    repositorioSesion = repositorioSesion,
                    onCerrar = { mostrarRecuperar = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaSinConfigurar(onSalir: () -> Unit) {
    BackHandler(onBack = onSalir)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nube_titulo)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.nube_sin_configurar_titulo),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.nube_sin_configurar_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSalir) {
                Text(stringResource(R.string.nube_volver))
            }
        }
    }
}
