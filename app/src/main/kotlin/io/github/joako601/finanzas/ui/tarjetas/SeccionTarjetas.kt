package io.github.joako601.finanzas.ui.tarjetas

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
    // Sin margen lateral propio: el carrusel ocupa todo el ancho para que se asome la tarjeta siguiente.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.padding(start = 20.dp, end = 16.dp),
            )
        }
        when (estado.tarjetas.size) {
            0 -> Unit
            1 -> estado.tarjetas.single().let { resumen ->
                TarjetaCredito(
                    resumen,
                    onPagar = { onPagar(resumen) },
                    onCapturar = { onCapturar(resumen) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            else -> CarruselTarjetas(estado.tarjetas, onPagar, onCapturar)
        }
    }
}

/** Etiqueta para encontrar el carrusel en las pruebas. */
const val ETIQUETA_CARRUSEL = "carrusel_tarjetas"

/**
 * Las tarjetas una al lado de otra: se desliza de izquierda a derecha. La siguiente se
 * asoma por la derecha para que se note que hay más, y los círculos de abajo dicen cuál
 * se ve y cuántas hay.
 */
@Composable
private fun CarruselTarjetas(
    tarjetas: List<ResumenTarjeta>,
    onPagar: (ResumenTarjeta) -> Unit,
    onCapturar: (ResumenTarjeta) -> Unit,
) {
    val estadoCarrusel = rememberPagerState { tarjetas.size }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(
            state = estadoCarrusel,
            modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_CARRUSEL),
            // Alineada con el resto de la pantalla a la izquierda; a la derecha deja ver la siguiente.
            contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            key = { tarjetas[it].tarjeta.id.valor },
        ) { pagina ->
            val resumen = tarjetas[pagina]
            TarjetaCredito(resumen, onPagar = { onPagar(resumen) }, onCapturar = { onCapturar(resumen) })
        }
        Indicador(actual = estadoCarrusel.currentPage, total = tarjetas.size)
    }
}

/** Un círculo por tarjeta; el de la que se ve, oscuro y alargado. TalkBack dice "Tarjeta 1 de 2". */
@Composable
private fun Indicador(actual: Int, total: Int) {
    val descripcion = stringResource(R.string.tarjeta_n_de_total, actual + 1, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { indice ->
            val elegida = indice == actual
            // Gris medio (≥3:1 sobre el fondo en claro y oscuro) y la elegida más larga:
            // se distingue por forma, no solo por color.
            val color by animateColorAsState(
                if (elegida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "circulo",
            )
            val ancho by animateDpAsState(if (elegida) 20.dp else 8.dp, label = "ancho_circulo")
            Box(Modifier.size(width = ancho, height = 8.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun TarjetaCredito(
    resumen: ResumenTarjeta,
    onPagar: () -> Unit,
    onCapturar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TarjetaFinanzas(modifier.fillMaxWidth()) {
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
                val aFavor = resumen.deuda.esNegativo
                Text(
                    stringResource(
                        // Con saldo a favor el disponible supera la línea; se dice por qué.
                        if (aFavor) R.string.disponible_con_saldo_a_favor else R.string.disponible_de,
                        resumen.disponible.formatear(),
                        resumen.tarjeta.lineaDeCredito.formatear(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSobreTarjeta,
                )
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Si se pagó de más, la tarjeta no debe: tiene saldo a favor (sin signo, como un monto).
                val aFavor = resumen.deuda.esNegativo
                Text(
                    stringResource(if (aFavor) R.string.saldo_a_favor else R.string.debes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(resumen.deuda.absoluto().formatear(), style = MaterialTheme.typography.titleLarge)
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
