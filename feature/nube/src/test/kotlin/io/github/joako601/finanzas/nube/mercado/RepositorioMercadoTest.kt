package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RepositorioMercadoTest {

    @get:Rule val carpeta = TemporaryFolder()

    private val ana = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com"))
    private val beto = EstadoSesion.Autenticado(UsuarioSesion("u2", "beto@ejemplo.com"))
    private val parte = datosDePrueba().let { ParteMercado(it.cotizaciones, it.indicadores, it.productos, it.diasInhabiles) }

    private class Escenario(
        val repo: RepositorioMercado,
        val sesion: MutableStateFlow<EstadoSesion>,
        val conectado: MutableStateFlow<Boolean>,
        val cache: CacheMercado,
    ) {
        var ahora = 0L
        var llamadas = 0
    }

    private fun TestScope.escenario(
        sesionInicial: EstadoSesion,
        resultado: () -> ResultadoFuente,
    ): Escenario {
        val sesion = MutableStateFlow(sesionInicial)
        val conectado = MutableStateFlow(true)
        val cache = CacheMercado(File(carpeta.root, "mercado"))
        lateinit var e: Escenario
        val despachador = UnconfinedTestDispatcher(testScheduler)
        val repo = RepositorioMercado(
            fuente = { e.llamadas++; resultado() },
            cache = cache,
            conectado = conectado,
            sesion = sesion,
            alcance = backgroundScope,
            io = despachador,
            reloj = { e.ahora },
        )
        e = Escenario(repo, sesion, conectado, cache)
        // El estado se calcula en backgroundScope: hay que dejarlo correr.
        backgroundScope.launch(despachador) { repo.estado.collect {} }
        testScheduler.runCurrent()
        return e
    }

    @Test
    fun nunca_entro_queda_oculto() = runTest {
        val e = escenario(EstadoSesion.SinSesion) { ResultadoFuente.Exito(parte) }
        e.repo.sincronizar(forzar = true)
        assertEquals(EstadoMercado.Oculto, e.repo.estado.value)
        assertEquals(0, e.llamadas)
    }

    @Test
    fun con_sesion_espera_y_luego_muestra_datos() = runTest {
        val e = escenario(ana) { ResultadoFuente.Exito(parte) }
        assertTrue(e.repo.estado.value is EstadoMercado.Esperando)
        e.ahora = 5_000
        e.repo.sincronizar()
        testScheduler.runCurrent()
        val datos = e.repo.estado.value as EstadoMercado.Datos
        assertEquals("u1", datos.datos.uid)
        assertEquals(5_000L, datos.datos.recibidoEn)
        assertEquals(datos.datos, e.cache.leer("u1"))
    }

    @Test
    fun sin_internet_con_cache_muestra_lo_guardado() = runTest {
        CacheMercado(File(carpeta.root, "mercado")).guardar(datosDePrueba())
        val e = escenario(ana) { ResultadoFuente.SinConexion }
        e.conectado.value = false
        e.repo.sincronizar(forzar = true)
        testScheduler.runCurrent()
        val datos = e.repo.estado.value as EstadoMercado.Datos
        assertEquals(false, datos.conectado)
        assertEquals(datosDePrueba(), datos.datos)
    }

    @Test
    fun parte_fallida_conserva_la_anterior() = runTest {
        CacheMercado(File(carpeta.root, "mercado")).guardar(datosDePrueba())
        val e = escenario(ana) { ResultadoFuente.Exito(ParteMercado(emptyList(), null, null, null)) }
        e.repo.sincronizar(forzar = true)
        testScheduler.runCurrent()
        val datos = (e.repo.estado.value as EstadoMercado.Datos).datos
        assertTrue(datos.cotizaciones.isEmpty())
        assertEquals(datosDePrueba().indicadores, datos.indicadores)
        assertEquals(datosDePrueba().diasInhabiles, datos.diasInhabiles)
    }

    @Test
    fun no_repite_la_llamada_antes_de_la_espera_salvo_forzada() = runTest {
        val e = escenario(ana) { ResultadoFuente.Exito(parte) }
        e.repo.sincronizar()
        e.ahora = RepositorioMercado.ESPERA_MS - 1
        e.repo.sincronizar()
        assertEquals(1, e.llamadas)
        e.repo.sincronizar(forzar = true)
        assertEquals(2, e.llamadas)
        e.ahora = 2 * RepositorioMercado.ESPERA_MS
        e.repo.sincronizar()
        assertEquals(3, e.llamadas)
    }

    @Test
    fun sin_conexion_no_cuenta_para_la_espera() = runTest {
        var respuesta: ResultadoFuente = ResultadoFuente.SinConexion
        val e = escenario(ana) { respuesta }
        e.repo.sincronizar()
        respuesta = ResultadoFuente.Exito(parte)
        e.repo.sincronizar()
        assertEquals(2, e.llamadas)
    }

    @Test
    fun sesion_rechazada_por_el_servidor_conserva_datos_y_avisa() = runTest {
        CacheMercado(File(carpeta.root, "mercado")).guardar(datosDePrueba())
        val e = escenario(ana) { ResultadoFuente.SesionExpirada }
        e.repo.sincronizar(forzar = true)
        testScheduler.runCurrent()
        val datos = e.repo.estado.value as EstadoMercado.Datos
        assertTrue(datos.sesionExpirada)
        assertEquals(datosDePrueba(), datos.datos)
    }

    @Test
    fun sesion_caida_por_fuera_conserva_datos_y_avisa() = runTest {
        CacheMercado(File(carpeta.root, "mercado")).guardar(datosDePrueba())
        val e = escenario(ana) { ResultadoFuente.Exito(parte) }
        e.sesion.value = EstadoSesion.SinSesion
        testScheduler.runCurrent()
        assertTrue((e.repo.estado.value as EstadoMercado.Datos).sesionExpirada)
    }

    @Test
    fun cerrar_sesion_a_proposito_borra_y_oculta() = runTest {
        CacheMercado(File(carpeta.root, "mercado")).guardar(datosDePrueba())
        val e = escenario(ana) { ResultadoFuente.Exito(parte) }
        e.repo.borrar()
        e.sesion.value = EstadoSesion.SinSesion
        testScheduler.runCurrent()
        assertEquals(EstadoMercado.Oculto, e.repo.estado.value)
        assertNull(e.cache.leerCualquiera())
    }

    @Test
    fun respuesta_que_llega_despues_de_borrar_se_descarta() = runTest {
        val puerta = CompletableDeferred<Unit>()
        val sesion = MutableStateFlow<EstadoSesion>(ana)
        val cache = CacheMercado(File(carpeta.root, "mercado"))
        val despachador = UnconfinedTestDispatcher(testScheduler)
        val repo = RepositorioMercado(
            fuente = { puerta.await(); ResultadoFuente.Exito(parte) },
            cache = cache,
            conectado = MutableStateFlow(true),
            sesion = sesion,
            alcance = backgroundScope,
            io = despachador,
        )
        backgroundScope.launch(despachador) { repo.estado.collect {} }
        val sync = launch(despachador) { repo.sincronizar() }
        repo.borrar()
        puerta.complete(Unit)
        sync.join()
        testScheduler.runCurrent()
        assertNull(cache.leerCualquiera())
        assertTrue(repo.estado.value is EstadoMercado.Esperando)
    }

    @Test
    fun otra_cuenta_invalida_la_cache_anterior() = runTest {
        CacheMercado(File(carpeta.root, "mercado")).guardar(datosDePrueba("u1"))
        val e = escenario(beto) { ResultadoFuente.Exito(parte) }
        assertTrue(e.repo.estado.value is EstadoMercado.Esperando)
        assertNull(e.cache.leerCualquiera())
    }
}
