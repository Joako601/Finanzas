package io.github.joako601.finanzas.ui.tarjetas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/** Días para la fecha límite a partir de los cuales el aviso se pinta de color de error. */
private const val DIAS_URGENTE = 3

/** Degradado del "plástico" de la tarjeta: amarillo a amarillo anaranjado, sin marcas. */
private val DegradadoTarjeta = Brush.linearGradient(listOf(Color(0xFFFDD835), Color(0xFFFFB300)))
private val TextoSobreTarjeta = Color(0xFF242424)

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        estado.tarjetas.forEach { resumen ->
            TarjetaCredito(resumen, onPagar = { onPagar(resumen) }, onCapturar = { onCapturar(resumen) })
        }
    }
}

@Composable
private fun TarjetaCredito(resumen: ResumenTarjeta, onPagar: () -> Unit, onCapturar: () -> Unit) {
    TarjetaFinanzas(Modifier.fillMaxWidth()) {
        // "Plástico": nombre, ícono y crédito disponible.
        Box(
            Modifier
                .fillMaxWidth()
                .background(DegradadoTarjeta)
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.credito),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextoSobreTarjeta,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_cat_tarjeta),
                        contentDescription = null,
                        tint = TextoSobreTarjeta,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(resumen.tarjeta.cuenta.nombre, style = MaterialTheme.typography.titleLarge, color = TextoSobreTarjeta)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.disponible_de, resumen.disponible.formatear(), resumen.tarjeta.lineaDeCredito.formatear()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSobreTarjeta,
                )
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.debes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(resumen.deuda.formatear(), style = MaterialTheme.typography.titleLarge)
            }
            LineaSituacion(resumen.situacion)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Botón secundario con borde y texto del color del texto (negro en claro,
                // blanco en oscuro); el gris por omisión de Material parecía deshabilitado.
                OutlinedButton(
                    onClick = onCapturar,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.estado_de_cuenta)) }
                Button(
                    onClick = onPagar,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Text(stringResource(R.string.pagar)) }
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
        is SituacionTarjeta.Pagado -> Triple(stringResource(R.string.situacion_pagado), null, TemaFinanzas.colores.ingreso)
        is SituacionTarjeta.CapturaPendiente -> Triple(
            stringResource(R.string.situacion_captura_pendiente, situacion.cortePredicho.formatoCorto()),
            null,
            colores.tertiary,
        )
        is SituacionTarjeta.EnCiclo -> Triple(
            stringResource(R.string.situacion_en_ciclo, situacion.proximoCorte.formatoCorto()),
            null,
            colores.onSurfaceVariant,
        )
    }
    Column {
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = color)
        if (detalle != null) Text(detalle, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
