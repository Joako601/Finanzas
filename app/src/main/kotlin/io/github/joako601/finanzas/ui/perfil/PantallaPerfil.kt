package io.github.joako601.finanzas.ui.perfil

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.recordatorios.abrirAjustesDeAvisos
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.BotonSecundario
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.TarjetaFinanzas
import io.github.joako601.finanzas.ui.componentes.rememberAnimacionesDesactivadas
import io.github.joako601.finanzas.ui.componentes.toqueTactil
import io.github.joako601.finanzas.ui.formatoDiaCorto
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.respaldo.DialogoNuevoRespaldo
import io.github.joako601.finanzas.ui.respaldo.DialogoRestaurar
import io.github.joako601.finanzas.ui.respaldo.EstadoRespaldo
import io.github.joako601.finanzas.ui.respaldo.Progreso
import io.github.joako601.finanzas.ui.respaldo.RespaldoViewModel
import io.github.joako601.finanzas.ui.tarjetas.recordarAvisos
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Un respaldo de hasta un mes se muestra en verde; más viejo, en color de aviso. */
private const val DIAS_RESPALDO_RECIENTE = 30

/** Pantallas que se abren desde una fila de "Ajustes" del Perfil. */
enum class SubpantallaPerfil { NINGUNA, CUENTAS, CATEGORIAS, PRIVACIDAD, APARIENCIA, AVISOS, RESPALDO, EXPORTAR, ACERCA }

/**
 * Pestaña 4 ("Perfil"): encabezado con la cuenta (si la hay) y una lista de ajustes a la que
 * se pueden sumar filas: respaldo cifrado, exportación CSV, recordatorios y, con sesión en la
 * distribución nube, catálogo y cerrar sesión (D-018, D-020).
 *
 * @param cuenta datos de la cuenta nube; `null` en la distribución privada.
 * @param subpantalla subpantalla abierta; vive en el contenedor para que el aviso de respaldo
 *   de Inicio pueda abrir Respaldo directo.
 */
