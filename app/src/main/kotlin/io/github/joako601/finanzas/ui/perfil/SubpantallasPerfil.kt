package io.github.joako601.finanzas.ui.perfil

import android.content.Intent
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.ETIQUETA_LOG
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.MainActivity
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.recordatorios.dispararProgramadorAvisos
import io.github.joako601.finanzas.recordatorios.abrirAjustesDeAvisos
import io.github.joako601.finanzas.ui.tarjetas.recordarAvisos
import io.github.joako601.finanzas.ui.componentes.SelectorSegmentadoSeda
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.FilterChip
import io.github.joako601.finanzas.ui.movimientos.EditorCategoria
import io.github.joako601.finanzas.ui.componentes.iconosPorNombre
import io.github.joako601.finanzas.ui.componentes.iconosElegibles
import io.github.joako601.finanzas.ui.componentes.iconoDeCategoria
import io.github.joako601.finanzas.ui.componentes.IconoEnCirculo
import io.github.joako601.finanzas.ui.componentes.Campo
import io.github.joako601.finanzas.core.domain.libro.ErrorCategoria
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.ui.componentes.BotonPrincipal
import io.github.joako601.finanzas.ui.componentes.enPantalla
import io.github.joako601.finanzas.ui.movimientos.MovimientosViewModel
import io.github.joako601.finanzas.ui.tema.TemaFinanzas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Privacidad y seguridad: modo discreto, pantalla segura, bloqueo y borrar los datos locales. */
@Composable
internal fun ContenidoPrivacidad() {
    val contexto = LocalContext.current
    val actividad = LocalActivity.current
    val alcance = rememberCoroutineScope()
    val privacidad = remember { PreferenciasPrivacidad(contexto) }
    // El PIN del teléfono puede ponerse o quitarse en Ajustes: se vuelve a leer al regresar.
    var seguro by remember { mutableStateOf(dispositivoSeguro(contexto)) }
    var iniciarOculto by remember { mutableStateOf(privacidad.iniciarOculto) }
    var recientes by remember { mutableStateOf(privacidad.ocultarEnRecientes) }
    var bloqueo by remember { mutableStateOf(privacidad.bloqueo) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    // Sin PIN el bloqueo no protege nada: no deja la pantalla segura trabada.
    fun aplicarSegura() {
        actividad?.window?.let { ventana -> aplicarPantallaSegura(ventana, recientes || (bloqueo && seguro)) }
    }
    LifecycleResumeEffect(Unit) {
        seguro = dispositivoSeguro(contexto)
        aplicarSegura()
        onPauseOrDispose {}
    }
    var borrando by remember { mutableStateOf(false) }
    var errorAlBorrar by remember { mutableStateOf(false) }

    FilaInterruptor(R.string.privacidad_discreto, R.string.privacidad_discreto_texto, iniciarOculto) {
        iniciarOculto = it
        privacidad.iniciarOculto = it
    }
    FilaInterruptor(R.string.privacidad_recientes, R.string.privacidad_recientes_texto, recientes) {
        recientes = it
        privacidad.ocultarEnRecientes = it
        aplicarSegura()
    }
    FilaInterruptor(
        titulo = R.string.privacidad_bloqueo,
        texto = if (seguro) R.string.privacidad_bloqueo_texto else R.string.privacidad_bloqueo_sin_pin,
        activo = bloqueo && seguro,
        habilitado = seguro,
    ) {
        bloqueo = it
        privacidad.bloqueo = it
        // Quien lo activa ya está dentro: no se le pide el PIN hasta que salga de la app.
        if (it) MainActivity.desbloqueada = true
        // Con bloqueo, la miniatura de recientes tampoco debe mostrar nada.
        aplicarSegura()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        stringResource(R.string.privacidad_borrar_texto),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (errorAlBorrar) Text(stringResource(R.string.privacidad_borrar_error), color = MaterialTheme.colorScheme.error)
    TextButton(
        onClick = { confirmarBorrado = true },
        enabled = !borrando,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(R.string.privacidad_borrar)) }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text(stringResource(R.string.privacidad_borrar_pregunta)) },
            text = { Text(stringResource(R.string.privacidad_borrar_ayuda)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarBorrado = false
                        borrando = true
                        errorAlBorrar = false
                        alcance.launch {
                            try {
                                privacidad.borrarDatosLocales(contexto.applicationContext as FinanzasApp)
                                // Arranca de cero como recién instalada: tarea nueva, sin ViewModels ni
                                // estado guardado (recreate() conservaría filtros, pestaña y montos ocultos).
                                actividad?.let { a ->
                                    a.startActivity(
                                        Intent(a, MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(ETIQUETA_LOG, "No se borraron los datos locales", e)
                                errorAlBorrar = true
                            } finally {
                                borrando = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.privacidad_borrar_confirmar)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = false }) { Text(stringResource(R.string.cancelar)) }
            },
        )
    }
}

/**
 * Cuentas y tarjetas: elegir con cuál se abre la captura, archivar las que ya no se usan y
 * reactivar las archivadas. Archivar nunca borra historial.
 */
@Composable
internal fun ContenidoCuentas(viewModel: MovimientosViewModel = viewModel(factory = MovimientosViewModel.Fabrica)) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val alRegistrar by viewModel.cuentaAlRegistrar.collectAsStateWithLifecycle()
    val error by viewModel.errorCuentas.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(viewModel::consumirErrorCuentas) }
    var porArchivar by remember { mutableStateOf<Cuenta?>(null) }
    // Lo que el usuario entiende: saldo en efectivo y débito, deuda (positiva) en una tarjeta.
    fun montoDe(cuenta: Cuenta): Dinero = (estado.saldos[cuenta.id] ?: Dinero.CERO).let { if (cuenta.tipo == TipoCuenta.PASIVO) -it else it }

    Text(
        stringResource(R.string.cuentas_al_registrar),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (error) Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)

    Encabezado(R.string.cuentas_activas)
    (estado.cuentasDeDinero.map { it.cuenta } + estado.tarjetas).forEach { cuenta ->
        FilaCuenta(
            cuenta = cuenta,
            monto = montoDe(cuenta),
            seleccionada = alRegistrar == cuenta.id,
            onElegir = { viewModel.elegirCuentaAlRegistrar(cuenta.id) },
            accion = R.string.archivar,
            accionHabilitada = viewModel.puedeArchivar(cuenta),
            onAccion = { porArchivar = cuenta },
        )
    }
    if (estado.cuentasDeDinero.size == 1) {
        Text(
            stringResource(R.string.cuentas_ultima_activa),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (estado.archivadas.isNotEmpty()) {
        Encabezado(R.string.cuentas_archivadas)
        estado.archivadas.forEach { (cuenta) ->
            FilaCuenta(
                cuenta = cuenta,
                monto = montoDe(cuenta),
                seleccionada = null,
                onElegir = {},
                accion = R.string.cuentas_reactivar,
                onAccion = { viewModel.cambiarArchivada(cuenta, archivada = false) },
            )
        }
    }

    porArchivar?.let { cuenta ->
        val monto = montoDe(cuenta)
        AlertDialog(
            onDismissRequest = { porArchivar = null },
            title = { Text(stringResource(R.string.archivar_pregunta, cuenta.nombre)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (cuenta.tipo == TipoCuenta.PASIVO) R.string.archivar_tarjeta_ayuda else R.string.archivar_cuenta_ayuda))
                    // Un saldo o una deuda que deja de contar cambia el disponible: se dice con su monto.
                    if (!monto.esCero) {
                        Text(
                            stringResource(
                                if (cuenta.tipo == TipoCuenta.PASIVO) R.string.cuentas_aviso_deuda else R.string.cuentas_aviso_saldo,
                                monto.formatear(),
                            ),
                            color = TemaFinanzas.colores.aviso,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cambiarArchivada(cuenta, archivada = true)
                    porArchivar = null
                }) { Text(stringResource(R.string.archivar)) }
            },
            dismissButton = { TextButton(onClick = { porArchivar = null }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

/**
 * Categorías de gasto e ingreso: crear, renombrar, elegir ícono, archivar y reactivar.
 * Archivar no borra: los movimientos conservan su categoría y vuelve al reactivarla.
 */
@Composable
internal fun ContenidoCategorias(viewModel: MovimientosViewModel = viewModel(factory = MovimientosViewModel.Fabrica)) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val editor by viewModel.editorCategoria.collectAsStateWithLifecycle()
    val error by viewModel.errorCuentas.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(viewModel::consumirErrorCuentas) }
    if (error) Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)

    listOf(
        Triple(R.string.categorias_gastos, TipoCuenta.GASTO, estado.categoriasDeGasto),
        Triple(R.string.categorias_ingresos, TipoCuenta.INGRESO, estado.categoriasDeIngreso),
    ).forEach { (titulo, tipo, lista) ->
        Encabezado(titulo)
        // Orden alfabético: aquí se buscan por nombre, no por uso como en la captura.
        lista.sortedBy { it.nombre }.forEach { categoria ->
            FilaCategoria(
                categoria = categoria,
                onEditar = { viewModel.abrirEditarCategoria(categoria) },
                accion = R.string.archivar,
                onAccion = { viewModel.cambiarArchivada(categoria, archivada = true) },
            )
        }
        TextButton(onClick = { viewModel.abrirNuevaCategoria(tipo) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (tipo == TipoCuenta.GASTO) R.string.categorias_nueva_gasto else R.string.categorias_nueva_ingreso))
        }
    }

    if (estado.categoriasArchivadas.isNotEmpty()) {
        Encabezado(R.string.categorias_archivadas)
        estado.categoriasArchivadas.sortedBy { it.nombre }.forEach { categoria ->
            FilaCategoria(
                categoria = categoria,
                onEditar = null,
                accion = R.string.cuentas_reactivar,
                onAccion = { viewModel.cambiarArchivada(categoria, archivada = false) },
            )
        }
    }

    editor?.let { actual ->
        DialogoCategoria(
            editor = actual,
            onCambio = viewModel::editarCategoria,
            onGuardar = viewModel::guardarCategoria,
            onCerrar = viewModel::cerrarEditorCategoria,
        )
    }
}

@Composable
private fun FilaCategoria(categoria: Cuenta, onEditar: (() -> Unit)?, @StringRes accion: Int, onAccion: () -> Unit) {
    val etiquetaEditar = stringResource(R.string.categorias_editar)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .then(if (onEditar != null) Modifier.clickable(onClickLabel = etiquetaEditar, onClick = onEditar) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconoEnCirculo(iconoDeCategoria(categoria), tamano = 36.dp)
            Text(categoria.nombre, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onAccion) { Text(stringResource(accion)) }
    }
}

@Composable
private fun DialogoCategoria(
    editor: EditorCategoria,
    onCambio: (EditorCategoria.() -> EditorCategoria) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.guardando) onCerrar() },
        title = {
            Text(
                stringResource(
                    when {
                        editor.editando != null -> R.string.categorias_editar
                        editor.tipo == TipoCuenta.GASTO -> R.string.categorias_nueva_gasto
                        else -> R.string.categorias_nueva_ingreso
                    },
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Campo(
                    valor = editor.nombre,
                    onCambio = { texto -> onCambio { copy(nombre = texto.take(Cuenta.LARGO_MAXIMO_NOMBRE)) } },
                    etiqueta = stringResource(R.string.categorias_nombre),
                    error = when (editor.error) {
                        ErrorCategoria.NOMBRE_VACIO -> stringResource(R.string.error_nombre_vacio)
                        ErrorCategoria.NOMBRE_REPETIDO -> stringResource(R.string.categorias_error_repetida)
                        null -> null
                    },
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                Text(stringResource(R.string.categorias_icono), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    iconosElegibles.forEach { (clave, icono) ->
                        val elegido = (editor.icono ?: claveDeIconoPorNombre(editor.editando?.nombre)) == clave
                        val nombre = nombreDeIcono(clave)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(if (elegido) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .selectable(selected = elegido, role = Role.RadioButton) { onCambio { copy(icono = clave) } }
                                .semantics { contentDescription = nombre },
                            contentAlignment = Alignment.Center,
                        ) { IconoEnCirculo(icono, tamano = 36.dp) }
                    }
                }
                if (editor.errorAlGuardar) Text(stringResource(R.string.error_al_guardar), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !editor.guardando) {
                Text(stringResource(if (editor.editando != null) R.string.guardar else R.string.crear))
            }
        },
        dismissButton = { TextButton(onClick = onCerrar, enabled = !editor.guardando) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** TalkBack lee el nombre de la categoría del catálogo que usa ese ícono ("Salud"). */
private fun nombreDeIcono(clave: String): String {
    val recurso = iconosElegibles[clave]
    return iconosPorNombre.entries.firstOrNull { it.value == recurso }?.key ?: clave
}

/** Una categoría del catálogo sin ícono elegido muestra marcado el que ya tiene por nombre. */
private fun claveDeIconoPorNombre(nombre: String?): String? {
    val recurso = iconosPorNombre[nombre] ?: return null
    return iconosElegibles.entries.firstOrNull { it.value == recurso }?.key
}

/** Tema de la app: el del sistema, claro u oscuro. Se aplica al instante. */
@Composable
internal fun ContenidoApariencia() {
    val contexto = LocalContext.current
    val preferencias = remember { PreferenciasApp(contexto) }
    val tema by PreferenciasApp.tema.collectAsStateWithLifecycle()
    Encabezado(R.string.apariencia_tema)
    SelectorSegmentadoSeda(
        opciones = ModoTema.entries,
        seleccionada = tema,
        etiqueta = { modo ->
            stringResource(
                when (modo) {
                    ModoTema.SISTEMA -> R.string.apariencia_sistema
                    ModoTema.CLARO -> R.string.apariencia_claro
                    ModoTema.OSCURO -> R.string.apariencia_oscuro
                },
            )
        },
        onSeleccionar = { preferencias.tema = it },
    )
    Text(
        stringResource(R.string.apariencia_ayuda),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Activar los avisos y elegir a qué hora llegan; al cambiar la hora se reprograma la alarma. */
@Composable
internal fun ContenidoAvisos() {
    val contexto = LocalContext.current
    val avisos = recordarAvisos()
    val preferencias = remember { PreferenciasApp(contexto) }
    var horaDia by remember { mutableIntStateOf(preferencias.horaDia) }
    var horaNoche by remember { mutableIntStateOf(preferencias.horaNoche) }

    Text(
        stringResource(if (avisos.activos) R.string.avisos_estado_activos else R.string.avisos_estado_inactivos),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (avisos.activos) {
        TextButton(onClick = { abrirAjustesDeAvisos(contexto) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.perfil_abrir_ajustes_avisos))
        }
    } else {
        BotonPrincipal(texto = stringResource(R.string.activar_avisos_boton), onClick = avisos.activar)
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Encabezado(R.string.avisos_hora_dia)
    Text(stringResource(R.string.avisos_hora_dia_ayuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SelectorHora(HORAS_DIA, horaDia) {
        horaDia = it
        preferencias.horaDia = it
        dispararProgramadorAvisos(contexto)
    }
    Encabezado(R.string.avisos_hora_noche)
    Text(stringResource(R.string.avisos_hora_noche_ayuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SelectorHora(HORAS_NOCHE, horaNoche) {
        horaNoche = it
        preferencias.horaNoche = it
        dispararProgramadorAvisos(contexto)
    }
}

@Composable
private fun SelectorHora(opciones: List<Int>, elegida: Int, onElegir: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { hora ->
            FilterChip(
                selected = hora == elegida,
                onClick = { onElegir(hora) },
                label = { Text(stringResource(R.string.avisos_hora, hora), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")) },
            )
        }
    }
}

/** Acerca de Kren: cómo se calcula el Disponible real, la privacidad local y la versión. */
@Composable
internal fun ContenidoAcerca() {
    val contexto = LocalContext.current
    val version = remember { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName.orEmpty() }
    Encabezado(R.string.acerca_disponible_titulo)
    Text(
        stringResource(R.string.acerca_disponible_formula),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(R.string.acerca_disponible_texto), style = MaterialTheme.typography.bodyMedium)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Encabezado(R.string.acerca_privacidad_titulo)
    Text(stringResource(R.string.acerca_privacidad_texto), style = MaterialTheme.typography.bodyMedium)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        stringResource(R.string.acerca_version, version),
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Encabezado(@StringRes texto: Int) {
    Text(stringResource(texto), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/** Toda la fila es el interruptor (≥ 56 dp), como en Ajustes de Android. */
@Composable
private fun FilaInterruptor(
    @StringRes titulo: Int,
    @StringRes texto: Int,
    activo: Boolean,
    habilitado: Boolean = true,
    onCambiar: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = activo, enabled = habilitado, role = Role.Switch, onValueChange = onCambiar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(texto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = activo, onCheckedChange = null, enabled = habilitado)
    }
}

/**
 * Una cuenta con su saldo. Con [seleccionada] no nula, tocarla la elige para registrar
 * (radio); la acción de la derecha archiva o reactiva.
 */
@Composable
private fun FilaCuenta(
    cuenta: Cuenta,
    monto: Dinero,
    seleccionada: Boolean?,
    onElegir: () -> Unit,
    @StringRes accion: Int,
    onAccion: () -> Unit,
    accionHabilitada: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .then(
                    if (seleccionada != null) Modifier.selectable(selected = seleccionada, role = Role.RadioButton, onClick = onElegir) else Modifier,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (seleccionada != null) RadioButton(selected = seleccionada, onClick = null)
            Column(Modifier.padding(start = if (seleccionada != null) 8.dp else 0.dp)) {
                Text(cuenta.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(
                    monto.enPantalla(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onAccion, enabled = accionHabilitada) { Text(stringResource(accion)) }
    }
}
