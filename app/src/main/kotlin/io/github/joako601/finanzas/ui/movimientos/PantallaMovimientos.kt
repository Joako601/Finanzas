package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.formatoEncabezado
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.tarjetas.DialogoEstadoDeCuenta
import io.github.joako601.finanzas.ui.tarjetas.DialogoNuevaTarjeta
import io.github.joako601.finanzas.ui.tarjetas.DialogoPagoTarjeta
import io.github.joako601.finanzas.ui.tarjetas.EstadoTarjetas
import io.github.joako601.finanzas.ui.tarjetas.ResumenTarjeta
import io.github.joako601.finanzas.ui.tarjetas.SeccionTarjetas
import io.github.joako601.finanzas.ui.tarjetas.TarjetasViewModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/** Acciones de la sección de tarjetas que la lista principal necesita disparar. */
class AccionesTarjetas(
    val agregar: () -> Unit,
    val pagar: (ResumenTarjeta) -> Unit,
    val capturar: (ResumenTarjeta) -> Unit,
)

/**
 * Pantalla principal con estado: obtiene los ViewModels y conecta sus flujos con la
 * interfaz. Todo lo visual está en [ContenidoMovimientos], que no conoce a los
 * ViewModels y por eso se puede previsualizar y probar con datos fijos.
 */
