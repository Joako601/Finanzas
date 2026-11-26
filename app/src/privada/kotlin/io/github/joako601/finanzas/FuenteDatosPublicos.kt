package io.github.joako601.finanzas

import android.content.Context
import io.github.joako601.finanzas.mercado.DatosPublicos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** La distribución privada no tiene red (D-005): nunca hay datos públicos. */
@Suppress("UNUSED_PARAMETER")
fun fuenteDatosPublicos(context: Context, alcance: CoroutineScope): StateFlow<DatosPublicos> =
    MutableStateFlow(DatosPublicos.VACIOS)
