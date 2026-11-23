package io.github.joako601.finanzas.ui.tarjetas

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.tarjetas.AnalisisTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.NivelUtilizacionLinea
import io.github.joako601.finanzas.core.domain.tarjetas.ProgresoPagoCiclo
import io.github.joako601.finanzas.core.domain.tarjetas.SituacionTarjeta
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.toqueTactil
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/** Días para la fecha límite a partir de los cuales el aviso se pinta de color de error. */
private const val DIAS_URGENTE = 3

/** Degradado monocromático del "plástico" de la tarjeta ("Tinta & Papel" / Apple Card oscura). */
private val DegradadoTarjeta = Brush.linearGradient(listOf(Color(0xFF18181B), Color(0xFF2C2C2E)))
private val BordePlasticoTarjeta = Color(0xFF3A3A3C)
private val TextoSobreTarjeta = Color.White
private val TextoSecundarioPlastico = Color(0xFFD1D1D6)
private val AvisoSobrePlastico = Color(0xFFFFB340)
private val ErrorSobrePlastico = Color(0xFFFF6961)

/** Encabezado y lista de tarjetas en la pantalla principal. */
@Composable
fun SeccionTarjetas(
    estado: EstadoTarjetas,
    onAgregar: () -> Unit,
    onPagar: (ResumenTarjeta) -> Unit,
    onCapturar: (ResumenTarjeta) -> Unit,
    onVerDetalle: (ResumenTarjeta) -> Unit = {},
) {
    // Sin margen lateral propio: el carrusel ocupa todo el ancho para que se asome la tarjeta siguiente.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    resumen = resumen,
                    onPagar = { onPagar(resumen) },
                    onCapturar = { onCapturar(resumen) },
                    onVerDetalle = { onVerDetalle(resumen) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            else -> CarruselTarjetas(
                tarjetas = estado.tarjetas,
                onPagar = onPagar,
                onCapturar = onCapturar,
                onVerDetalle = onVerDetalle,
            )
        }
    }
}

/** Etiqueta para encontrar el carrusel en las pruebas. */
const val ETIQUETA_CARRUSEL = "carrusel_tarjetas"

/** Etiquetas de una tarjeta y de su plástico superior, para medirlas en las pruebas. */
fun etiquetaTarjeta(resumen: ResumenTarjeta) = "tarjeta_${resumen.tarjeta.id.valor}"
fun etiquetaPlastico(resumen: ResumenTarjeta) = "plastico_${resumen.tarjeta.id.valor}"

/** Margen del carrusel: a la izquierda alineado con la pantalla; a la derecha se asoma la siguiente. */
private val MargenInicioCarrusel = 16.dp
private val MargenFinCarrusel = 40.dp

/**
 * Las tarjetas una al lado de otra: se desliza de izquierda a derecha. La siguiente se
 * asoma por la derecha para que se note que hay más, y los círculos de abajo dicen cuál
 * se ve y cuántas hay.
 *
 * Todas miden lo mismo aunque su texto no: antes de dibujar se mide el plástico superior y
 * el cuerpo de abajo de cada una, al ancho real de una página, y a todas se les da el alto de la
 * más alta. Así no cambia el tamaño al deslizar, con cualquier texto o tamaño de letra.
 */
