package io.github.joako601.finanzas.ui.disponible

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.disponible.EventoFuturo
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.disponible.TipoEvento
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Encabezado de la tarjeta de saldos: el disponible real, hasta cuándo, cuánto hay en
 * cuentas y cuánto está comprometido, y el aviso si algún día no alcanzaría.
 */
@Composable
fun ResumenDisponible(linea: LineaDeTiempo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Text(textoHorizonte(linea), style = MaterialTheme.typography.bodyMedium)
        val comprometido = linea.comprometido.map { it.monto }.sumar()
        if (comprometido.esPositivo) {
            Text(
                stringResource(R.string.en_cuentas_por_pagar, linea.enCuentas.formatear(), comprometido.formatear()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        linea.primerFaltante?.let { faltante ->
            Text(
                stringResource(R.string.faltante, faltante.fecha.formatoCorto(), faltante.falta.formatear()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun textoHorizonte(linea: LineaDeTiempo): String = linea.ingresoQueCierra?.let {
    stringResource(R.string.disponible_hasta_ingreso, it.nombre, it.fecha.formatoCorto())
} ?: stringResource(R.string.disponible_hasta_fecha, linea.horizonte.formatoCorto())

/**
 * Hoja con lo que pasa en los próximos 30 días, en dos grupos: lo que cuenta para el
 * disponible (hasta el ingreso que cierra el plazo) y lo que viene después.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaLineaDeTiempo(estado: EstadoLineaDeTiempo, onCerrar: () -> Unit) {
    val linea = estado.linea ?: return
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Fondo de pantalla, no de tarjeta: así las tarjetas de cada grupo se distinguen, como en la pantalla principal.
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.proximos_30_dias), style = MaterialTheme.typography.titleLarge)
            ResumenDisponible(linea)
            Text(
                explicacion(linea),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (linea.eventos.isEmpty()) {
                Text(
                    stringResource(R.string.sin_eventos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // El ingreso que cierra el plazo abre el segundo grupo.
            val corte = linea.ingresoQueCierra?.let { linea.eventos.indexOf(it) } ?: linea.eventos.size
            Grupo(
                titulo = linea.ingresoQueCierra?.let { stringResource(R.string.grupo_antes_de_ingreso, it.nombre) }
                    ?: stringResource(R.string.grupo_hasta_fecha, linea.horizonte.formatoCorto()),
                eventos = linea.eventos.subList(0, corte),
                estado = estado,
            )
            Grupo(
                titulo = stringResource(R.string.grupo_despues),
                eventos = linea.eventos.subList(corte, linea.eventos.size),
                estado = estado,
            )
        }
    }
}

@Composable
private fun explicacion(linea: LineaDeTiempo): String {
    val comprometido = linea.comprometido.map { it.monto }.sumar().formatear()
    val enCuentas = linea.enCuentas.formatear()
    return linea.ingresoQueCierra?.let {
        stringResource(R.string.explicacion_disponible_ingreso, enCuentas, it.nombre, it.fecha.formatoCorto(), comprometido)
    } ?: stringResource(R.string.explicacion_disponible_fecha, enCuentas, linea.horizonte.formatoCorto(), comprometido)
}

@Composable
private fun Grupo(titulo: String, eventos: List<EventoFuturo>, estado: EstadoLineaDeTiempo) {
    if (eventos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        TarjetaFinanzas(Modifier.fillMaxWidth()) {
            eventos.forEachIndexed { indice, evento ->
                if (indice > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FilaEvento(evento, estado)
            }
        }
    }
}

@Composable
private fun FilaEvento(evento: EventoFuturo, estado: EstadoLineaDeTiempo) {
    val titulo = when (evento.tipo) {
        TipoEvento.PAGO_TARJETA -> stringResource(R.string.evento_pago_tarjeta, evento.nombre)
        TipoEvento.CORTE -> stringResource(R.string.evento_corte, evento.nombre)
        TipoEvento.PAGO_FIJO, TipoEvento.INGRESO_ESPERADO -> evento.nombre
    }
    val etiqueta = when {
        evento.vencido -> stringResource(R.string.etiqueta_vencido)
        evento.exacto -> null
        evento.tipo == TipoEvento.PAGO_TARJETA -> stringResource(R.string.etiqueta_estimado)
        else -> stringResource(R.string.etiqueta_aproximado)
    }
    val detalle = listOfNotNull(
        evento.fecha.formatoCorto(),
        etiqueta,
        stringResource(R.string.queda_saldo, evento.saldoDespues.formatear()).takeIf { evento.tipo != TipoEvento.CORTE },
    ).joinToString(" · ")
    val colorDetalle = if (evento.vencido || evento.saldoDespues.esNegativo) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconoEnCirculo(icono(evento, estado)) },
        headlineContent = { Text(titulo, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(detalle, color = colorDetalle) },
        trailingContent = { Monto(evento) },
    )
}

/**
 * Pago fijo con "−" y color de gasto; ingreso con "+" y color de ingreso. El pago de
 * tarjeta saca dinero del débito, pero no es un gasto nuevo (ya se contó al comprar):
 * lleva "−" en color neutro. El corte no mueve dinero.
 */
@Composable
private fun Monto(evento: EventoFuturo) {
    val (signo, color) = when (evento.tipo) {
        TipoEvento.PAGO_FIJO -> "−" to TemaFinanzas.colores.gasto
        TipoEvento.INGRESO_ESPERADO -> "+" to TemaFinanzas.colores.ingreso
        TipoEvento.PAGO_TARJETA -> "−" to MaterialTheme.colorScheme.onSurface
        TipoEvento.CORTE -> return
    }
    Text(signo + evento.monto.formatear(), color = color, style = MaterialTheme.typography.titleMedium)
}

@DrawableRes
private fun icono(evento: EventoFuturo, estado: EstadoLineaDeTiempo): Int = when (evento.tipo) {
    TipoEvento.PAGO_TARJETA, TipoEvento.CORTE -> R.drawable.ic_cat_tarjeta
    TipoEvento.PAGO_FIJO, TipoEvento.INGRESO_ESPERADO -> iconoDeCategoria(evento.categoria?.let { estado.categorias[it]?.nombre })
}
