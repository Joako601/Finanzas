package io.github.joako601.finanzas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.SeccionNube
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.movimientos.PantallaMovimientos

private enum class ModoInicioNube {
    SELECCION,
    NUBE,
    PRIVADO,
}

/**
 * Contenido raíz de la distribución nube (D-020): al abrir la app permite elegir entre
 * conectarse a la nube (funciones extra: cuenta y catálogo) o entrar en modo privado
 * sin funciones de la nube, sin acoplar `app/src/main` a Firebase.
 */
@Composable
fun ContenidoApp() {
    var modo by rememberSaveable { mutableStateOf(ModoInicioNube.SELECCION) }

    when (modo) {
        ModoInicioNube.SELECCION -> PantallaBienvenidaNube(
            onConectarNube = { modo = ModoInicioNube.NUBE },
            onEntrarPrivado = { modo = ModoInicioNube.PRIVADO },
        )

        ModoInicioNube.NUBE -> SeccionNube(
            onSalir = { modo = ModoInicioNube.SELECCION },
        )

        // En modo privado la interfaz principal sigue limpia sin botones de nube a la vista,
        // pero desde el menú ⋮ se puede volver a elegir modo o conectarse sin cerrar la app.
        ModoInicioNube.PRIVADO -> PantallaMovimientos(
            onAbrirNube = { modo = ModoInicioNube.SELECCION },
        )
    }
}

@Composable
private fun PantallaBienvenidaNube(
    onConectarNube: () -> Unit,
    onEntrarPrivado: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { margenes ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(margenes)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.bienvenida_nube_titulo),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )

                Text(
                    text = stringResource(R.string.bienvenida_nube_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                // Opción 1: Conectar a la nube (funciones extra)
                TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bienvenida_opcion_nube_titulo),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.bienvenida_opcion_nube_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BotonPrincipal(
                            texto = stringResource(R.string.bienvenida_opcion_nube_boton),
                            onClick = onConectarNube,
                        )
                    }
                }

                // Opción 2: Modo privado (sin funciones de la nube)
                TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bienvenida_opcion_privado_titulo),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.bienvenida_opcion_privado_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BotonSecundario(
                            texto = stringResource(R.string.bienvenida_opcion_privado_boton),
                            onClick = onEntrarPrivado,
                        )
                    }
                }
            }
        }
    }
}
