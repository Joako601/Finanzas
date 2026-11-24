package io.github.joako601.finanzas.ui.boveda

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.recordatorios.abrirAjustesDeAvisos
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.formatoDiaCorto
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.respaldo.DialogoNuevoRespaldo
import io.github.joako601.finanzas.ui.respaldo.DialogoRestaurar
import io.github.joako601.finanzas.ui.respaldo.Progreso
import io.github.joako601.finanzas.ui.respaldo.RespaldoViewModel
import io.github.joako601.finanzas.ui.tarjetas.recordarAvisos
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Pestaña 4 ("Bóveda"): casa permanente de privacidad, respaldo cifrado local,
 * exportación CSV, estado real de notificaciones y (solo en el flavor `nube`)
 * acceso a la cuenta y catálogo en la nube (D-018, D-020).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBoveda(
    viewModel: RespaldoViewModel = viewModel(factory = RespaldoViewModel.Fabrica),
    onAbrirNube: (() -> Unit)? = null,
    margenInferiorExtra: Dp = 0.dp,
) {
    val context = LocalContext.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val nuevo by viewModel.nuevo.collectAsStateWithLifecycle()
    val porRestaurar by viewModel.porRestaurar.collectAsStateWithLifecycle()
    var fechaDeHoy by remember { mutableStateOf(hoy()) }
    val libre = estado.tarea == null

    val avisos = recordarAvisos()
    val avisosActivos = avisos.activos

    LifecycleResumeEffect(Unit) {
        fechaDeHoy = hoy()
        onPauseOrDispose { }
    }

    val destinoRespaldo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.guardarRespaldo(it?.toString())
    }
    val destinoCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        viewModel.exportarCsv(it?.toString())
    }
    val origenRespaldo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.elegirParaRestaurar(it?.toString())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_boveda),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { margenes ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = margenes.calculateTopPadding())
                .consumeWindowInsets(margenes),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = margenes.calculateBottomPadding() + margenInferiorExtra + 28.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Envuelto en TarjetaFinanzas (surfaceContainerLowest) para contraste WCAG AA en errores.
                if (estado.tarea != null || estado.aviso != null) {
                    TarjetaFinanzas(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp)) {
                            Progreso(estado)
                        }
                    }
                }

                // 1. Respaldo cifrado
                TarjetaFinanzas(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val ultimo = estado.ultimo
                        EncabezadoConInsignia(
                            titulo = stringResource(R.string.respaldo_cifrado),
                            insignia = stringResource(
                                if (ultimo != null) R.string.boveda_estado_al_dia else R.string.boveda_estado_pendiente,
                            ),
                            colorInsignia = if (ultimo != null) TemaFinanzas.colores.ingreso else TemaFinanzas.colores.aviso,
                        )
                        if (ultimo != null) {
                            Text(
                                text = stringResource(R.string.ultimo_respaldo, ultimo.formatoDiaCorto(fechaDeHoy)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.sin_respaldo),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TemaFinanzas.colores.aviso,
                            )
                        }
                        Text(
                            text = stringResource(R.string.explicacion_respaldo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BotonPrincipal(
                            texto = stringResource(R.string.crear_respaldo),
                            onClick = viewModel::abrirNuevoRespaldo,
                            habilitado = libre,
                        )
                        BotonSecundario(
                            texto = stringResource(R.string.restaurar_respaldo),
                            onClick = { origenRespaldo.launch(arrayOf("*/*")) },
                            habilitado = libre,
                        )
                    }
                }

                // 2. Exportación CSV
                TarjetaFinanzas(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.exportar_csv),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.explicacion_csv),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BotonSecundario(
                            texto = stringResource(R.string.exportar_movimientos),
                            onClick = { destinoCsv.launch("finanzas-movimientos-${hoy()}.csv") },
                            habilitado = libre,
                        )
                    }
                }

                // 3. Recordatorios y notificaciones
                TarjetaFinanzas(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EncabezadoConInsignia(
                            titulo = stringResource(R.string.boveda_avisos_titulo),
                            insignia = stringResource(
                                if (avisosActivos) R.string.boveda_avisos_activos else R.string.boveda_avisos_inactivos,
                            ),
                            colorInsignia = if (avisosActivos) TemaFinanzas.colores.ingreso else TemaFinanzas.colores.aviso,
                        )
                        Text(
                            text = stringResource(
                                if (avisosActivos) R.string.boveda_avisos_texto_activos else R.string.boveda_avisos_texto_inactivos,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (avisosActivos) {
                            BotonSecundario(
                                texto = stringResource(R.string.boveda_ajustes_avisos),
                                onClick = { abrirAjustesDeAvisos(context) },
                            )
                        } else {
                            BotonPrincipal(
                                texto = stringResource(R.string.activar_avisos_boton),
                                onClick = avisos.activar,
                            )
                        }
                    }
                }

                // 4. Cuenta y catálogo en la nube (únicamente en el flavor `nube`; ausente en `privada`).
                if (onAbrirNube != null) {
                    TarjetaFinanzas(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.boveda_nube_titulo),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(R.string.boveda_nube_texto),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            BotonSecundario(
                                texto = stringResource(R.string.boveda_abrir_nube),
                                onClick = onAbrirNube,
                            )
                        }
                    }
                }
            }
        }
    }

    nuevo?.let { actual ->
        DialogoNuevoRespaldo(
            nuevo = actual,
            onEditar = viewModel::editarNuevo,
            onContinuar = { if (viewModel.validarNuevo()) destinoRespaldo.launch("finanzas-${hoy()}.respaldo") },
            onCerrar = viewModel::cerrarNuevo,
        )
    }

    porRestaurar?.let { actual ->
        DialogoRestaurar(
            porRestaurar = actual,
            onEditar = viewModel::editarRestaurar,
            onConfirmar = viewModel::confirmarRestaurar,
            onCerrar = viewModel::cerrarRestaurar,
        )
    }
}

/**
 * Encabezado de tarjeta con insignia de estado sobre `surfaceContainerLowest` y borde de 1 dp
 * para garantizar contraste WCAG AA >= 4.5:1. Usa [FlowRow] para que a `font_scale = 2.0`
 * la insignia baje limpiamente sin partir palabras del título.
 */
@Composable
private fun EncabezadoConInsignia(
    titulo: String,
    insignia: String,
    colorInsignia: Color,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(end = 8.dp)
                .semantics { heading() },
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = insignia,
                style = MaterialTheme.typography.labelSmall,
                color = colorInsignia,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
