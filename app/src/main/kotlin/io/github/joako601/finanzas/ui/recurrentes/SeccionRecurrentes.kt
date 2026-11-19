package io.github.joako601.finanzas.ui.recurrentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/** Encabezado y lista de pagos y cobros fijos, de la fecha más cercana a la más lejana. */
@Composable
fun SeccionRecurrentes(
    estado: EstadoRecurrentes,
    onAgregar: () -> Unit,
    onTocar: (ResumenRecurrente) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.recurrentes_titulo),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAgregar) { Text(stringResource(R.string.agregar_recurrente)) }
        }
        if (estado.recurrentes.isEmpty()) {
            Text(
                stringResource(R.string.sin_recurrentes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        } else {
            TarjetaFinanzas(Modifier.fillMaxWidth()) {
                estado.recurrentes.forEachIndexed { indice, resumen ->
                    if (indice > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    FilaRecurrente(resumen, onClick = { onTocar(resumen) })
                }
            }
        }
    }
}

@Composable
private fun FilaRecurrente(resumen: ResumenRecurrente, onClick: () -> Unit) {
    val recurrente = resumen.recurrente
    val (signo, colorMonto) = when (recurrente.clase) {
        ClaseRegistro.GASTO -> "−" to TemaFinanzas.colores.gasto
        ClaseRegistro.INGRESO -> "+" to TemaFinanzas.colores.ingreso
    }
    val pendiente = resumen.diasParaProxima < 0
    val cuando = when (resumen.diasParaProxima) {
        0 -> stringResource(R.string.hoy)
        1 -> stringResource(R.string.manana)
        else -> if (pendiente) {
            stringResource(R.string.pendiente_desde, recurrente.proxima.formatoCorto())
        } else {
            recurrente.proxima.formatoCorto()
        }
    }
    val frecuencia = stringResource(Frecuencia.de(recurrente.periodicidad)?.etiqueta ?: R.string.frecuencia_otra)

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconoEnCirculo(iconoDeCategoria(resumen.categoria?.nombre)) },
        headlineContent = { Text(recurrente.nombre, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                "$frecuencia · $cuando",
                color = if (pendiente) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(signo + recurrente.montoEstimado.formatear(), color = colorMonto, style = MaterialTheme.typography.titleMedium)
        },
    )
}
