package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.enPantalla

/** Lo que cada paso de la bienvenida abre: los mismos diálogos de siempre. */
class AccionesBienvenida(
    val saldoEfectivo: (() -> Unit)?,
    val agregarDebito: () -> Unit,
    val agregarIngresoFijo: () -> Unit,
    val agregarTarjeta: () -> Unit,
    val siguiente: () -> Unit,
    val saltar: () -> Unit,
)

/**
 * Bienvenida del Día 1 en tres pasos (dinero de hoy, ingreso fijo, tarjeta). No guarda nada
 * por sí misma: cada botón abre el diálogo que ya existe y, al cerrarlo, se vuelve aquí.
 * Cualquier paso se puede saltar.
 */
@Composable
fun DialogoBienvenida(paso: Int, cuentas: List<SaldoCuenta>, acciones: AccionesBienvenida) {
    val (titulo, texto) = when (paso) {
        1 -> R.string.bienvenida_paso1_titulo to R.string.bienvenida_paso1_texto
        2 -> R.string.bienvenida_paso2_titulo to R.string.bienvenida_paso2_texto
        else -> R.string.bienvenida_paso3_titulo to R.string.bienvenida_paso3_texto
    }
    AlertDialog(
        onDismissRequest = {},
        title = {
            Column {
                Text(
                    stringResource(R.string.bienvenida_paso_de, paso),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(titulo), modifier = Modifier.semantics { heading() })
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(texto))
                when (paso) {
                    1 -> {
                        // Lo capturado se ve aquí mismo: así se nota que quedó guardado.
                        cuentas.forEach { (cuenta, saldo) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cuenta.nombre)
                                Text(saldo.enPantalla(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                            }
                        }
                        acciones.saldoEfectivo?.let { BotonSecundario(stringResource(R.string.bienvenida_saldo_efectivo), it) }
                        BotonSecundario(stringResource(R.string.bienvenida_agregar_debito), acciones.agregarDebito)
                    }
                    2 -> BotonSecundario(stringResource(R.string.bienvenida_agregar_ingreso), acciones.agregarIngresoFijo)
                    else -> BotonSecundario(stringResource(R.string.agregar_tarjeta), acciones.agregarTarjeta)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.siguiente) {
                Text(stringResource(if (paso < 3) R.string.bienvenida_siguiente else R.string.bienvenida_listo))
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.saltar) { Text(stringResource(R.string.bienvenida_saltar)) }
        },
    )
}