@Composable
private fun CarruselTarjetas(
    tarjetas: List<ResumenTarjeta>,
    onPagar: (ResumenTarjeta) -> Unit,
    onCapturar: (ResumenTarjeta) -> Unit,
    onVerDetalle: (ResumenTarjeta) -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { restricciones ->
        val anchoPagina = (restricciones.maxWidth - (MargenInicioCarrusel + MargenFinCarrusel).roundToPx()).coerceAtLeast(0)
        val medida = Constraints(minWidth = anchoPagina, maxWidth = anchoPagina)
        // Copias solo para medir: nunca se colocan y no aportan nada a TalkBack.
        val altoPlastico = subcompose("medir_plastico") {
            tarjetas.forEach {
                PlasticoTarjeta(
                    resumen = it,
                    alto = null,
                    onVerDetalle = null,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }.maxOf { it.measure(medida).height }
        val altoCuerpo = subcompose("medir_cuerpo") {
            tarjetas.forEach {
                CuerpoTarjeta(
                    resumen = it,
                    onPagar = {},
                    onCapturar = {},
                    alto = null,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }.maxOf { it.measure(medida).height }

        val contenido = subcompose("carrusel") {
            CarruselIgualado(
                tarjetas = tarjetas,
                onPagar = onPagar,
                onCapturar = onCapturar,
                onVerDetalle = onVerDetalle,
                altoPlastico = altoPlastico.toDp(),
                altoCuerpo = altoCuerpo.toDp(),
            )
        }.map { it.measure(restricciones.copy(minHeight = 0)) }
        layout(restricciones.maxWidth, contenido.maxOf { it.height }) {
            contenido.forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun CarruselIgualado(
    tarjetas: List<ResumenTarjeta>,
    onPagar: (ResumenTarjeta) -> Unit,
    onCapturar: (ResumenTarjeta) -> Unit,
    onVerDetalle: (ResumenTarjeta) -> Unit,
    altoPlastico: Dp,
    altoCuerpo: Dp,
) {
    val estadoCarrusel = rememberPagerState { tarjetas.size }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(
            state = estadoCarrusel,
            modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_CARRUSEL),
            contentPadding = PaddingValues(start = MargenInicioCarrusel, end = MargenFinCarrusel),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            key = { tarjetas[it].tarjeta.id.valor },
        ) { pagina ->
            val resumen = tarjetas[pagina]
            TarjetaCredito(
                resumen = resumen,
                onPagar = { onPagar(resumen) },
                onCapturar = { onCapturar(resumen) },
                onVerDetalle = { onVerDetalle(resumen) },
                altoPlastico = altoPlastico,
                altoCuerpo = altoCuerpo,
            )
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
            val color by animateColorAsState(
                if (elegida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "circulo",
            )
            val ancho by animateDpAsState(if (elegida) 20.dp else 8.dp, label = "ancho_circulo")
            Box(Modifier.size(width = ancho, height = 8.dp).clip(CircleShape).background(color))
        }
    }
}

/**
 * Una tarjeta: el "plástico" oscuro arriba y el saldo, la situación y los botones abajo.
 * Con [altoPlastico] y [altoCuerpo] (en el carrusel) cada parte toma ese alto: lo que
 * sobra queda entre el nombre y el disponible, y entre la situación y los botones.
 */
@Composable
private fun TarjetaCredito(
    resumen: ResumenTarjeta,
    onPagar: () -> Unit,
    onCapturar: () -> Unit,
    onVerDetalle: () -> Unit,
    modifier: Modifier = Modifier,
    altoPlastico: Dp? = null,
    altoCuerpo: Dp? = null,
) {
    TarjetaFinanzas(modifier.fillMaxWidth().testTag(etiquetaTarjeta(resumen))) {
        PlasticoTarjeta(
            resumen = resumen,
            alto = altoPlastico,
            onVerDetalle = onVerDetalle,
            modifier = Modifier.testTag(etiquetaPlastico(resumen)),
        )
        CuerpoTarjeta(resumen, onPagar, onCapturar, altoCuerpo)
    }
}

/**
 * "Plástico": nombre, día de corte, medidor 1D de utilización de línea y crédito disponible.
 * Tocar el plástico abre la hoja secundaria con el detalle del ciclo, historial y edición.
 */
@Composable
private fun PlasticoTarjeta(
    resumen: ResumenTarjeta,
    alto: Dp?,
    onVerDetalle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val formaPlastico = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val desglose = resumen.analisis.desglose
    val interaccion = remember { MutableInteractionSource() }

    val colorBarraUso = when (desglose.nivelUtilizacion) {
        NivelUtilizacionLinea.SOBREGIRO -> ErrorSobrePlastico
        NivelUtilizacionLinea.ALTO -> AvisoSobrePlastico
        NivelUtilizacionLinea.MODERADO,
        NivelUtilizacionLinea.SALUDABLE,
        -> TextoSobreTarjeta.copy(alpha = 0.88f)
    }

    val modificadorClick = if (onVerDetalle != null) {
        Modifier
            .clip(formaPlastico)
            .toqueTactil(interaccion)
            .clickable(
                interactionSource = interaccion,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.tarjeta_ver_detalle_cd, resumen.tarjeta.cuenta.nombre),
                onClick = onVerDetalle,
            )
    } else {
        Modifier
    }

    Column(
        modifier
            .fillMaxWidth()
            .then(if (alto != null) Modifier.height(alto) else Modifier)
            .then(modificadorClick)
            .background(DegradadoTarjeta, formaPlastico)
            .border(1.dp, BordePlasticoTarjeta, formaPlastico)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.tarjeta_credito_corte_dia, resumen.tarjeta.diaDeCorte),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = TextoSecundarioPlastico,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_cat_tarjeta),
                contentDescription = null,
                tint = TextoSobreTarjeta,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = resumen.tarjeta.cuenta.nombre,
            style = MaterialTheme.typography.titleLarge,
            color = TextoSobreTarjeta,
        )

        Spacer(Modifier.height(14.dp))
        if (alto != null) Spacer(Modifier.weight(1f))

        // Medidor 1D de utilización de línea de crédito
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(TextoSobreTarjeta.copy(alpha = 0.16f), CircleShape),
            ) {
                if (desglose.proporcionBarraUtilizacion > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = desglose.proporcionBarraUtilizacion)
                            .fillMaxHeight()
                            .background(colorBarraUso, CircleShape),
                    )
                }
            }
            Text(
                text = stringResource(R.string.tarjeta_porcentaje_usado, desglose.porcentajeUtilizacion),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = if (desglose.nivelUtilizacion == NivelUtilizacionLinea.SOBREGIRO) {
                    ErrorSobrePlastico
                } else {
                    TextoSecundarioPlastico
                },
            )
        }

        Spacer(Modifier.height(6.dp))

        val aFavor = resumen.deuda.esNegativo
        val textoDisponible = when {
            desglose.sobregiro.esPositivo -> stringResource(
                R.string.tarjeta_sobregiro_plastico,
                desglose.sobregiro.formatear(),
                resumen.tarjeta.lineaDeCredito.formatear(),
            )
            aFavor -> stringResource(
                R.string.disponible_con_saldo_a_favor,
                desglose.creditoDisponible.formatear(),
                resumen.tarjeta.lineaDeCredito.formatear(),
            )
            else -> stringResource(
                R.string.disponible_de,
                desglose.creditoDisponible.formatear(),
                resumen.tarjeta.lineaDeCredito.formatear(),
            )
        }

        Text(
            text = textoDisponible,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = if (desglose.sobregiro.esPositivo) ErrorSobrePlastico else TextoSobreTarjeta,
        )
    }
}

