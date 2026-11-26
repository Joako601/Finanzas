package io.github.joako601.finanzas.nube.red

import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.ResultadoTokenId
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClienteHttpBackendTest {

    @Test
    fun validacion_url_exige_https_en_release_y_solo_localhost_o_emulador_en_debug() {
        assertEquals("https://api.finanzas.mx", validarUrlBaseBackend("https://api.finanzas.mx/", permitirHttpLocal = false))
        assertNull(validarUrlBaseBackend("http://localhost:8090", permitirHttpLocal = false))
        assertNull(validarUrlBaseBackend("http://api.externo.com", permitirHttpLocal = true))
        // Bloquea bypass por prefijo que antes pasaba `startsWith("10.")` o `startsWith("192.168.")`.
        assertNull(validarUrlBaseBackend("http://10.attacker.com:8090", permitirHttpLocal = true))
        assertNull(validarUrlBaseBackend("http://192.168.evil.com:8090", permitirHttpLocal = true))
        assertNull(validarUrlBaseBackend("https://user:pass@api.finanzas.mx", permitirHttpLocal = false))
        assertNull(validarUrlBaseBackend("https://api.finanzas.mx?a=1", permitirHttpLocal = false))
        assertEquals("http://localhost:8090", validarUrlBaseBackend("http://localhost:8090/", permitirHttpLocal = true))
        assertEquals("http://10.0.2.2:8090", validarUrlBaseBackend("http://10.0.2.2:8090", permitirHttpLocal = true))
    }

    @Test
    fun get_envia_bearer_y_devuelve_exito() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("uid-1", "ana@ejemplo.com")),
        )
        var urlRecibida = ""
        var tokenRecibido = ""
        val cliente = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { url, token ->
                urlRecibida = url
                tokenRecibido = token
                ResultadoTransporte.Respuesta(200, """{"ok":true}""")
            },
            despachadorIo = despachador,
        )

        val respuesta = cliente.get("/api/v1/productos")

        assertEquals("http://localhost:8090/api/v1/productos", urlRecibida)
        assertEquals("token-falso-uid-1", tokenRecibido)
        assertEquals(RespuestaHttp.Exito(200, """{"ok":true}"""), respuesta)
    }

    @Test
    fun ante_401_concurrentes_refresca_token_una_sola_vez_y_reintenta() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("uid-2", "luis@ejemplo.com")),
        )
        val cliente = ClienteHttpBackend(
            baseUrl = "https://api.ejemplo.mx",
            repositorioSesion = sesion,
            permitirHttpLocal = false,
            transporte = TransporteHttp { _, token ->
                if (token == "token-falso-uid-2") {
                    ResultadoTransporte.Respuesta(401, "")
                } else {
                    ResultadoTransporte.Respuesta(200, "[]")
                }
            },
            despachadorIo = despachador,
        )

        val respuestas = listOf("/api/v1/productos", "/api/v1/divisas/actual?base=USD&destino=MXN")
            .map { ruta -> async { cliente.get(ruta) } }
            .awaitAll()

        assertTrue(respuestas.all { it == RespuestaHttp.Exito(200, "[]") })
        assertEquals(1, sesion.vecesRefrescoForzadoToken)
    }

    @Test
    fun doble_401_no_entra_en_bucle_y_retorna_no_autorizado() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("uid-401", "ana@ejemplo.com")),
        )
        var llamadasHttp = 0
        val cliente = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { _, _ ->
                llamadasHttp++
                ResultadoTransporte.Respuesta(401, "")
            },
            despachadorIo = despachador,
        )

        val respuesta = cliente.get("/api/v1/productos")

        assertEquals(RespuestaHttp.NoAutorizado, respuesta)
        assertEquals(1, sesion.vecesRefrescoForzadoToken)
        assertEquals(2, llamadasHttp)
    }

    @Test
    fun error_de_red_al_obtener_token_retorna_sin_conexion_no_sesion_expirada() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("uid-net", "ana@ejemplo.com")),
        ).apply {
            resultadoTokenIdForzado = ResultadoTokenId.SinConexion
        }
        val cliente = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(200, "[]") },
            despachadorIo = despachador,
        )

        assertEquals(RespuestaHttp.SinConexion, cliente.get("/api/v1/productos"))
    }

    @Test
    fun rechaza_rutas_invalidas_y_mapea_errores_de_red_y_servidor() = runTest {
        val despachador = StandardTestDispatcher(testScheduler)
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("uid-3", "eva@ejemplo.com")),
        )

        val clienteRed = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { _, _ -> ResultadoTransporte.ErrorRed },
            despachadorIo = despachador,
        )
        assertEquals(RespuestaHttp.SinConexion, clienteRed.get("/api/v1/productos"))
        assertTrue(clienteRed.get("sin-barra") is RespuestaHttp.ErrorServidor)
        assertTrue(clienteRed.get("/api/v1/productos con espacio") is RespuestaHttp.ErrorServidor)

        val cliente500 = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(503, "") },
            despachadorIo = despachador,
        )
        val res = cliente500.get("/api/v1/productos")
        assertTrue(res is RespuestaHttp.ErrorServidor && res.codigo == 503)
    }

    @Test
    fun segundo_token_expirado_tras_un_refresco_vuelve_a_refrescar() = runTest {
        val sesion = SesionConTokens(vigente = "t1", refrescos = ArrayDeque(listOf("r1", "r2")))
        var validos = setOf("r1")
        val cliente = ClienteHttpBackend(
            baseUrl = "https://api.ejemplo.mx",
            repositorioSesion = sesion,
            transporte = TransporteHttp { _, token ->
                ResultadoTransporte.Respuesta(if (token in validos) 200 else 401, "[]")
            },
            despachadorIo = StandardTestDispatcher(testScheduler),
        )
        assertEquals(RespuestaHttp.Exito(200, "[]"), cliente.get("/api/v1/productos"))

        // Firebase rotó el token por su cuenta y después ese también expiró.
        sesion.vigente = "t3"
        validos = setOf("r2")
        assertEquals(RespuestaHttp.Exito(200, "[]"), cliente.get("/api/v1/productos"))
        assertEquals(2, sesion.refrescosForzados)
    }

    @Test
    fun cambio_de_usuario_no_reusa_el_jwt_refrescado_del_anterior() = runTest {
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("uid-a", "a@ejemplo.com")),
            cuentasIniciales = mapOf("b@ejemplo.com" to "contrasena-b"),
        )
        val enviados = mutableListOf<String>()
        val cliente = ClienteHttpBackend(
            baseUrl = "https://api.ejemplo.mx",
            repositorioSesion = sesion,
            transporte = TransporteHttp { _, token ->
                enviados += token
                ResultadoTransporte.Respuesta(if (token.startsWith("token-falso-refrescado")) 200 else 401, "[]")
            },
            despachadorIo = StandardTestDispatcher(testScheduler),
        )
        cliente.get("/api/v1/productos")
        sesion.cerrarSesion()
        sesion.iniciarSesion("b@ejemplo.com", "contrasena-b")
        enviados.clear()

        assertEquals(RespuestaHttp.Exito(200, "[]"), cliente.get("/api/v1/productos"))
        assertFalse(enviados.any { "uid-a" in it })
        assertEquals("token-falso-refrescado-falso-b@ejemplo.com", enviados.last())
    }
}

/** Sesión con tokens guionados: [vigente] es el token en caché y cada refresco forzado toma el siguiente. */
private class SesionConTokens(
    var vigente: String,
    private val refrescos: ArrayDeque<String>,
) : RepositorioSesion by RepositorioSesionFalso() {
    var refrescosForzados = 0

    override suspend fun obtenerTokenId(forzarRefresco: Boolean): ResultadoTokenId {
        if (forzarRefresco) {
            refrescosForzados++
            vigente = refrescos.removeFirst()
        }
        return ResultadoTokenId.Exito(vigente)
    }
}