@Composable
fun PantallaMovimientos(
    viewModel: MovimientosViewModel = viewModel(factory = MovimientosViewModel.Fabrica),
    tarjetasViewModel: TarjetasViewModel = viewModel(factory = TarjetasViewModel.Fabrica),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val captura by viewModel.captura.collectAsStateWithLifecycle()
    val nuevaCuenta by viewModel.nuevaCuenta.collectAsStateWithLifecycle()
    val estadoTarjetas by tarjetasViewModel.estado.collectAsStateWithLifecycle()
    val nuevaTarjeta by tarjetasViewModel.nuevaTarjeta.collectAsStateWithLifecycle()
    val pago by tarjetasViewModel.pago.collectAsStateWithLifecycle()
    val capturaEstado by tarjetasViewModel.captura.collectAsStateWithLifecycle()
    var porEliminar by remember { mutableStateOf<MovimientoResumido?>(null) }

    ContenidoMovimientos(
        estado = estado,
        estadoTarjetas = estadoTarjetas,
        onRegistrar = viewModel::abrirCaptura,
        onAgregarCuenta = viewModel::abrirNuevaCuenta,
        accionesTarjetas = AccionesTarjetas(
            agregar = tarjetasViewModel::abrirNuevaTarjeta,
            pagar = tarjetasViewModel::abrirPago,
            capturar = tarjetasViewModel::abrirCaptura,
        ),
        onTocarMovimiento = { porEliminar = it },
    )

    captura?.let { actual ->
        HojaCaptura(
            captura = actual,
            categorias = when (actual.clase) {
                ClaseRegistro.GASTO -> estado.categoriasDeGasto
                ClaseRegistro.INGRESO -> estado.categoriasDeIngreso
            },
            // Un gasto se puede pagar con tarjeta; un ingreso solo llega a efectivo o débito.
            cuentas = estado.cuentasDeDinero.map { it.cuenta } +
                if (actual.clase == ClaseRegistro.GASTO) estado.tarjetas else emptyList(),
            acciones = AccionesCaptura(
                cambiarClase = viewModel::cambiarClase,
                cambiarMonto = viewModel::cambiarMonto,
                elegirCategoria = viewModel::elegirCategoria,
                elegirCuenta = viewModel::elegirCuenta,
                cambiarFecha = viewModel::cambiarFecha,
                cambiarNota = viewModel::cambiarNota,
                guardar = viewModel::guardarCaptura,
                cerrar = viewModel::cerrarCaptura,
            ),
        )
    }

    nuevaCuenta?.let { actual ->
        DialogoNuevaCuenta(
            nuevaCuenta = actual,
            onCambiarNombre = viewModel::cambiarNombreCuenta,
            onCambiarSaldo = viewModel::cambiarSaldoCuenta,
            onGuardar = viewModel::guardarNuevaCuenta,
            onCerrar = viewModel::cerrarNuevaCuenta,
        )
    }

    nuevaTarjeta?.let { actual ->
        DialogoNuevaTarjeta(
            nueva = actual,
            onEditar = tarjetasViewModel::editarNuevaTarjeta,
            onGuardar = tarjetasViewModel::guardarNuevaTarjeta,
            onCerrar = tarjetasViewModel::cerrarNuevaTarjeta,
        )
    }

    pago?.let { actual ->
        DialogoPagoTarjeta(
            pago = actual,
            cuentas = estadoTarjetas.cuentasParaPagar,
            onEditar = tarjetasViewModel::editarPago,
            onGuardar = tarjetasViewModel::guardarPago,
            onCerrar = tarjetasViewModel::cerrarPago,
        )
    }

    capturaEstado?.let { actual ->
        DialogoEstadoDeCuenta(
            captura = actual,
            onEditar = tarjetasViewModel::editarCaptura,
            onGuardar = tarjetasViewModel::guardarCaptura,
            onCerrar = tarjetasViewModel::cerrarCaptura,
        )
    }

    porEliminar?.let { movimiento ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text(stringResource(R.string.eliminar_movimiento_pregunta)) },
            text = {
                Text(
                    listOfNotNull(
                        etiqueta(movimiento),
                        movimiento.monto.formatear(),
                        movimiento.movimiento.fecha.formatoCorto(),
                        movimiento.movimiento.descripcion.ifBlank { null },
                    ).joinToString("\n"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    movimiento.movimiento.id?.let(viewModel::eliminar)
                    porEliminar = null
                }) { Text(stringResource(R.string.eliminar)) }
            },
            dismissButton = {
                TextButton(onClick = { porEliminar = null }) { Text(stringResource(R.string.cancelar)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoMovimientos(
    estado: EstadoMovimientos,
    estadoTarjetas: EstadoTarjetas,
    onRegistrar: () -> Unit,
    onAgregarCuenta: () -> Unit,
    accionesTarjetas: AccionesTarjetas,
    onTocarMovimiento: (MovimientoResumido) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRegistrar,
                icon = { Icon(painterResource(R.drawable.ic_agregar), contentDescription = null) },
                text = { Text(stringResource(R.string.registrar)) },
            )
        },
    ) { margenes ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize().padding(margenes), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val fechaDeHoy = remember { hoy() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Espacio extra abajo para que el botón flotante no tape el último movimiento.
            contentPadding = PaddingValues(
                top = margenes.calculateTopPadding(),
                bottom = margenes.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item(key = "saldos") {
                TarjetaSaldos(estado, onAgregarCuenta)
            }

            item(key = "tarjetas") {
                SeccionTarjetas(
                    estado = estadoTarjetas,
                    onAgregar = accionesTarjetas.agregar,
                    onPagar = accionesTarjetas.pagar,
                    onCapturar = accionesTarjetas.capturar,
                )
            }

            if (estado.dias.isEmpty()) {
                item(key = "vacio") {
                    Text(
                        text = stringResource(R.string.sin_movimientos),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            estado.dias.forEach { dia ->
                item(key = "dia-${dia.fecha}") {
                    Text(
                        text = when (dia.fecha) {
                            fechaDeHoy -> stringResource(R.string.hoy)
                            fechaDeHoy.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.ayer)
                            else -> dia.fecha.formatoEncabezado(fechaDeHoy)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(dia.movimientos, key = { "mov-${it.movimiento.id?.valor}" }) { movimiento ->
                    FilaMovimiento(movimiento, onClick = { onTocarMovimiento(movimiento) })
                }
            }
        }
    }
}

@Composable
private fun TarjetaSaldos(estado: EstadoMovimientos, onAgregarCuenta: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.total_en_cuentas), style = MaterialTheme.typography.labelLarge)
            Text(estado.totalEnCuentas.formatear(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                estado.cuentasDeDinero.forEach { (cuenta, saldo) ->
                    Column {
                        Text(cuenta.nombre, style = MaterialTheme.typography.labelMedium)
                        Text(saldo.formatear(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TextButton(onClick = onAgregarCuenta, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.agregar_cuenta))
            }
        }
    }
}

@Composable
private fun FilaMovimiento(movimiento: MovimientoResumido, onClick: () -> Unit) {
    val esDeuda = movimiento.cuenta?.tipo == TipoCuenta.PASIVO
    val (signo, color) = when (movimiento.clase) {
        ClaseMovimiento.GASTO -> "−" to MaterialTheme.colorScheme.onSurface
        ClaseMovimiento.INGRESO -> "+" to MaterialTheme.colorScheme.primary
        // Una deuda inicial no es dinero que entra: sin signo.
        ClaseMovimiento.SALDO_INICIAL ->
            (if (esDeuda) "" else "+") to (if (esDeuda) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        // Pagar la tarjeta mueve dinero entre cuentas propias: no es gasto ni ingreso.
        ClaseMovimiento.PAGO_TARJETA, ClaseMovimiento.TRANSFERENCIA, ClaseMovimiento.OTRO ->
            "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val detalle = listOfNotNull(
        movimiento.cuenta?.nombre,
        movimiento.movimiento.descripcion.ifBlank { null }?.takeIf { movimiento.clase != ClaseMovimiento.SALDO_INICIAL },
    ).joinToString(" · ")

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(etiqueta(movimiento)) },
        supportingContent = if (detalle.isNotEmpty()) ({ Text(detalle) }) else null,
        trailingContent = {
            Text(signo + movimiento.monto.formatear(), color = color, style = MaterialTheme.typography.bodyLarge)
        },
    )
}

@Composable
private fun etiqueta(movimiento: MovimientoResumido): String = when (movimiento.clase) {
    ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO ->
        movimiento.categoria?.nombre ?: stringResource(R.string.movimiento)
    ClaseMovimiento.SALDO_INICIAL ->
        stringResource(if (movimiento.cuenta?.tipo == TipoCuenta.PASIVO) R.string.deuda_inicial else R.string.saldo_inicial)
    ClaseMovimiento.PAGO_TARJETA ->
        movimiento.contraparte?.let { stringResource(R.string.pago_a_tarjeta, it.nombre) } ?: stringResource(R.string.movimiento)
    ClaseMovimiento.TRANSFERENCIA -> stringResource(R.string.transferencia)
    ClaseMovimiento.OTRO -> stringResource(R.string.movimiento)
}