@Composable
fun PantallaPerfil(
    cuenta: CuentaPerfil?,
    subpantalla: SubpantallaPerfil,
    onSubpantalla: (SubpantallaPerfil) -> Unit,
    viewModel: RespaldoViewModel = viewModel(factory = RespaldoViewModel.Fabrica),
    margenInferiorExtra: Dp = 0.dp,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val nuevo by viewModel.nuevo.collectAsStateWithLifecycle()
    val porRestaurar by viewModel.porRestaurar.collectAsStateWithLifecycle()
    var fechaDeHoy by remember { mutableStateOf(hoy()) }
    val libre = estado.tarea == null

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

    // Respaldo y Exportar comparten el ViewModel: el aviso de una no debe aparecer en la otra.
    LaunchedEffect(subpantalla) { viewModel.limpiarAviso() }

    // Registrado después del de ContenedorPrincipal: Atrás cierra primero la subpantalla.
    BackHandler(enabled = subpantalla != SubpantallaPerfil.NINGUNA) {
        onSubpantalla(SubpantallaPerfil.NINGUNA)
    }

    val sinAnimaciones = rememberAnimacionesDesactivadas()
    val desplazamientoPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val volver = { onSubpantalla(SubpantallaPerfil.NINGUNA) }

    AnimatedContent(
        targetState = subpantalla,
        transitionSpec = {
            if (sinAnimaciones) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(120))
            } else {
                val direccion = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(300)) { direccion * desplazamientoPx } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(220)) { -direccion * desplazamientoPx } + fadeOut(tween(220)))
            }
        },
        label = "subpantallaPerfil",
    ) { actual ->
        when (actual) {
            SubpantallaPerfil.NINGUNA -> RaizPerfil(
                cuenta = cuenta,
                ultimoRespaldo = estado.ultimo,
                fechaDeHoy = fechaDeHoy,
                onSubpantalla = onSubpantalla,
                margenInferiorExtra = margenInferiorExtra,
            )

            SubpantallaPerfil.RESPALDO -> Subpantalla(
                titulo = stringResource(R.string.respaldo_cifrado),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) {
                val ultimo = estado.ultimo
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

            SubpantallaPerfil.CUENTAS -> Subpantalla(
                titulo = stringResource(R.string.perfil_cuentas),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) { ContenidoCuentas() }

            SubpantallaPerfil.CATEGORIAS -> Subpantalla(
                titulo = stringResource(R.string.perfil_categorias),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) { ContenidoCategorias() }

            SubpantallaPerfil.APARIENCIA -> Subpantalla(
                titulo = stringResource(R.string.perfil_apariencia),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) { ContenidoApariencia() }

            SubpantallaPerfil.AVISOS -> Subpantalla(
                titulo = stringResource(R.string.perfil_recordatorios),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) { ContenidoAvisos() }

            SubpantallaPerfil.PRIVACIDAD -> Subpantalla(
                titulo = stringResource(R.string.perfil_privacidad),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) { ContenidoPrivacidad() }

            SubpantallaPerfil.ACERCA -> Subpantalla(
                titulo = stringResource(R.string.perfil_acerca),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) { ContenidoAcerca() }

            SubpantallaPerfil.EXPORTAR -> Subpantalla(
                titulo = stringResource(R.string.exportar_csv),
                estado = estado,
                onVolver = volver,
                margenInferiorExtra = margenInferiorExtra,
            ) {
                Text(
                    text = stringResource(R.string.explicacion_csv),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BotonSecundario(
                    texto = stringResource(R.string.exportar_movimientos),
                    onClick = { destinoCsv.launch("kren-movimientos-${hoy()}.csv") },
                    habilitado = libre,
                )
            }
        }
    }

    nuevo?.let { actual ->
        DialogoNuevoRespaldo(
            nuevo = actual,
            onEditar = viewModel::editarNuevo,
            onContinuar = { if (viewModel.validarNuevo()) destinoRespaldo.launch("kren-${hoy()}.respaldo") },
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

@Composable
private fun RaizPerfil(
    cuenta: CuentaPerfil?,
    ultimoRespaldo: LocalDate?,
    fechaDeHoy: LocalDate,
    onSubpantalla: (SubpantallaPerfil) -> Unit,
    margenInferiorExtra: Dp,
) {
    val context = LocalContext.current
    val avisos = recordarAvisos()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { margenes ->
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
                        top = 32.dp,
                        bottom = margenes.calculateBottomPadding() + margenInferiorExtra + 28.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.nav_perfil),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(24.dp))

                EncabezadoPerfil(cuenta)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (cuenta?.conSesion == true) {
                    Spacer(Modifier.height(24.dp))
                    TarjetaCatalogo(onClick = cuenta.onAbrirCatalogo)
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.perfil_ajustes),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))

                FilaAjuste(
                    icono = R.drawable.ic_cat_tarjeta,
                    titulo = stringResource(R.string.perfil_cuentas),
                    onClick = { onSubpantalla(SubpantallaPerfil.CUENTAS) },
                )
                FilaAjuste(
                    icono = R.drawable.ic_cat_otros,
                    titulo = stringResource(R.string.perfil_categorias),
                    onClick = { onSubpantalla(SubpantallaPerfil.CATEGORIAS) },
                )
                FilaAjuste(
                    icono = R.drawable.ic_candado,
                    titulo = stringResource(R.string.perfil_privacidad),
                    onClick = { onSubpantalla(SubpantallaPerfil.PRIVACIDAD) },
                )
                FilaAjuste(
                    icono = R.drawable.ic_ojo,
                    titulo = stringResource(R.string.perfil_apariencia),
                    onClick = { onSubpantalla(SubpantallaPerfil.APARIENCIA) },
                )
                FilaAjuste(
                    icono = R.drawable.ic_respaldo,
                    titulo = stringResource(R.string.respaldo_cifrado),
                    // La fecha real en lugar de un "Al día" perpetuo; pasa a aviso si tiene más de 30 días.
                    estado = ultimoRespaldo?.formatoDiaCorto(fechaDeHoy) ?: stringResource(R.string.perfil_estado_pendiente),
                    colorEstado = if (ultimoRespaldo != null && ultimoRespaldo.daysUntil(fechaDeHoy) <= DIAS_RESPALDO_RECIENTE) {
                        TemaFinanzas.colores.ingreso
                    } else {
                        TemaFinanzas.colores.aviso
                    },
                    onClick = { onSubpantalla(SubpantallaPerfil.RESPALDO) },
                )
                FilaAjuste(
                    icono = R.drawable.ic_exportar,
                    titulo = stringResource(R.string.perfil_exportar),
                    onClick = { onSubpantalla(SubpantallaPerfil.EXPORTAR) },
                )
                FilaAjuste(
                    icono = R.drawable.ic_avisos,
                    titulo = stringResource(R.string.perfil_recordatorios),
                    estado = stringResource(if (avisos.activos) R.string.perfil_avisos_activos else R.string.perfil_avisos_inactivos),
                    colorEstado = if (avisos.activos) TemaFinanzas.colores.ingreso else TemaFinanzas.colores.aviso,
                    // Abre la subpantalla: activar y elegir las horas de los avisos.
                    onClick = { onSubpantalla(SubpantallaPerfil.AVISOS) },
                    conDivisor = cuenta?.conSesion == true,
                )
                if (cuenta?.conSesion == true) {
                    FilaAjuste(
                        icono = R.drawable.ic_salir,
                        titulo = stringResource(R.string.perfil_cerrar_sesion),
                        conFlecha = false,
                        conDivisor = false,
                        onClick = cuenta.onCerrarSesion,
                    )
                }
                Spacer(Modifier.height(32.dp))
                FilaAjuste(
                    icono = R.drawable.ic_info,
                    titulo = stringResource(R.string.perfil_acerca),
                    conDivisor = false,
                    onClick = { onSubpantalla(SubpantallaPerfil.ACERCA) },
                )
            }
        }
    }
}

