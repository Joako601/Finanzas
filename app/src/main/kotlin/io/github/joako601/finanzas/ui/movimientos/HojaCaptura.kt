package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.SelectorFecha
import kotlinx.datetime.LocalDate

/** Todas las acciones del formulario agrupadas, para no pasar ocho lambdas sueltas. */
class AccionesCaptura(
    val cambiarClase: (ClaseRegistro) -> Unit,
    val cambiarMonto: (String) -> Unit,
    val elegirCategoria: (CuentaId) -> Unit,
    val elegirCuenta: (CuentaId) -> Unit,
    val cambiarFecha: (LocalDate) -> Unit,
    val cambiarNota: (String) -> Unit,
    val guardar: () -> Unit,
    val cerrar: () -> Unit,
    /** Solo al editar un movimiento guardado: pide confirmación antes de borrarlo. */
    val eliminar: (() -> Unit)? = null,
)

/**
 * Hoja inferior para capturar un gasto o un ingreso.
 *
 * Camino corto (la meta de "dos o tres toques"): tocar Registrar, escribir el monto
 * (el teclado numérico aparece solo), tocar la categoría y tocar Guardar. La cuenta
 * viene preseleccionada con la última usada y la fecha con hoy. Guardar queda fijo
 * sobre el teclado para no tener que deslizar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCaptura(
    captura: Captura,
    categorias: List<Cuenta>,
    cuentas: List<Cuenta>,
    acciones: AccionesCaptura,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foco = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = acciones.cerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // Dos zonas: el formulario se desplaza y el botón Guardar queda fijo abajo.
        // imePadding sube todo por encima del teclado, así Guardar nunca queda tapado.
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ClaseRegistro.entries.forEachIndexed { indice, clase ->
                        SegmentedButton(
                            selected = captura.clase == clase,
                            onClick = { acciones.cambiarClase(clase) },
                            shape = SegmentedButtonDefaults.itemShape(indice, ClaseRegistro.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (clase) {
                                        ClaseRegistro.GASTO -> R.string.gasto
                                        ClaseRegistro.INGRESO -> R.string.ingreso
                                    },
                                ),
                            )
                        }
                    }
                }

                val errorMonto = captura.errores.firstOrNull {
                    it == ErrorRegistro.MONTO_VACIO || it == ErrorRegistro.MONTO_INVALIDO || it == ErrorRegistro.MONTO_CERO
                }
                OutlinedTextField(
                    value = captura.montoTexto,
                    onValueChange = acciones.cambiarMonto,
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                    label = { Text(stringResource(R.string.monto)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    isError = errorMonto != null,
                    supportingText = errorMonto?.let { { Text(textoError(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )

                Seccion(
                    titulo = stringResource(R.string.categoria),
                    error = captura.errores.firstOrNull {
                        it == ErrorRegistro.SIN_CATEGORIA || it == ErrorRegistro.CATEGORIA_NO_CORRESPONDE
                    },
                ) {
                    categorias.forEach { categoria ->
                        FilterChip(
                            selected = captura.categoriaId == categoria.id,
                            onClick = { acciones.elegirCategoria(categoria.id) },
                            label = { Text(categoria.nombre) },
                        )
                    }
                }

                Seccion(
                    titulo = stringResource(R.string.cuenta),
                    error = captura.errores.firstOrNull { it == ErrorRegistro.SIN_CUENTA },
                ) {
                    cuentas.forEach { cuenta ->
                        FilterChip(
                            selected = captura.cuentaId == cuenta.id,
                            onClick = { acciones.elegirCuenta(cuenta.id) },
                            label = { Text(cuenta.nombre) },
                        )
                    }
                }

                Seccion(titulo = stringResource(R.string.fecha), error = null) {
                    SelectorFecha(fecha = captura.fecha, onCambiar = acciones.cambiarFecha)
                }

                val errorNota = captura.errores.firstOrNull { it == ErrorRegistro.NOTA_DEMASIADO_LARGA }
                OutlinedTextField(
                    value = captura.nota,
                    onValueChange = acciones.cambiarNota,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.nota_opcional)) },
                    singleLine = true,
                    isError = errorNota != null,
                    supportingText = errorNota?.let { { Text(textoError(it)) } },
                )

                if (captura.errorAlGuardar) {
                    Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
                }
            }

            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                acciones.eliminar?.let { eliminar ->
                    TextButton(
                        onClick = eliminar,
                        enabled = !captura.guardando,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.eliminar)) }
                }
                BotonPrincipal(
                    texto = stringResource(R.string.guardar),
                    onClick = acciones.guardar,
                    habilitado = !captura.guardando,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // El monto es lo primero que se escribe: el cursor y el teclado aparecen ahí solos.
    // Al editar no: lo que se corrige puede ser la categoría, y el teclado la taparía.
    LaunchedEffect(Unit) { if (captura.editando == null) foco.requestFocus() }
}

@Composable
private fun Seccion(titulo: String, error: ErrorRegistro?, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
        if (error != null) {
            Text(textoError(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Texto de un error de captura; también lo usa el registro de pagos fijos. */
@Composable
fun textoError(error: ErrorRegistro): String = stringResource(
    when (error) {
        ErrorRegistro.MONTO_VACIO -> R.string.error_monto_vacio
        ErrorRegistro.MONTO_INVALIDO -> R.string.error_monto_invalido
        ErrorRegistro.MONTO_CERO -> R.string.error_monto_cero
        ErrorRegistro.SIN_CUENTA -> R.string.error_sin_cuenta
        ErrorRegistro.SIN_CATEGORIA -> R.string.error_sin_categoria
        ErrorRegistro.CATEGORIA_NO_CORRESPONDE -> R.string.error_categoria_no_corresponde
        ErrorRegistro.NOTA_DEMASIADO_LARGA -> R.string.error_nota_larga
        ErrorRegistro.SIN_DESTINO -> R.string.error_sin_destino
        ErrorRegistro.MISMA_CUENTA -> R.string.error_misma_cuenta
    },
)
