package io.github.joako601.finanzas.ui.movimientos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.iconoDe
import io.github.joako601.finanzas.ui.disponible.AccionesLineaDeTiempo
import io.github.joako601.finanzas.ui.disponible.HojaLineaDeTiempo
import io.github.joako601.finanzas.ui.disponible.LineaDeTiempoViewModel
import io.github.joako601.finanzas.ui.disponible.ResumenDisponible
import io.github.joako601.finanzas.ui.formatoCorto
import io.github.joako601.finanzas.ui.formatoEncabezado
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.recurrentes.AccionesRegistroRecurrente
import io.github.joako601.finanzas.ui.recurrentes.DialogoNuevoRecurrente
import io.github.joako601.finanzas.ui.recurrentes.DialogoRegistroRecurrente
import io.github.joako601.finanzas.ui.recurrentes.EstadoRecurrentes
import io.github.joako601.finanzas.ui.recurrentes.RecurrentesViewModel
import io.github.joako601.finanzas.ui.recurrentes.ResumenRecurrente
import io.github.joako601.finanzas.ui.recurrentes.SeccionRecurrentes
import io.github.joako601.finanzas.ui.respaldo.HojaRespaldo
import io.github.joako601.finanzas.ui.respaldo.RespaldoViewModel
import io.github.joako601.finanzas.ui.tarjetas.DialogoEstadoDeCuenta
import io.github.joako601.finanzas.ui.tarjetas.DialogoNuevaTarjeta
import io.github.joako601.finanzas.ui.tarjetas.DialogoPagoTarjeta
import io.github.joako601.finanzas.ui.tarjetas.EstadoTarjetas
import io.github.joako601.finanzas.ui.tarjetas.ResumenTarjeta
import io.github.joako601.finanzas.ui.tarjetas.SeccionTarjetas
import io.github.joako601.finanzas.ui.tarjetas.TarjetaActivarAvisos
import io.github.joako601.finanzas.ui.tarjetas.TarjetasViewModel
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/** Acciones de la sección de pagos y cobros fijos que la lista principal necesita disparar. */
class AccionesRecurrentes(
    val agregar: () -> Unit,
    val tocar: (ResumenRecurrente) -> Unit,
)

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
    recurrentesViewModel: RecurrentesViewModel = viewModel(factory = RecurrentesViewModel.Fabrica),
    lineaDeTiempoViewModel: LineaDeTiempoViewModel = viewModel(factory = LineaDeTiempoViewModel.Fabrica),
    onAbrirNube: (() -> Unit)? = null,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val captura by viewModel.captura.collectAsStateWithLifecycle()
    val nuevaCuenta by viewModel.nuevaCuenta.collectAsStateWithLifecycle()
    val transferencia by viewModel.transferencia.collectAsStateWithLifecycle()
    val estadoTarjetas by tarjetasViewModel.estado.collectAsStateWithLifecycle()
    val nuevaTarjeta by tarjetasViewModel.nuevaTarjeta.collectAsStateWithLifecycle()
    val pago by tarjetasViewModel.pago.collectAsStateWithLifecycle()
    val capturaEstado by tarjetasViewModel.captura.collectAsStateWithLifecycle()
    val estadoRecurrentes by recurrentesViewModel.estado.collectAsStateWithLifecycle()
    val nuevoRecurrente by recurrentesViewModel.nuevo.collectAsStateWithLifecycle()
    val registroRecurrente by recurrentesViewModel.registro.collectAsStateWithLifecycle()
    val estadoLinea by lineaDeTiempoViewModel.estado.collectAsStateWithLifecycle()
    var verLineaDeTiempo by rememberSaveable { mutableStateOf(false) }
    var verRespaldo by rememberSaveable { mutableStateOf(false) }
    var porEliminar by remember { mutableStateOf<MovimientoResumido?>(null) }

    ContenidoMovimientos(
        estado = estado,
        estadoTarjetas = estadoTarjetas,
        onRegistrar = viewModel::abrirCaptura,
        onAgregarCuenta = viewModel::abrirNuevaCuenta,
        onTransferir = viewModel::abrirTransferencia,
        lineaDeTiempo = estadoLinea.linea,
        onVerLineaDeTiempo = { verLineaDeTiempo = true },
        accionesTarjetas = AccionesTarjetas(
            agregar = tarjetasViewModel::abrirNuevaTarjeta,
            pagar = tarjetasViewModel::abrirPago,
            capturar = tarjetasViewModel::abrirCaptura,
        ),
        estadoRecurrentes = estadoRecurrentes,
        accionesRecurrentes = AccionesRecurrentes(
            agregar = recurrentesViewModel::abrirNuevo,
            tocar = recurrentesViewModel::abrirRegistro,
        ),
        // Gastos, ingresos y transferencias se editan; lo demás solo se puede eliminar.
        onTocarMovimiento = {
            if (viewModel.esEditable(it)) viewModel.abrirEdicion(it) else porEliminar = it
        },
        onRespaldo = { verRespaldo = true },
        onAbrirNube = onAbrirNube,
    )

    if (verRespaldo) {
        HojaRespaldo(viewModel(factory = RespaldoViewModel.Fabrica), onCerrar = { verRespaldo = false })
    }

    if (verLineaDeTiempo) {
        // Al tocar algo se cierra la hoja y se abre el diálogo que corresponde.
        HojaLineaDeTiempo(
            estado = estadoLinea,
            acciones = AccionesLineaDeTiempo(
                tocarFijo = { id ->
                    estadoRecurrentes.recurrentes.firstOrNull { it.recurrente.id == id }?.let {
                        verLineaDeTiempo = false
                        recurrentesViewModel.abrirRegistro(it)
                    }
                },
                tocarTarjeta = { id, monto ->
                    estadoTarjetas.tarjetas.firstOrNull { it.tarjeta.id == id }?.let {
                        verLineaDeTiempo = false
                        tarjetasViewModel.abrirPago(it, monto)
                    }
                },
                agregarFijo = {
                    verLineaDeTiempo = false
                    recurrentesViewModel.abrirNuevo()
                },
                cerrar = { verLineaDeTiempo = false },
            ),
        )
    }

    nuevoRecurrente?.let { actual ->
        DialogoNuevoRecurrente(
            nuevo = actual,
            categorias = estadoRecurrentes.categoriasDe(actual.clase),
            onEditar = recurrentesViewModel::editarNuevo,
            onGuardar = recurrentesViewModel::guardarNuevo,
            onCerrar = recurrentesViewModel::cerrarNuevo,
        )
    }

    registroRecurrente?.let { actual ->
        DialogoRegistroRecurrente(
            registro = actual,
            cuentas = estadoRecurrentes.cuentasPara(actual.resumen.recurrente.clase),
            acciones = AccionesRegistroRecurrente(
                editar = recurrentesViewModel::editarRegistro,
                registrar = recurrentesViewModel::guardarRegistro,
                omitir = recurrentesViewModel::omitir,
                eliminar = recurrentesViewModel::eliminar,
                cerrar = recurrentesViewModel::cerrarRegistro,
            ),
        )
    }

    captura?.let { actual ->
        HojaCaptura(
            captura = actual,
            // Al editar, la categoría o cuenta archivada del movimiento sigue disponible.
            categorias = when (actual.clase) {
                ClaseRegistro.GASTO -> estado.categoriasDeGasto + actual.archivadas.filter { it.tipo == TipoCuenta.GASTO }
                ClaseRegistro.INGRESO -> estado.categoriasDeIngreso + actual.archivadas.filter { it.tipo == TipoCuenta.INGRESO }
            },
            // Un gasto se puede pagar con tarjeta; un ingreso solo llega a efectivo o débito.
            cuentas = estado.cuentasDeDinero.map { it.cuenta } +
                (if (actual.clase == ClaseRegistro.GASTO) estado.tarjetas else emptyList()) +
                actual.archivadas.filter { it.tipo.esDeDinero },
            acciones = AccionesCaptura(
                cambiarClase = viewModel::cambiarClase,
                cambiarMonto = viewModel::cambiarMonto,
                elegirCategoria = viewModel::elegirCategoria,
                elegirCuenta = viewModel::elegirCuenta,
                cambiarFecha = viewModel::cambiarFecha,
                cambiarNota = viewModel::cambiarNota,
                guardar = viewModel::guardarCaptura,
                cerrar = viewModel::cerrarCaptura,
                eliminar = actual.editando?.let { id -> { pedirEliminar(id, estado, viewModel::cerrarCaptura) { porEliminar = it } } },
            ),
        )
    }

    transferencia?.let { actual ->
        DialogoTransferencia(
            transferencia = actual,
            cuentas = estado.cuentasDeDinero.map { it.cuenta } + actual.archivadas,
            acciones = AccionesTransferencia(
                editar = viewModel::editarTransferencia,
                guardar = viewModel::guardarTransferencia,
                eliminar = actual.editando?.let { id -> { pedirEliminar(id, estado, viewModel::cerrarTransferencia) { porEliminar = it } } },
                cerrar = viewModel::cerrarTransferencia,
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
    onTransferir: () -> Unit,
    lineaDeTiempo: LineaDeTiempo?,
    onVerLineaDeTiempo: () -> Unit,
    accionesTarjetas: AccionesTarjetas,
    estadoRecurrentes: EstadoRecurrentes,
    accionesRecurrentes: AccionesRecurrentes,
    onTocarMovimiento: (MovimientoResumido) -> Unit,
    onRespaldo: () -> Unit,
    onAbrirNube: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall) },
                actions = { MenuPrincipal(onRespaldo = onRespaldo, onAbrirNube = onAbrirNube) },
            )
        },
        floatingActionButton = {
            val registrar = stringResource(R.string.registrar)
            ExtendedFloatingActionButton(
                onClick = onRegistrar,
                // El texto del botón extendido no llega al árbol de accesibilidad: TalkBack
                // solo decía "Botón" (comprobado en el S24 FE con AccesibilidadTest). Se
                // deja un único nodo con nombre, rol y acción.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = registrar
                    role = Role.Button
                    onClick {
                        onRegistrar()
                        true
                    }
                },
                icon = { Icon(painterResource(R.drawable.ic_agregar), contentDescription = null) },
                text = { Text(registrar) },
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
                TarjetaSaldos(estado, lineaDeTiempo, onAgregarCuenta, onTransferir, onVerLineaDeTiempo)
            }

            if (estadoTarjetas.tarjetas.isNotEmpty()) {
                item(key = "avisos") {
                    TarjetaActivarAvisos(Modifier.padding(bottom = 16.dp))
                }
            }

            item(key = "tarjetas") {
                SeccionTarjetas(
                    estado = estadoTarjetas,
                    onAgregar = accionesTarjetas.agregar,
                    onPagar = accionesTarjetas.pagar,
                    onCapturar = accionesTarjetas.capturar,
                )
            }

            item(key = "recurrentes") {
                SeccionRecurrentes(
                    estado = estadoRecurrentes,
                    onAgregar = accionesRecurrentes.agregar,
                    onTocar = accionesRecurrentes.tocar,
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

            // Cada día es una tarjeta con sus movimientos. Un día rara vez tiene más de unas
            // decenas de movimientos, así que agruparlos en un solo elemento de la lista no
            // afecta el rendimiento.
            items(estado.dias, key = { "dia-${it.fecha}" }) { dia ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = when (dia.fecha) {
                            fechaDeHoy -> stringResource(R.string.hoy)
                            fechaDeHoy.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.ayer)
                            else -> dia.fecha.formatoEncabezado(fechaDeHoy)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
                    )
                    TarjetaFinanzas(Modifier.fillMaxWidth()) {
                        dia.movimientos.forEachIndexed { indice, movimiento ->
                            if (indice > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            FilaMovimiento(movimiento, onClick = { onTocarMovimiento(movimiento) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Saldos: el disponible real con su cuenta y el acceso a los próximos 30 días; debajo,
 * separado, el saldo de cada cuenta y la acción de agregar otra.
 */
@Composable
private fun TarjetaSaldos(
    estado: EstadoMovimientos,
    linea: LineaDeTiempo?,
    onAgregarCuenta: () -> Unit,
    onTransferir: () -> Unit,
    onVerLineaDeTiempo: () -> Unit,
) {
    TarjetaFinanzas(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(20.dp)) {
            if (estado.cuentasDeDinero.isEmpty()) {
                // Sin efectivo ni débito el disponible sería una alarma falsa.
                Text(
                    stringResource(R.string.sin_cuentas_para_disponible),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (linea == null) {
                // Solo esta tarjeta espera a la línea de tiempo; la lista de movimientos ya se ve.
                // No se muestra otra cifra mientras tanto, para que la principal no cambie de significado.
                CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
            } else {
                ResumenDisponible(linea)
            }
            TextButton(onClick = onVerLineaDeTiempo, contentPadding = PaddingValues(0.dp), enabled = linea != null) {
                Text(stringResource(R.string.ver_proximos_30_dias))
            }
            if (estado.cuentasDeDinero.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    estado.cuentasDeDinero.forEach { (cuenta, saldo) ->
                        Column {
                            Text(cuenta.nombre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(saldo.formatear(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TextButton(onClick = onAgregarCuenta, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.agregar_cuenta))
                }
                // Con una sola cuenta no hay a dónde transferir.
                if (estado.cuentasDeDinero.size >= 2) {
                    TextButton(onClick = onTransferir, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.transferir))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaMovimiento(movimiento: MovimientoResumido, onClick: () -> Unit) {
    val esDeuda = movimiento.cuenta?.tipo == TipoCuenta.PASIVO
    val neutro = MaterialTheme.colorScheme.onSurfaceVariant
    val (signo, color) = when (movimiento.clase) {
        ClaseMovimiento.GASTO -> "−" to TemaFinanzas.colores.gasto
        ClaseMovimiento.INGRESO -> "+" to TemaFinanzas.colores.ingreso
        // Una deuda inicial no es dinero que entra: sin signo.
        ClaseMovimiento.SALDO_INICIAL -> if (esDeuda) "" to neutro else "+" to TemaFinanzas.colores.ingreso
        // Pagar la tarjeta mueve dinero entre cuentas propias: no es gasto ni ingreso.
        ClaseMovimiento.PAGO_TARJETA, ClaseMovimiento.TRANSFERENCIA, ClaseMovimiento.OTRO -> "" to neutro
    }
    val desde = movimiento.cuenta?.nombre
    val hacia = movimiento.contraparte?.nombre
    val esTransferencia = movimiento.clase == ClaseMovimiento.TRANSFERENCIA && desde != null && hacia != null
    val origenDestino = if (esTransferencia) stringResource(R.string.de_a, desde!!, hacia!!) else desde
    val nota = movimiento.movimiento.descripcion.ifBlank { null }?.takeIf { movimiento.clase != ClaseMovimiento.SALDO_INICIAL }
    // TalkBack leería la flecha como "flecha derecha"; se le da la frase completa.
    val detalleHablado = if (esTransferencia) {
        listOfNotNull(stringResource(R.string.de_a_hablado, desde!!, hacia!!), nota).joinToString(", ")
    } else {
        null
    }
    val detalle = listOfNotNull(
        origenDestino,
        movimiento.movimiento.descripcion.ifBlank { null }?.takeIf { movimiento.clase != ClaseMovimiento.SALDO_INICIAL },
    ).joinToString(" · ")

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconoEnCirculo(iconoDe(movimiento)) },
        headlineContent = { Text(etiqueta(movimiento), style = MaterialTheme.typography.titleSmall) },
        supportingContent = if (detalle.isNotEmpty()) {
            {
                Text(
                    detalle,
                    modifier = if (detalleHablado != null) Modifier.semantics { contentDescription = detalleHablado } else Modifier,
                )
            }
        } else {
            null
        },
        trailingContent = {
            Text(signo + movimiento.monto.formatear(), color = color, style = MaterialTheme.typography.titleMedium)
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

/** Menú de tres puntos de la barra superior: lo que no es del día a día. */
@Composable
private fun MenuPrincipal(
    onRespaldo: () -> Unit,
    onAbrirNube: (() -> Unit)? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(painterResource(R.drawable.ic_mas), contentDescription = stringResource(R.string.mas_opciones))
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.respaldo_y_exportar)) },
                onClick = {
                    abierto = false
                    onRespaldo()
                },
            )
            if (onAbrirNube != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cuenta_y_catalogo)) },
                    onClick = {
                        abierto = false
                        onAbrirNube()
                    },
                )
            }
        }
    }
}

/** Cierra el formulario de edición y abre la confirmación de borrado del mismo movimiento. */
private fun pedirEliminar(
    id: io.github.joako601.finanzas.core.domain.libro.MovimientoId,
    estado: EstadoMovimientos,
    cerrar: () -> Unit,
    confirmar: (MovimientoResumido) -> Unit,
) {
    val resumen = estado.dias.firstNotNullOfOrNull { dia -> dia.movimientos.firstOrNull { it.movimiento.id == id } } ?: return
    cerrar()
    confirmar(resumen)
}
