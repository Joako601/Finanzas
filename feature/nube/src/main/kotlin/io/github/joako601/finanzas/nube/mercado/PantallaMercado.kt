package io.github.joako601.finanzas.nube.mercado

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.catalogo.TarjetaProductoFinanciero
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube
import io.github.joako601.finanzas.nube.componentes.toqueTactil
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import io.github.joako601.finanzas.nube.divisas.formatearMontoDivisa
import io.github.joako601.finanzas.nube.divisas.formatearTasaDivisa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class VistaMercado(val etiquetaRes: Int) {
    DIVISAS(R.string.mercado_vista_divisas),
    INDICADORES(R.string.mercado_vista_indicadores),
    CATALOGO(R.string.mercado_vista_catalogo),
}

private const val DIA_MS = 24 * 60 * 60 * 1000L

/**
 * Pestaña Mercado (5.ª pestaña de la distribución nube). Muestra lo último guardado al
 * instante; actualizar (botón o deslizar hacia abajo) reemplaza los datos sin vaciar la
 * pantalla. Si no hay red, todo sigue a la vista con un aviso de antigüedad.
 *
 * @param margenInferior espacio que ocupa la barra de pestañas flotante.
 * @param onIniciarSesion abre el login cuando la sesión expiró.
 * @param avisosActivos si la app puede mostrar notificaciones; sin ellas las alertas invitan a activarlas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMercado(
    repositorio: RepositorioMercado,
    margenInferior: Dp,
    onIniciarSesion: () -> Unit,
    modifier: Modifier = Modifier,
    avisosActivos: Boolean = true,
    onActivarAvisos: () -> Unit = {},
) {
    val estado by repositorio.estado.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    var jalando by remember { mutableStateOf(false) }
    var vista by rememberSaveable { mutableStateOf(VistaMercado.DIVISAS) }
    // Reloj de minuto en minuto para que "hace X min" no se quede congelado.
    val ahora by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val actualizar: () -> Unit = {
        alcance.launch {
            jalando = true
            try {
                repositorio.sincronizar(forzar = true)
            } finally {
                jalando = false
            }
        }
    }
    val actual = estado
    val actualizando = when (actual) {
        is EstadoMercado.Datos -> actual.actualizando
        is EstadoMercado.Esperando -> actual.actualizando
        EstadoMercado.Oculto -> false
    }

    PullToRefreshBox(
        isRefreshing = jalando && actualizando,
        onRefresh = actualizar,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = margenInferior + 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "encabezado") {
                Encabezado(
                    recibidoEn = (actual as? EstadoMercado.Datos)?.datos?.recibidoEn,
                    actualizando = actualizando,
                    onActualizar = actualizar,
                )
            }
            when (actual) {
                EstadoMercado.Oculto -> Unit
                is EstadoMercado.Esperando -> item(key = "esperando") {
                    TarjetaEsperando(conectado = actual.conectado, actualizando = actualizando, onActualizar = actualizar)
                }
                is EstadoMercado.Datos -> {
                    item(key = "aviso") {
                        AvisoEstado(actual, ahora, onIniciarSesion)
                    }
                    item(key = "selector") {
                        SelectorVista(vista = vista, onCambiar = { vista = it })
                    }
                    when (vista) {
                        VistaMercado.DIVISAS -> {
                            vistaDivisas(actual.datos.cotizaciones, actual.conectado)
                            repositorio.alertas?.let { alertas ->
                                item(key = "alertas") {
                                    SeccionAlertas(alertas, actual.datos.cotizaciones, avisosActivos, onActivarAvisos)
                                }
                            }
                        }
                        VistaMercado.INDICADORES -> vistaIndicadores(actual.datos.indicadores)
                        VistaMercado.CATALOGO -> item(key = "catalogo") { VistaCatalogo(actual.datos) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Encabezado(recibidoEn: Long?, actualizando: Boolean, onActualizar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.mercado_titulo),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            val subtitulo = when {
                actualizando -> stringResource(R.string.mercado_actualizando)
                recibidoEn != null -> textoActualizado(recibidoEn)
                else -> null
            }
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall.tnum(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onActualizar, enabled = !actualizando, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_actualizar),
                contentDescription = stringResource(R.string.mercado_actualizar),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TarjetaEsperando(conectado: Boolean, actualizando: Boolean, onActualizar: () -> Unit) {
    Tarjeta(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(R.string.mercado_esperando_titulo), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(if (conectado) R.string.mercado_esperando_texto else R.string.mercado_esperando_sin_conexion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BotonContorno(stringResource(R.string.mercado_boton_actualizar), enabled = !actualizando, onClick = onActualizar)
    }
}

/** Un solo aviso a la vez: sesión expirada pesa más que sin conexión, y esta más que datos viejos. */
@Composable
private fun AvisoEstado(estado: EstadoMercado.Datos, ahora: Long, onIniciarSesion: () -> Unit) {
    val antiguedad = textoAntiguedad(ahora - estado.datos.recibidoEn)
    val (texto, color) = when {
        estado.sesionExpirada -> stringResource(R.string.mercado_sesion_expirada) to MaterialTheme.colorScheme.error
        !estado.conectado -> stringResource(R.string.mercado_sin_conexion, antiguedad) to ColoresSemanticosNube.aviso()
        ahora - estado.datos.recibidoEn > DIA_MS ->
            stringResource(R.string.mercado_datos_viejos, antiguedad) to ColoresSemanticosNube.aviso()
        else -> return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(texto, style = MaterialTheme.typography.bodyMedium.tnum(), color = MaterialTheme.colorScheme.onSurface)
            if (estado.sesionExpirada) {
                TextButton(onClick = onIniciarSesion, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.mercado_iniciar_sesion))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorVista(vista: VistaMercado, onCambiar: (VistaMercado) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        VistaMercado.entries.forEach { opcion ->
            SegmentedButton(
                selected = opcion == vista,
                onClick = { onCambiar(opcion) },
                shape = SegmentedButtonDefaults.itemShape(opcion.ordinal, VistaMercado.entries.size),
                icon = {},
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(opcion.etiquetaRes), maxLines = 1)
            }
        }
    }
}

