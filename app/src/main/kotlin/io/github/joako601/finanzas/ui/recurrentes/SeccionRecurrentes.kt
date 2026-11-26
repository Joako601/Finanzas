package io.github.joako601.finanzas.ui.recurrentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.ui.componentes.enPantalla
import io.github.joako601.finanzas.ui.componentes.fundidoAlOcultar
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
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
            var verTodos by rememberSaveable { mutableStateOf(false) }
            val cercanos = remember(estado.recurrentes) { recurrentesCercanos(estado.recurrentes) }
            val visibles = if (verTodos) estado.recurrentes else cercanos
            val sinAnimaciones = rememberAnimacionesDesactivadas()
            TarjetaFinanzas(if (sinAnimaciones) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().animateContentSize()) {
                visibles.forEachIndexed { indice, resumen ->
                    if (indice > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    FilaRecurrente(resumen, onClick = { onTocar(resumen) })
                }
                if (cercanos.size < estado.recurrentes.size) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Fila completa y no TextButton: el gris al tocar cubre todo el ancho de la tarjeta.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { verTodos = !verTodos }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (verTodos) stringResource(R.string.ver_menos)
                            else stringResource(R.string.ver_todos_recurrentes, estado.recurrentes.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Días hacia adelante que cuentan como "próximo" en Inicio. */
private const val DIAS_PROXIMOS = 7

/** Cuántos se muestran en Inicio antes de "Ver todos". */
private const val MAXIMO_CERCANOS = 3

/**
 * Lo que se ve en Inicio sin desplegar: los vencidos y los de los próximos [DIAS_PROXIMOS] días,
 * hasta [MAXIMO_CERCANOS]; si no hay ninguno, el más cercano para que la sección no quede vacía.
 * La lista ya llega ordenada por fecha, así que los vencidos van primero.
 */
internal fun recurrentesCercanos(recurrentes: List<ResumenRecurrente>): List<ResumenRecurrente> =
    recurrentes.filter { it.diasParaProxima <= DIAS_PROXIMOS }
        .ifEmpty { recurrentes.take(1) }
        .take(MAXIMO_CERCANOS)

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
        leadingContent = { IconoEnCirculo(iconoDeCategoria(resumen.categoria)) },
        headlineContent = { Text(recurrente.nombre, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                "$frecuencia · $cuando",
                color = if (pendiente) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(signo + recurrente.montoEstimado.enPantalla(), color = colorMonto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fundidoAlOcultar())
        },
    )
}
