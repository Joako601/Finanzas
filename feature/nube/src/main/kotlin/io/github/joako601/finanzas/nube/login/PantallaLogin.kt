package io.github.joako601.finanzas.nube.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joako601.finanzas.nube.BuildConfig
import io.github.joako601.finanzas.nube.EntradaNube
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.AnimacionLottie
import io.github.joako601.finanzas.nube.componentes.BannerAvisoNube
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube
import io.github.joako601.finanzas.nube.componentes.IconoVisibilidad
import io.github.joako601.finanzas.nube.componentes.TransformacionContrasena
import io.github.joako601.finanzas.nube.componentes.rememberAnimacionesDesactivadasNube
import io.github.joako601.finanzas.nube.componentes.toqueTactil
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio de sesión seguro alineada con OWASP A07 / WSTG-IDNT-04.
 *
 * Nunca muestra errores por campo ni validación Regex de complejidad de contraseña;
 * cualquier falla de autenticación se comunica mediante un único aviso global accesible.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PantallaLogin(
    repositorioSesion: RepositorioSesion,
    onLoginExitoso: () -> Unit,
    onIrARegistro: () -> Unit,
    onAbrirRecuperar: (correo: String) -> Unit,
    onSalir: () -> Unit,
    entrada: EntradaNube? = null,
) {
    val fabrica = remember(repositorioSesion) { LoginViewModel.fabrica(repositorioSesion) }
    val viewModel: LoginViewModel = viewModel(
        key = "login-${System.identityHashCode(repositorioSesion)}",
        factory = fabrica,
    )
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    // Atrás del sistema: en la contraseña regresa al correo; en el correo sale y limpia todo.
    BackHandler {
        if (estado.paso == PasoLogin.Contrasena) {
            viewModel.cambiarCorreo()
        } else {
            viewModel.reiniciar()
            onSalir()
        }
    }

    val onLoginExitosoActual by rememberUpdatedState(onLoginExitoso)
    if (estado.loginCompletado) {
        SideEffect {
            viewModel.reiniciar()
            onLoginExitosoActual()
        }
    }

    val scrollState = rememberScrollState()
    // Dónde va el texto del correo dentro del campo (0..1): el punto del símbolo lo sigue.
    var avanceCorreo by remember { mutableFloatStateOf(0f) }
    // Centro del símbolo en pantalla: de ahí sale la onda de éxito del fondo.
    var centroSimbolo by remember { mutableStateOf(Offset.Zero) }

    // Vibración: un toque suave cuando se forma la palomita, uno doble y seco con el error.
    val haptica = LocalHapticFeedback.current
    LaunchedEffect(estado.estadoMascota, estado.intentoError) {
        when (estado.estadoMascota) {
            EstadoMascotaLogin.Exito -> haptica.performHapticFeedback(HapticFeedbackType.Confirm)
            EstadoMascotaLogin.Error -> haptica.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
    // Android no expone el bloqueo de mayúsculas de los teclados en pantalla. Con teclado físico
    // se lee el estado real; con el de pantalla, dos letras mayúsculas seguidas lo delatan
    // (una sola es Shift, como en "Hola").
    var bloqMayusFisico by remember { mutableStateOf(false) }
    val ultimas = estado.contrasena.filter { it.isLetter() }.takeLast(2)
    val mayusculas = estado.paso == PasoLogin.Contrasena &&
        (bloqMayusFisico || (ultimas.length == 2 && ultimas.all { it.isUpperCase() }))
    LaunchedEffect(estado.avisoGlobal, estado.intentoError) {
        if (estado.avisoGlobal != null) {
            scrollState.animateScrollTo(0)
        }
    }

    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val interactivo = !estado.cargando && estado.estadoMascota != EstadoMascotaLogin.Exito
    val googleConfigurado = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    // En debug el botón se ve aunque falte firebase.webClientId, para revisar el diseño;
    // en release solo aparece cuando funciona.
    val conGoogle = googleConfigurado || BuildConfig.DEBUG
    // Mientras el selector de Google se abre (300–1500 ms) `cargando` sigue en false: sin este
    // candado, un segundo toque lanzaba otra petición que fallaba y pintaba error detrás de la hoja.
    var solicitandoGoogle by remember { mutableStateOf(false) }
    val continuarConGoogle: () -> Unit = {
        if (!googleConfigurado) {
            viewModel.googleNoDisponible()
        } else if (!solicitandoGoogle) {
            solicitandoGoogle = true
            alcance.launch {
                try {
                    when (val cuenta = pedirCuentaGoogle(contexto, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
                        is CuentaGoogle.Token -> viewModel.iniciarSesionConGoogle(cuenta.idToken)
                        CuentaGoogle.Cancelada -> Unit
                        CuentaGoogle.NoDisponible -> viewModel.googleNoDisponible()
                    }
                } finally {
                    solicitandoGoogle = false
                }
            }
        }
    }

    // Entrada de la app: el símbolo espera al logo del splash; cuando aterriza, aparecen el
    // título y los campos (360 ms) y luego las líneas del fondo (600 ms).
    val listo = entrada?.splashTerminado ?: { true }
    val aparicion = remember { Animatable(if (listo()) 1f else 0f) }
    val aparicionFondo = remember { Animatable(if (listo()) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (aparicion.value == 1f) return@LaunchedEffect
        snapshotFlow { listo() }.first { it }
        launch { aparicion.animateTo(1f, tween(360, easing = CurvaSedaLogin)) }
        delay(120)
        aparicionFondo.animateTo(1f, tween(600, easing = CurvaSedaLogin))
    }
    val subidaPx = with(LocalDensity.current) { 12.dp.toPx() }
    val entra = Modifier.graphicsLayer {
        alpha = aparicion.value
        translationY = (1f - aparicion.value) * subidaPx
    }
    val tinta = MaterialTheme.colorScheme.onSurface
    val verde = ColoresSemanticosNube.exito()

    // El fondo va detrás de todo el Scaffold: cubre también la barra de "Volver" y la de estado,
    // y no cambia de tamaño cuando aparece el teclado.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    FondoLineasKren(
        estado = estado.estadoMascota,
        intentoError = estado.intentoError,
        origenOnda = centroSimbolo,
        modifier = Modifier.matchParentSize().graphicsLayer { alpha = aparicionFondo.value },
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    // Siendo la entrada de la app no hay a dónde volver.
                    if (entrada == null) TextButton(
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
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
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
                SimboloKren(
                    estado = estado.estadoMascota,
                    contrasenaVisible = estado.contrasenaVisible,
                    intentoError = estado.intentoError,
                    avanceCorreo = avanceCorreo,
                    largoContrasena = estado.contrasena.length,
                    mayusculas = mayusculas,
                    modifier = Modifier
                        .onGloballyPositioned {
                            val caja = it.boundsInRoot()
                            centroSimbolo = caja.center
                            // El logo del splash mide 66 unidades; el símbolo muestra 64 en su lado.
                            val lado = caja.width * 66f / 64f
                            entrada?.onDestinoLogo(Rect(caja.center - Offset(lado / 2f, lado / 2f), Size(lado, lado)), tinta, verde)
                        }
                        // Oculto hasta que el logo del splash aterriza encima: nunca se ven dos.
                        .graphicsLayer { alpha = if (listo()) 1f else 0f },
                )

                Text(
                    text = stringResource(
                        if (estado.paso == PasoLogin.Correo) R.string.login_titulo_correo else R.string.login_titulo_contrasena,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = entra.semantics { heading() },
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

                val sinAnimaciones = rememberAnimacionesDesactivadasNube()
                val desplazamientoPx = with(LocalDensity.current) { 24.dp.roundToPx() }
                SharedTransitionLayout(entra) {
                AnimatedContent(
                    targetState = estado.paso,
                    transitionSpec = {
                        if (sinAnimaciones) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        } else {
                            // Seda iOS: entra en 300 ms y sale en 220 ms; al regresar, al revés.
                            val haciaAdelante = targetState == PasoLogin.Contrasena
                            val signo = if (haciaAdelante) 1 else -1
                            (slideInHorizontally(tween(300)) { signo * desplazamientoPx } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(tween(220)) { -signo * desplazamientoPx } + fadeOut(tween(220)))
                        }
                    },
                    label = "pasoLogin",
                ) { paso ->
                    // El campo de correo y la caja del paso 2 son el mismo dato: se transforma uno
                    // en otro en lugar de deslizarse.
                    val correoCompartido = Modifier.sharedBounds(
                        rememberSharedContentState("correo"),
                        animatedVisibilityScope = this,
                        boundsTransform = { _, _ -> if (sinAnimaciones) snap() else tween(300) },
                    )
                    when (paso) {
                        PasoLogin.Correo -> PasoCorreo(
                            estado = estado,
                            interactivo = interactivo,
                            conGoogle = conGoogle,
                            onCorreoCambio = viewModel::onCorreoCambio,
                            onAvance = { avanceCorreo = it },
                            onFoco = viewModel::onFocoCorreo,
                            onSinFoco = viewModel::onSinFoco,
                            onContinuar = viewModel::continuar,
                            onGoogle = continuarConGoogle,
                            onIrARegistro = onIrARegistro,
                            onUsarSinCuenta = entrada?.onUsarSinCuenta,
                            modificadorCorreo = correoCompartido,
                        )
                        PasoLogin.Contrasena -> PasoContrasena(
                            estado = estado,
                            interactivo = interactivo,
                            onContrasenaCambio = viewModel::onContrasenaCambio,
                            onFoco = viewModel::onFocoContrasena,
                            onSinFoco = viewModel::onSinFoco,
                            onAlternarVisibilidad = viewModel::onAlternarVisibilidadContrasena,
                            onCambiarCorreo = viewModel::cambiarCorreo,
                            onIniciarSesion = viewModel::iniciarSesion,
                            onAbrirRecuperar = onAbrirRecuperar,
                            mayusculas = mayusculas,
                            onTeclaFisica = { bloqMayusFisico = it },
                            modificadorCorreo = correoCompartido,
                        )
                    }
                }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    }
}

/** Paso 1: correo, Continuar, Google y crear cuenta. */
@Composable
private fun PasoCorreo(
    estado: EstadoUiLogin,
    interactivo: Boolean,
    conGoogle: Boolean,
    onCorreoCambio: (String) -> Unit,
    onAvance: (Float) -> Unit,
    onFoco: () -> Unit,
    onSinFoco: () -> Unit,
    onContinuar: () -> Unit,
    onGoogle: () -> Unit,
    onIrARegistro: () -> Unit,
    onUsarSinCuenta: (() -> Unit)?,
    modificadorCorreo: Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Ancho útil del texto: el campo menos sus 16 dp de relleno a cada lado.
    var anchoTexto by remember { mutableFloatStateOf(1f) }
    val rellenoPx = with(LocalDensity.current) { 32.dp.toPx() }
    val medidor = rememberTextMeasurer()
    val estiloCampo = LocalTextStyle.current
    LaunchedEffect(estado.correo, anchoTexto) {
        onAvance(medidor.measure(estado.correo, estiloCampo).size.width / anchoTexto)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = estado.correo,
            onValueChange = onCorreoCambio,
            label = { Text(stringResource(R.string.login_campo_correo)) },
            singleLine = true,
            enabled = interactivo,
            shape = MaterialTheme.shapes.small,
            colors = coloresCampoOpaco(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onContinuar() }),
            modifier = modificadorCorreo
                .fillMaxWidth()
                .flotaAlEnfocar()
                .onSizeChanged { anchoTexto = (it.width - rellenoPx).coerceAtLeast(1f) }
                .onFocusChanged { if (it.isFocused) onFoco() else onSinFoco() },
        )

        BotonLoginPrincipal(
            texto = stringResource(R.string.login_continuar),
            cargando = false,
            habilitado = interactivo,
            onClick = {
                focusManager.clearFocus()
                onContinuar()
            },
        )

        if (conGoogle) {
            SeparadorO()
            val interaccionGoogle = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = onGoogle,
                enabled = interactivo,
                interactionSource = interaccionGoogle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toqueTactil(interaccionGoogle),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                // El logo es decorativo: el texto ya dice "Continuar con Google".
                Image(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.login_continuar_google), style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.login_sin_cuenta),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onIrARegistro, enabled = interactivo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.login_crear_cuenta))
            }
        }

        // Opción secundaria de la entrada: la app funciona completa sin cuenta.
        if (onUsarSinCuenta != null) {
            TextButton(onClick = onUsarSinCuenta, enabled = interactivo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = stringResource(R.string.login_usar_sin_cuenta),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Paso 2: el correo elegido con "Cambiar", la contraseña y entrar. */
@Composable
private fun PasoContrasena(
    estado: EstadoUiLogin,
    interactivo: Boolean,
    onContrasenaCambio: (String) -> Unit,
    onFoco: () -> Unit,
    onSinFoco: () -> Unit,
    onAlternarVisibilidad: () -> Unit,
    onCambiarCorreo: () -> Unit,
    onIniciarSesion: () -> Unit,
    onAbrirRecuperar: (correo: String) -> Unit,
    mayusculas: Boolean,
    onTeclaFisica: (bloqMayus: Boolean) -> Unit,
    modificadorCorreo: Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focoContrasena = remember { FocusRequester() }
    LaunchedEffect(Unit) { focoContrasena.requestFocus() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // El correo elegido va en una caja con el mismo borde y relleno que los campos:
        // se lee como dato ya escrito, no como texto suelto de la pantalla, y tapa las líneas.
        Surface(
            modifier = modificadorCorreo.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InicialCuenta(estado.correo)
            Text(
                text = estado.correo.trim(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCambiarCorreo, enabled = interactivo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.login_cambiar_correo))
            }
        }
        }

        OutlinedTextField(
            value = estado.contrasena,
            onValueChange = onContrasenaCambio,
            label = { Text(stringResource(R.string.login_campo_contrasena)) },
            singleLine = true,
            enabled = interactivo,
            shape = MaterialTheme.shapes.small,
            colors = coloresCampoOpaco(),
            supportingText = if (mayusculas) {
                { Text(stringResource(R.string.login_mayusculas)) }
            } else {
                null
            },
            visualTransformation = if (estado.contrasenaVisible) VisualTransformation.None else TransformacionContrasena,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onIniciarSesion()
                },
            ),
            trailingIcon = {
                val descripcionOjo = stringResource(
                    if (estado.contrasenaVisible) R.string.login_ocultar_contrasena else R.string.login_mostrar_contrasena,
                )
                IconButton(
                    onClick = onAlternarVisibilidad,
                    enabled = interactivo,
                    modifier = Modifier.semantics { contentDescription = descripcionOjo },
                ) {
                    IconoVisibilidad(visible = estado.contrasenaVisible, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focoContrasena)
                .flotaAlEnfocar()
                .onPreviewKeyEvent {
                    onTeclaFisica(it.nativeKeyEvent.isCapsLockOn)
                    false
                }
                .onFocusChanged { if (it.isFocused) onFoco() else onSinFoco() },
        )

        BotonLoginPrincipal(
            texto = stringResource(R.string.login_boton_entrar),
            cargando = estado.cargando,
            habilitado = interactivo,
            onClick = {
                focusManager.clearFocus()
                onIniciarSesion()
            },
        )

        TextButton(onClick = { onAbrirRecuperar(estado.correo) }, enabled = interactivo, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.login_olvide_contrasena))
        }
    }
}

