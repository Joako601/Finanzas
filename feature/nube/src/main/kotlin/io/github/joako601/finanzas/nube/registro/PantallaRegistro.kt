package io.github.joako601.finanzas.nube.registro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.AnimacionLottie
import io.github.joako601.finanzas.nube.componentes.BannerAvisoNube
import io.github.joako601.finanzas.nube.componentes.IconoVisibilidad
import io.github.joako601.finanzas.nube.componentes.TransformacionContrasena
import io.github.joako601.finanzas.nube.componentes.toqueTactil
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso

/**
 * Pantalla de registro de cuenta institucional (`@tecdesoftware.edu.mx`).
 *
 * Muestra validaciones claras por campo, lista en tiempo real de las 5 reglas de contraseña
 * y confirmación explícita antes de regresar al inicio de sesión.
 */
@Composable
fun PantallaRegistro(
    repositorioSesion: RepositorioSesion,
    onIrALogin: () -> Unit,
) {
    val factory = remember(repositorioSesion) { RegistroViewModel.factory(repositorioSesion) }
    val viewModel: RegistroViewModel = viewModel(
        key = "registro-${System.identityHashCode(repositorioSesion)}",
        factory = factory,
    )
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Atrás del sistema hace lo mismo que "Volver": sin esto la contraseña quedaba escrita y visible.
    BackHandler {
        viewModel.reiniciar()
        onIrALogin()
    }

    ContenidoRegistro(
        estado = estado,
        onCambiarCorreo = viewModel::alCambiarCorreo,
        onCambiarContrasena = viewModel::alCambiarContrasena,
        onCambiarConfirmarContrasena = viewModel::alCambiarConfirmarContrasena,
        onAlternarMostrarContrasena = viewModel::alAlternarMostrarContrasena,
        onAlternarMostrarConfirmarContrasena = viewModel::alAlternarMostrarConfirmarContrasena,
        onEnviar = viewModel::alEnviar,
        onConfirmarExito = {
            viewModel.alConfirmarExito()
            onIrALogin()
        },
        onIrALogin = {
            viewModel.reiniciar()
            onIrALogin()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContenidoRegistro(
    estado: EstadoRegistroUi,
    onCambiarCorreo: (String) -> Unit,
    onCambiarContrasena: (String) -> Unit,
    onCambiarConfirmarContrasena: (String) -> Unit,
    onAlternarMostrarContrasena: () -> Unit,
    onAlternarMostrarConfirmarContrasena: () -> Unit,
    onEnviar: () -> Unit,
    onConfirmarExito: () -> Unit,
    onIrALogin: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        text = stringResource(R.string.nube_registro_titulo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onIrALogin,
                        enabled = !estado.cargando,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.nube_volver))
                    }
                },
            )
        },
    ) { margenes ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .consumeWindowInsets(margenes)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.registro_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                estado.errorGlobal?.let { errorGlobal ->
                    BannerAvisoNube(mensaje = mensajeErrorGlobal(errorGlobal))
                }

                TarjetaFormularioRegistro(
                    estado = estado,
                    focusManager = focusManager,
                    onCambiarCorreo = onCambiarCorreo,
                    onCambiarContrasena = onCambiarContrasena,
                    onCambiarConfirmarContrasena = onCambiarConfirmarContrasena,
                    onAlternarMostrarContrasena = onAlternarMostrarContrasena,
                    onAlternarMostrarConfirmarContrasena = onAlternarMostrarConfirmarContrasena,
                    onEnviar = onEnviar,
                )

                val interaccionCrear = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onEnviar()
                    },
                    enabled = !estado.cargando,
                    interactionSource = interaccionCrear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toqueTactil(interaccionCrear),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    if (estado.cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.registro_boton_creando),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.registro_boton_crear),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                TextButton(
                    onClick = onIrALogin,
                    enabled = !estado.cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.registro_ir_a_login))
                }
            }
        }
    }

    if (estado.registroExitoso) {
        DialogoRegistroExitoso(onConfirmar = onConfirmarExito)
    }
}

