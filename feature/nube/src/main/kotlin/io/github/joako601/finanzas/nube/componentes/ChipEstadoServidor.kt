package io.github.joako601.finanzas.nube.componentes

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.R

/** Estado real de conexión mostrado en los encabezados de `:feature:nube`. */
internal enum class EstadoConexionServidor {
    DEMO,
    CONECTANDO,
    CONECTADO,
    SIN_CONEXION,
    SESION_EXPIRADA,
    ERROR_SERVIDOR,
}

/** Pastilla visual que refleja el estado real de la última consulta al backend. */
@Composable
internal fun ChipEstadoServidor(
    estado: EstadoConexionServidor,
    modifier: Modifier = Modifier,
) {
    val (textoRes, fondo, contenido) = when (estado) {
        EstadoConexionServidor.DEMO -> Triple(
            R.string.catalogo_origen_demo,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        EstadoConexionServidor.CONECTANDO -> Triple(
            R.string.catalogo_origen_conectando,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        EstadoConexionServidor.CONECTADO -> Triple(
            R.string.catalogo_origen_servidor,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        EstadoConexionServidor.SIN_CONEXION -> Triple(
            R.string.catalogo_origen_sin_conexion,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        EstadoConexionServidor.SESION_EXPIRADA -> Triple(
            R.string.catalogo_origen_sesion_expirada,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        EstadoConexionServidor.ERROR_SERVIDOR -> Triple(
            R.string.catalogo_origen_error_servidor,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = fondo,
    ) {
        Text(
            text = stringResource(textoRes),
            style = MaterialTheme.typography.labelSmall,
            color = contenido,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
