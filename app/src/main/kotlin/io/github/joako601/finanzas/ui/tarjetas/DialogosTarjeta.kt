package io.github.joako601.finanzas.ui.tarjetas

import io.github.joako601.finanzas.core.domain.tarjetas.AjusteInhabil
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.tarjetas.AnalizadorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.CategoriaSimulacionPago
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorEstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorTarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.SimulacionPagoTarjeta
import io.github.joako601.finanzas.ui.componentes.enPantalla
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.ui.componentes.ErrorAlGuardar
import io.github.joako601.finanzas.ui.componentes.SelectorFecha
import io.github.joako601.finanzas.ui.componentes.TECLADO_MONTO
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.limpiarMonto
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.movimientos.ConfirmarArchivar
import io.github.joako601.finanzas.ui.movimientos.textoEditable
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.launch

private fun limpiarDia(texto: String) = texto.filter { it.isDigit() }.take(2)
private val TASAS_REFERENCIA = listOf(45, 60, 85)

/** Alta de tarjeta de crédito. */
@Composable
fun DialogoNuevaTarjeta(
    nueva: NuevaTarjeta,
    onEditar: (NuevaTarjeta.() -> NuevaTarjeta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.nueva_tarjeta)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Campo(
                    valor = nueva.nombre,
                    onCambio = { v -> onEditar { copy(nombre = v.take(Cuenta.LARGO_MAXIMO_NOMBRE)) } },
                    etiqueta = stringResource(R.string.nombre_tarjeta),
                    error = if (ErrorTarjeta.NOMBRE_VACIO in nueva.errores) stringResource(R.string.error_nombre_vacio) else null,
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Campo(
                    valor = nueva.linea,
                    onCambio = { v -> onEditar { copy(linea = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.linea_de_credito),
                    prefijo = "$",
                    error = if (ErrorTarjeta.LINEA_INVALIDA in nueva.errores) stringResource(R.string.error_linea_invalida) else null,
                    teclado = TECLADO_MONTO,
                )
                Campo(
                    valor = nueva.diaCorte,
                    onCambio = { v -> onEditar { copy(diaCorte = limpiarDia(v)) } },
                    etiqueta = stringResource(R.string.dia_de_corte),
                    error = if (ErrorTarjeta.DIA_DE_CORTE_INVALIDO in nueva.errores) stringResource(R.string.error_dia_invalido) else null,
                    teclado = TECLADO_DIA,
                )
                Campo(
                    valor = nueva.diaLimite,
                    onCambio = { v -> onEditar { copy(diaLimite = limpiarDia(v)) } },
                    etiqueta = stringResource(R.string.dia_limite),
                    error = if (ErrorTarjeta.DIA_LIMITE_INVALIDO in nueva.errores) stringResource(R.string.error_dia_invalido) else null,
                    teclado = TECLADO_DIA,
                )
                Campo(
                    valor = nueva.deuda,
                    onCambio = { v -> onEditar { copy(deuda = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.deuda_actual),
                    prefijo = "$",
                    error = if (ErrorTarjeta.DEUDA_INVALIDA in nueva.errores) stringResource(R.string.error_deuda_invalida) else null,
                    teclado = TECLADO_MONTO,
                )
                Text(stringResource(R.string.ayuda_dias_tarjeta), style = MaterialTheme.typography.bodySmall)
                ErrorAlGuardar(nueva.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !nueva.guardando) { Text(stringResource(R.string.crear)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Edición de nombre, línea de crédito y días de corte/límite de una tarjeta existente. */
@Composable
fun DialogoEditarTarjeta(
    edicion: EdicionTarjeta,
    onEditar: (EdicionTarjeta.() -> EdicionTarjeta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
    onPedirArchivar: () -> Unit = {},
    onCancelarArchivar: () -> Unit = {},
    onArchivar: () -> Unit = {},
) {
    if (edicion.confirmandoArchivar) {
        ConfirmarArchivar(
            nombre = edicion.actual.cuenta.nombre,
            ayuda = stringResource(R.string.archivar_tarjeta_ayuda),
            trabajando = edicion.guardando,
            error = edicion.errorAlGuardar,
            onArchivar = onArchivar,
            onCancelar = onCancelarArchivar,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.editar_tarjeta)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Campo(
                    valor = edicion.nombre,
                    onCambio = { v -> onEditar { copy(nombre = v.take(Cuenta.LARGO_MAXIMO_NOMBRE)) } },
                    etiqueta = stringResource(R.string.nombre_tarjeta),
                    error = if (ErrorTarjeta.NOMBRE_VACIO in edicion.errores) stringResource(R.string.error_nombre_vacio) else null,
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Campo(
                    valor = edicion.linea,
                    onCambio = { v -> onEditar { copy(linea = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.linea_de_credito),
                    prefijo = "$",
                    error = if (ErrorTarjeta.LINEA_INVALIDA in edicion.errores) stringResource(R.string.error_linea_invalida) else null,
                    teclado = TECLADO_MONTO,
                )
                Campo(
                    valor = edicion.diaCorte,
                    onCambio = { v -> onEditar { copy(diaCorte = limpiarDia(v)) } },
                    etiqueta = stringResource(R.string.dia_de_corte),
                    error = if (ErrorTarjeta.DIA_DE_CORTE_INVALIDO in edicion.errores) stringResource(R.string.error_dia_invalido) else null,
                    teclado = TECLADO_DIA,
                )
                Campo(
                    valor = edicion.diaLimite,
                    onCambio = { v -> onEditar { copy(diaLimite = limpiarDia(v)) } },
                    etiqueta = stringResource(R.string.dia_limite),
                    error = if (ErrorTarjeta.DIA_LIMITE_INVALIDO in edicion.errores) stringResource(R.string.error_dia_invalido) else null,
                    teclado = TECLADO_DIA.copy(imeAction = ImeAction.Done),
                )
                Text(stringResource(R.string.ayuda_dias_tarjeta), style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = onPedirArchivar,
                    enabled = !edicion.guardando,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.archivar_tarjeta)) }
                ErrorAlGuardar(edicion.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !edicion.guardando) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/**
 * Pago inteligente a una tarjeta desde efectivo o débito:
 * - Píldoras de selección rápida (Sin intereses, Pago mínimo, Liquidar todo).
 * - Simulador en vivo de consecuencia financiera e intereses + 16% IVA.
 * - Saldo disponible visible en cada cuenta origen (`Desde`) con alerta si excede el saldo.
 */
@Composable
fun DialogoPagoTarjeta(
    pago: PagoTarjeta,
    cuentas: List<Cuenta>,
    onEditar: (PagoTarjeta.() -> PagoTarjeta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
    saldosCuentas: Map<CuentaId, Dinero> = emptyMap(),
    onAgregarCuenta: (() -> Unit)? = null,
    onEliminar: (() -> Unit)? = null,
) {
    val analisis = pago.analisis
    val opcionesDesde = cuentas + listOfNotNull(pago.origenArchivado)
    val montoActual = remember(pago.montoTexto) { Dinero.parsear(pago.montoTexto) }
    val simulacion = remember(analisis, montoActual, pago.tasaAnualPorcentaje) {
        if (analisis != null && montoActual != null && montoActual.esPositivo) {
            AnalizadorTarjeta.simularPago(
                analisis = analisis,
                montoAPagar = montoActual,
                tasaAnualPorcentaje = pago.tasaAnualPorcentaje,
            )
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Text(
                stringResource(
                    if (pago.editando != null) R.string.editar_pago_tarjeta else R.string.pagar_tarjeta,
                    pago.tarjeta.cuenta.nombre,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Píldoras de selección rápida de monto (1 toque)
                if (analisis != null) {
                    val exigible = analisis.desglose.exigibleCorteActual
                    val minimo = analisis.progresoCiclo?.restanteMinimo ?: Dinero.CERO
                    val deudaTotal = analisis.desglose.deudaTotal

                    if (exigible.esPositivo || deudaTotal.esPositivo) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (exigible.esPositivo) {
                                FilterChip(
                                    selected = montoActual == exigible,
                                    onClick = { onEditar { copy(montoTexto = exigible.textoEditable()) } },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.pago_rapido_sin_intereses, exigible.formatear()),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        )
                                    },
                                )
                            }
                            if (minimo.esPositivo && minimo < exigible) {
                                FilterChip(
                                    selected = montoActual == minimo,
                                    onClick = { onEditar { copy(montoTexto = minimo.textoEditable()) } },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.pago_rapido_minimo, minimo.formatear()),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        )
                                    },
                                )
                            }
                            if (deudaTotal.esPositivo && deudaTotal != exigible) {
                                FilterChip(
                                    selected = montoActual == deudaTotal,
                                    onClick = { onEditar { copy(montoTexto = deudaTotal.textoEditable()) } },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.pago_rapido_deuda_total, deudaTotal.formatear()),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // 2. Campo de monto
                Campo(
                    valor = pago.montoTexto,
                    onCambio = { v -> onEditar { copy(montoTexto = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.monto),
                    prefijo = "$",
                    error = if (ErrorPago.MONTO_INVALIDO in pago.errores) stringResource(R.string.error_monto_pago) else null,
                    teclado = TECLADO_MONTO,
                )

                // 3. Termómetro de Consecuencia Financiera e Intereses + IVA en vivo
                if (simulacion != null) {
                    BloqueSimulacionIntereses(
                        simulacion = simulacion,
                        onCambiarTasa = { nuevaTasa -> onEditar { copy(tasaAnualPorcentaje = nuevaTasa) } },
                    )
                }

                // 4. Selector de cuenta origen ("Desde") con saldo disponible a la vista
                Text(stringResource(R.string.desde), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    opcionesDesde.forEach { cuenta ->
                        val saldoCuenta = saldosCuentas[cuenta.id]
                        val etiquetaChip = if (saldoCuenta != null) {
                            stringResource(R.string.pago_cuenta_con_saldo, cuenta.nombre, saldoCuenta.formatear())
                        } else {
                            cuenta.nombre
                        }
                        FilterChip(
                            selected = pago.desdeId == cuenta.id,
                            onClick = { onEditar { copy(desdeId = cuenta.id) } },
                            label = {
                                Text(
                                    text = etiquetaChip,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                )
                            },
                        )
                    }
                    if (onAgregarCuenta != null) {
                        FilterChip(
                            selected = false,
                            onClick = onAgregarCuenta,
                            label = { Text(stringResource(R.string.chip_agregar_cuenta)) },
                        )
                    }
                }

                // Aviso preventivo si el monto supera el saldo actual de la cuenta de débito/efectivo elegida
                val cuentaElegida = opcionesDesde.firstOrNull { it.id == pago.desdeId }
                val saldoElegida = cuentaElegida?.let { saldosCuentas[it.id] }
                if (cuentaElegida != null && saldoElegida != null && montoActual != null && montoActual > saldoElegida) {
                    Text(
                        text = stringResource(
                            R.string.pago_aviso_supera_saldo_cuenta,
                            cuentaElegida.nombre,
                            saldoElegida.formatear(),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = TemaFinanzas.colores.aviso,
                    )
                }

                if (ErrorPago.SIN_CUENTA in pago.errores) {
                    Text(stringResource(R.string.error_sin_cuenta_pago), color = MaterialTheme.colorScheme.error)
                }
                SelectorFecha(fecha = pago.fecha, onCambiar = { f -> onEditar { copy(fecha = f) } })
                if (onEliminar != null) {
                    TextButton(
                        onClick = onEliminar,
                        enabled = !pago.guardando,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.eliminar), color = MaterialTheme.colorScheme.error) }
                }
                ErrorAlGuardar(pago.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !pago.guardando) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun BloqueSimulacionIntereses(
    simulacion: SimulacionPagoTarjeta,
    onCambiarTasa: (Int) -> Unit,
) {
    val estiloTabular = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val estiloTitulo = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    )

    val (colorTexto, titulo, detalle) = when (simulacion.categoria) {
        CategoriaSimulacionPago.LIQUIDACION_TOTAL -> Triple(
            TemaFinanzas.colores.ingreso,
            stringResource(R.string.pago_sim_liquidacion_total),
            null,
        )
        CategoriaSimulacionPago.SIN_INTERESES -> Triple(
            TemaFinanzas.colores.ingreso,
            if (simulacion.deudaRestanteTrasPago.esPositivo) {
                stringResource(R.string.pago_sim_sin_intereses, simulacion.deudaRestanteTrasPago.formatear())
            } else {
                stringResource(R.string.pago_sim_liquidacion_total)
            },
            null,
        )
        CategoriaSimulacionPago.SALDO_A_FAVOR -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.pago_sim_saldo_a_favor, simulacion.excedenteSaldoAFavor.formatear()),
            null,
        )
        CategoriaSimulacionPago.SIN_ESTADO_CAPTURADO -> Triple(
            TemaFinanzas.colores.aviso,
            stringResource(R.string.pago_sim_sin_estado_titulo),
            stringResource(R.string.pago_sim_sin_estado_detalle, simulacion.deudaRestanteTrasPago.formatear()),
        )
        CategoriaSimulacionPago.GENERA_INTERESES_ORDINARIOS -> Triple(
            TemaFinanzas.colores.aviso,
            stringResource(R.string.pago_sim_intereses_titulo, simulacion.saldoFinanciado.formatear()),
            stringResource(
                R.string.pago_sim_intereses_detalle,
                simulacion.tasaAnualPorcentaje,
                simulacion.interesSobreSaldoFinanciado.formatear(),
                simulacion.interesSobreSaldoPromedioCorte.formatear(),
            ),
        )
        CategoriaSimulacionPago.DEBAJO_DEL_MINIMO -> Triple(
            MaterialTheme.colorScheme.error,
            stringResource(
                R.string.pago_sim_bajo_minimo_titulo,
                simulacion.faltanteParaMinimo.formatear(),
                (simulacion.montoAPagar + simulacion.faltanteParaMinimo).formatear(),
            ),
            stringResource(
                R.string.pago_sim_bajo_minimo_detalle,
                simulacion.interesSobreSaldoPromedioCorte.formatear(),
            ),
        )
    }

    val mostrarSelectorTasa = simulacion.categoria == CategoriaSimulacionPago.GENERA_INTERESES_ORDINARIOS ||
        simulacion.categoria == CategoriaSimulacionPago.DEBAJO_DEL_MINIMO

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = titulo, style = estiloTitulo, color = colorTexto)
            if (detalle != null) {
                Text(
                    text = detalle,
                    style = estiloTabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mostrarSelectorTasa) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.pago_tasa_anual_etiqueta),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TASAS_REFERENCIA.forEach { tasa ->
                            val activa = simulacion.tasaAnualPorcentaje == tasa
                            Surface(
                                onClick = { onCambiarTasa(tasa) },
                                shape = CircleShape,
                                color = if (activa) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.semantics {
                                    role = Role.RadioButton
                                    selected = activa
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.pago_tasa_opcion, tasa),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Medium,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = if (activa) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Captura de los datos impresos en el estado de cuenta (D-011). */
@Composable
fun DialogoEstadoDeCuenta(
    captura: CapturaEstadoDeCuenta,
    onEditar: (CapturaEstadoDeCuenta.() -> CapturaEstadoDeCuenta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
    onPedirEliminar: () -> Unit = {},
    onCancelarEliminar: () -> Unit = {},
    onEliminar: () -> Unit = {},
) {
    val corteOriginal = captura.fechaCorteOriginal
    if (corteOriginal != null && captura.confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = { if (!captura.guardando) onCancelarEliminar() },
            title = { Text(stringResource(R.string.eliminar_estado_pregunta, corteOriginal.formatoCorto())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.eliminar_estado_ayuda), style = MaterialTheme.typography.bodyMedium)
                    ErrorAlGuardar(captura.errorAlGuardar)
                }
            },
            confirmButton = {
                TextButton(onClick = onEliminar, enabled = !captura.guardando) {
                    Text(stringResource(R.string.eliminar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelarEliminar, enabled = !captura.guardando) { Text(stringResource(R.string.cancelar)) }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.capturar_estado_titulo, captura.tarjeta.cuenta.nombre)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ayuda_estado_de_cuenta), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorFecha(
                        fecha = captura.fechaCorte,
                        onCambiar = { f -> onEditar { copy(fechaCorte = f) } },
                        etiqueta = stringResource(R.string.fecha_de_corte),
                    )
                    SelectorFecha(
                        fecha = captura.fechaLimite,
                        onCambiar = { f -> onEditar { copy(fechaLimite = f) } },
                        etiqueta = stringResource(R.string.fecha_limite),
                    )
                }
                if (ErrorEstadoDeCuenta.LIMITE_NO_POSTERIOR_AL_CORTE in captura.errores) {
                    Text(stringResource(R.string.error_limite_antes_del_corte), color = MaterialTheme.colorScheme.error)
                }
                Campo(
                    valor = captura.pagoSinIntereses,
                    onCambio = { v -> onEditar { copy(pagoSinIntereses = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.pago_sin_intereses),
                    prefijo = "$",
                    error = if (ErrorEstadoDeCuenta.PAGO_SIN_INTERESES_INVALIDO in captura.errores) {
                        stringResource(R.string.error_pago_invalido)
                    } else {
                        null
                    },
                    teclado = TECLADO_MONTO,
                )
                Campo(
                    valor = captura.pagoMinimo,
                    onCambio = { v -> onEditar { copy(pagoMinimo = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.pago_minimo),
                    prefijo = "$",
                    error = when {
                        ErrorEstadoDeCuenta.PAGO_MINIMO_INVALIDO in captura.errores ->
                            stringResource(R.string.error_pago_invalido)
                        ErrorEstadoDeCuenta.MINIMO_MAYOR_A_SIN_INTERESES in captura.errores ->
                            stringResource(R.string.error_minimo_mayor_sin_intereses)
                        else -> null
                    },
                    teclado = TECLADO_MONTO.copy(imeAction = ImeAction.Done),
                )
                if (corteOriginal != null) {
                    TextButton(
                        onClick = onPedirEliminar,
                        enabled = !captura.guardando,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.eliminar_estado), color = MaterialTheme.colorScheme.error) }
                }
                ErrorAlGuardar(captura.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !captura.guardando) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/**
 * Hoja secundaria (Nivel 3) con la radiografía completa de la tarjeta:
 * utilización de línea, fechas clave del ciclo, desglose contable, historial de estados
 * de cuenta capturados, pagos recientes y acción para editar los datos de la tarjeta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDetalleTarjeta(
    resumen: ResumenTarjeta,
    onEditarTarjeta: () -> Unit,
    onCapturarEstado: () -> Unit,
    onPagar: () -> Unit,
    onCerrar: () -> Unit,
    onEditarEstado: (EstadoDeCuenta) -> Unit = {},
    onElegirAjuste: (AjusteInhabil) -> Unit = {},
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    val analisis = resumen.analisis
    val desglose = analisis.desglose

    fun cerrarYEjecutar(accion: () -> Unit) {
        alcance.launch { estadoHoja.hide() }.invokeOnCompletion {
            onCerrar()
            accion()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Encabezado + Botón Editar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resumen.tarjeta.cuenta.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.tarjeta_credito_corte_dia, resumen.tarjeta.diaDeCorte),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { cerrarYEjecutar(onEditarTarjeta) }) {
                    Text(stringResource(R.string.editar_tarjeta))
                }
            }

            // Tarjeta de Ciclo y Desglose Contable
            TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.detalle_tarjeta_ciclo),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    // Barra 1D de utilización
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    ) {
                        if (desglose.proporcionBarraUtilizacion > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = desglose.proporcionBarraUtilizacion)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                            )
                        }
                    }

                    FilaDatoDetalle(
                        etiqueta = stringResource(R.string.detalle_tarjeta_exigible_corte),
                        valor = desglose.exigibleCorteActual.enPantalla(),
                        resaltar = desglose.exigibleCorteActual.esPositivo,
                    )
                    FilaDatoDetalle(
                        etiqueta = stringResource(R.string.detalle_tarjeta_compras_nuevas),
                        valor = desglose.comprasNuevoCiclo.enPantalla(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FilaDatoDetalle(
                        etiqueta = stringResource(R.string.detalle_tarjeta_ultimo_corte),
                        valor = analisis.ultimoCorte.formatoCorto(),
                    )
                    FilaDatoDetalle(
                        etiqueta = stringResource(R.string.detalle_tarjeta_proximo_corte),
                        valor = analisis.proximoCorte.formatoCorto(),
                    )
                }
            }

            resumen.fechaInhabil?.let { BloqueFechaInhabil(it, onElegirAjuste) }
            resumen.contraCetes?.let { BloqueContraCetes(it) }

            // Compras a meses activas (D-022): mensualidades facturadas y cuota.
            if (resumen.compras.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.msi_activas),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    resumen.compras.forEach { compra ->
                        FilaDatoDetalle(
                            etiqueta = stringResource(
                                R.string.msi_fila,
                                compra.movimiento.descripcion.ifBlank { compra.total.enPantalla() },
                                compra.facturadasAl(analisis.ultimoCorte),
                                compra.meses,
                            ),
                            valor = stringResource(R.string.msi_cuota, compra.cuota.enPantalla()),
                        )
                    }
                    FilaDatoDetalle(
                        etiqueta = stringResource(R.string.msi_por_facturar),
                        valor = resumen.compras.map { it.porFacturarAl(analisis.ultimoCorte) }.sumar().enPantalla(),
                    )
                }
            }

            // Acciones rápidas desde la hoja
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonSecundario(
                    texto = stringResource(R.string.estado_de_cuenta),
                    onClick = { cerrarYEjecutar(onCapturarEstado) },
                    modifier = Modifier.weight(1f),
                )
                BotonPrincipal(
                    texto = stringResource(R.string.pagar),
                    onClick = { cerrarYEjecutar(onPagar) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Historial de Estados de Cuenta Capturados
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.detalle_tarjeta_estados_recientes),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (analisis.estadosPropios.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detalle_tarjeta_sin_estados),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    analisis.estadosPropios.take(4).forEach { est ->
                        // Tocar un estado abre su captura para corregirlo o eliminarlo.
                        val etiquetaCorregir = stringResource(R.string.corregir_estado)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClickLabel = etiquetaCorregir) { cerrarYEjecutar { onEditarEstado(est) } },
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.detalle_tarjeta_item_estado,
                                        est.fechaCorte.formatoCorto(),
                                        est.fechaLimite.formatoCorto(),
                                    ),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.detalle_tarjeta_item_estado_montos,
                                        est.pagoSinIntereses.enPantalla(),
                                        est.pagoMinimo.enPantalla(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Historial de Pagos Recientes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.detalle_tarjeta_pagos_recientes),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (analisis.pagosPropios.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detalle_tarjeta_sin_pagos),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    analisis.pagosPropios.take(5).forEach { pagoItem ->
                        FilaDatoDetalle(
                            etiqueta = pagoItem.fecha.formatoCorto(),
                            valor = pagoItem.monto.enPantalla(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaDatoDetalle(
    etiqueta: String,
    valor: String,
    resaltar: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = valor,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (resaltar) FontWeight.Bold else FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val TECLADO_DIA = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
