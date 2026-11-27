package io.github.joako601.finanzas.nube.catalogo

import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.red.ResultadoTransporte
import io.github.joako601.finanzas.nube.red.TransporteHttp
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorioCatalogoRemotoTest {

    @Test
    fun parsea_respuesta_json_del_backend_con_y_sin_tasa() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )
        val json = """
            [
              {
                "id": 1,
                "nombre": "Nu México",
                "categoria": "Tarjeta de crédito",
                "entidad": "Nu México",
                "costoReferenciaPesos": 0,
                "periodicidad": "Anual",
                "tasaAnualReferencia": null,
                "detalleReferencia": "Sin anualidad y diferimiento en app",
                "fuenteReferencia": "CONDUSEF / Nu",
                "imageUrl": null
              },
              {
                "id": 2,
                "nombre": "CETES 28 días",
                "categoria": "Inversión",
                "entidad": "Cetesdirecto",
                "costoReferenciaPesos": 0,
                "periodicidad": "Sin costo",
                "tasaAnualReferencia": 10.25,
                "verificado": true,
                "detalleReferencia": "Instrumento gubernamental a 28 días",
                "fuenteReferencia": "Banxico / Cetesdirecto",
                "imageUrl": "https://cdn.ejemplo.mx/cetes.png"
              }
            ]
        """.trimIndent()

        val cliente = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(200, json) },
            despachadorIo = despachador,
        )
        val repo = RepositorioCatalogoRemoto(cliente)

        val resultado = repo.obtenerProductos()

        assertTrue(resultado is ResultadoCatalogo.Exito)
        val productos = (resultado as ResultadoCatalogo.Exito).productos
        assertEquals(2, productos.size)
        assertEquals("Nu México", productos[0].nombre)
        assertNull(productos[0].tasaAnualReferencia)
        assertEquals(10.25, productos[1].tasaAnualReferencia!!, 0.001)
        // Sin el campo (backend anterior) cuenta como no verificado.
        assertEquals(false, productos[0].verificado)
        assertEquals(true, productos[1].verificado)
        assertEquals("https://cdn.ejemplo.mx/cetes.png", productos[1].imageUrl)
    }

    @Test
    fun mapea_arreglo_vacio_sin_conexion_401_y_json_roto() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )

        val repoVacio = RepositorioCatalogoRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(200, "[]") },
                despachadorIo = despachador,
            ),
        )
        assertEquals(ResultadoCatalogo.Exito(emptyList()), repoVacio.obtenerProductos())

        val repoSinRed = RepositorioCatalogoRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { _, _ -> ResultadoTransporte.ErrorRed },
                despachadorIo = despachador,
            ),
        )
        assertEquals(ResultadoCatalogo.SinConexion, repoSinRed.obtenerProductos())

        val repo401 = RepositorioCatalogoRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(401, "") },
                despachadorIo = despachador,
            ),
        )
        assertEquals(ResultadoCatalogo.SesionExpirada, repo401.obtenerProductos())

        val repoJsonRoto = RepositorioCatalogoRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(200, "{no-es-arreglo}") },
                despachadorIo = despachador,
            ),
        )
        assertEquals(ResultadoCatalogo.ErrorServidor, repoJsonRoto.obtenerProductos())
    }
}
