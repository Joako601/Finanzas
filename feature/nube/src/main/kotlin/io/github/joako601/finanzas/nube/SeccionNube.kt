package io.github.joako601.finanzas.nube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.joako601.finanzas.nube.catalogo.PantallaCatalogo
import io.github.joako601.finanzas.nube.componentes.rememberAnimacionesDesactivadasNube
import io.github.joako601.finanzas.nube.componentes.toqueTactil
import io.github.joako601.finanzas.nube.login.PantallaLogin
import io.github.joako601.finanzas.nube.navegacion.RutaNube
import io.github.joako601.finanzas.nube.recuperar.DialogoRecuperar
import io.github.joako601.finanzas.nube.registro.PantallaRegistro
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion

private sealed interface DestinoNube {
    data object Cargando : DestinoNube
    data object Login : DestinoNube
    data object Registro : DestinoNube
    data class Catalogo(val usuario: UsuarioSesion) : DestinoNube
}

/**
 * Mantiene vivo el repositorio de demostración en debug al rotar la pantalla para no
 * perder la sesión ni las cuentas creadas durante la prueba manual.
 */
private object DemoSesionHolder {
    val repositorio: RepositorioSesionFalso by lazy {
        RepositorioSesionFalso(
            cuentasIniciales = mapOf("demo@tecdesoftware.edu.mx" to "Demo1234!"),
        )
    }
}

/**
 * Punto de entrada público de `:feature:nube` (D-020, D-021).
 *
 * Si `firebase.properties` no estaba configurado al compilar, muestra un aviso claro con
 * instrucciones y un botón para volver, sin cerrarse.
 */
@Composable
fun SeccionNube(onSalir: () -> Unit) {
    val context = LocalContext.current
    val contenedor = remember(context) { ContenedorNube.obtener(context) }
    val repositorio = contenedor.repositorioSesion
    if (repositorio == null) {
        // Solo en debug permitimos explorar las pantallas con un repositorio falso local
        // mientras todavía no se configura firebase.properties. En release nunca se habilita.
        var usarDemo by rememberSaveable { mutableStateOf(false) }
        if (BuildConfig.DEBUG && usarDemo) {
            SeccionNube(repositorioSesion = DemoSesionHolder.repositorio, onSalir = onSalir)
        } else {
            PantallaSinConfigurar(
                onExplorarDemo = if (BuildConfig.DEBUG) ({ usarDemo = true }) else null,
                onSalir = onSalir,
            )
        }
    } else {
        SeccionNube(repositorioSesion = repositorio, onSalir = onSalir)
    }
}

/**
 * Sobrecarga con [repositorioSesion] explícito para pruebas y vistas previas con
 * `RepositorioSesionFalso`.
 */
