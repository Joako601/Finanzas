package io.github.joako601.finanzas.ui.disponible

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.ui.componentes.enPantalla
import io.github.joako601.finanzas.ui.componentes.fundidoAlOcultar
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.disponible.EventoFuturo
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.disponible.TipoEvento
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.metas.DesgloseDisponibleMetas
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.TextoMontoAnimado
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.formatoDiaCorto
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.launch

/**
 * El disponible real con su cuenta a la vista: cuánto hay en cuentas, cuánto se aparta
 * para pagos, qué día llega el saldo más bajo y, si algún día no alcanzaría, el aviso.
 * TalkBack lo lee como un solo bloque.
 */
@Composable
fun ResumenDisponible(
    linea: LineaDeTiempo,
    desgloseMetas: DesgloseDisponibleMetas? = null,
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.disponible_real),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextoMontoAnimado(
            monto = linea.disponible,
            style = MaterialTheme.typography.headlineLarge,
            color = if (linea.disponible.esNegativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        val secundario = MaterialTheme.colorScheme.onSurfaceVariant
        val bajo = linea.puntoMasBajo
        val faltante = linea.primerFaltante
        when {
            // En negativo, el aviso de faltante lo dice todo; la cuenta de lo apartado confundiría.
            faltante != null -> {
                Text(
                    when {
                        faltante.fecha != linea.hoy ->
                            stringResource(R.string.faltante, faltante.fecha.formatoDiaCorto(linea.hoy), faltante.falta.enPantalla())
                        linea.enCuentas.esNegativo && -linea.enCuentas == faltante.falta ->
                            stringResource(R.string.faltante_hoy, faltante.falta.enPantalla())
                        else -> stringResource(R.string.faltante_hoy_vencidos, faltante.falta.enPantalla())
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fundidoAlOcultar(),
                )
                // Si más adelante falta más, se dice cuándo: así cuadra con la cifra de arriba.
                linea.faltanteMayor?.let { mayor ->
                    Text(
                        stringResource(R.string.faltante_mayor, mayor.fecha.formatoDiaCorto(linea.hoy), mayor.falta.enPantalla()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fundidoAlOcultar(),
                    )
                }
            }
            bajo != null -> {
                Text(
                    stringResource(R.string.en_cuentas_para_pagos, linea.enCuentas.enPantalla(), linea.comprometido.enPantalla()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = secundario,
                    modifier = Modifier.fundidoAlOcultar(),
                )
                Text(
                    stringResource(R.string.saldo_mas_bajo, (linea.diaMasBajo ?: bajo.fecha).formatoDiaCorto(linea.hoy)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = secundario,
                )
            }
            else -> Text(
                stringResource(
                    if (linea.eventos.any { it.esSalida }) R.string.ingresos_cubren_pagos else R.string.sin_pagos_30_dias,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = secundario,
            )
        }

        // Capa informativa de apartados en metas: solo aparece si el usuario tiene metas reservadas.
        if (desgloseMetas != null && desgloseMetas.hayApartados) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.disponible_apartado_en_metas),
                    style = MaterialTheme.typography.bodySmall,
                    color = secundario,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = desgloseMetas.apartadoEnMetas.enPantalla(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fundidoAlOcultar(),
                )
            }
            when {
                !linea.disponible.esPositivo -> {
                    val hayPagos = linea.comprometido.esPositivo || linea.eventos.any { it.esSalida }
                    Text(
                        text = stringResource(
                            if (hayPagos) {
                                R.string.disponible_apartados_sin_respaldo
                            } else {
                                R.string.disponible_apartados_sin_saldo
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TemaFinanzas.colores.aviso,
                    )
                }
                desgloseMetas.excesoApartados.esPositivo -> {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.disponible_libre_para_gastar),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = Dinero.CERO.enPantalla(),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            color = TemaFinanzas.colores.aviso,
                            modifier = Modifier.fundidoAlOcultar(),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.disponible_apartados_exceden,
                            desgloseMetas.excesoApartados.enPantalla(),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = TemaFinanzas.colores.aviso,
                        modifier = Modifier.fundidoAlOcultar(),
                    )
                }
                else -> {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.disponible_libre_para_gastar),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = desgloseMetas.disponibleLibre.enPantalla(),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fundidoAlOcultar(),
                        )
                    }
                }
            }
        }
    }
}

