package io.github.joako601.finanzas.nube.mercado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.mercado.ComparadorTarjetas
import io.github.joako601.finanzas.core.domain.mercado.FilaComparacion
import io.github.joako601.finanzas.core.domain.mercado.ProductoTarjeta
import io.github.joako601.finanzas.core.domain.mercado.UsoPropio
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.catalogo.ProductoFinanciero
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube
import java.util.Locale

/** Igual que `MONTO_OCULTO` de la app (modo discreto). */
private const val MONTO_OCULTO = "$ ••••"

/**
 * Tarjetas de crédito del catálogo que se pueden comparar: solo las que el backend marca como
 * verificadas y con un costo que se pueda llevar a un año.
 */
internal fun productosParaComparar(productos: List<ProductoFinanciero>): List<ProductoTarjeta> =
    productos.mapNotNull { p ->
        if (!p.categoria.contains("crédito", ignoreCase = true)) return@mapNotNull null
        if (!p.verificado) return@mapNotNull null
        val vecesPorAnio = when (p.periodicidad.trim().lowercase(Locale.ROOT)) {
            "anual" -> 1
            "mensual" -> 12
            "bimestral" -> 6
            "sin costo" -> 0
            else -> if (p.costoReferenciaPesos <= 0) 0 else return@mapNotNull null
        }
        val anualidad = Dinero.pesos(maxOf(p.costoReferenciaPesos, 0) * vecesPorAnio)
        ProductoTarjeta(p.id, p.nombre, p.entidad, anualidad, p.tasaAnualReferencia)
    }

/** Comparación local de las tarjetas del catálogo contra el uso propio. Nada sale del teléfono. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaComparador(
    productos: List<ProductoTarjeta>,
    uso: UsoPropio,
    montosOcultos: Boolean,
    onCerrar: () -> Unit,
) {
    val filas = remember(productos, uso) { ComparadorTarjetas.comparar(productos, uso) }
    val monto: (Dinero) -> String = { if (montosOcultos) MONTO_OCULTO else it.formatear() }

    ModalBottomSheet(onDismissRequest = onCerrar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.comparador_titulo),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Tarjeta {
                Text(stringResource(R.string.comparador_tu_uso), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.comparador_gasto_mensual, monto(uso.gastoMensualPromedio)),
                    style = MaterialTheme.typography.bodyMedium.tnum(),
                )
                Text(
                    stringResource(R.string.comparador_deuda, monto(uso.deuda), uso.tasaPropia),
                    style = MaterialTheme.typography.bodyMedium.tnum(),
                )
            }
            filas.forEachIndexed { i, fila ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FilaComparador(fila, monto)
            }
            TextoSecundario(stringResource(R.string.comparador_nota))
        }
    }
}

@Composable
private fun FilaComparador(fila: FilaComparacion, monto: (Dinero) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(fila.producto.nombre, style = MaterialTheme.typography.titleMedium)
        TextoSecundario(fila.producto.entidad)
        val costo = fila.costoAnual
        if (costo == null) {
            TextoSecundario(stringResource(R.string.comparador_sin_tasa, monto(fila.producto.anualidad)))
        } else {
            Text(
                stringResource(R.string.comparador_costo_anual, monto(costo)),
                style = MaterialTheme.typography.bodyLarge.tnum(),
            )
        }
        fila.diferenciaAnual?.let { dif ->
            val (texto, color) = when {
                dif.centavos < 0 -> stringResource(R.string.comparador_mas_barata, monto(-dif)) to ColoresSemanticosNube.exito()
                dif.centavos > 0 -> stringResource(R.string.comparador_mas_cara, monto(dif)) to MaterialTheme.colorScheme.error
                else -> stringResource(R.string.comparador_igual) to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(texto, style = MaterialTheme.typography.bodyMedium.tnum(), color = color)
        }
        fila.anualidadSobreGasto?.let {
            TextoSecundario(stringResource(R.string.comparador_anualidad_sobre_gasto, it))
        }
    }
}
