package io.github.joako601.finanzas.nube.mercado

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.joako601.finanzas.core.domain.mercado.AlertaCambio
import io.github.joako601.finanzas.core.domain.mercado.CondicionAlerta
import io.github.joako601.finanzas.core.domain.mercado.EvaluacionAlertas
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import io.github.joako601.finanzas.nube.divisas.formatearTasaDivisa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal

private val MONEDAS_ALERTA = MONEDAS_CONVERSOR - "MXN"

/** "Alertas de tipo de cambio" al final de Divisas: la lista y el formulario para crear una. */
@Composable
internal fun SeccionAlertas(
    alertas: AlertasLocales,
    cotizaciones: List<TipoCambio>,
    avisosActivos: Boolean,
    onActivarAvisos: () -> Unit,
) {
    val lista by alertas.alertas.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    var moneda by rememberSaveable { mutableStateOf("USD") }
    var condicion by rememberSaveable { mutableStateOf(CondicionAlerta.BAJA_DE) }
    var umbralTexto by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val umbral = umbralTexto.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    val tasas = tasasEnPesos(cotizaciones)

    // El borrado de datos locales quita el archivo por fuera.
    LaunchedEffect(alertas) { withContext(Dispatchers.IO) { alertas.recargar() } }

    fun enDisco(accion: () -> Unit) {
        alcance.launch {
            error = try {
                withContext(Dispatchers.IO) { accion() }
                false
            } catch (_: IOException) {
                true
            }
        }
    }

    Tarjeta {
        Text(
            text = stringResource(R.string.mercado_alertas_titulo),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (!avisosActivos) {
            TextoSecundario(stringResource(R.string.mercado_alertas_sin_permiso))
            BotonContorno(stringResource(R.string.mercado_alertas_activar), enabled = true, onClick = onActivarAvisos)
        }
        if (lista.isEmpty()) {
            TextoSecundario(stringResource(R.string.mercado_alertas_vacio))
        }
        lista.forEach { alerta ->
            FilaAlerta(alerta, onQuitar = { enDisco { alertas.quitar(alerta.id) } })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        SelectorMoneda(stringResource(R.string.mercado_alertas_moneda), moneda, MONEDAS_ALERTA) { moneda = it }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CondicionAlerta.entries.forEach { opcion ->
                SegmentedButton(
                    selected = opcion == condicion,
                    onClick = { condicion = opcion },
                    shape = SegmentedButtonDefaults.itemShape(opcion.ordinal, CondicionAlerta.entries.size),
                    icon = {},
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(opcion.etiqueta())) }
            }
        }
        OutlinedTextField(
            value = umbralTexto,
            onValueChange = { nuevo -> if (nuevo.length <= 12 && nuevo.all { it.isDigit() || it == '.' || it == ',' }) umbralTexto = nuevo },
            label = { Text(stringResource(R.string.mercado_alertas_umbral, moneda)) },
            supportingText = tasas[moneda]?.let { { Text(stringResource(R.string.mercado_alertas_hoy, tasaTexto(it))) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.tnum(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        BotonContorno(stringResource(R.string.mercado_alertas_crear), enabled = umbral != null) {
            val valor = umbral ?: return@BotonContorno
            val elegida = moneda
            val tipo = condicion
            enDisco { alertas.agregar(elegida, tipo, valor, tasas[elegida]) }
            umbralTexto = ""
        }
        if (error) {
            Text(
                stringResource(R.string.mercado_alertas_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextoSecundario(stringResource(R.string.mercado_alertas_nota))
    }
}

@Composable
private fun FilaAlerta(alerta: AlertaCambio, onQuitar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.mercado_alerta_fila,
                    alerta.moneda,
                    stringResource(alerta.condicion.etiqueta()).lowercase(),
                    tasaTexto(alerta.umbral),
                ),
                style = MaterialTheme.typography.bodyLarge.tnum(),
            )
            if (alerta.cumplida) {
                Text(
                    stringResource(R.string.mercado_alerta_cumplida),
                    style = MaterialTheme.typography.bodySmall,
                    color = ColoresSemanticosNube.exito(),
                )
            } else {
                TextoSecundario(stringResource(R.string.mercado_alerta_esperando))
            }
        }
        TextButton(onClick = onQuitar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.mercado_alerta_quitar))
        }
    }
}

private fun CondicionAlerta.etiqueta(): Int = when (this) {
    CondicionAlerta.BAJA_DE -> R.string.mercado_alertas_baja_de
    CondicionAlerta.SUBE_DE -> R.string.mercado_alertas_sube_de
}

private fun tasaTexto(pesos: Double): String = "$" + formatearTasaDivisa(BigDecimal.valueOf(pesos))

/** Título y texto de la notificación; la app la publica con su canal y su versión privada. */
fun textoDisparo(context: Context, disparo: EvaluacionAlertas.Disparo): Pair<String, String> {
    val alerta = disparo.alerta
    val titulo = context.getString(
        if (alerta.condicion == CondicionAlerta.BAJA_DE) R.string.aviso_alerta_bajo else R.string.aviso_alerta_subio,
        alerta.moneda,
        tasaTexto(alerta.umbral),
    )
    return titulo to context.getString(R.string.aviso_alerta_texto, alerta.moneda, tasaTexto(disparo.tasa))
}
