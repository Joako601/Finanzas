package io.github.joako601.finanzas.ui.respaldo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.data.respaldo.ErrorRespaldo
import io.github.joako601.finanzas.ui.componentes.AnimacionLottie
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.ui.componentes.TECLADO_CONTRASENA

/** Lo que está pasando o el resultado de lo último que se hizo. TalkBack lo anuncia al cambiar. */
@Composable
internal fun Progreso(estado: EstadoRespaldo) {
    val tarea = estado.tarea
    val aviso = estado.aviso
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            tarea != null -> {
                Text(
                    stringResource(
                        when (tarea) {
                            Tarea.CREANDO -> R.string.creando_respaldo
                            Tarea.EXPORTANDO -> R.string.exportando_movimientos
                            Tarea.LEYENDO -> R.string.leyendo_respaldo
                            Tarea.RESTAURANDO -> R.string.restaurando
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            aviso != null -> {
                val esError = aviso in setOf(Aviso.NO_SE_PUDO_ESCRIBIR, Aviso.NO_SE_PUDO_LEER, Aviso.NO_ES_RESPALDO, Aviso.RESPALDO_INTERRUMPIDO)
                val textoAviso = stringResource(
                    when (aviso) {
                        Aviso.RESPALDO_GUARDADO -> R.string.aviso_respaldo_guardado
                        Aviso.DATOS_RESTAURADOS -> R.string.aviso_datos_restaurados
                        Aviso.CSV_GUARDADO -> R.string.aviso_csv_guardado
                        Aviso.NO_SE_PUDO_ESCRIBIR -> R.string.aviso_no_se_pudo_escribir
                        Aviso.NO_SE_PUDO_LEER -> R.string.aviso_no_se_pudo_leer
                        Aviso.NO_ES_RESPALDO -> R.string.aviso_no_es_respaldo
                        Aviso.RESPALDO_INTERRUMPIDO -> R.string.aviso_respaldo_interrumpido
                    },
                )
                if (esError) {
                    Text(
                        textoAviso,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        key(aviso) {
                            AnimacionLottie(
                                resId = R.raw.anim_respaldo_exito,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Text(
                            textoAviso,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DialogoNuevoRespaldo(
    nuevo: NuevoRespaldo,
    onEditar: (NuevoRespaldo.() -> NuevoRespaldo) -> Unit,
    onContinuar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.contrasena_respaldo_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contrasena_respaldo_texto), style = MaterialTheme.typography.bodyMedium)
                Campo(
                    valor = nuevo.contrasena,
                    onCambio = { v -> onEditar { copy(contrasena = v) } },
                    etiqueta = stringResource(R.string.contrasena),
                    error = if (nuevo.error == ErrorContrasena.CORTA) stringResource(R.string.error_contrasena_corta) else null,
                    teclado = TECLADO_CONTRASENA,
                    oculto = true,
                )
                Campo(
                    valor = nuevo.confirmacion,
                    onCambio = { v -> onEditar { copy(confirmacion = v) } },
                    etiqueta = stringResource(R.string.repite_contrasena),
                    error = if (nuevo.error == ErrorContrasena.NO_COINCIDE) stringResource(R.string.error_contrasena_no_coincide) else null,
                    teclado = TECLADO_CONTRASENA.copy(imeAction = ImeAction.Done),
                    oculto = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onContinuar) { Text(stringResource(R.string.elegir_donde_guardar)) } },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
internal fun DialogoRestaurar(
    porRestaurar: PorRestaurar,
    onEditar: (String) -> Unit,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.restaurar_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restaurar_texto), style = MaterialTheme.typography.bodyMedium)
                Campo(
                    valor = porRestaurar.contrasena,
                    onCambio = onEditar,
                    etiqueta = stringResource(R.string.contrasena_respaldo_titulo),
                    error = porRestaurar.error?.let { motivo ->
                        stringResource(
                            when (motivo) {
                                ErrorRespaldo.Motivo.CONTRASENA_O_DANADO -> R.string.error_restaurar_contrasena
                                ErrorRespaldo.Motivo.NO_ES_RESPALDO -> R.string.aviso_no_es_respaldo
                                ErrorRespaldo.Motivo.VERSION_NUEVA -> R.string.error_restaurar_version
                                ErrorRespaldo.Motivo.BASE_INVALIDA -> R.string.error_restaurar_base
                            },
                        )
                    },
                    teclado = TECLADO_CONTRASENA.copy(imeAction = ImeAction.Done),
                    oculto = true,
                )
                if (porRestaurar.trabajando) {
                    Text(
                        stringResource(R.string.restaurando),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                enabled = !porRestaurar.trabajando && porRestaurar.contrasena.isNotEmpty(),
                // Rojo porque borra lo actual; deshabilitado se ve gris como cualquier botón.
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.reemplazar_mis_datos))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !porRestaurar.trabajando) { Text(stringResource(R.string.cancelar)) }
        },
    )
}
