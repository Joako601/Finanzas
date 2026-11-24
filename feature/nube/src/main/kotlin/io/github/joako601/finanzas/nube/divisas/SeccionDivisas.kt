package io.github.joako601.finanzas.nube.divisas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.ChipEstadoServidor
import io.github.joako601.finanzas.nube.componentes.EstadoConexionServidor
import io.github.joako601.finanzas.nube.componentes.toqueTactil

/**
 * Contenido de la pestaña de Divisas: cotizaciones vigentes (USD, EUR, CAD -> MXN)
 * y conversor rápido conectado en vivo al backend.
 */
@Composable
internal fun SeccionDivisas(
    estado: EstadoUiDivisas,
    conectadoAlServidor: Boolean,
    margenes: PaddingValues,
    onReintentar: () -> Unit,
    onCerrarSesion: () -> Unit,
    onCambiarMonto: (String) -> Unit,
    onSeleccionarPar: (ParDivisas) -> Unit,
    onConvertir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = margenes.calculateStartPadding(layoutDirection) + 20.dp,
            top = 12.dp,
            end = margenes.calculateEndPadding(layoutDirection) + 20.dp,
            bottom = margenes.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "encabezado-divisas") {
            val estadoConexion = if (!conectadoAlServidor) {
                EstadoConexionServidor.DEMO
            } else if (estado.refrescando) {
                EstadoConexionServidor.CONECTANDO
            } else {
                when (val listaActual = estado.lista) {
                    EstadoListaDivisas.Cargando -> EstadoConexionServidor.CONECTANDO
                    is EstadoListaDivisas.Contenido -> EstadoConexionServidor.CONECTADO
                    is EstadoListaDivisas.Error -> when (listaActual.tipo) {
                        ErrorDivisas.SIN_CONEXION -> EstadoConexionServidor.SIN_CONEXION
                        ErrorDivisas.SESION_EXPIRADA -> EstadoConexionServidor.SESION_EXPIRADA
                        ErrorDivisas.ERROR_SERVIDOR -> EstadoConexionServidor.ERROR_SERVIDOR
                    }
                }
            }
            EncabezadoDivisas(
                estadoConexion = estadoConexion,
                cargando = estado.lista is EstadoListaDivisas.Cargando || estado.refrescando,
                onActualizar = onReintentar,
            )
        }

        when (val lista = estado.lista) {
            EstadoListaDivisas.Cargando -> item(key = "cargando-divisas") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            text = stringResource(R.string.divisas_cargando),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is EstadoListaDivisas.Error -> item(key = "error-divisas") {
                TarjetaErrorDivisas(
                    tipo = lista.tipo,
                    onReintentar = onReintentar,
                    onCerrarSesion = onCerrarSesion,
                )
            }

            is EstadoListaDivisas.Contenido -> {
                items(
                    items = lista.cotizaciones,
                    key = { "${it.monedaBase}-${it.monedaDestino}" },
                ) { cotizacion ->
                    TarjetaTipoCambio(cotizacion = cotizacion)
                }
            }
        }

        item(key = "conversor-divisas") {
            TarjetaConversorDivisas(
                montoTexto = estado.montoTexto,
                parSeleccionado = estado.parSeleccionado,
                estadoConversion = estado.conversion,
                onCambiarMonto = onCambiarMonto,
                onSeleccionarPar = onSeleccionarPar,
                onConvertir = onConvertir,
                onCerrarSesion = onCerrarSesion,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncabezadoDivisas(
    estadoConexion: EstadoConexionServidor,
    cargando: Boolean,
    onActualizar: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.divisas_titulo_cotizaciones),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                ChipEstadoServidor(estado = estadoConexion)
            }
            Text(
                text = stringResource(R.string.divisas_subtitulo_cotizaciones),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onActualizar,
                enabled = !cargando,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.divisas_actualizar))
            }
        }
    }
}