// --- Divisas ---

private fun LazyListScope.vistaDivisas(cotizaciones: List<TipoCambio>, conectado: Boolean) {
    item(key = "conversor") { TarjetaConversor(cotizaciones, conectado) }
    item(key = "cotizaciones") {
        Tarjeta {
            Text(
                text = stringResource(R.string.mercado_cotizaciones_titulo),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (cotizaciones.isEmpty()) {
                TextoSecundario(stringResource(R.string.mercado_sin_cotizaciones))
            }
            cotizaciones.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mercado_cotizacion_fila, c.monedaBase, formatearTasaDivisa(c.tasa)),
                        style = MaterialTheme.typography.bodyLarge.tnum(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(fechaCorta(c.fecha), style = MaterialTheme.typography.bodySmall.tnum(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaConversor(cotizaciones: List<TipoCambio>, conectado: Boolean) {
    var montoTexto by rememberSaveable { mutableStateOf("100") }
    var de by rememberSaveable { mutableStateOf("USD") }
    var a by rememberSaveable { mutableStateOf("MXN") }
    val monto = montoTexto.replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
    val resultado = monto?.let { convertirLocal(it, de, a, cotizaciones) }
    val fechaTasa = cotizaciones.maxOfOrNull { it.fecha }

    Tarjeta {
        Text(
            text = stringResource(R.string.mercado_conversor_titulo),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = montoTexto,
            onValueChange = { nuevo -> if (nuevo.length <= 15 && nuevo.all { it.isDigit() || it == '.' || it == ',' }) montoTexto = nuevo },
            label = { Text(stringResource(R.string.mercado_conversor_monto)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.tnum(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        SelectorMoneda(stringResource(R.string.mercado_conversor_de), de) { de = it }
        SelectorMoneda(stringResource(R.string.mercado_conversor_a), a) { a = it }
        TextButton(
            onClick = { de = a.also { a = de } },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.mercado_conversor_invertir)) }
        Text(
            text = when {
                monto == null -> "—"
                resultado == null -> stringResource(R.string.mercado_conversor_sin_tasa)
                else -> formatearMontoDivisa(resultado, a)
            },
            style = if (resultado != null) {
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold).tnum()
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (fechaTasa != null) {
            TextoSecundario(
                stringResource(
                    if (conectado) R.string.mercado_conversor_nota else R.string.mercado_conversor_nota_offline,
                    fechaCorta(fechaTasa),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectorMoneda(
    etiqueta: String,
    seleccionada: String,
    monedas: List<String> = MONEDAS_CONVERSOR,
    onElegir: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextoSecundario(etiqueta)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            monedas.forEach { moneda ->
                FilterChip(
                    selected = moneda == seleccionada,
                    onClick = { onElegir(moneda) },
                    label = { Text(moneda) },
                )
            }
        }
    }
}

// --- Indicadores ---

private fun LazyListScope.vistaIndicadores(indicadores: List<IndicadorEconomico>) {
    if (indicadores.isEmpty()) {
        item(key = "sin-indicadores") { Tarjeta { TextoSecundario(stringResource(R.string.mercado_sin_indicadores)) } }
        return
    }
    items(indicadores, key = { "indicador-${it.clave}" }) { indicador ->
        Tarjeta(Modifier.semantics(mergeDescendants = true) {}) {
            TextoSecundario(indicador.nombre)
            Text(
                text = "${indicador.valor.toPlainString()} ${indicador.unidad}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold).tnum(),
            )
            Text(
                text = stringResource(R.string.mercado_indicador_meta, fechaCorta(indicador.fecha), indicador.fuente),
                style = MaterialTheme.typography.bodySmall.tnum(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Catálogo ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VistaCatalogo(datos: DatosMercado) {
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    val categorias = remember(datos.productos) { datos.productos.map { it.categoria }.distinct() }
    val visibles = remember(datos.productos, categoria) {
        datos.productos.filter { categoria == null || it.categoria == categoria }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (datos.productos.isEmpty()) {
            Tarjeta { TextoSecundario(stringResource(R.string.mercado_sin_productos)) }
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = categoria == null,
                onClick = { categoria = null },
                label = { Text(stringResource(R.string.mercado_categoria_todas)) },
            )
            categorias.forEach { c ->
                FilterChip(selected = c == categoria, onClick = { categoria = c }, label = { Text(c) })
            }
        }
        visibles.forEach { TarjetaProductoFinanciero(it) }
    }
}

// --- Piezas comunes ---

/** `TarjetaFinanzas` de la app: plana, borde 1 dp, radio 20 dp, padding 20 dp. */
@Composable
internal fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenido() }
    }
}

@Composable
internal fun BotonContorno(texto: String, enabled: Boolean, onClick: () -> Unit) {
    val interaccion = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaccion,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toqueTactil(interaccion),
    ) { Text(texto) }
}

@Composable
internal fun TextoSecundario(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall.tnum(), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = "tnum")

private val localeMx = Locale.forLanguageTag("es-MX")
private val formatoDia = DateTimeFormatter.ofPattern("d MMM", localeMx)
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm", localeMx)

/** `2026-11-26` → `26 nov`; si no es una fecha ISO se muestra tal cual. */
private fun fechaCorta(iso: String): String =
    runCatching { LocalDate.parse(iso).format(formatoDia).removeSuffix(".") }.getOrDefault(iso)

@Composable
private fun textoActualizado(recibidoEn: Long): String {
    val zona = ZoneId.systemDefault()
    val momento = Instant.ofEpochMilli(recibidoEn).atZone(zona)
    val dia = if (momento.toLocalDate() == LocalDate.now(zona)) {
        stringResource(R.string.mercado_hoy)
    } else {
        momento.format(formatoDia).removeSuffix(".")
    }
    return stringResource(R.string.mercado_actualizado, dia, momento.format(formatoHora))
}

@Composable
private fun textoAntiguedad(ms: Long): String {
    val minutos = (ms / 60_000).coerceAtLeast(0)
    return when {
        minutos < 60 -> stringResource(R.string.mercado_hace_min, minutos.toInt())
        minutos < 24 * 60 -> stringResource(R.string.mercado_hace_horas, (minutos / 60).toInt())
        else -> stringResource(R.string.mercado_hace_dias, (minutos / (24 * 60)).toInt())
    }
}