@Composable
fun SeccionNube(
    repositorioSesion: RepositorioSesion,
    onSalir: () -> Unit,
) {
    val estadoSesion by repositorioSesion.estado.collectAsStateWithLifecycle()
    var rutaActual by rememberSaveable { mutableStateOf<RutaNube?>(null) }
    var mostrarRecuperar by rememberSaveable { mutableStateOf(false) }

    val rutaEfectiva = siguienteRuta(rutaActual, estadoSesion)
    SideEffect {
        if (rutaEfectiva != rutaActual) {
            rutaActual = rutaEfectiva
        }
    }

    BackHandler {
        when {
            mostrarRecuperar -> mostrarRecuperar = false
            rutaEfectiva == RutaNube.REGISTRO -> rutaActual = RutaNube.LOGIN
            // Desde CATALOGO o LOGIN el botón atrás sale de la sección nube (nunca vuelve a LOGIN desde CATALOGO).
            else -> onSalir()
        }
    }

    val sinAnimaciones = rememberAnimacionesDesactivadasNube()
    val desplazamientoPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val destinoActual: DestinoNube = when (rutaEfectiva) {
        null -> DestinoNube.Cargando
        RutaNube.LOGIN -> DestinoNube.Login
        RutaNube.REGISTRO -> DestinoNube.Registro
        RutaNube.CATALOGO -> {
            val autenticado = estadoSesion as? EstadoSesion.Autenticado
            if (autenticado != null) DestinoNube.Catalogo(autenticado.usuario) else DestinoNube.Cargando
        }
    }

    AnimatedContent(
        targetState = destinoActual,
        contentKey = { it::class },
        transitionSpec = {
            if (sinAnimaciones) {
                fadeIn(tween(durationMillis = 120)) togetherWith fadeOut(tween(durationMillis = 120))
            } else {
                val esRetroceso = targetState is DestinoNube.Login &&
                    (initialState is DestinoNube.Registro || initialState is DestinoNube.Catalogo)
                val direccion = if (esRetroceso) -1 else 1
                (
                    slideInHorizontally(tween(durationMillis = 300)) { direccion * desplazamientoPx } +
                        fadeIn(tween(durationMillis = 300))
                    ).togetherWith(
                    slideOutHorizontally(tween(durationMillis = 220)) { -direccion * desplazamientoPx } +
                        fadeOut(tween(durationMillis = 220)),
                )
            }
        },
        label = "transicionRutaNube",
    ) { destino ->
        when (destino) {
            DestinoNube.Cargando -> PantallaCargaSesion()

            is DestinoNube.Catalogo -> PantallaCatalogo(
                usuario = destino.usuario,
                repositorioSesion = repositorioSesion,
                onSalir = onSalir,
            )

            DestinoNube.Registro -> PantallaRegistro(
                repositorioSesion = repositorioSesion,
                onIrALogin = { rutaActual = RutaNube.LOGIN },
            )

            DestinoNube.Login -> {
                PantallaLogin(
                    repositorioSesion = repositorioSesion,
                    onLoginExitoso = {
                        mostrarRecuperar = false
                        rutaActual = RutaNube.CATALOGO
                    },
                    onIrARegistro = { rutaActual = RutaNube.REGISTRO },
                    onAbrirRecuperar = { mostrarRecuperar = true },
                    onSalir = onSalir,
                )
            }
        }
    }

    if (rutaEfectiva == RutaNube.LOGIN && mostrarRecuperar) {
        DialogoRecuperar(
            repositorioSesion = repositorioSesion,
            onCerrar = { mostrarRecuperar = false },
        )
    }
}

@Composable
private fun PantallaCargaSesion() {
    val descripcionCarga = stringResource(R.string.nube_verificando_sesion)
    Scaffold { margenes ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = descripcionCarga
                },
            )
        }
    }
}

/**
 * Calcula las transiciones automáticas de ruta según [EstadoSesion].
 *
 * - Al entrar (`rutaActual == null`), abre [RutaNube.CATALOGO] si ya había sesión activa o
 *   [RutaNube.LOGIN] si no la hay.
 * - Estando en [RutaNube.LOGIN] o [RutaNube.REGISTRO], nunca salta automáticamente a
 *   [RutaNube.CATALOGO] al autenticarse, para que Login pueda reproducir su animación de éxito
 *   y avanzar mediante `onLoginExitoso`.
 * - Estando en [RutaNube.CATALOGO], si la sesión se cierra regresa automáticamente a [RutaNube.LOGIN].
 */
internal fun siguienteRuta(rutaActual: RutaNube?, estado: EstadoSesion): RutaNube? = when {
    rutaActual == null -> when (estado) {
        EstadoSesion.Cargando -> null
        is EstadoSesion.Autenticado -> RutaNube.CATALOGO
        EstadoSesion.SinSesion -> RutaNube.LOGIN
    }
    rutaActual == RutaNube.CATALOGO && estado is EstadoSesion.SinSesion -> RutaNube.LOGIN
    else -> rutaActual
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaSinConfigurar(
    onExplorarDemo: (() -> Unit)?,
    onSalir: () -> Unit,
) {
    BackHandler(onBack = onSalir)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nube_titulo),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onSalir,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.nube_volver))
                    }
                },
            )
        },
    ) { margenes ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(margenes)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.nube_sin_configurar_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.nube_sin_configurar_texto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onExplorarDemo != null) {
                    val interaccionDemo = remember { MutableInteractionSource() }
                    Button(
                        onClick = onExplorarDemo,
                        interactionSource = interaccionDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .toqueTactil(interaccionDemo),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.nube_explorar_demo),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                val interaccionVolver = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onSalir,
                    interactionSource = interaccionVolver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toqueTactil(interaccionVolver),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.nube_volver),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
