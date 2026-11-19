package io.github.joako601.finanzas.ui.componentes

import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.ui.formatoCorto
import kotlinx.datetime.LocalDate

private const val MILIS_POR_DIA = 86_400_000L

/**
 * Chip que muestra una fecha y abre el calendario de Material al tocarlo.
 * El calendario trabaja en milisegundos UTC a medianoche; aquí se convierte a LocalDate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorFecha(fecha: LocalDate, onCambiar: (LocalDate) -> Unit, etiqueta: String? = null) {
    var mostrarCalendario by rememberSaveable { mutableStateOf(false) }

    AssistChip(
        onClick = { mostrarCalendario = true },
        label = { Text(listOfNotNull(etiqueta, fecha.formatoCorto()).joinToString(": ")) },
    )

    if (mostrarCalendario) {
        val estadoCalendario = rememberDatePickerState(initialSelectedDateMillis = fecha.toEpochDays() * MILIS_POR_DIA)
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoCalendario.selectedDateMillis?.let { millis ->
                        onCambiar(LocalDate.fromEpochDays(Math.floorDiv(millis, MILIS_POR_DIA)))
                    }
                    mostrarCalendario = false
                }) { Text(stringResource(R.string.aceptar)) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text(stringResource(R.string.cancelar)) }
            },
        ) {
            DatePicker(state = estadoCalendario)
        }
    }
}
