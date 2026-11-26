package io.github.joako601.finanzas.nube.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.joako601.finanzas.nube.R
import io.github.joako601.finanzas.nube.componentes.ColoresSemanticosNube
import io.github.joako601.finanzas.nube.componentes.rememberAnimacionesDesactivadasNube

/*
 * El logo de Kren como mascota del login. Las medidas salen de ic_launcher_frente.xml
 * (lienzo de 108 con centro en 54,54): barra de arriba de (-20,-16) a (8,-16), barra de
 * abajo de (-8,16) a (20,16), grosor 8.5 y punto verde de radio 5.5 al centro.
 * Aquí todo va en esas unidades relativas al centro y se escala al tamaño del lienzo.
 */
private const val GROSOR = 8.5f
private const val RADIO_PUNTO = 5.5f
private const val UNIDADES_VISIBLES = 64f

private val ARRIBA_INICIO = Offset(-20f, -16f)
private val ARRIBA_FIN = Offset(8f, -16f)
private val ABAJO_INICIO = Offset(-8f, 16f)
private val ABAJO_FIN = Offset(20f, 16f)

// Palomita de éxito: la barra de arriba se vuelve el trazo corto y la de abajo el largo.
private val PALOMITA_CORTA_INICIO = Offset(-16f, 1f)
private val PALOMITA_VERTICE = Offset(-5f, 12f)
private val PALOMITA_LARGA_FIN = Offset(17f, -12f)

/**
 * Símbolo de Kren animado según el estado del login:
 * - Reposo: respira y parpadea de vez en cuando (las barras se acercan al punto).
 * - Correo: el punto mira hacia el campo y sigue el texto de izquierda a derecha.
 * - Contraseña: las barras se cierran con cada carácter; al mostrarla se abren para espiar y
 *   al ocultarla vuelven con rebote. Con mayúsculas el punto sube.
 * - Cargando: las barras giran alrededor del punto.
 * - Éxito: las barras se vuelven una palomita verde.
 * - Error: sacudida y punto rojo.
 * Con "Quitar animaciones" todo cambia sin movimiento.
 */