/** Encabezado tipo "Profile": avatar, nombre y una línea de apoyo. */
@Composable
private fun EncabezadoPerfil(cuenta: CuentaPerfil?) {
    val conSesion = cuenta?.conSesion == true
    val titulo: String
    val texto: String
    when {
        cuenta == null -> {
            titulo = stringResource(R.string.perfil_privada_titulo)
            texto = stringResource(R.string.perfil_privada_texto)
        }
        conSesion -> {
            titulo = cuenta.nombreVisible.ifEmpty { stringResource(R.string.perfil_tu_cuenta) }
            texto = cuenta.correo.orEmpty()
        }
        else -> {
            titulo = stringResource(R.string.perfil_sin_sesion_titulo)
            texto = stringResource(R.string.perfil_sin_sesion_texto)
        }
    }
    // Sin sesión en la nube el encabezado es el botón para entrar; en los demás casos solo informa.
    val onClick = if (cuenta != null && !conSesion) cuenta.onEntrar else null
    val interaccion = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .toqueTactil(interaccion)
                        .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    // Sin acción, TalkBack lee nombre y correo de una vez.
                    Modifier.semantics(mergeDescendants = true) { }
                },
            )
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conSesion && cuenta.inicial.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(TemaFinanzas.colores.fondoIcono)
                    // Decorativo: el nombre ya está escrito al lado.
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cuenta.inicial,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TemaFinanzas.colores.icono,
                )
            }
        } else {
            IconoEnCirculo(icono = R.drawable.ic_perfil, tamano = 64.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_flecha_derecha),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TarjetaCatalogo(onClick: () -> Unit) {
    val interaccion = remember { MutableInteractionSource() }
    TarjetaFinanzas(
        Modifier
            .fillMaxWidth()
            .toqueTactil(interaccion)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.perfil_catalogo_titulo),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.perfil_catalogo_texto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_flecha_derecha),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Fila de ajuste tipo lista: ícono, título, estado opcional y flecha. Toda la fila es el
 * botón (≥ 56 dp), con divisor `outlineVariant` debajo.
 */
@Composable
private fun FilaAjuste(
    @DrawableRes icono: Int,
    titulo: String,
    onClick: () -> Unit,
    estado: String? = null,
    colorEstado: Color = Color.Unspecified,
    etiquetaAccion: String? = null,
    conFlecha: Boolean = true,
    conDivisor: Boolean = true,
) {
    val interaccion = remember { MutableInteractionSource() }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toqueTactil(interaccion)
                .clickable(
                    interactionSource = interaccion,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = etiquetaAccion,
                    onClick = onClick,
                )
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icono),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (estado != null) {
                Spacer(Modifier.width(8.dp))
                Insignia(texto = estado, color = colorEstado)
            }
            if (conFlecha) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_flecha_derecha),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (conDivisor) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Subpantalla de un ajuste: barra con "Volver", progreso/aviso del respaldo y el contenido en
 * una tarjeta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Subpantalla(
    titulo: String,
    estado: EstadoRespaldo,
    onVolver: () -> Unit,
    margenInferiorExtra: Dp,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            painter = painterResource(R.drawable.ic_volver),
                            contentDescription = stringResource(R.string.perfil_volver),
                        )
                    }
                },
                title = {
                    Text(
                        text = titulo,
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
                TarjetaFinanzas(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = contenido,
                    )
                }
            }
        }
    }
}

/**
 * Insignia de estado sobre `surfaceContainerLowest` y borde de 1 dp para garantizar contraste
 * WCAG AA >= 4.5:1.
 */
@Composable
private fun Insignia(texto: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
