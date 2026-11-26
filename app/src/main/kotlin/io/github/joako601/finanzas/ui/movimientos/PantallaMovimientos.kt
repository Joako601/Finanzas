package io.github.joako601.finanzas.ui.movimientos

import io.github.joako601.finanzas.ui.perfil.PreferenciasPrivacidad
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import io.github.joako601.finanzas.ui.formatoMes
import kotlinx.datetime.LocalDate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieConstants
import io.github.joako601.finanzas.ui.componentes.LocalMontosOcultos
import io.github.joako601.finanzas.ui.componentes.enPantalla
import io.github.joako601.finanzas.ui.componentes.fundidoAlOcultar
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.ui.metas.GastoDesdeRetiro
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.metas.DesgloseDisponibleMetas
import io.github.joako601.finanzas.ui.componentes.AnimacionLottie
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.TextoMontoAnimado
import io.github.joako601.finanzas.ui.componentes.iconoDe
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.diasDesde
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
import io.github.joako601.finanzas.ui.tarjetas.DialogoEditarTarjeta
import io.github.joako601.finanzas.ui.tarjetas.DialogoEstadoDeCuenta
import io.github.joako601.finanzas.ui.tarjetas.DialogoNuevaTarjeta
import io.github.joako601.finanzas.ui.tarjetas.DialogoPagoTarjeta
import io.github.joako601.finanzas.ui.tarjetas.EstadoTarjetas
import io.github.joako601.finanzas.ui.tarjetas.HojaDetalleTarjeta
import io.github.joako601.finanzas.ui.tarjetas.ResumenTarjeta
import io.github.joako601.finanzas.ui.tarjetas.SeccionTarjetas
import io.github.joako601.finanzas.ui.tarjetas.TarjetaActivarAvisos
import io.github.joako601.finanzas.ui.tarjetas.TarjetasViewModel
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.delay
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
    val verDetalle: (ResumenTarjeta) -> Unit = {},
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
    margenInferiorExtra: Dp = 0.dp,
    onAlternarMontos: () -> Unit = {},
    gastoPedido: GastoDesdeRetiro? = null,
    onGastoAtendido: () -> Unit = {},
    movimientoPedido: MovimientoId? = null,
    onMovimientoAtendido: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisoTope by viewModel.avisoTope.collectAsStateWithLifecycle()
    // Llegada desde "Retirar y registrar gasto" en Metas: abre la captura ya con monto y nota.
    LaunchedEffect(gastoPedido, estado.cargando) {
        if (gastoPedido != null && !estado.cargando) {
            viewModel.abrirCaptura(gastoPedido.monto, gastoPedido.nota)
            onGastoAtendido()
        }
    }
    // Llegada desde el detalle de un tope en Metas: abre ese movimiento para editarlo.
    LaunchedEffect(movimientoPedido, estado.cargando) {
        if (movimientoPedido != null && !estado.cargando) {
            estado.dias.asSequence().flatMap { it.movimientos }
                .firstOrNull { it.movimiento.id == movimientoPedido }
                ?.let(viewModel::abrirEdicion)
            onMovimientoAtendido()
        }
    }
    val filtro by viewModel.filtro.collectAsStateWithLifecycle()
    val resultado by viewModel.resultado.collectAsStateWithLifecycle()
    // Atrás cierra primero la búsqueda.
    BackHandler(enabled = filtro != null, onBack = viewModel::cerrarBusqueda)
    val captura by viewModel.captura.collectAsStateWithLifecycle()
    val nuevaCuenta by viewModel.nuevaCuenta.collectAsStateWithLifecycle()
    val transferencia by viewModel.transferencia.collectAsStateWithLifecycle()
    val avisoGuardado by viewModel.avisoGuardado.collectAsStateWithLifecycle()
    val estadoTarjetas by tarjetasViewModel.estado.collectAsStateWithLifecycle()
    val nuevaTarjeta by tarjetasViewModel.nuevaTarjeta.collectAsStateWithLifecycle()
    val edicionTarjeta by tarjetasViewModel.edicionTarjeta.collectAsStateWithLifecycle()
    val detalleTarjetaId by tarjetasViewModel.detalleTarjetaId.collectAsStateWithLifecycle()
    val pago by tarjetasViewModel.pago.collectAsStateWithLifecycle()
    val capturaEstado by tarjetasViewModel.captura.collectAsStateWithLifecycle()
    val estadoRecurrentes by recurrentesViewModel.estado.collectAsStateWithLifecycle()
    val nuevoRecurrente by recurrentesViewModel.nuevo.collectAsStateWithLifecycle()
    val registroRecurrente by recurrentesViewModel.registro.collectAsStateWithLifecycle()
    val estadoLinea by lineaDeTiempoViewModel.estado.collectAsStateWithLifecycle()
    var verLineaDeTiempo by rememberSaveable { mutableStateOf(false) }
    var porEliminar by remember { mutableStateOf<MovimientoResumido?>(null) }

    val haptica = LocalHapticFeedback.current
    LaunchedEffect(avisoGuardado?.id) {
        if (avisoGuardado != null) {
            delay(320)
            haptica.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1_250)
            viewModel.consumirAvisoGuardado()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContenidoMovimientos(
            estado = estado,
            estadoTarjetas = estadoTarjetas,
            onRegistrar = viewModel::abrirCaptura,
            onAgregarCuenta = viewModel::abrirNuevaCuenta,
            onEditarCuenta = viewModel::abrirEditarCuenta,
            onTransferir = viewModel::abrirTransferencia,
            lineaDeTiempo = estadoLinea.linea,
            desgloseMetas = estadoLinea.desgloseMetas,
            onVerLineaDeTiempo = { verLineaDeTiempo = true },
            accionesTarjetas = AccionesTarjetas(
                agregar = tarjetasViewModel::abrirNuevaTarjeta,
                pagar = tarjetasViewModel::abrirPago,
                capturar = tarjetasViewModel::abrirCaptura,
                verDetalle = tarjetasViewModel::abrirDetalle,
            ),
            estadoRecurrentes = estadoRecurrentes,
            accionesRecurrentes = AccionesRecurrentes(
                agregar = recurrentesViewModel::abrirNuevo,
                tocar = recurrentesViewModel::abrirRegistro,
            ),
            // Gastos, ingresos y transferencias se editan; lo demás solo se puede eliminar.
            onTocarMovimiento = {
                when {
                    viewModel.esEditable(it) -> viewModel.abrirEdicion(it)
                    // Un pago a tarjeta se corrige en su propio diálogo (si la tarjeta sigue activa).
                    it.clase == ClaseMovimiento.PAGO_TARJETA &&
                        estadoTarjetas.tarjetas.any { t -> t.tarjeta.cuenta.id == it.contraparte?.id } ->
                        tarjetasViewModel.abrirEdicionPago(it)
                    else -> porEliminar = it
                }
            },
            margenInferiorExtra = margenInferiorExtra,
            busqueda = Busqueda(
                filtro = filtro,
                resultado = resultado,
                abrir = viewModel::abrirBusqueda,
                cambiar = viewModel::cambiarFiltro,
                cerrar = viewModel::cerrarBusqueda,
            ),
            onAlternarMontos = onAlternarMontos,
        )

        val sinAnimaciones = rememberAnimacionesDesactivadas()
        AnimatedVisibility(
            visible = avisoGuardado != null,
            enter = if (sinAnimaciones) {
                fadeIn(tween(120))
            } else {
                fadeIn(tween(220)) +
                    scaleIn(
                        initialScale = 0.86f,
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f),
                    ) +
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f),
                    ) { it / 3 }
            },
            exit = if (sinAnimaciones) {
                fadeOut(tween(120))
            } else {
                fadeOut(tween(320)) +
                    scaleOut(targetScale = 0.94f, animationSpec = tween(320))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp + margenInferiorExtra),
        ) {
            avisoGuardado?.let { actual ->
                val anuncio = stringResource(
                    if (actual.clase == ClaseRegistro.INGRESO) {
                        R.string.ingreso_registrado
                    } else {
                        R.string.gasto_registrado
                    },
                )
                key(actual.id) {
                    Box(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = anuncio
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimacionLottie(
                            resId = R.raw.anim_movimiento_exito,
                            modifier = Modifier.size(120.dp),
                        )
                    }
                }
            }
        }
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
            cuentas = estadoRecurrentes.cuentasPara(actual.clase),
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
                editarSerie = recurrentesViewModel::abrirEdicionDesdeRegistro,
                pedirEliminar = recurrentesViewModel::pedirConfirmarEliminar,
                cancelarEliminar = recurrentesViewModel::cancelarConfirmarEliminar,
                agregarCuenta = viewModel::abrirNuevaCuenta,
            ),
        )
    }

    captura?.let { actual ->
        HojaCaptura(
            captura = actual,
            avisoTope = avisoTope,
            // Al editar, la categoría o cuenta archivada del movimiento sigue disponible.
            categorias = when (actual.clase) {
                ClaseRegistro.GASTO -> estado.categoriasDeGasto + actual.archivadas.filter { it.tipo == TipoCuenta.GASTO }
                ClaseRegistro.INGRESO -> estado.categoriasDeIngreso + actual.archivadas.filter { it.tipo == TipoCuenta.INGRESO }
            },
            // Un gasto se puede pagar con tarjeta; un ingreso solo llega a efectivo o débito.
            cuentas = estado.cuentasDeDinero.map { it.cuenta } +
                (if (actual.clase == ClaseRegistro.GASTO) estado.tarjetas else emptyList()) +
                actual.archivadas.filter {
                    if (actual.clase == ClaseRegistro.GASTO) it.tipo.esDeDinero else it.tipo == TipoCuenta.ACTIVO
                },
            acciones = AccionesCaptura(
                cambiarClase = viewModel::cambiarClase,
                cambiarMonto = viewModel::cambiarMonto,
                elegirCategoria = viewModel::elegirCategoria,
                elegirCuenta = viewModel::elegirCuenta,
                cambiarFecha = viewModel::cambiarFecha,
                cambiarNota = viewModel::cambiarNota,
                elegirMeses = viewModel::elegirMeses,
                guardar = viewModel::guardarCaptura,
                cerrar = viewModel::cerrarCaptura,
                agregarCuenta = viewModel::abrirNuevaCuenta,
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

    nuevaTarjeta?.let { actual ->
        DialogoNuevaTarjeta(
            nueva = actual,
            onEditar = tarjetasViewModel::editarNuevaTarjeta,
            onGuardar = tarjetasViewModel::guardarNuevaTarjeta,
            onCerrar = tarjetasViewModel::cerrarNuevaTarjeta,
        )
    }

    edicionTarjeta?.let { actual ->
        DialogoEditarTarjeta(
            edicion = actual,
            onEditar = tarjetasViewModel::editarTarjetaExistente,
            onGuardar = tarjetasViewModel::guardarEdicionTarjeta,
            onCerrar = tarjetasViewModel::cerrarEdicionTarjeta,
            onPedirArchivar = tarjetasViewModel::pedirArchivarTarjeta,
            onCancelarArchivar = tarjetasViewModel::cancelarArchivarTarjeta,
            onArchivar = tarjetasViewModel::archivarTarjeta,
        )
    }

    detalleTarjetaId?.let { id ->
        estadoTarjetas.tarjetas.firstOrNull { it.tarjeta.id == id }?.let { resumen ->
            HojaDetalleTarjeta(
                resumen = resumen,
                onEditarTarjeta = { tarjetasViewModel.abrirEdicionTarjeta(resumen.tarjeta) },
                onCapturarEstado = { tarjetasViewModel.abrirCaptura(resumen) },
                onPagar = { tarjetasViewModel.abrirPago(resumen) },
                onCerrar = tarjetasViewModel::cerrarDetalle,
                onEditarEstado = { tarjetasViewModel.abrirEdicionEstado(resumen.tarjeta, it) },
            )
        }
    }

    pago?.let { actual ->
        DialogoPagoTarjeta(
            pago = actual,
            cuentas = estadoTarjetas.cuentasParaPagar,
            saldosCuentas = estadoTarjetas.saldosCuentasParaPagar,
            onEditar = tarjetasViewModel::editarPago,
            onGuardar = tarjetasViewModel::guardarPago,
            onCerrar = tarjetasViewModel::cerrarPago,
            onAgregarCuenta = viewModel::abrirNuevaCuenta,
            onEliminar = actual.editando?.let { id -> { pedirEliminar(id, estado, tarjetasViewModel::cerrarPago) { porEliminar = it } } },
        )
    }

    capturaEstado?.let { actual ->
        DialogoEstadoDeCuenta(
            captura = actual,
            onEditar = tarjetasViewModel::editarCaptura,
            onGuardar = tarjetasViewModel::guardarCaptura,
            onCerrar = tarjetasViewModel::cerrarCaptura,
            onPedirEliminar = tarjetasViewModel::pedirEliminarEstado,
            onCancelarEliminar = tarjetasViewModel::cancelarEliminarEstado,
            onEliminar = tarjetasViewModel::eliminarEstado,
        )
    }

    // DialogoNuevaCuenta va después de los diálogos de pago y recurrentes para que, al abrirse
    // desde "+ Agregar cuenta", su ventana siempre quede encima incluso tras rotar la pantalla.
    nuevaCuenta?.let { actual ->
        DialogoNuevaCuenta(
            nuevaCuenta = actual,
            onCambiarNombre = viewModel::cambiarNombreCuenta,
            onCambiarSaldo = viewModel::cambiarSaldoCuenta,
            onPedirArchivar = viewModel::pedirArchivarCuenta,
            onCancelarArchivar = viewModel::cancelarArchivarCuenta,
            onArchivar = viewModel::archivarCuenta,
            puedeArchivar = actual.editando?.let { viewModel.puedeArchivar(it.cuenta) } ?: true,
            onVerMovimientos = actual.editando?.let { { viewModel.verMovimientosDe(it.cuenta.id) } },
            onGuardar = {
                viewModel.guardarNuevaCuenta { cuentaId ->
                    tarjetasViewModel.seleccionarCuentaPago(cuentaId)
                    recurrentesViewModel.seleccionarCuentaRegistro(cuentaId)
                }
            },
            onCerrar = viewModel::cerrarNuevaCuenta,
        )
    }

    // Bienvenida del Día 1: una sola vez y solo con el historial vacío (quien actualiza no la ve).
    val contexto = LocalContext.current
    val privacidad = remember { PreferenciasPrivacidad(contexto) }
    var pasoBienvenida by remember { mutableIntStateOf(0) }
    LaunchedEffect(estado.cargando) {
        if (!estado.cargando && pasoBienvenida == 0) {
            pasoBienvenida = if (!privacidad.bienvenidaVista && estado.dias.isEmpty()) 1 else -1
        }
    }
    val terminarBienvenida = {
        privacidad.bienvenidaVista = true
        pasoBienvenida = -1
    }
    // Mientras el diálogo de un paso está abierto, la bienvenida espera debajo.
    if (pasoBienvenida in 1..3 && nuevaCuenta == null && nuevoRecurrente == null && nuevaTarjeta == null) {
        DialogoBienvenida(
            paso = pasoBienvenida,
            cuentas = estado.cuentasDeDinero,
            acciones = AccionesBienvenida(
                saldoEfectivo = estado.cuentasDeDinero.firstOrNull()?.let { { viewModel.abrirEditarCuenta(it) } },
                agregarDebito = viewModel::abrirNuevaCuenta,
                agregarIngresoFijo = { recurrentesViewModel.abrirNuevo(ClaseRegistro.INGRESO) },
                agregarTarjeta = tarjetasViewModel::abrirNuevaTarjeta,
                siguiente = { if (pasoBienvenida < 3) pasoBienvenida++ else terminarBienvenida() },
                saltar = terminarBienvenida,
            ),
        )
    }

    val errorAlEliminar by viewModel.errorAlEliminar.collectAsStateWithLifecycle()
    if (errorAlEliminar) {
        AlertDialog(
            onDismissRequest = viewModel::consumirErrorAlEliminar,
            text = { Text(stringResource(R.string.error_al_eliminar)) },
            confirmButton = {
                TextButton(onClick = viewModel::consumirErrorAlEliminar) { Text(stringResource(R.string.aceptar)) }
            },
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
    margenInferiorExtra: Dp = 0.dp,
    onEditarCuenta: (SaldoCuenta) -> Unit = {},
    desgloseMetas: DesgloseDisponibleMetas? = null,
    busqueda: Busqueda = Busqueda(),
    onAlternarMontos: () -> Unit = {},
) {
    val montosOcultos = LocalMontosOcultos.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    // Modo discreto para usar la app en público: "$ ••••" en lugar de montos.
                    IconButton(onClick = onAlternarMontos) {
                        Icon(
                            painterResource(if (montosOcultos) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo),
                            contentDescription = stringResource(if (montosOcultos) R.string.mostrar_montos else R.string.ocultar_montos),
                        )
                    }
                    if (busqueda.filtro == null) {
                        IconButton(onClick = busqueda.abrir) {
                            Icon(painterResource(R.drawable.ic_buscar), contentDescription = stringResource(R.string.buscar_movimientos))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val registrar = stringResource(R.string.registrar)
            ExtendedFloatingActionButton(
                onClick = onRegistrar,
                // El texto del botón extendido no llega al árbol de accesibilidad: TalkBack
                // solo decía "Botón" (comprobado en el S24 FE con AccesibilidadTest). Se
                // deja un único nodo con nombre, rol y acción.
                modifier = Modifier
                    .padding(bottom = margenInferiorExtra)
                    .clearAndSetSemantics {
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

        // Cambia sola a medianoche aunque la app siga abierta: "Hoy" no se queda en ayer.
        val fechaDeHoy by remember { diasDesde(::hoy) }.collectAsState(hoy())
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(margenes),
            // Espacio extra abajo para que el botón flotante y la barra inferior no tapen el último movimiento.
            contentPadding = PaddingValues(
                top = margenes.calculateTopPadding(),
                bottom = margenes.calculateBottomPadding() + 88.dp + margenInferiorExtra,
            ),
        ) {
            val filtro = busqueda.filtro
            if (filtro != null) {
                item(key = "busqueda", contentType = "busqueda") {
                    PanelBusqueda(filtro, busqueda.resultado, estado, busqueda.cambiar, busqueda.cerrar)
                }
            } else {
                item(key = "saldos", contentType = "saldos") {
                    TarjetaSaldos(
                        estado = estado,
                        linea = lineaDeTiempo,
                        desgloseMetas = desgloseMetas,
                        onAgregarCuenta = onAgregarCuenta,
                        onEditarCuenta = onEditarCuenta,
                        onTransferir = onTransferir,
                        onVerLineaDeTiempo = onVerLineaDeTiempo,
                    )
                }

                val tieneRecordatoriosProgramables = estadoTarjetas.tarjetas.isNotEmpty() ||
                    estadoRecurrentes.recurrentes.any { it.recurrente.clase == ClaseRegistro.GASTO }
                if (tieneRecordatoriosProgramables) {
                    item(key = "avisos", contentType = "avisos") {
                        TarjetaActivarAvisos(Modifier.padding(bottom = 16.dp))
                    }
                }

                item(key = "tarjetas", contentType = "tarjetas") {
                    SeccionTarjetas(
                        estado = estadoTarjetas,
                        onAgregar = accionesTarjetas.agregar,
                        onPagar = accionesTarjetas.pagar,
                        onCapturar = accionesTarjetas.capturar,
                        onVerDetalle = accionesTarjetas.verDetalle,
                    )
                }

                item(key = "recurrentes", contentType = "recurrentes") {
                    SeccionRecurrentes(
                        estado = estadoRecurrentes,
                        onAgregar = accionesRecurrentes.agregar,
                        onTocar = accionesRecurrentes.tocar,
                    )
                }

                if (estado.dias.isEmpty()) {
                    item(key = "vacio", contentType = "vacio") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AnimacionLottie(
                                resId = R.raw.anim_finanzas_vacio,
                                modifier = Modifier.size(112.dp),
                                iteraciones = LottieConstants.IterateForever,
                            )
                            Text(
                                text = stringResource(R.string.sin_movimientos),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

            }

            // Cada día es una tarjeta con sus movimientos. Un día rara vez tiene más de unas
            // decenas de movimientos, así que agruparlos en un solo elemento de la lista no
            // afecta el rendimiento.
            val dias = if (filtro != null) busqueda.resultado?.dias.orEmpty() else estado.dias
            items(dias, key = { "dia-${it.fecha}" }, contentType = { "dia" }) { dia ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = when (dia.fecha) {
                            fechaDeHoy -> stringResource(R.string.hoy)
                            fechaDeHoy.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.ayer)
                            else -> dia.fecha.formatoEncabezado(fechaDeHoy)
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
                    )
                    TarjetaFinanzas(Modifier.fillMaxWidth()) {
                        dia.movimientos.forEachIndexed { indice, movimiento ->
                            if (indice > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            FilaMovimiento(movimiento, onClick = { onTocarMovimiento(movimiento) })
                        }
                        // Con un solo movimiento el total repetiría su monto; se muestra desde dos.
                        // Misma regla que la búsqueda: neto, o la suma del tipo filtrado.
                        val total = totalDe(dia.movimientos, filtro?.clase)
                        if (dia.movimientos.size > 1 && !total.esCero) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.total_del_dia),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    // Con filtro por clase es una suma de montos, no un neto: sin "+" (igual que la búsqueda).
                                    text = when {
                                        total.esNegativo -> "−" + (-total).enPantalla()
                                        filtro?.clase == null -> "+" + total.enPantalla()
                                        else -> total.enPantalla()
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                    modifier = Modifier.fundidoAlOcultar(),
                                )
                            }
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
    desgloseMetas: DesgloseDisponibleMetas?,
    onAgregarCuenta: () -> Unit,
    onEditarCuenta: (SaldoCuenta) -> Unit,
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
                ResumenDisponible(linea, desgloseMetas)
            }
            TextButton(onClick = onVerLineaDeTiempo, contentPadding = PaddingValues(0.dp), enabled = linea != null) {
                Text(stringResource(R.string.ver_proximos_30_dias))
            }
            if (estado.cuentasDeDinero.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val etiquetaEditarCuenta = stringResource(R.string.editar_cuenta)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    estado.cuentasDeDinero.forEach { saldoCuenta ->
                        val (cuenta, saldo) = saldoCuenta
                        key(cuenta.id) {
                            Column(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = etiquetaEditarCuenta,
                                        onClick = { onEditarCuenta(saldoCuenta) },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(cuenta.nombre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextoMontoAnimado(monto = saldo, style = MaterialTheme.typography.titleMedium)
                            }
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
internal fun FilaMovimiento(movimiento: MovimientoResumido, onClick: (() -> Unit)?) {
    val esDeuda = movimiento.cuenta?.tipo == TipoCuenta.PASIVO
    val neutro = MaterialTheme.colorScheme.onSurfaceVariant
    val (signo, color) = when (movimiento.clase) {
        ClaseMovimiento.GASTO -> "−" to TemaFinanzas.colores.gasto
        ClaseMovimiento.INGRESO -> "+" to TemaFinanzas.colores.ingreso
        // Una deuda inicial no es dinero que entra: sin signo. Un ajuste negativo en activo lleva "−" neutro.
        ClaseMovimiento.SALDO_INICIAL -> when {
            esDeuda -> "" to neutro
            movimiento.esAjusteALaBaja -> "−" to neutro
            else -> "+" to TemaFinanzas.colores.ingreso
        }
        // Pagar la tarjeta mueve dinero entre cuentas propias: no es gasto ni ingreso.
        ClaseMovimiento.PAGO_TARJETA, ClaseMovimiento.TRANSFERENCIA, ClaseMovimiento.OTRO -> "" to neutro
    }
    val desde = movimiento.cuenta?.nombre
    val hacia = movimiento.contraparte?.nombre
    val origenDestino = if (movimiento.clase == ClaseMovimiento.TRANSFERENCIA && desde != null && hacia != null) {
        stringResource(R.string.de_a, desde, hacia)
    } else {
        desde
    }
    val nota = movimiento.movimiento.descripcion.ifBlank { null }?.takeIf { movimiento.clase != ClaseMovimiento.SALDO_INICIAL }
    // TalkBack leería la flecha como "flecha derecha"; se le da la frase completa.
    val detalleHablado = if (movimiento.clase == ClaseMovimiento.TRANSFERENCIA && desde != null && hacia != null) {
        listOfNotNull(stringResource(R.string.de_a_hablado, desde, hacia), nota).joinToString(", ")
    } else {
        null
    }
    val detalle = listOfNotNull(
        origenDestino,
        movimiento.movimiento.descripcion.ifBlank { null }?.takeIf { movimiento.clase != ClaseMovimiento.SALDO_INICIAL },
    ).joinToString(" · ")

    ListItem(
        // Sin acción (p. ej. en Análisis) se une como un solo elemento para TalkBack.
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {},
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
            Text(signo + movimiento.monto.enPantalla(), color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fundidoAlOcultar())
        },
    )
}

@Composable
private fun etiqueta(movimiento: MovimientoResumido): String = when (movimiento.clase) {
    ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO ->
        movimiento.categoria?.nombre ?: stringResource(R.string.movimiento)
    ClaseMovimiento.SALDO_INICIAL -> stringResource(
        when {
            movimiento.movimiento.descripcion == Registro.DESCRIPCION_AJUSTE -> R.string.ajuste_de_saldo
            movimiento.cuenta?.tipo == TipoCuenta.PASIVO -> R.string.deuda_inicial
            else -> R.string.saldo_inicial
        },
    )
    ClaseMovimiento.PAGO_TARJETA ->
        movimiento.contraparte?.let { stringResource(R.string.pago_a_tarjeta, it.nombre) } ?: stringResource(R.string.movimiento)
    ClaseMovimiento.TRANSFERENCIA -> stringResource(R.string.transferencia)
    ClaseMovimiento.OTRO -> stringResource(R.string.movimiento)
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

/** Estado y acciones de la búsqueda de Inicio. Con [filtro] `null` está cerrada. */
class Busqueda(
    val filtro: Filtro? = null,
    val resultado: ResultadoFiltro? = null,
    val abrir: () -> Unit = {},
    val cambiar: (Filtro.() -> Filtro) -> Unit = {},
    val cerrar: () -> Unit = {},
)

/** Texto, tipo, mes y cuenta; debajo, cuántos movimientos quedan y cuánto suman. */
@Composable
private fun PanelBusqueda(
    filtro: Filtro,
    resultado: ResultadoFiltro?,
    estado: EstadoMovimientos,
    cambiar: (Filtro.() -> Filtro) -> Unit,
    cerrar: () -> Unit,
) {
    val meses = remember(estado.dias) { estado.dias.map { LocalDate(it.fecha.year, it.fecha.month, 1) }.distinct() }
    val cuentas = estado.cuentasDeDinero.map { it.cuenta } + estado.tarjetas + estado.archivadas.map { it.cuenta }
    // Solo las que aparecen en el historial (incluidas archivadas), del tipo elegido si hay uno.
    val categorias = remember(estado.dias, filtro.clase) {
        estado.dias.asSequence().flatMap { it.movimientos }
            .filter { filtro.clase == null || it.clase == filtro.clase }
            .mapNotNull { it.categoria }.distinctBy { it.id }.sortedBy { it.nombre }.toList()
    }
    TarjetaFinanzas(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = filtro.texto,
                onValueChange = { texto -> cambiar { copy(texto = texto) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.buscar_nota_categoria)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_buscar), contentDescription = null) },
                singleLine = true,
            )
            FilaFiltro(stringResource(R.string.filtro_tipo)) {
                FilterChip(selected = filtro.clase == null, onClick = { cambiar { copy(clase = null, categoriaId = null) } }, label = { Text(stringResource(R.string.filtro_todos)) })
                CLASES_FILTRO.forEach { clase ->
                    FilterChip(
                        selected = filtro.clase == clase,
                        // La categoría elegida puede no existir en el tipo nuevo: se limpia para no quedar oculta.
                        onClick = { cambiar { copy(clase = clase, categoriaId = null) } },
                        label = {
                            Text(
                                stringResource(
                                    when (clase) {
                                        ClaseMovimiento.GASTO -> R.string.gasto
                                        ClaseMovimiento.INGRESO -> R.string.ingreso
                                        else -> R.string.transferencia
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            FilaFiltro(stringResource(R.string.filtro_mes)) {
                FilterChip(selected = filtro.mes == null, onClick = { cambiar { copy(mes = null) } }, label = { Text(stringResource(R.string.filtro_todos)) })
                meses.forEach { mes ->
                    FilterChip(
                        selected = filtro.mes == mes,
                        onClick = { cambiar { copy(mes = mes) } },
                        label = { Text("${mes.formatoMes()} ${mes.year}") },
                    )
                }
            }
            if (categorias.isNotEmpty()) {
                FilaFiltro(stringResource(R.string.categoria)) {
                    FilterChip(selected = filtro.categoriaId == null, onClick = { cambiar { copy(categoriaId = null) } }, label = { Text(stringResource(R.string.filtro_todas)) })
                    categorias.forEach { categoria ->
                        FilterChip(
                            selected = filtro.categoriaId == categoria.id,
                            onClick = { cambiar { copy(categoriaId = categoria.id) } },
                            label = { Text(categoria.nombre) },
                        )
                    }
                }
            }
            FilaFiltro(stringResource(R.string.cuenta)) {
                FilterChip(selected = filtro.cuentaId == null, onClick = { cambiar { copy(cuentaId = null) } }, label = { Text(stringResource(R.string.filtro_todas)) })
                cuentas.forEach { cuenta ->
                    FilterChip(
                        selected = filtro.cuentaId == cuenta.id,
                        onClick = { cambiar { copy(cuentaId = cuenta.id) } },
                        label = { Text(cuenta.nombre) },
                    )
                }
            }
            if (resultado != null) {
                Text(
                    text = stringResource(
                        if (filtro.clase == null) R.string.filtro_resultado_neto else R.string.filtro_resultado_total,
                        pluralStringResource(R.plurals.filtro_conteo, resultado.conteo, resultado.conteo),
                        if (resultado.total.esNegativo) "−" + (-resultado.total).enPantalla() else resultado.total.enPantalla(),
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.fundidoAlOcultar(),
                )
            }
            TextButton(onClick = cerrar, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.cerrar_busqueda))
            }
        }
    }
}

/** Una fila de chips con desplazamiento horizontal: los meses y las cuentas pueden ser muchos. */
@Composable
private fun FilaFiltro(titulo: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