@Composable
internal fun SimboloKren(
    estado: EstadoMascotaLogin,
    contrasenaVisible: Boolean,
    intentoError: Int,
    avanceCorreo: Float,
    largoContrasena: Int,
    mayusculas: Boolean,
    modifier: Modifier = Modifier,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadasNube()
    fun <T> resorte() = if (sinAnimaciones) snap<T>() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    val enContrasena = estado == EstadoMascotaLogin.EscribiendoContrasena
    // Oculta: se cierran un poco más con cada carácter (55 % vacía, 100 % a los 8).
    // Visible: se abren de golpe para "espiar"; al ocultar vuelven con un rebote.
    val cierre by animateFloatAsState(
        targetValue = when {
            !enContrasena -> 0f
            contrasenaVisible -> 0.15f
            else -> 0.55f + 0.45f * minOf(largoContrasena, CARACTERES_CIERRE) / CARACTERES_CIERRE
        },
        animationSpec = when {
            sinAnimaciones -> snap()
            contrasenaVisible -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            else -> spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "cierre",
    )
    // Con la contraseña oculta el punto no se ve nunca, aunque las barras aún no cierren del todo.
    val puntoVisible by animateFloatAsState(
        targetValue = if (enContrasena && !contrasenaVisible) 0f else 1f,
        animationSpec = resorte(),
        label = "puntoVisible",
    )
    // Con mayúsculas la barra de arriba (y el punto, si se ve) sube: avisa junto con el texto bajo el campo.
    val alzado by animateFloatAsState(if (mayusculas) 1f else 0f, resorte(), label = "alzado")
    val mirada by animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.EscribiendoCorreo) 1f else 0f,
        animationSpec = resorte(),
        label = "mirada",
    )
    // 0 = el texto apenas empieza (mira a la izquierda), 1 = llegó al final del campo (derecha).
    val lado by animateFloatAsState(avanceCorreo.coerceIn(0f, 1f), resorte(), label = "lado")
    val palomita by animateFloatAsState(
        targetValue = if (estado == EstadoMascotaLogin.Exito) 1f else 0f,
        animationSpec = resorte(),
        label = "palomita",
    )

    val verde = ColoresSemanticosNube.exito()
    val colorBarras by animateColorAsState(
        targetValue = if (estado == EstadoMascotaLogin.Exito) verde else MaterialTheme.colorScheme.onSurface,
        animationSpec = if (sinAnimaciones) snap() else tween(220),
        label = "colorBarras",
    )
    val colorPunto by animateColorAsState(
        targetValue = if (estado == EstadoMascotaLogin.Error) MaterialTheme.colorScheme.error else verde,
        animationSpec = if (sinAnimaciones) snap() else tween(220),
        label = "colorPunto",
    )

    val sacudida = remember { Animatable(0f) }
    LaunchedEffect(estado, intentoError, sinAnimaciones) {
        if (estado == EstadoMascotaLogin.Error && !sinAnimaciones) {
            for (x in floatArrayOf(-7f, 7f, -5f, 5f, -2f, 0f)) sacudida.animateTo(x, tween(48, easing = FastOutSlowInEasing))
        } else {
            sacudida.snapTo(0f)
        }
    }

    val enMovimiento = !sinAnimaciones
    val transicion = rememberInfiniteTransition(label = "simbolo")
    // Respiración y parpadeo solo en reposo o escribiendo el correo; giro solo al cargar.
    val respiracion = if (enMovimiento) {
        transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "respira")
    } else {
        null
    }
    val parpadeo = if (enMovimiento) {
        transicion.animateFloat(
            0f,
            1f,
            infiniteRepeatable(
                androidx.compose.animation.core.keyframes {
                    durationMillis = 4200
                    0f at 0
                    0f at 3900
                    1f at 4020
                    0f at 4200
                },
            ),
            "parpadea",
        )
    } else {
        null
    }
    val giro = if (enMovimiento && estado == EstadoMascotaLogin.Cargando) {
        transicion.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), "gira")
    } else {
        null
    }

    val descripcion = stringResource(
        when (estado) {
            EstadoMascotaLogin.Reposo -> R.string.login_mascota_reposo
            EstadoMascotaLogin.EscribiendoCorreo -> R.string.login_mascota_correo
            EstadoMascotaLogin.EscribiendoContrasena -> R.string.login_mascota_contrasena
            EstadoMascotaLogin.Cargando -> R.string.login_mascota_cargando
            EstadoMascotaLogin.Exito -> R.string.login_mascota_exito
            EstadoMascotaLogin.Error -> R.string.login_mascota_error
        },
    )

    Canvas(
        modifier = modifier
            .size(112.dp)
            .semantics { contentDescription = descripcion },
    ) {
        val escala = size.minDimension / UNIDADES_VISIBLES
        val tranquilo = estado == EstadoMascotaLogin.Reposo || estado == EstadoMascotaLogin.EscribiendoCorreo
        val latido = (respiracion?.value ?: 0f) * if (tranquilo || estado == EstadoMascotaLogin.Exito) 1f else 0f
        // El parpadeo cierra un 60 % las barras un instante, solo en reposo (escribiendo se veía raro).
        val enReposo = estado == EstadoMascotaLogin.Reposo
        val cierreTotal = maxOf(cierre, if (enReposo) (parpadeo?.value ?: 0f) * 0.6f else 0f)

        withTransform({
            translate(left = size.width / 2f + sacudida.value * escala, top = size.height / 2f)
            scale(escala * (1f + 0.035f * latido), escala * (1f + 0.035f * latido), pivot = Offset.Zero)
        }) {
            rotate(giro?.value ?: 0f, pivot = Offset.Zero) {
                // Cerrar: cada barra viaja hacia la línea del punto (y = 0).
                fun cerrada(p: Offset) = Offset(p.x, lerp(p.y, 0f, cierreTotal * 0.86f))
                // Mayúsculas: la barra de arriba se levanta como una ceja.
                val ceja = Offset(0f, -5f * alzado)
                barra(
                    desde = mezcla(cerrada(ARRIBA_INICIO) + ceja, PALOMITA_CORTA_INICIO, palomita),
                    hasta = mezcla(cerrada(ARRIBA_FIN) + ceja, PALOMITA_VERTICE, palomita),
                    color = colorBarras,
                )
                barra(
                    desde = mezcla(cerrada(ABAJO_INICIO), PALOMITA_VERTICE, palomita),
                    hasta = mezcla(cerrada(ABAJO_FIN), PALOMITA_LARGA_FIN, palomita),
                    color = colorBarras,
                )
            }
            // El punto mira al campo de abajo, desaparece del todo con la contraseña oculta
            // (se asoma si se muestra) y se encoge cuando aparece la palomita.
            val radio = RADIO_PUNTO * (1f - palomita) * (1f - cierreTotal) * puntoVisible
            if (radio > 0.2f) {
                drawCircle(colorPunto, radius = radio, center = Offset(lerp(-MIRADA_X, MIRADA_X, lado) * mirada, 4.5f * mirada - 4f * alzado))
            }
        }
    }
}

/** Caracteres con los que las barras terminan de cerrarse. */
private const val CARACTERES_CIERRE = 8

/** Cuánto se desplaza el punto hacia cada lado al seguir el texto (unidades del logo). */
private const val MIRADA_X = 6f

