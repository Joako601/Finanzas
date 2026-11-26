package io.github.joako601.finanzas.ui.tarjetas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.mercado.ComparacionDeuda
import io.github.joako601.finanzas.core.domain.tarjetas.AjusteInhabil
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.enPantalla
import io.github.joako601.finanzas.ui.formatoCorto
import java.util.Locale

/** Tasa en % con dos decimales, como la publica Banxico. */
fun Double.comoPorcentaje(): String = String.format(Locale.forLanguageTag("es-MX"), "%.2f", this)

/** La fecha límite predicha cae en día inhábil: el usuario elige hacia dónde moverla (S14). */
@Composable
internal fun BloqueFechaInhabil(fecha: FechaInhabil, onElegir: (AjusteInhabil) -> Unit) {
    val elegido = fecha.ajustada?.let { if (it < fecha.nominal) AjusteInhabil.ANTERIOR else AjusteInhabil.SIGUIENTE }
    TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.inhabil_titulo), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.inhabil_explicacion, fecha.nominal.formatoCorto()),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = elegido == AjusteInhabil.ANTERIOR,
                    onClick = { onElegir(AjusteInhabil.ANTERIOR) },
                    label = { Text(stringResource(R.string.inhabil_anterior)) },
                )
                FilterChip(
                    selected = elegido == AjusteInhabil.SIGUIENTE,
                    onClick = { onElegir(AjusteInhabil.SIGUIENTE) },
                    label = { Text(stringResource(R.string.inhabil_siguiente)) },
                )
            }
        }
    }
}

/** Lo que cuesta un año de esta deuda frente a lo que rendiría el mismo dinero en CETES. */
@Composable
internal fun BloqueContraCetes(comparacion: ComparacionDeuda) {
    TarjetaFinanzas(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.cetes_titulo), style = MaterialTheme.typography.titleSmall)
            FilaDato(
                stringResource(R.string.cetes_costo_deuda, comparacion.tasaTarjetaAnual),
                comparacion.costoAnualDeuda.enPantalla(),
            )
            FilaDato(
                stringResource(R.string.cetes_rendimiento, comparacion.tasaReferenciaAnual.comoPorcentaje()),
                comparacion.rendimientoAnualReferencia.enPantalla(),
            )
            Text(
                text = if (comparacion.diferencia.esPositivo) {
                    stringResource(R.string.cetes_conclusion_pagar, comparacion.diferencia.enPantalla())
                } else {
                    stringResource(R.string.cetes_conclusion_igual)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