@Composable
private fun TarjetaFormularioRegistro(
    estado: EstadoRegistroUi,
    focusManager: FocusManager,
    onCambiarCorreo: (String) -> Unit,
    onCambiarContrasena: (String) -> Unit,
    onCambiarConfirmarContrasena: (String) -> Unit,
    onAlternarMostrarContrasena: () -> Unit,
    onAlternarMostrarConfirmarContrasena: () -> Unit,
    onEnviar: () -> Unit,
) {
    val textoErrorCorreo = estado.errorCorreo?.let { mensajeErrorCorreo(it) }
    val textoErrorContrasena = estado.errorContrasena?.let { mensajeErrorContrasena(it) }
    val textoErrorConfirmacion = estado.errorConfirmacion?.let { mensajeErrorConfirmacion(it) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = estado.correo,
                onValueChange = onCambiarCorreo,
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.cargando,
                singleLine = true,
                label = { Text(stringResource(R.string.registro_correo_etiqueta)) },
                placeholder = { Text(stringResource(R.string.registro_correo_placeholder)) },
                isError = textoErrorCorreo != null,
                supportingText = {
                    if (textoErrorCorreo != null) {
                        Text(
                            text = textoErrorCorreo,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        Text(stringResource(R.string.registro_correo_ayuda))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )

            OutlinedTextField(
                value = estado.contrasena,
                onValueChange = onCambiarContrasena,
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.cargando,
                singleLine = true,
                label = { Text(stringResource(R.string.registro_contrasena_etiqueta)) },
                visualTransformation = if (estado.mostrarContrasena) {
                    VisualTransformation.None
                } else {
                    TransformacionContrasena
                },
                trailingIcon = {
                    BotonVisibilidadContrasena(
                        visible = estado.mostrarContrasena,
                        habilitado = !estado.cargando,
                        descripcionMostrar = stringResource(R.string.registro_mostrar_contrasena_desc),
                        descripcionOcultar = stringResource(R.string.registro_ocultar_contrasena_desc),
                        onAlternar = onAlternarMostrarContrasena,
                    )
                },
                isError = textoErrorContrasena != null,
                supportingText = textoErrorContrasena?.let { mensaje ->
                    {
                        Text(
                            text = mensaje,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )

            ListaRequisitosContrasena(
                requisitos = estado.requisitosContrasena,
                resaltarFaltantes = estado.errorContrasena != null,
            )

            OutlinedTextField(
                value = estado.confirmarContrasena,
                onValueChange = onCambiarConfirmarContrasena,
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.cargando,
                singleLine = true,
                label = { Text(stringResource(R.string.registro_confirmar_etiqueta)) },
                visualTransformation = if (estado.mostrarConfirmarContrasena) {
                    VisualTransformation.None
                } else {
                    TransformacionContrasena
                },
                trailingIcon = {
                    BotonVisibilidadContrasena(
                        visible = estado.mostrarConfirmarContrasena,
                        habilitado = !estado.cargando,
                        descripcionMostrar = stringResource(R.string.registro_mostrar_confirmar_desc),
                        descripcionOcultar = stringResource(R.string.registro_ocultar_confirmar_desc),
                        onAlternar = onAlternarMostrarConfirmarContrasena,
                    )
                },
                isError = textoErrorConfirmacion != null,
                supportingText = textoErrorConfirmacion?.let { mensaje ->
                    {
                        Text(
                            text = mensaje,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onEnviar()
                    },
                ),
            )
        }
    }
}

@Composable
private fun BotonVisibilidadContrasena(
    visible: Boolean,
    habilitado: Boolean,
    descripcionMostrar: String,
    descripcionOcultar: String,
    onAlternar: () -> Unit,
) {
    val descripcion = if (visible) descripcionOcultar else descripcionMostrar
    IconButton(
        onClick = onAlternar,
        enabled = habilitado,
        modifier = Modifier.semantics { contentDescription = descripcion },
    ) {
        IconoVisibilidad(
            visible = visible,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListaRequisitosContrasena(
    requisitos: RequisitosContrasena,
    resaltarFaltantes: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.registro_requisitos_titulo),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FilaRequisitoContrasena(
            texto = stringResource(R.string.registro_requisito_longitud),
            cumplido = requisitos.longitudMinima,
            resaltarFaltante = resaltarFaltantes,
        )
        FilaRequisitoContrasena(
            texto = stringResource(R.string.registro_requisito_mayuscula),
            cumplido = requisitos.tieneMayuscula,
            resaltarFaltante = resaltarFaltantes,
        )
        FilaRequisitoContrasena(
            texto = stringResource(R.string.registro_requisito_minuscula),
            cumplido = requisitos.tieneMinuscula,
            resaltarFaltante = resaltarFaltantes,
        )
        FilaRequisitoContrasena(
            texto = stringResource(R.string.registro_requisito_numero),
            cumplido = requisitos.tieneNumero,
            resaltarFaltante = resaltarFaltantes,
        )
        FilaRequisitoContrasena(
            texto = stringResource(R.string.registro_requisito_simbolo),
            cumplido = requisitos.tieneSimbolo,
            resaltarFaltante = resaltarFaltantes,
        )
    }
}

@Composable
private fun FilaRequisitoContrasena(
    texto: String,
    cumplido: Boolean,
    resaltarFaltante: Boolean,
) {
    val descripcion = stringResource(
        if (cumplido) R.string.registro_requisito_cumplido_desc else R.string.registro_requisito_pendiente_desc,
        texto,
    )
    val colorTexto = when {
        cumplido -> MaterialTheme.colorScheme.primary
        resaltarFaltante -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val simbolo = when {
        cumplido -> "✓"
        resaltarFaltante -> "✕"
        else -> "○"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = descripcion
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = simbolo,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = colorTexto,
        )
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (cumplido) FontWeight.Medium else FontWeight.Normal,
            color = colorTexto,
        )
    }
}

@Composable
private fun DialogoRegistroExitoso(onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirmar,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        icon = {
            AnimacionLottie(
                resId = R.raw.anim_registro_exito,
                modifier = Modifier.size(88.dp),
            )
        },
        title = { Text(stringResource(R.string.registro_exito_titulo)) },
        text = { Text(stringResource(R.string.registro_exito_mensaje)) },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.registro_exito_confirmar),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

@Composable
private fun mensajeErrorGlobal(errorGlobal: ErrorGlobalRegistro): String = when (errorGlobal) {
    ErrorGlobalRegistro.SinConexion -> stringResource(R.string.registro_error_sin_conexion)
    ErrorGlobalRegistro.DemasiadosIntentos -> stringResource(R.string.registro_error_demasiados_intentos)
    ErrorGlobalRegistro.ErrorInesperado -> stringResource(R.string.registro_error_inesperado)
}

@Composable
private fun mensajeErrorCorreo(error: ErrorCorreoRegistro): String = when (error) {
    ErrorCorreoRegistro.Vacio -> stringResource(R.string.registro_error_correo_vacio)
    ErrorCorreoRegistro.FormatoInvalido -> stringResource(R.string.registro_error_correo_formato)
    ErrorCorreoRegistro.DominioNoInstitucional -> stringResource(R.string.registro_error_correo_dominio)
    ErrorCorreoRegistro.YaRegistrado -> stringResource(R.string.registro_error_correo_en_uso)
}

@Composable
private fun mensajeErrorContrasena(error: ErrorContrasenaRegistro): String = when (error) {
    ErrorContrasenaRegistro.Vacia -> stringResource(R.string.registro_error_contrasena_vacia)
    ErrorContrasenaRegistro.RequisitosIncompletos -> stringResource(R.string.registro_error_contrasena_requisitos)
    ErrorContrasenaRegistro.DebilEnServidor -> stringResource(R.string.registro_error_contrasena_debil)
}

@Composable
private fun mensajeErrorConfirmacion(error: ErrorConfirmacionRegistro): String = when (error) {
    ErrorConfirmacionRegistro.Vacia -> stringResource(R.string.registro_error_confirmacion_vacia)
    ErrorConfirmacionRegistro.NoCoincide -> stringResource(R.string.registro_error_confirmacion_no_coincide)
}

@Preview(showBackground = true)
@Composable
private fun PreviewPantallaRegistro() {
    MaterialTheme {
        PantallaRegistro(
            repositorioSesion = RepositorioSesionFalso(),
            onIrALogin = {},
        )
    }
}
