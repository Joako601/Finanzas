package io.github.joako601.finanzas

import android.content.Context
import io.github.joako601.finanzas.mercado.DatosPublicos
import io.github.joako601.finanzas.nube.ContenedorNube
import io.github.joako601.finanzas.nube.mercado.DatosMercado
import io.github.joako601.finanzas.nube.mercado.EstadoMercado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/**
 * Puente de Mercado hacia el libro (D-020): solo aquí se juntan :feature:nube y :core:data.
 * Pasa días inhábiles y CETES; con la sesión cerrada (caché borrada) queda vacío.
 */
fun fuenteDatosPublicos(context: Context, alcance: CoroutineScope): StateFlow<DatosPublicos> =
    ContenedorNube.obtener(context).repositorioMercado.estado
        .map { estado -> (estado as? EstadoMercado.Datos)?.datos?.aPublicos() ?: DatosPublicos.VACIOS }
        .stateIn(alcance, SharingStarted.Eagerly, DatosPublicos.VACIOS)

private fun DatosMercado.aPublicos() = DatosPublicos(
    inhabiles = diasInhabiles.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet().ifEmpty { null },
    cetes28 = indicador(DatosMercado.CETES_28)?.valor?.toDouble(),
)