@Composable
private fun TarjetaTipoCambio(cotizacion: TipoCambio) {
    val tasaFormateada = remember(cotizacion.tasa) { formatearTasaDivisa(cotizacion.tasa) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.divisas_unidad_equivalencia, cotizacion.monedaBase),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(
                            if (cotizacion.desdeCache) R.string.divisas_etiqueta_cache else R.string.divisas_etiqueta_en_vivo,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = "$$tasaFormateada ${cotizacion.monedaDestino}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.divisas_meta_fuente, cotizacion.fuente, cotizacion.fecha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TarjetaErrorDivisas(
    tipo: ErrorDivisas,
    onReintentar: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    val (tituloRes, textoRes) = when (tipo) {
        ErrorDivisas.SIN_CONEXION ->
            R.string.divisas_error_sin_conexion_titulo to R.string.divisas_error_sin_conexion_texto
        ErrorDivisas.SESION_EXPIRADA ->
            R.string.divisas_error_sesion_titulo to R.string.divisas_error_sesion_texto
        ErrorDivisas.ERROR_SERVIDOR ->
            R.string.divisas_error_servidor_titulo to R.string.divisas_error_servidor_texto
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(tituloRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(textoRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val interaccionPrimaria = remember { MutableInteractionSource() }
            val esSesionExpirada = tipo == ErrorDivisas.SESION_EXPIRADA
            Button(
                onClick = if (esSesionExpirada) onCerrarSesion else onReintentar,
                interactionSource = interaccionPrimaria,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toqueTactil(interaccionPrimaria),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (esSesionExpirada) R.string.nube_cerrar_sesion else R.string.catalogo_reintentar,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaConversorDivisas(
    montoTexto: String,
    parSeleccionado: ParDivisas,
    estadoConversion: EstadoConversion,
    onCambiarMonto: (String) -> Unit,
    onSeleccionarPar: (ParDivisas) -> Unit,
    onConvertir: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    val convirtiendo = estadoConversion is EstadoConversion.Convirtiendo
    val errorMonto = estadoConversion is EstadoConversion.Error &&
        estadoConversion.tipo == ErrorConversion.MONTO_INVALIDO

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.divisas_conversor_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PARES_CONVERSOR_DISPONIBLES.forEach { par ->
                    FilterChip(
                        selected = par == parSeleccionado,
                        onClick = { onSeleccionarPar(par) },
                        modifier = Modifier.semantics { contentDescription = par.descripcionAccesible },
                        label = { Text(text = par.etiqueta) },
                    )
                }
            }

            OutlinedTextField(
                value = montoTexto,
                onValueChange = onCambiarMonto,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMonto,
                label = { Text(stringResource(R.string.divisas_conversor_monto_etiqueta)) },
                suffix = { Text(parSeleccionado.origen) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!convirtiendo) onConvertir()
                    },
                ),
            )

            val interaccionConvertir = remember { MutableInteractionSource() }
            Button(
                onClick = onConvertir,
                enabled = !convirtiendo,
                interactionSource = interaccionConvertir,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toqueTactil(interaccionConvertir),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (convirtiendo) R.string.divisas_conversor_convirtiendo else R.string.divisas_conversor_boton,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            when (estadoConversion) {
                EstadoConversion.Inactivo, EstadoConversion.Convirtiendo -> Unit
                is EstadoConversion.Error -> {
                    val errorRes = when (estadoConversion.tipo) {
                        ErrorConversion.MONTO_INVALIDO -> R.string.divisas_error_monto
                        ErrorConversion.SIN_CONEXION -> R.string.divisas_error_sin_conexion_texto
                        ErrorConversion.SESION_EXPIRADA -> R.string.divisas_error_sesion_texto
                        ErrorConversion.ERROR_SERVIDOR -> R.string.divisas_error_servidor_texto
                    }
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (estadoConversion.tipo == ErrorConversion.SESION_EXPIRADA) {
                        OutlinedButton(
                            onClick = onCerrarSesion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(text = stringResource(R.string.nube_cerrar_sesion))
                        }
                    }
                }
                is EstadoConversion.Resultado -> {
                    val c = estadoConversion.conversion
                    val origenTexto = remember(c.montoOrigen, c.monedaOrigen) {
                        formatearMontoDivisa(c.montoOrigen, c.monedaOrigen)
                    }
                    val destinoTexto = remember(c.montoDestino, c.monedaDestino) {
                        formatearMontoDivisa(c.montoDestino, c.monedaDestino)
                    }
                    val tasaTexto = remember(c.tasa) { formatearTasaDivisa(c.tasa) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "$origenTexto =",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = destinoTexto,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = stringResource(
                                    R.string.divisas_conversor_resultado_tasa,
                                    tasaTexto,
                                    c.fuente,
                                    c.fechaTasa,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