/** Qué hacer al tocar algo de la línea de tiempo. */
class AccionesLineaDeTiempo(
    val tocarFijo: (RecurrenteId) -> Unit,
    /** La tarjeta y el monto que se sugiere pagar. */
    val tocarTarjeta: (CuentaId, Dinero) -> Unit,
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
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    // La hoja baja con su animación y después se abre el diálogo, en lugar de desaparecer de golpe.
    fun alCerrar(accion: () -> Unit) {
        alcance.launch { estadoHoja.hide() }.invokeOnCompletion { accion() }
    }
    val conAnimacion = AccionesLineaDeTiempo(
        tocarFijo = { id -> alCerrar { acciones.tocarFijo(id) } },
        tocarTarjeta = { id, monto -> alCerrar { acciones.tocarTarjeta(id, monto) } },
        agregarFijo = { alCerrar(acciones.agregarFijo) },
        cerrar = acciones.cerrar,
    )
    ModalBottomSheet(
        onDismissRequest = acciones.cerrar,
        sheetState = estadoHoja,
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
                Column(Modifier.padding(20.dp)) { ResumenDisponible(linea, estado.desgloseMetas) }
            }
            Text(
                stringResource(R.string.explicacion_disponible),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val metasReservadas = estado.desgloseMetas?.metasReservadas.orEmpty()
            if (metasReservadas.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.disponible_seccion_apartados),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 8.dp)
                            .semantics { heading() },
                    )
                    TarjetaFinanzas(Modifier.fillMaxWidth()) {
                        metasReservadas.forEachIndexed { indice, meta ->
                            if (indice > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .semantics(mergeDescendants = true) {},
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = meta.nombre,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Text(
                                    text = meta.apartado.enPantalla(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fundidoAlOcultar(),
                                )
                            }
                        }
                    }
                }
            }

            if (linea.eventos.isEmpty()) {
                Text(
                    stringResource(R.string.sin_eventos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val pendientes = remember(estado.proximasRecurrentes, linea) { estado.pendientes(linea) }
                val bajo = linea.puntoMasBajo
                // Hasta el saldo más bajo, inclusive: esos movimientos fijan el disponible.
                val corte = bajo?.let { linea.eventos.indexOf(it) + 1 } ?: 0
                if (bajo != null) {
                    Grupo(
                        titulo = stringResource(R.string.grupo_definen, (linea.diaMasBajo ?: bajo.fecha).formatoDiaCorto(linea.hoy)),
                        eventos = linea.eventos.subList(0, corte),
                        linea = linea,
                        estado = estado,
                        pendientes = pendientes,
                        acciones = conAnimacion,
                    )
                }
                Grupo(
                    // Sin fecha: el mismo día puede caer en los dos grupos (la renta antes que la quincena).
                    titulo = stringResource(if (bajo != null) R.string.grupo_despues else R.string.grupo_lo_que_viene),
                    eventos = linea.eventos.subList(corte, linea.eventos.size),
                    linea = linea,
                    estado = estado,
                    pendientes = pendientes,
                    acciones = conAnimacion,
                )
            }
            TextButton(onClick = conAnimacion.agregarFijo) { Text(stringResource(R.string.agregar_pago_o_cobro_fijo)) }
        }
    }
}

@Composable
private fun Grupo(
    titulo: String,
    eventos: List<EventoFuturo>,
    linea: LineaDeTiempo,
    estado: EstadoLineaDeTiempo,
    pendientes: Set<EventoFuturo>,
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
                FilaEvento(evento, linea, estado, pendientes, acciones)
            }
        }
    }
}

@Composable
private fun FilaEvento(
    evento: EventoFuturo,
    linea: LineaDeTiempo,
    estado: EstadoLineaDeTiempo,
    pendientes: Set<EventoFuturo>,
    acciones: AccionesLineaDeTiempo,
) {
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
        when {
            evento.tipo == TipoEvento.CORTE -> null
            // Domiciliado a tarjeta: el saldo no cambia; decirlo evita pensar que falta restarlo.
            evento.tipo == TipoEvento.PAGO_FIJO && !evento.esSalida -> stringResource(R.string.cargo_a_tarjeta)
            else -> stringResource(R.string.queda_saldo, evento.saldoDespues.enPantalla())
        },
    ).joinToString(" · ")
    val colorDetalle = if (evento.vencido || evento.saldoDespues.esNegativo) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Tocar un pago fijo o un ingreso abre su registro; tocar un pago de tarjeta abre el pago.
    // Solo la fecha pendiente real de cada pago fijo abre el registro: es la que se registra.
    val recurrente = evento.recurrente?.takeIf { evento in pendientes }
    val tarjeta = evento.tarjeta
    val etiquetaAccion = stringResource(if (recurrente != null) R.string.accion_registrar else R.string.accion_pagar)
    val alTocar: (() -> Unit)? = when {
        recurrente != null -> { { acciones.tocarFijo(recurrente) } }
        evento.tipo == TipoEvento.PAGO_TARJETA && tarjeta != null -> { { acciones.tocarTarjeta(tarjeta, evento.monto) } }
        else -> null
    }
    // clickable ya une la fila para TalkBack y anuncia la acción; sin acción se une a mano.
    val modificador = alTocar?.let { Modifier.clickable(onClickLabel = etiquetaAccion, onClick = it) }
        ?: Modifier.semantics(mergeDescendants = true) {}

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
    Text(signo + evento.monto.enPantalla(), color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fundidoAlOcultar())
}

@DrawableRes
private fun icono(evento: EventoFuturo, estado: EstadoLineaDeTiempo): Int = when (evento.tipo) {
    TipoEvento.PAGO_TARJETA, TipoEvento.CORTE -> R.drawable.ic_cat_tarjeta
    TipoEvento.PAGO_FIJO, TipoEvento.INGRESO_ESPERADO -> iconoDeCategoria(evento.categoria?.let { estado.categorias[it] })
}

/** Solo los eventos que coinciden con la fecha pendiente real ([Recurrente.proxima]) abren el registro. */
private fun EstadoLineaDeTiempo.pendientes(linea: LineaDeTiempo): Set<EventoFuturo> =
    linea.eventos.filterTo(mutableSetOf()) { evento ->
        val id = evento.recurrente ?: return@filterTo false
        proximasRecurrentes[id] == evento.fecha
    }
