package io.github.joako601.finanzas.ui.metas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.metas.AnalisisMetaAhorro
import io.github.joako601.finanzas.core.domain.metas.EstadoMetaAhorro
import io.github.joako601.finanzas.core.domain.metas.MetaAhorroId
import io.github.joako601.finanzas.core.domain.metas.ResumenMetasAhorro
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.TextoMontoAnimado
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Contenido completo de la sub-pestaña `Ahorro` ("Apartados / Cajitas") dentro de [PantallaMetas].
 */
@Composable
internal fun ContenidoAhorro(
    resumen: ResumenMetasAhorro?,
    onNuevaMeta: (nombre: String, objetivo: Dinero?, meses: Int?) -> Unit,
    onAportar: (AnalisisMetaAhorro) -> Unit,
    onRetirar: (AnalisisMetaAhorro) -> Unit,
    onVerDetalle: (MetaAhorroId) -> Unit,
) {
    var verArchivadas by rememberSaveable { mutableStateOf(false) }

    if (resumen == null || (resumen.metasActivas.isEmpty() && resumen.metasArchivadas.isEmpty())) {
        EstadoVacioAhorro(onNuevaMeta = onNuevaMeta)
        return
    }

    TarjetaResumenAhorro(
        resumen = resumen,
        onNuevaMeta = { onNuevaMeta("", null, null) },
    )

    resumen.metasActivas.forEach { analisis ->
        TarjetaMetaAhorro(
            analisis = analisis,
            onClick = { analisis.meta.id?.let(onVerDetalle) },
            onAportar = { onAportar(analisis) },
            onRetirar = { onRetirar(analisis) },
        )
    }

    if (resumen.metasArchivadas.isNotEmpty()) {
        TextButton(
            onClick = { verArchivadas = !verArchivadas },
        ) {
            Text(
                text = if (verArchivadas) {
                    stringResource(R.string.metas_ahorro_archivadas_ocultar)
                } else {
                    stringResource(R.string.metas_ahorro_archivadas_mostrar, resumen.metasArchivadas.size)
                },
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
        if (verArchivadas) {
            resumen.metasArchivadas.forEach { analisis ->
                TarjetaMetaAhorro(
                    analisis = analisis,
                    onClick = { analisis.meta.id?.let(onVerDetalle) },
                    onAportar = { onAportar(analisis) },
                    onRetirar = { onRetirar(analisis) },
                )
            }
        }
    }
}

@Composable
private fun EstadoVacioAhorro(
    onNuevaMeta: (nombre: String, objetivo: Dinero?, meses: Int?) -> Unit,
) {
    val plantillaEmergencia = stringResource(R.string.metas_plantilla_emergencia)
    val plantillaAnuales = stringResource(R.string.metas_plantilla_anuales)
    val plantillaViaje = stringResource(R.string.metas_plantilla_viaje)

    TarjetaFinanzas(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.metas_ahorro_vacio_titulo),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.metas_ahorro_vacio_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.metas_ahorro_plantillas_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AssistChip(
                        onClick = { onNuevaMeta(plantillaEmergencia, Dinero.pesos(10_000), null) },
                        label = { Text(plantillaEmergencia) },
                    )
                    AssistChip(
                        onClick = { onNuevaMeta(plantillaAnuales, Dinero.pesos(5_000), 6) },
                        label = { Text(plantillaAnuales) },
                    )
                    AssistChip(
                        onClick = { onNuevaMeta(plantillaViaje, Dinero.pesos(6_000), 6) },
                        label = { Text(plantillaViaje) },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            BotonPrincipal(
                texto = stringResource(R.string.metas_ahorro_nueva),
                onClick = { onNuevaMeta("", null, null) },
            )
        }
    }
}

@Composable
private fun TarjetaResumenAhorro(
    resumen: ResumenMetasAhorro,
    onNuevaMeta: () -> Unit,
) {
    val sinActivas = resumen.metasActivas.isEmpty()
    TarjetaFinanzas(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.metas_ahorro_total_apartado),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextoMontoAnimado(
                    monto = resumen.totalApartado,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (sinActivas) {
                Text(
                    text = stringResource(R.string.metas_ahorro_sin_activas),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val conteos = listOfNotNull(
                    if (resumen.cantidadEnCurso > 0) {
                        pluralStringResource(R.plurals.metas_ahorro_en_curso, resumen.cantidadEnCurso, resumen.cantidadEnCurso)
                    } else {
                        null
                    },
                    if (resumen.cantidadCompletadas > 0) {
                        pluralStringResource(
                            R.plurals.metas_ahorro_completadas,
                            resumen.cantidadCompletadas,
                            resumen.cantidadCompletadas,
                        )
                    } else {
                        null
                    },
                ).joinToString(" · ")
                if (conteos.isNotEmpty()) {
                    Text(
                        text = conteos,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (resumen.totalReservadoDeDisponible.esPositivo) {
                Text(
                    text = stringResource(
                        R.string.metas_ahorro_reservado_disponible,
                        resumen.totalReservadoDeDisponible.formatear(),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (resumen.sobreReserva.esPositivo) {
                Text(
                    text = stringResource(R.string.metas_ahorro_sobre_reserva, resumen.sobreReserva.formatear()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = TemaFinanzas.colores.aviso,
                )
            }

            if (sinActivas) {
                BotonPrincipal(
                    texto = stringResource(R.string.metas_ahorro_nueva),
                    onClick = onNuevaMeta,
                )
            } else {
                BotonSecundario(
                    texto = stringResource(R.string.metas_ahorro_nueva),
                    onClick = onNuevaMeta,
                )
            }
        }
    }
}

@Composable
private fun TarjetaMetaAhorro(
    analisis: AnalisisMetaAhorro,
    onClick: () -> Unit,
    onAportar: () -> Unit,
    onRetirar: () -> Unit,
) {
    val meta = analisis.meta
    val etiquetaDetalle = stringResource(R.string.metas_ahorro_ver_detalle)

    TarjetaFinanzas(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(
                role = Role.Button,
                onClickLabel = etiquetaDetalle,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = if (meta.archivada) 20.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = meta.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp),
                )
                InsigniaEstadoMeta(estado = analisis.estado, archivada = meta.archivada)
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = meta.apartado.formatear(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (!meta.archivada && analisis.estado == EstadoMetaAhorro.COMPLETADA) {
                        TemaFinanzas.colores.ingreso
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = stringResource(
                        R.string.metas_ahorro_de_objetivo_pct,
                        meta.objetivo.formatear(),
                        analisis.porcentajeEntero,
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BarraProgresoMeta(
                fraccion = analisis.fraccionAvance,
                estado = analisis.estado,
                porcentajeEntero = analisis.porcentajeEntero,
            )

            Text(
                text = textoSubtituloMeta(analisis),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = if (!meta.archivada && analisis.estado == EstadoMetaAhorro.FECHA_ALCANZADA) {
                    TemaFinanzas.colores.aviso
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            // Las metas archivadas no muestran acciones rápidas para evitar reactivaciones accidentales.
            if (!meta.archivada) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onAportar,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(stringResource(R.string.metas_ahorro_aportar))
                    }
                    if (meta.apartado.esPositivo) {
                        TextButton(
                            onClick = onRetirar,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(stringResource(R.string.metas_ahorro_retirar_usar))
                        }
                    }
                }
            }
        }
    }
}

/** Barra de progreso 1D monocromática / semántica para una meta de ahorro. */
@Composable
internal fun BarraProgresoMeta(
    fraccion: Float,
    estado: EstadoMetaAhorro,
    porcentajeEntero: Int,
    modifier: Modifier = Modifier,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val avance by animateFloatAsState(
        targetValue = fraccion.coerceIn(0f, 1f),
        animationSpec = if (sinAnimaciones) snap() else spring(dampingRatio = 0.90f, stiffness = 420f),
        label = "barraProgresoMeta",
    )
    val colorPista = MaterialTheme.colorScheme.secondaryContainer
    val colorRelleno = when (estado) {
        EstadoMetaAhorro.COMPLETADA -> TemaFinanzas.colores.ingreso
        EstadoMetaAhorro.FECHA_ALCANZADA -> TemaFinanzas.colores.aviso
        EstadoMetaAhorro.EN_CURSO -> MaterialTheme.colorScheme.onSurface
    }
    val descripcion = stringResource(R.string.metas_ahorro_barra_cd, porcentajeEntero)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics { contentDescription = descripcion },
    ) {
        if (size.width <= 0f) return@Canvas
        val radio = size.height / 2f
        val esquina = CornerRadius(radio, radio)
        drawRoundRect(
            color = colorPista,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = esquina,
        )
        if (avance > 0f) {
            val ancho = (size.width * avance).coerceAtLeast(size.height).coerceAtMost(size.width)
            drawRoundRect(
                color = colorRelleno,
                topLeft = Offset.Zero,
                size = Size(ancho, size.height),
                cornerRadius = esquina,
            )
        }
    }
}

/** Insignia de estado sobre `surfaceContainerLowest` + borde 1 dp (WCAG AA >= 4.5:1). */
@Composable
internal fun InsigniaEstadoMeta(
    estado: EstadoMetaAhorro,
    archivada: Boolean = false,
) {
    val (textoRes, colorTexto) = if (archivada) {
        R.string.metas_ahorro_estado_archivada to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        when (estado) {
            EstadoMetaAhorro.COMPLETADA -> R.string.metas_ahorro_estado_completada to TemaFinanzas.colores.ingreso
            EstadoMetaAhorro.FECHA_ALCANZADA -> R.string.metas_ahorro_estado_fecha to TemaFinanzas.colores.aviso
            EstadoMetaAhorro.EN_CURSO -> R.string.metas_ahorro_estado_en_curso to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = stringResource(textoRes),
            style = MaterialTheme.typography.labelSmall,
            color = colorTexto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun textoSubtituloMeta(analisis: AnalisisMetaAhorro): String = when (analisis.estado) {
    EstadoMetaAhorro.COMPLETADA -> if (analisis.excedente.esPositivo) {
        stringResource(R.string.metas_ahorro_meta_alcanzada_extra, analisis.excedente.formatear())
    } else {
        stringResource(R.string.metas_ahorro_meta_alcanzada)
    }
    EstadoMetaAhorro.FECHA_ALCANZADA -> stringResource(
        R.string.metas_ahorro_fecha_alcanzada_falta,
        analisis.faltante.formatear(),
    )
    EstadoMetaAhorro.EN_CURSO -> when {
        analisis.cuotaQuincenal != null -> stringResource(
            R.string.metas_ahorro_falta_y_quincena,
            analisis.faltante.formatear(),
            analisis.cuotaQuincenal!!.formatear(),
        )
        analisis.diasRestantes != null && analisis.diasRestantes!! > 0 -> pluralStringResource(
            R.plurals.metas_ahorro_falta_dias,
            analisis.diasRestantes!!,
            analisis.faltante.formatear(),
            analisis.diasRestantes!!,
        )
        else -> stringResource(R.string.metas_ahorro_solo_falta, analisis.faltante.formatear())
    }
}
