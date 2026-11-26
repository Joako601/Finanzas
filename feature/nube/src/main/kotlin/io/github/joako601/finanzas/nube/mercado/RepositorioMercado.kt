package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException

/** Lo que muestra la pestaña Mercado. */
sealed interface EstadoMercado {
    /** Sin sesión y sin datos guardados: la pestaña no aparece. */
    data object Oculto : EstadoMercado

    /** Con sesión, pero todavía no llega la primera respuesta. */
    data class Esperando(val actualizando: Boolean, val conectado: Boolean) : EstadoMercado

    /**
     * Hay datos guardados. [sesionExpirada]: el servidor rechazó la sesión o se cerró por
     * fuera; se siguen mostrando y se invita a iniciar sesión para actualizar.
     */
    data class Datos(
        val datos: DatosMercado,
        val conectado: Boolean,
        val actualizando: Boolean,
        val sesionExpirada: Boolean,
    ) : EstadoMercado
}

/**
 * Fuente única de la pestaña Mercado: une la caché, la sesión y la conexión.
 *
 * - Las actualizaciones reemplazan los datos en silencio: mientras llega la respuesta se
 *   sigue viendo lo anterior, y si una parte falla se conserva la anterior de esa parte.
 * - [sincronizar] sin forzar no repite la llamada si hubo una respuesta hace menos de
 *   [ESPERA_MS]. Solo corre una a la vez.
 * - [borrar] es para cerrar sesión a propósito: la pestaña desaparece. Una respuesta que
 *   llegue después de borrar se descarta.
 */
class RepositorioMercado internal constructor(
    private val fuente: FuenteMercado,
    private val cache: CacheMercado,
    conectado: StateFlow<Boolean>,
    private val sesion: StateFlow<EstadoSesion>,
    alcance: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val datos = MutableStateFlow<DatosMercado?>(null)
    private val actualizando = MutableStateFlow(false)
    private val expirada = MutableStateFlow(false)
    private val mutex = Mutex()

    @Volatile private var ultimaRespuesta: Long? = null

    @Volatile private var generacion = 0

    val estado: StateFlow<EstadoMercado> =
        combine(datos, actualizando, expirada, conectado, sesion) { d, act, exp, con, s ->
            val autenticado = s is EstadoSesion.Autenticado
            when {
                d != null -> EstadoMercado.Datos(d, con, act, exp || !autenticado)
                autenticado -> EstadoMercado.Esperando(act, con)
                else -> EstadoMercado.Oculto
            }
        }.stateIn(alcance, SharingStarted.Eagerly, EstadoMercado.Oculto)

    init {
        alcance.launch {
            sesion.collect { s ->
                val gen = generacion
                val leidos = when (s) {
                    // Si la caché es de otra cuenta, leer la borra.
                    is EstadoSesion.Autenticado -> withContext(io) { cache.leer(s.usuario.uid) }
                    EstadoSesion.SinSesion -> withContext(io) { cache.leerCualquiera() }
                    EstadoSesion.Cargando -> return@collect
                }
                if (gen != generacion) return@collect
                if (leidos?.uid != datos.value?.uid) {
                    expirada.value = false
                    ultimaRespuesta = null
                }
                datos.value = leidos
            }
        }
    }

    suspend fun sincronizar(forzar: Boolean = false) {
        val uid = (sesion.value as? EstadoSesion.Autenticado)?.usuario?.uid ?: return
        if (!mutex.tryLock()) return
        try {
            val ultima = ultimaRespuesta
            if (!forzar && ultima != null && reloj() - ultima < ESPERA_MS) return
            val gen = generacion
            actualizando.value = true
            val resultado = fuente.obtener()
            if (gen != generacion || (sesion.value as? EstadoSesion.Autenticado)?.usuario?.uid != uid) return
            when (resultado) {
                is ResultadoFuente.Exito -> guardar(uid, resultado.parte, gen)
                ResultadoFuente.SesionExpirada -> expirada.value = true
                ResultadoFuente.SinConexion, ResultadoFuente.ErrorServidor -> Unit
            }
        } finally {
            actualizando.value = false
            mutex.unlock()
        }
    }

    suspend fun borrar() {
        generacion++
        withContext(io) { cache.borrar() }
        datos.value = null
        expirada.value = false
        ultimaRespuesta = null
    }

    private suspend fun guardar(uid: String, parte: ParteMercado, gen: Int) {
        val anterior = datos.value?.takeIf { it.uid == uid }
        val nuevos = DatosMercado(
            uid = uid,
            recibidoEn = reloj(),
            cotizaciones = parte.cotizaciones ?: anterior?.cotizaciones.orEmpty(),
            indicadores = parte.indicadores ?: anterior?.indicadores.orEmpty(),
            productos = parte.productos ?: anterior?.productos.orEmpty(),
            diasInhabiles = parte.diasInhabiles ?: anterior?.diasInhabiles.orEmpty(),
        )
        try {
            withContext(io) { cache.guardar(nuevos) }
        } catch (_: IOException) {
            // Sin espacio en disco: se muestran igual, solo no sobreviven al cierre de la app.
        }
        if (gen != generacion) return
        ultimaRespuesta = nuevos.recibidoEn
        expirada.value = false
        datos.value = nuevos
    }

    companion object {
        const val ESPERA_MS: Long = 15 * 60 * 1000L
    }
}