/** Saldo, desglose por ciclo, situación del corte y botones; los botones van siempre hasta abajo. */
@Composable
private fun CuerpoTarjeta(
    resumen: ResumenTarjeta,
    onPagar: () -> Unit,
    onCapturar: () -> Unit,
    alto: Dp?,
    modifier: Modifier = Modifier,
) {
    val desglose = resumen.analisis.desglose
    val aFavor = resumen.deuda.esNegativo

    Column(
        modifier
            .then(if (alto != null) Modifier.height(alto) else Modifier)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (aFavor) R.string.saldo_a_favor else R.string.debes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = resumen.deuda.absoluto().formatear(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                )
            }

            // Si hay deuda tanto del corte cerrado como de nuevas compras, explicarlo sin ambigüedad.
            if (desglose.exigibleCorteActual.esPositivo && desglose.comprasNuevoCiclo.esPositivo) {
                Text(
                    text = stringResource(
                        R.string.tarjeta_compras_nuevo_ciclo,
                        desglose.comprasNuevoCiclo.formatear(),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LineaSituacion(analisis = resumen.analisis)

        if (alto != null) Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun LineaSituacion(analisis: AnalisisTarjeta) {
    val colores = MaterialTheme.colorScheme
    val vencido = analisis.vencidoPendiente

    val (texto, detalle, color) = when {
        vencido != null -> Triple(
            stringResource(
                R.string.tarjeta_vencido_titulo,
                vencido.estado.fechaLimite.formatoCorto(),
                vencido.restante.formatear(),
            ),
            pluralStringResource(
                R.plurals.tarjeta_vencido_dias,
                vencido.diasVencido,
                vencido.diasVencido,
            ),
            colores.error,
        )
        else -> when (val situacion = analisis.situacion) {
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
            is SituacionTarjeta.Pagado -> Triple(
                stringResource(R.string.situacion_pagado),
                null,
                TemaFinanzas.colores.ingreso,
            )
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
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = color,
            )
            if (detalle != null) {
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = color,
                )
            }
        }

        analisis.progresoCiclo?.let { progreso ->
            BloqueProgresoCiclo(progreso = progreso)
        }
    }
}

@Composable
private fun BloqueProgresoCiclo(progreso: ProgresoPagoCiclo) {
    if (!progreso.tieneAbonoParcial) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progreso.proporcionPagada.coerceIn(0.05f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.tarjeta_abono_parcial,
                    progreso.pagadoEnCiclo.formatear(),
                    (progreso.pagadoEnCiclo + progreso.restanteSinIntereses).formatear(),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (progreso.pagoMinimoOriginal.esPositivo) {
                val (etiquetaMinimo, colorMinimo) = if (progreso.minimoCubierto) {
                    stringResource(R.string.tarjeta_minimo_cubierto_badge) to TemaFinanzas.colores.ingreso
                } else {
                    stringResource(
                        R.string.tarjeta_minimo_pendiente_badge,
                        progreso.restanteMinimo.formatear(),
                    ) to TemaFinanzas.colores.aviso
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = etiquetaMinimo,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colorMinimo,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
