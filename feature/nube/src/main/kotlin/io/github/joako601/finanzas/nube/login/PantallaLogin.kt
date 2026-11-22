package io.github.joako601.finanzas.nube.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.AnimacionLottie
import io.github.joako601.finanzas.nube.componentes.BannerAvisoNube
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube
import io.github.joako601.finanzas.nube.componentes.IconoVisibilidad
import io.github.joako601.finanzas.nube.componentes.TransformacionContrasena
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion

/**
 * Pantalla de inicio de sesión seguro alineada con OWASP A07 / WSTG-IDNT-04.
 *
 * Nunca muestra errores por campo ni validación Regex de complejidad de contraseña;
 * cualquier falla de autenticación se comunica mediante un único aviso global accesible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLogin(
    repositorioSesion: RepositorioSesion,
    onLoginExitoso: () -> Unit,
    onIrARegistro: () -> Unit,
    onAbrirRecuperar: () -> Unit,
    onSalir: () -> Unit,
) {
    val fabrica = remember(repositorioSesion) { LoginViewModel.fabrica(repositorioSesion) }
    val viewModel: LoginViewModel = viewModel(
        key = "login-${System.identityHashCode(repositorioSesion)}",
        factory = fabrica,
    )
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    val onLoginExitosoActual by rememberUpdatedState(onLoginExitoso)
    if (estado.loginCompletado) {
        SideEffect {
            viewModel.reiniciar()
            onLoginExitosoActual()
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(estado.avisoGlobal, estado.intentoError) {
        if (estado.avisoGlobal != null) {
            scrollState.animateScrollTo(0)
        }
    }

    val focusManager = LocalFocusManager.current
    var focoCorreo by remember { mutableStateOf(false) }
    var focoContrasena by remember { mutableStateOf(false) }

    val interactivo = !estado.cargando && estado.estadoMascota != EstadoMascotaLogin.Exito

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nube_login_titulo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            viewModel.reiniciar()
                            onSalir()
                        },
                        enabled = interactivo,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.nube_volver))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MascotaLogin(
                    estado = estado.estadoMascota,
                    contrasenaVisible = estado.contrasenaVisible,
                    intentoError = estado.intentoError,
                )

                Text(
                    text = stringResource(R.string.login_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                AnimatedVisibility(
                    visible = estado.avisoGlobal != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    estado.avisoGlobal?.let { aviso ->
                        BannerAvisoNube(
                            mensaje = textoDeAviso(aviso),
                            icono = {
                                key(estado.intentoError) {
                                    AnimacionLottie(
                                        resId = R.raw.anim_login_error,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = estado.estadoMascota == EstadoMascotaLogin.Exito,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    BannerExitoGlobal(mensaje = stringResource(R.string.login_exito_banner))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = estado.correo,
                            onValueChange = viewModel::onCorreoCambio,
                            label = { Text(stringResource(R.string.login_campo_correo)) },
                            singleLine = true,
                            enabled = interactivo,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { estadoFoco ->
                                    focoCorreo = estadoFoco.isFocused
                                    when {
                                        focoCorreo -> viewModel.onFocoCorreo()
                                        !focoContrasena -> viewModel.onSinFoco()
                                    }
                                },
                        )

                        OutlinedTextField(
                            value = estado.contrasena,
                            onValueChange = viewModel::onContrasenaCambio,
                            label = { Text(stringResource(R.string.login_campo_contrasena)) },
                            singleLine = true,
                            enabled = interactivo,
                            visualTransformation = if (estado.contrasenaVisible) {
                                VisualTransformation.None
                            } else {
                                TransformacionContrasena
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.iniciarSesion()
                                },
                            ),
                            trailingIcon = {
                                val descripcionOjo = stringResource(
                                    if (estado.contrasenaVisible) {
                                        R.string.login_ocultar_contrasena
                                    } else {
                                        R.string.login_mostrar_contrasena
                                    },
                                )
                                IconButton(
                                    onClick = viewModel::onAlternarVisibilidadContrasena,
                                    enabled = interactivo,
                                    modifier = Modifier.semantics {
                                        contentDescription = descripcionOjo
                                    },
                                ) {
                                    IconoVisibilidad(
                                        visible = estado.contrasenaVisible,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { estadoFoco ->
                                    focoContrasena = estadoFoco.isFocused
                                    when {
                                        focoContrasena -> viewModel.onFocoContrasena()
                                        !focoCorreo -> viewModel.onSinFoco()
                                    }
                                },
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            TextButton(
                                onClick = onAbrirRecuperar,
                                enabled = interactivo,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.login_olvide_contrasena))
                            }
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.iniciarSesion()
                            },
                            enabled = interactivo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            if (estado.cargando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.login_cargando),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.login_boton_entrar),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onIrARegistro,
                    enabled = interactivo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (interactivo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.login_crear_cuenta),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun textoDeAviso(aviso: AvisoLogin): String = stringResource(
    when (aviso) {
        AvisoLogin.CamposVacios -> R.string.login_error_campos_vacios
        AvisoLogin.CredencialesInvalidas -> R.string.login_error_credenciales
        AvisoLogin.SinConexion -> R.string.login_error_sin_conexion
        AvisoLogin.DemasiadosIntentos -> R.string.login_error_demasiados_intentos
        AvisoLogin.ErrorInesperado -> R.string.login_error_inesperado
    },
)

@Composable
private fun BannerExitoGlobal(mensaje: String) {
    val colorExito = ColoresSemanticosNube.exito()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = colorExito.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colorExito.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimacionLottie(
                resId = R.raw.anim_login_exito,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