private fun mezcla(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

private fun DrawScope.barra(desde: Offset, hasta: Offset, color: androidx.compose.ui.graphics.Color) {
    drawLine(color, desde, hasta, strokeWidth = GROSOR, cap = StrokeCap.Round)
}

// Retícula del fondo, en dp: todas las barras iguales, paralelas a 60° y con separación fija.
private const val ANGULO_FONDO = 60.0
private const val LARGO_FONDO = 64f
private const val GROSOR_FONDO = 10f
private const val PASO_A_LO_LARGO = 128f // de una barra a la siguiente sobre su misma diagonal
private const val PASO_DE_LADO = 52f // entre diagonales vecinas
private const val VELOCIDAD_FONDO = 14f // dp por segundo
private const val ALFA_FONDO = 0.12f

/**
 * Fondo de barras redondeadas como las del logo, todas paralelas a 60°, avanzando juntas de
 * izquierda a derecha sobre su diagonal. Van a la misma velocidad, así que la separación no
 * cambia y nunca se tocan. Gris del tema, sin color decorativo. Acompaña al símbolo:
 * - Escribiendo la contraseña baja el ritmo.
 * - Error: se sacude junto con el símbolo.
 * - Éxito: sale una onda desde [origenOnda] (el centro del símbolo) y las líneas aceleran y
 *   se desvanecen, como si la app arrancara.
 * Con "Quitar animaciones" quedan quietas. La fase se lee solo al dibujar: el movimiento no
 * provoca recomposiciones.
 */
@Composable
internal fun FondoLineasKren(
    estado: EstadoMascotaLogin,
    intentoError: Int,
    origenOnda: Offset,
    modifier: Modifier = Modifier,
) {
    val sinAnimaciones = rememberAnimacionesDesactivadasNube()
    val exito = estado == EstadoMascotaLogin.Exito && !sinAnimaciones
    val ritmo by animateFloatAsState(
        targetValue = when {
            exito -> RITMO_SALIDA
            estado == EstadoMascotaLogin.EscribiendoContrasena -> 0.25f
            else -> 1f
        },
        animationSpec = if (exito) tween(700, easing = FastOutLinearInEasing) else tween(600),
        label = "ritmoFondo",
    )
    val desvanecer by animateFloatAsState(if (exito) 0f else 1f, tween(if (exito) 700 else 300), label = "desvanecer")
    val onda = remember { Animatable(0f) }
    LaunchedEffect(exito) {
        if (exito) {
            onda.snapTo(0f)
            onda.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        } else {
            onda.snapTo(0f)
        }
    }
    val sacudida = remember { Animatable(0f) }
    LaunchedEffect(estado, intentoError, sinAnimaciones) {
        if (estado == EstadoMascotaLogin.Error && !sinAnimaciones) {
            for (x in floatArrayOf(-5f, 5f, -3f, 3f, -1f, 0f)) sacudida.animateTo(x, tween(48, easing = FastOutSlowInEasing))
        } else {
            sacudida.snapTo(0f)
        }
    }
    val fase = remember { mutableFloatStateOf(0f) }
    if (!sinAnimaciones) {
        LaunchedEffect(Unit) {
            var anterior = withFrameNanos { it }
            while (true) {
                withFrameNanos { ahora ->
                    fase.floatValue += (ahora - anterior) / 1_000_000_000f * ritmo
                    anterior = ahora
                }
            }
        }
    }
    val tinta = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val dp = density
        val rad = Math.toRadians(ANGULO_FONDO)
        // A lo largo: hacia la derecha y arriba (en pantalla, y crece hacia abajo).
        val u = Offset(kotlin.math.cos(rad).toFloat(), -kotlin.math.sin(rad).toFloat())
        // De lado: perpendicular a u.
        val n = Offset(-u.y, u.x)
        val pasoU = PASO_A_LO_LARGO * dp
        val pasoN = PASO_DE_LADO * dp
        val medio = u * (LARGO_FONDO * dp / 2f)
        val avance = (fase.floatValue * VELOCIDAD_FONDO * dp) % pasoU
        val corrimiento = Offset(sacudida.value * dp, 0f)
        // Anclada a la esquina superior izquierda: si el alto cambia (teclado), la retícula no salta.
        val margen = LARGO_FONDO * dp
        val radio = kotlin.math.hypot(size.width, size.height) + margen
        // La onda es un anillo que crece desde el símbolo hasta cubrir la pantalla.
        val frenteOnda = onda.value * radio
        val anchoOnda = ANCHO_ONDA * dp
        val filas = (radio / pasoN).toInt() + 1
        val columnas = (radio / pasoU).toInt() + 2
        for (i in -filas..filas) {
            // Las diagonales vecinas se desfasan media barra: patrón escalonado como el logo.
            val escalon = if (i % 2 == 0) 0f else pasoU / 2f
            for (j in -columnas..columnas) {
                val p = n * (i * pasoN) + u * (j * pasoU + escalon + avance) + corrimiento
                if (p.x < -margen || p.x > size.width + margen || p.y < -margen || p.y > size.height + margen) continue
                val enOnda = if (onda.value > 0f) {
                    (1f - kotlin.math.abs((p - origenOnda).getDistance() - frenteOnda) / anchoOnda).coerceIn(0f, 1f)
                } else {
                    0f
                }
                drawLine(
                    color = tinta.copy(alpha = (ALFA_FONDO + 0.22f * enOnda) * desvanecer),
                    start = p - medio,
                    end = p + medio,
                    strokeWidth = GROSOR_FONDO * dp * (1f + 0.35f * enOnda),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Multiplica la velocidad al entrar: las líneas salen disparadas. */
private const val RITMO_SALIDA = 24f

/** Grosor del anillo de la onda de éxito, en dp. */
private const val ANCHO_ONDA = 90f
