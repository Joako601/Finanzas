package io.github.joako601.finanzas

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.joako601.finanzas.nube.ContenedorNube
import io.github.joako601.finanzas.nube.EntradaNube
import io.github.joako601.finanzas.nube.SeccionNube
import io.github.joako601.finanzas.nube.recuperar.DialogoCerrarSesion
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.ui.componentes.DestinoLogoSplash
import io.github.joako601.finanzas.ui.componentes.EstadoSplashKren
import io.github.joako601.finanzas.ui.navegacion.ContenedorPrincipal
import io.github.joako601.finanzas.ui.perfil.CuentaPerfil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class ModoInicioNube {
    /** Primera pantalla sin sesión: el login es la entrada y ofrece usar la app sin cuenta. */
    ENTRADA,

    /** La nube abierta desde el menú ⋮ de finanzas. */
    NUBE,
    PRIVADO,
}

/**
 * Contenido raíz de la distribución nube (D-020). Sin sesión iniciada, la app abre en el
 * login (una sola pantalla: correo, Google o "usar sin cuenta") y el logo del splash aterriza
 * en su símbolo. Con sesión iniciada entra directo a finanzas.
 */
@Composable
fun ContenidoApp() {
    val context = LocalContext.current
    var modo by rememberSaveable {
        // El estado inicial de la sesión es síncrono (Firebase ya conoce currentUser): sin parpadeo.
        val sesion = ContenedorNube.obtener(context).repositorioSesion?.estado?.value
        mutableStateOf(if (sesion is EstadoSesion.Autenticado) ModoInicioNube.PRIVADO else ModoInicioNube.ENTRADA)
    }

    // Guarda pestaña y subpantalla de finanzas mientras la nube está abierta: al volver, sigues donde estabas.
    val guardadorFinanzas = rememberSaveableStateHolder()

    when (modo) {
        ModoInicioNube.ENTRADA -> {
            // Se olvida el destino al salir de la entrada, para que otro splash se despida normal.
            DisposableEffect(Unit) {
                onDispose { EstadoSplashKren.destinoLogo = null }
            }
            val entrada = remember {
                EntradaNube(
                    onUsarSinCuenta = { modo = ModoInicioNube.PRIVADO },
                    splashTerminado = { EstadoSplashKren.terminado },
                    onDestinoLogo = { caja, barras, punto ->
                        EstadoSplashKren.destinoLogo = DestinoLogoSplash(caja, barras, punto)
                    },
                )
            }
            SeccionNube(
                // Atrás en la entrada cierra la app, como en cualquier primera pantalla.
                onSalir = { (context as? Activity)?.finish() },
                onIrAFinanzas = { modo = ModoInicioNube.PRIVADO },
                entrada = entrada,
            )
        }

        ModoInicioNube.NUBE -> SeccionNube(
            onSalir = { modo = ModoInicioNube.PRIVADO },
            onIrAFinanzas = { modo = ModoInicioNube.PRIVADO },
        )

        // En modo privado la nube se abre desde el Perfil (entrar o catálogo).
        ModoInicioNube.PRIVADO -> guardadorFinanzas.SaveableStateProvider("finanzas") {
            FinanzasConPerfil(onAbrirNube = { modo = ModoInicioNube.NUBE })
        }
    }
}

/** Finanzas con el Perfil al tanto de la sesión: muestra la cuenta y deja cerrar sesión. */
@Composable
private fun FinanzasConPerfil(onAbrirNube: () -> Unit) {
    val context = LocalContext.current
    val repositorio = remember(context) { ContenedorNube.obtener(context).repositorioSesion }
    // Sin Firebase configurado no hay sesión posible: el Perfil invita a entrar y la nube explica qué falta.
    val estado by (repositorio?.estado ?: remember { MutableStateFlow(EstadoSesion.SinSesion) })
        .collectAsStateWithLifecycle()
    val usuario = (estado as? EstadoSesion.Autenticado)?.usuario
    var confirmarCierre by rememberSaveable { mutableStateOf(false) }
    var cerrando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    val cuenta = remember(usuario, onAbrirNube) {
        CuentaPerfil(
            correo = usuario?.correo,
            nombre = usuario?.nombre,
            onEntrar = onAbrirNube,
            onAbrirCatalogo = onAbrirNube,
            onCerrarSesion = { confirmarCierre = true },
        )
    }
    ContenedorPrincipal(cuenta = cuenta)

    // Si la sesión se cerró por fuera (token revocado, desde el catálogo), el diálogo ya no aplica.
    if (confirmarCierre && repositorio != null && usuario != null) {
        DialogoCerrarSesion(
            enCurso = cerrando,
            onConfirmar = {
                cerrando = true
                alcance.launch {
                    try {
                        repositorio.cerrarSesion()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Igual que el catálogo: si falla, el diálogo se cierra y la sesión sigue visible.
                    } finally {
                        cerrando = false
                        confirmarCierre = false
                    }
                }
            },
            onCancelar = { confirmarCierre = false },
        )
    }
}
