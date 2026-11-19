package io.github.joako601.finanzas.ui.disponible

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.disponible.EventoFuturo
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.disponible.TipoEvento
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.formatoDiaCorto
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * El disponible real con su cuenta a la vista: cuánto hay en cuentas, cuánto se aparta
 * para pagos, qué día llega el saldo más bajo y, si algún día no alcanzaría, el aviso.
 * TalkBack lo lee como un solo bloque.
 */
@Composable
fun ResumenDisponible(linea: LineaDeTiempo) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.disponible_real),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            linea.disponible.formatear(),
            style = MaterialTheme.typography.headlineLarge,
            color = if (linea.disponible.esNegativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        val secundario = MaterialTheme.colorScheme.onSurfaceVariant
        val bajo = linea.puntoMasBajo
        if (bajo == null) {
            Text(stringResource(R.string.sin_pagos_30_dias), style = MaterialTheme.typography.bodyMedium, color = secundario)
        } else {
            Text(
                stringResource(R.string.en_cuentas_para_pagos, linea.enCuentas.formatear(), linea.comprometido.formatear()),
                style = MaterialTheme.typography.bodyMedium,
                color = secundario,
            )
            Text(
                stringResource(R.string.saldo_mas_bajo, bajo.fecha.formatoDiaCorto(linea.hoy)),
                style = MaterialTheme.typography.bodyMedium,
                color = secundario,
            )
        }
        linea.primerFaltante?.let { faltante ->
            Text(
                stringResource(R.string.faltante, faltante.fecha.formatoDiaCorto(linea.hoy), faltante.falta.formatear()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Qué hacer al tocar algo de la línea de tiempo. */
class AccionesLineaDeTiempo(
    val tocarFijo: (RecurrenteId) -> Unit,
    val tocarTarjeta: (CuentaId) -> Unit,
    val agregarFijo: () -> Unit,
    val cerrar: () -> Unit,
)

/**
 * Hoja con lo que pasa en los próximos 30 días. Si el saldo baja de lo que hay hoy, se
 * parte en dos: lo que llega hasta el saldo más bajo (lo que define el disponible) y lo
 * que viene después.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaLineaDeTiempo(estado: EstadoLineaDeTiempo, acciones: AccionesLineaDeTiempo) {
    val linea = estado.linea ?: return
    ModalBottomSheet(
        onDismissRequest = acciones.cerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Fondo de pantalla, no de tarjeta: así las tarjetas se distinguen, como en la pantalla principal.
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.proximos_30_dias),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            // En tarjeta, como en la pantalla principal: además el rojo del faltante cumple contraste sobre blanco.
            TarjetaFinanzas(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) { ResumenDisponible(linea) }
            }
            Text(
                stringResource(R.string.explicacion_disponible),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (linea.eventos.isEmpty()) {
                Text(
                    stringResource(R.string.sin_eventos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val bajo = linea.puntoMasBajo
                // Hasta el saldo más bajo, inclusive: esos movimientos fijan el disponible.
                val corte = bajo?.let { linea.eventos.indexOf(it) + 1 } ?: 0
                if (bajo != null) {
                    Grupo(
                        titulo = stringResource(R.string.grupo_definen, bajo.fecha.formatoDiaCorto(linea.hoy)),
                        eventos = linea.eventos.subList(0, corte),
                        linea = linea,
                        estado = estado,
                        acciones = acciones,
                    )
                }
                Grupo(
                    titulo = bajo?.let { stringResource(R.string.grupo_despues, it.fecha.formatoDiaCorto(linea.hoy)) }
                        ?: stringResource(R.string.grupo_lo_que_viene),
                    eventos = linea.eventos.subList(corte, linea.eventos.size),
                    linea = linea,
                    estado = estado,
                    acciones = acciones,
                )
            }
            TextButton(onClick = acciones.agregarFijo) { Text(stringResource(R.string.agregar_pago_o_cobro_fijo)) }
        }
    }
}

@Composable
private fun Grupo(
    titulo: String,
    eventos: List<EventoFuturo>,
    linea: LineaDeTiempo,
    estado: EstadoLineaDeTiempo,
    acciones: AccionesLineaDeTiempo,
) {
    if (eventos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
        )
        TarjetaFinanzas(Modifier.fillMaxWidth()) {
            eventos.forEachIndexed { indice, evento ->
                if (indice > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FilaEvento(evento, linea, estado, acciones)
            }
        }
    }
}

@Composable
private fun FilaEvento(evento: EventoFuturo, linea: LineaDeTiempo, estado: EstadoLineaDeTiempo, acciones: AccionesLineaDeTiempo) {
    val titulo = when (evento.tipo) {
        TipoEvento.PAGO_TARJETA -> stringResource(R.string.evento_pago_tarjeta, evento.nombre)
        TipoEvento.CORTE -> stringResource(R.string.evento_corte, evento.nombre)
        TipoEvento.PAGO_FIJO, TipoEvento.INGRESO_ESPERADO -> evento.nombre
    }
    // "aproximado" no va por fila: casi todo lo sería; la explicación de arriba lo dice una vez.
    val etiqueta = when {
        evento.vencido -> stringResource(R.string.etiqueta_vencido)
        evento.tipo == TipoEvento.PAGO_TARJETA && !evento.exacto -> stringResource(R.string.etiqueta_estimado)
        else -> null
    }
    val detalle = listOfNotNull(
        evento.fecha.formatoDiaCorto(linea.hoy),
        etiqueta,
        stringResource(R.string.queda_saldo, evento.saldoDespues.formatear()).takeIf { evento.tipo != TipoEvento.CORTE },
    ).joinToString(" · ")
    val colorDetalle = if (evento.vencido || evento.saldoDespues.esNegativo) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Tocar un pago fijo o un ingreso abre su registro; tocar un pago de tarjeta abre el pago.
    val recurrente = evento.recurrente
    val tarjeta = evento.tarjeta
    val alTocar: (() -> Unit)? = when {
        recurrente != null -> { { acciones.tocarFijo(recurrente) } }
        evento.tipo == TipoEvento.PAGO_TARJETA && tarjeta != null -> { { acciones.tocarTarjeta(tarjeta) } }
        else -> null
    }
    // clickable ya une la fila para TalkBack; sin acción se une a mano.
    val modificador = alTocar?.let { Modifier.clickable(onClick = it) } ?: Modifier.semantics(mergeDescendants = true) {}

    ListItem(
        modifier = modificador,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconoEnCirculo(icono(evento, estado)) },
        headlineContent = { Text(titulo, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(detalle, color = colorDetalle) },
        trailingContent = { Monto(evento) },
    )
}

/**
 * Montos según diseno-finanzas, sección 4: pago fijo con "−" y color de gasto; ingreso
 * con "+" y color de ingreso; pago de tarjeta sin signo y en color secundario, igual que
 * en la lista de movimientos (no es un gasto nuevo: se contó al comprar). El corte no
 * mueve dinero.
 */
@Composable
private fun Monto(evento: EventoFuturo) {
    val (signo, color) = when (evento.tipo) {
        TipoEvento.PAGO_FIJO -> "−" to TemaFinanzas.colores.gasto
        TipoEvento.INGRESO_ESPERADO -> "+" to TemaFinanzas.colores.ingreso
        TipoEvento.PAGO_TARJETA -> "" to MaterialTheme.colorScheme.onSurfaceVariant
        TipoEvento.CORTE -> return
    }
    Text(signo + evento.monto.formatear(), color = color, style = MaterialTheme.typography.titleMedium)
}

@DrawableRes
private fun icono(evento: EventoFuturo, estado: EstadoLineaDeTiempo): Int = when (evento.tipo) {
    TipoEvento.PAGO_TARJETA, TipoEvento.CORTE -> R.drawable.ic_cat_tarjeta
    TipoEvento.PAGO_FIJO, TipoEvento.INGRESO_ESPERADO -> iconoDeCategoria(evento.categoria?.let { estado.categorias[it]?.nombre })
}
