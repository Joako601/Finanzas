package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.ui.componentes.ErrorAlGuardar
import io.github.joako601.finanzas.ui.componentes.SelectorFecha
import io.github.joako601.finanzas.ui.componentes.TECLADO_MONTO

/** Acciones del diálogo de transferencia. [eliminar] solo existe al editar una guardada. */
class AccionesTransferencia(
    val editar: (Transferencia.() -> Transferencia) -> Unit,
    val guardar: () -> Unit,
    val eliminar: (() -> Unit)?,
    val cerrar: () -> Unit,
)

/** Pasar dinero entre efectivo y débito: retirar del cajero, mover a otra cuenta. */
@Composable
fun DialogoTransferencia(transferencia: Transferencia, cuentas: List<Cuenta>, acciones: AccionesTransferencia) {
    val erroresMonto = setOf(ErrorRegistro.MONTO_VACIO, ErrorRegistro.MONTO_INVALIDO, ErrorRegistro.MONTO_CERO)
    AlertDialog(
        onDismissRequest = acciones.cerrar,
        title = { Text(stringResource(R.string.transferencia)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(
                    valor = transferencia.montoTexto,
                    onCambio = { v -> acciones.editar { copy(montoTexto = v) } },
                    etiqueta = stringResource(R.string.monto),
                    prefijo = "$",
                    error = transferencia.errores.firstOrNull { it in erroresMonto }?.let { textoError(it) },
                    teclado = TECLADO_MONTO.copy(imeAction = ImeAction.Done),
                )
                ElegirCuenta(
                    titulo = stringResource(R.string.desde),
                    cuentas = cuentas,
                    elegida = transferencia.desdeId,
                    error = transferencia.errores.firstOrNull { it == ErrorRegistro.SIN_CUENTA },
                    onElegir = { id -> acciones.editar { copy(desdeId = id) } },
                )
                ElegirCuenta(
                    titulo = stringResource(R.string.llega_a),
                    cuentas = cuentas,
                    elegida = transferencia.haciaId,
                    error = transferencia.errores.firstOrNull { it == ErrorRegistro.SIN_DESTINO || it == ErrorRegistro.MISMA_CUENTA },
                    onElegir = { id -> acciones.editar { copy(haciaId = id) } },
                )
                SelectorFecha(
                    fecha = transferencia.fecha,
                    onCambiar = { f -> acciones.editar { copy(fecha = f) } },
                    etiqueta = stringResource(R.string.fecha),
                )
                Campo(
                    valor = transferencia.nota,
                    onCambio = { v -> acciones.editar { copy(nota = v) } },
                    etiqueta = stringResource(R.string.nota_opcional),
                    error = transferencia.errores.firstOrNull { it == ErrorRegistro.NOTA_DEMASIADO_LARGA }?.let { textoError(it) },
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                ErrorAlGuardar(transferencia.errorAlGuardar)
                acciones.eliminar?.let { eliminar ->
                    TextButton(
                        onClick = eliminar,
                        enabled = !transferencia.guardando,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.eliminar)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardar, enabled = !transferencia.guardando) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = acciones.cerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun ElegirCuenta(
    titulo: String,
    cuentas: List<Cuenta>,
    elegida: CuentaId?,
    error: ErrorRegistro?,
    onElegir: (CuentaId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cuentas.forEach { cuenta ->
                FilterChip(selected = elegida == cuenta.id, onClick = { onElegir(cuenta.id) }, label = { Text(cuenta.nombre) })
            }
        }
        if (error != null) Text(textoError(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
