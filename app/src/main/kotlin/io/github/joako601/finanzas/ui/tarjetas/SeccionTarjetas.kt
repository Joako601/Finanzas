package io.github.joako601.finanzas.ui.tarjetas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.ui.formatoCorto

/** Días para la fecha límite a partir de los cuales el aviso se pinta de color de error. */
private const val DIAS_URGENTE = 3

/** Encabezado y lista de tarjetas en la pantalla principal. */
@Composable
fun SeccionTarjetas(
    estado: EstadoTarjetas,
    onAgregar: () -> Unit,
    onPagar: (ResumenTarjeta) -> Unit,
    onCapturar: (ResumenTarjeta) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tarjetas_de_credito),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAgregar) { Text(stringResource(R.string.agregar_tarjeta)) }
        }
        if (estado.tarjetas.isEmpty()) {
            Text(
                stringResource(R.string.sin_tarjetas),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        estado.tarjetas.forEach { resumen ->
            TarjetaCredito(resumen, onPagar = { onPagar(resumen) }, onCapturar = { onCapturar(resumen) })
        }
    }
}

@Composable
private fun TarjetaCredito(resumen: ResumenTarjeta, onPagar: () -> Unit, onCapturar: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(resumen.tarjeta.cuenta.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.deuda_y_disponible, resumen.deuda.formatear(), resumen.disponible.formatear()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LineaSituacion(resumen.situacion)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCapturar, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(stringResource(R.string.estado_de_cuenta))
                }
                TextButton(onClick = onPagar, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(stringResource(R.string.pagar))
                }
            }
        }
    }
}

@Composable
private fun LineaSituacion(situacion: SituacionTarjeta) {
    val colores = MaterialTheme.colorScheme
    val (texto, detalle, color) = when (situacion) {
        is SituacionTarjeta.PagoPendiente -> Triple(
            stringResource(
                R.string.situacion_pago_pendiente,
                situacion.restante.formatear(),
                situacion.estado.fechaLimite.formatoCorto(),
            ),
            if (situacion.diasParaLimite == 0) {
                stringResource(R.string.vence_hoy)
            } else {
                pluralStringResource(R.plurals.dias_restantes, situacion.diasParaLimite, situacion.diasParaLimite)
            },
            if (situacion.diasParaLimite <= DIAS_URGENTE) colores.error else colores.onSurface,
        )
        is SituacionTarjeta.Pagado -> Triple(stringResource(R.string.situacion_pagado), null, colores.primary)
        is SituacionTarjeta.CapturaPendiente -> Triple(
            stringResource(R.string.situacion_captura_pendiente, situacion.cortePredicho.formatoCorto()),
            null,
            colores.primary,
        )
        is SituacionTarjeta.EnCiclo -> Triple(
            stringResource(R.string.situacion_en_ciclo, situacion.proximoCorte.formatoCorto()),
            null,
            colores.onSurfaceVariant,
        )
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = color)
        if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
