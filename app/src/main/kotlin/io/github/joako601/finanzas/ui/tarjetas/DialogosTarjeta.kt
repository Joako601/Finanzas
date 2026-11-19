package io.github.joako601.finanzas.ui.tarjetas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorEstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.ErrorTarjeta
import io.github.joako601.finanzas.ui.componentes.SelectorFecha

/** Solo caracteres que pueden formar un monto. */
private fun limpiarMonto(texto: String) = texto.filter { it.isDigit() || it == '.' || it == ',' }.take(20)

private fun limpiarDia(texto: String) = texto.filter { it.isDigit() }.take(2)

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

/** Pago a una tarjeta desde efectivo o débito. */
@Composable
fun DialogoPagoTarjeta(
    pago: PagoTarjeta,
    cuentas: List<Cuenta>,
    onEditar: (PagoTarjeta.() -> PagoTarjeta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.pagar_tarjeta, pago.tarjeta.cuenta.nombre)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Campo(
                    valor = pago.montoTexto,
                    onCambio = { v -> onEditar { copy(montoTexto = limpiarMonto(v)) } },
                    etiqueta = stringResource(R.string.monto),
                    prefijo = "$",
                    error = if (ErrorPago.MONTO_INVALIDO in pago.errores) stringResource(R.string.error_monto_pago) else null,
                    teclado = TECLADO_MONTO,
                )
                Text(stringResource(R.string.desde), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cuentas.forEach { cuenta ->
                        FilterChip(
                            selected = pago.desdeId == cuenta.id,
                            onClick = { onEditar { copy(desdeId = cuenta.id) } },
                            label = { Text(cuenta.nombre) },
                        )
                    }
                }
                if (ErrorPago.SIN_CUENTA in pago.errores) {
                    Text(stringResource(R.string.error_sin_cuenta_pago), color = MaterialTheme.colorScheme.error)
                }
                SelectorFecha(fecha = pago.fecha, onCambiar = { f -> onEditar { copy(fecha = f) } })
                ErrorAlGuardar(pago.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !pago.guardando) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Captura de los datos impresos en el estado de cuenta (D-011). */
@Composable
fun DialogoEstadoDeCuenta(
    captura: CapturaEstadoDeCuenta,
    onEditar: (CapturaEstadoDeCuenta.() -> CapturaEstadoDeCuenta) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
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
                    error = if (ErrorEstadoDeCuenta.PAGO_MINIMO_INVALIDO in captura.errores) {
                        stringResource(R.string.error_pago_invalido)
                    } else {
                        null
                    },
                    teclado = TECLADO_MONTO.copy(imeAction = ImeAction.Done),
                )
                ErrorAlGuardar(captura.errorAlGuardar)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !captura.guardando) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

private val TECLADO_MONTO = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
private val TECLADO_DIA = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

@Composable
private fun Campo(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    error: String?,
    teclado: KeyboardOptions,
    prefijo: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        prefix = prefijo?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = teclado,
    )
}

@Composable
private fun ErrorAlGuardar(hayError: Boolean) {
    if (hayError) Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
}
