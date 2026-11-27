package io.github.joako601.finanzas

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.joako601.finanzas.nube.ContenedorNube
import io.github.joako601.finanzas.nube.EntradaNube
import io.github.joako601.finanzas.nube.SeccionNube
import io.github.joako601.finanzas.nube.mercado.EstadoMercado
import io.github.joako601.finanzas.nube.mercado.PantallaMercado
import io.github.joako601.finanzas.nube.mercado.textoDisparo
import io.github.joako601.finanzas.recordatorios.Avisos
import io.github.joako601.finanzas.ui.tarjetas.recordarAvisos
import io.github.joako601.finanzas.nube.recuperar.DialogoCerrarSesion
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.ui.componentes.DestinoLogoSplash
import io.github.joako601.finanzas.ui.componentes.EstadoSplashKren
import io.github.joako601.finanzas.ui.navegacion.ContenedorPrincipal
import io.github.joako601.finanzas.ui.perfil.CuentaPerfil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import io.github.joako601.finanzas.core.domain.mercado.calcularUsoPropio
import io.github.joako601.finanzas.ui.componentes.LocalMontosOcultos
import io.github.joako601.finanzas.ui.hoy

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

        // En modo privado la nube se abre desde el Perfil o desde Mercado para iniciar sesión.
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
            onCerrarSesion = { confirmarCierre = true },
        )
    }
    val repositorioMercado = remember(context) { ContenedorNube.obtener(context).repositorioMercado }
    val estadoMercado by repositorioMercado.estado.collectAsStateWithLifecycle()
    // Sin WorkManager: Mercado se actualiza solo con la app al frente (la espera de 15 min evita llamadas de más).
    LifecycleStartEffect(repositorioMercado, usuario?.uid) {
        val tarea = alcance.launch { repositorioMercado.sincronizar() }
        onStopOrDispose { tarea.cancel() }
    }
    // Las alertas se evalúan al sincronizar, que solo pasa con esta pantalla viva: aquí se publican.
    LaunchedEffect(repositorioMercado) {
        val avisos = Avisos(context.applicationContext)
        repositorioMercado.alertas?.disparos?.collect { disparo ->
            val (titulo, texto) = textoDisparo(context, disparo)
            avisos.mostrarAlertaCambio(disparo.alerta.id, titulo, texto)
        }
    }
    val avisos = recordarAvisos()
    // El comparador de Mercado necesita el uso propio; se calcula aquí porque :feature:nube no ve la base.
    val usoPropio by remember(context) {
        val contenedor = (context.applicationContext as FinanzasApp).contenedor
        val libro = contenedor.repositorioLibro
        combine(
            contenedor.repositorioTarjetas.observarTarjetas(),
            libro.observarSaldos(),
            libro.observarMovimientos(),
            libro.observarCuentas(),
        ) { tarjetas, saldos, movimientos, cuentas ->
            calcularUsoPropio(tarjetas, saldos, movimientos, cuentas.associateBy { it.id }, hoy())
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(initialValue = null)
    ContenedorPrincipal(
        cuenta = cuenta,
        mercado = if (estadoMercado is EstadoMercado.Oculto) {
            null
        } else {
            { margenInferior ->
                PantallaMercado(
                    repositorio = repositorioMercado,
                    margenInferior = margenInferior,
                    onIniciarSesion = onAbrirNube,
                    avisosActivos = avisos.activos,
                    onActivarAvisos = avisos.activar,
                    usoPropio = usoPropio,
                    montosOcultos = LocalMontosOcultos.current,
                )
            }
        },
    )

    // Si la sesión se cerró por fuera (token revocado, desde el catálogo), el diálogo ya no aplica
    // y no debe reaparecer al volver a entrar.
    LaunchedEffect(usuario) { if (usuario == null) confirmarCierre = false }
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