/** Círculo en tinta con la inicial del correo, como en las cuentas de Google. */
@Composable
private fun InicialCuenta(correo: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.onSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = correo.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.surface,
            // Decorativa: el correo completo ya se lee al lado.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private val CurvaSedaLogin = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Botón negro de ancho completo (52 dp, radio 14 dp) con su estado de carga. */
@Composable
private fun BotonLoginPrincipal(texto: String, cargando: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    val interaccion = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = habilitado,
        interactionSource = interaccion,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toqueTactil(interaccion),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = stringResource(R.string.login_cargando), style = MaterialTheme.typography.labelLarge)
        } else {
            Text(text = texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Relleno opaco del color del fondo: las líneas no se ven a través del campo, desaparecen
 * justo en su borde, como si el campo se las tragara.
 */
@Composable
private fun coloresCampoOpaco() = MaterialTheme.colorScheme.background.let {
    OutlinedTextFieldDefaults.colors(focusedContainerColor = it, unfocusedContainerColor = it, disabledContainerColor = it)
}

/**
 * Mientras el campo tiene el foco, una sombra suave lo hace flotar sobre las líneas.
 * La sombra sigue solo el recuadro: el OutlinedTextField reserva 8 dp arriba para la etiqueta.
 */
@Composable
private fun Modifier.flotaAlEnfocar(): Modifier {
    var enfocado by remember { mutableStateOf(false) }
    val sinAnimaciones = rememberAnimacionesDesactivadasNube()
    val elevacion by animateDpAsState(
        targetValue = if (enfocado) 8.dp else 0.dp,
        animationSpec = if (sinAnimaciones) snap() else tween(if (enfocado) 300 else 220),
        label = "flota",
    )
    return onFocusChanged { enfocado = it.isFocused }.shadow(elevacion, FormaRecuadroCampo, clip = false)
}

/** El recuadro visible del campo: radio 14 dp, empezando 8 dp abajo (espacio de la etiqueta). */
private object FormaRecuadroCampo : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val arriba = with(density) { 8.dp.toPx() }
        val radio = with(density) { 14.dp.toPx() }
        return Outline.Rounded(RoundRect(0f, arriba, size.width, size.height, CornerRadius(radio)))
    }
}

/** Línea — o — línea. */
@Composable
private fun SeparadorO() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.login_o),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun textoDeAviso(aviso: AvisoLogin): String = stringResource(
    when (aviso) {
        AvisoLogin.CorreoVacio -> R.string.login_error_correo_vacio
        AvisoLogin.ContrasenaVacia -> R.string.login_error_contrasena_vacia
        AvisoLogin.CredencialesInvalidas -> R.string.login_error_credenciales
        AvisoLogin.SinConexion -> R.string.login_error_sin_conexion
        AvisoLogin.DemasiadosIntentos -> R.string.login_error_demasiados_intentos
        AvisoLogin.ErrorInesperado -> R.string.login_error_inesperado
        AvisoLogin.GoogleNoDisponible -> R.string.login_error_google_no_disponible
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
