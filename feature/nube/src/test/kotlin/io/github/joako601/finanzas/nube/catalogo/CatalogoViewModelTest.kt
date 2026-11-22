package io.github.joako601.finanzas.nube.catalogo

import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogoViewModelTest {

    private val despachador = StandardTestDispatcher()

    @Before
    fun configurar() {
        Dispatchers.setMain(despachador)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun carga_exitosa_devuelve_12_productos_con_categorias_no_vacias() = runTest(despachador) {
        val repositorioSesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = RepositorioCatalogoFalso(),
            repositorioSesion = repositorioSesion,
        )

        advanceUntilIdle()

        val lista = viewModel.estado.value.lista
        assertTrue("Se esperaba estado Contenido, pero fue $lista", lista is EstadoListaCatalogo.Contenido)
        val productos = (lista as EstadoListaCatalogo.Contenido).productos
        assertEquals(12, productos.size)
        assertEquals(12, productos.map { it.id }.distinct().size)
        assertTrue(productos.all { it.nombre.isNotBlank() })
        assertTrue(productos.all { it.categoria.isNotBlank() })
        assertTrue(productos.all { it.entidad.isNotBlank() })
        assertTrue(productos.all { it.detalleReferencia.isNotBlank() })
        assertTrue(productos.all { it.fuenteReferencia.isNotBlank() })
    }

    @Test
    fun catalogo_vacio_pasa_a_estado_vacio() = runTest(despachador) {
        val repositorio = RepositorioCatalogoProgramable(
            respuestas = ArrayDeque(listOf(ResultadoCatalogo.Exito(emptyList()))),
        )
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = repositorio,
            repositorioSesion = RepositorioSesionFalso(),
        )

        advanceUntilIdle()

        assertEquals(EstadoListaCatalogo.Vacio, viewModel.estado.value.lista)
    }

    @Test
    fun error_de_red_pasa_a_error_y_reintentar_recupera_contenido() = runTest(despachador) {
        val productosEsperados = (RepositorioCatalogoFalso().obtenerProductos() as ResultadoCatalogo.Exito).productos
        val repositorio = RepositorioCatalogoProgramable(
            respuestas = ArrayDeque(
                listOf(
                    ResultadoCatalogo.SinConexion,
                    ResultadoCatalogo.Exito(productosEsperados),
                ),
            ),
        )
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = repositorio,
            repositorioSesion = RepositorioSesionFalso(),
        )

        advanceUntilIdle()
        assertEquals(EstadoListaCatalogo.Error(ErrorCatalogo.SIN_CONEXION), viewModel.estado.value.lista)

        viewModel.reintentar()
        advanceUntilIdle()

        val lista = viewModel.estado.value.lista
        assertTrue("Se esperaba Contenido tras reintentar, pero fue $lista", lista is EstadoListaCatalogo.Contenido)
        assertEquals(12, (lista as EstadoListaCatalogo.Contenido).productos.size)
    }

    @Test
    fun error_de_servidor_pasa_a_error_y_reintentar_recupera_contenido() = runTest(despachador) {
        val productosEsperados = (RepositorioCatalogoFalso().obtenerProductos() as ResultadoCatalogo.Exito).productos
        val repositorio = RepositorioCatalogoProgramable(
            respuestas = ArrayDeque(
                listOf(
                    ResultadoCatalogo.ErrorServidor,
                    ResultadoCatalogo.Exito(productosEsperados),
                ),
            ),
        )
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = repositorio,
            repositorioSesion = RepositorioSesionFalso(),
        )

        advanceUntilIdle()
        assertEquals(EstadoListaCatalogo.Error(ErrorCatalogo.ERROR_SERVIDOR), viewModel.estado.value.lista)

        viewModel.reintentar()
        advanceUntilIdle()

        val lista = viewModel.estado.value.lista
        assertTrue("Se esperaba Contenido tras reintentar, pero fue $lista", lista is EstadoListaCatalogo.Contenido)
        assertEquals(12, (lista as EstadoListaCatalogo.Contenido).productos.size)
    }

    @Test
    fun excepcion_inesperada_en_obtenerProductos_pasa_a_error_servidor() = runTest(despachador) {
        val repositorio = object : RepositorioCatalogo {
            override suspend fun obtenerProductos(): ResultadoCatalogo {
                throw IllegalStateException("Fallo inesperado del cliente")
            }
        }
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = repositorio,
            repositorioSesion = RepositorioSesionFalso(),
        )

        advanceUntilIdle()

        assertEquals(EstadoListaCatalogo.Error(ErrorCatalogo.ERROR_SERVIDOR), viewModel.estado.value.lista)
    }

    @Test
    fun cerrar_sesion_exitoso_mantiene_estado_de_cierre_sin_parpadear() = runTest(despachador) {
        val repositorioSesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = RepositorioCatalogoFalso(),
            repositorioSesion = repositorioSesion,
        )
        advanceUntilIdle()

        viewModel.cerrarSesion()
        advanceUntilIdle()

        assertEquals(1, repositorioSesion.vecesCerrarSesion)
        assertEquals(EstadoSesion.SinSesion, repositorioSesion.estado.value)
        assertTrue(viewModel.estado.value.cerrandoSesion)
    }

    @Test
    fun cerrar_sesion_con_fallo_restablece_estado_de_cierre() = runTest(despachador) {
        val delegado = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )
        val repositorioSesion = object : RepositorioSesion by delegado {
            override suspend fun cerrarSesion() {
                throw IllegalStateException("Error al cerrar sesión")
            }
        }
        val viewModel = CatalogoViewModel(
            repositorioCatalogo = RepositorioCatalogoFalso(),
            repositorioSesion = repositorioSesion,
        )
        advanceUntilIdle()

        viewModel.cerrarSesion()
        advanceUntilIdle()

        assertFalse(viewModel.estado.value.cerrandoSesion)
    }

    @Test
    fun formato_de_costos_maneja_cero_y_montos_positivos() {
        assertEquals("Sin anualidad", formatearCostoReferencia(0L, "Anual"))
        assertEquals("Sin costo", formatearCostoReferencia(0L, "Sin costo"))
        assertEquals("Sin costo", formatearCostoReferencia(0L, "Mensual"))
        assertEquals("$748 MXN / Anual", formatearCostoReferencia(748L, "Anual"))
        assertEquals("$1,250 MXN / Anual", formatearCostoReferencia(1250L, "Anual"))
        assertEquals("$129 MXN / Mensual", formatearCostoReferencia(129L, "Mensual"))
        assertEquals("$450 MXN / Bimestral", formatearCostoReferencia(450L, "Bimestral"))
    }
}

private class RepositorioCatalogoProgramable(
    private val respuestas: ArrayDeque<ResultadoCatalogo>,
) : RepositorioCatalogo {
    override suspend fun obtenerProductos(): ResultadoCatalogo =
        respuestas.removeFirstOrNull() ?: ResultadoCatalogo.ErrorServidor
}
