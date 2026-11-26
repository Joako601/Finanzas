package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.catalogo.RepositorioCatalogoRemoto
import io.github.joako601.finanzas.nube.divisas.RepositorioDivisasRemoto
import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.red.ResultadoTransporte
import io.github.joako601.finanzas.nube.red.TransporteHttp
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FuenteMercadoRemotaTest {

    private fun TestScope.fuente(responder: (String) -> ResultadoTransporte): FuenteMercadoRemota {
        val sesion = RepositorioSesionFalso(EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")))
        val cliente = ClienteHttpBackend(
            baseUrl = "http://localhost:8090",
            repositorioSesion = sesion,
            permitirHttpLocal = true,
            transporte = TransporteHttp { url, _ -> responder(url) },
            despachadorIo = StandardTestDispatcher(testScheduler),
        )
        return FuenteMercadoRemota(cliente, RepositorioDivisasRemoto(cliente), RepositorioCatalogoRemoto(cliente)) { LocalDate.of(2026, 12, 1) }
    }

    private fun ok(cuerpo: String) = ResultadoTransporte.Respuesta(200, cuerpo)

    private fun respuestas(url: String): ResultadoTransporte = when {
        "/divisas/actual" in url -> {
            val base = url.substringAfter("base=").take(3)
            ok("""{"monedaBase":"$base","monedaDestino":"MXN","fecha":"2026-11-30","tasa":18.1,"fuente":"Banxico","desdeCache":false}""")
        }
        "/indicadores/actual" in url ->
            ok("""[{"clave":"CETES_28","nombre":"CETES 28 días","valor":7.25,"unidad":"%","fecha":"2026-11-26","fuente":"Banxico","desdeCache":false}]""")
        "anio=2026" in url -> ok("""[{"fecha":"2026-12-25","nombre":"Navidad","tipo":"OFICIAL","anio":2026}]""")
        "anio=2027" in url -> ok("""[{"fecha":"2027-01-01","nombre":"Año nuevo","tipo":"OFICIAL","anio":2027}]""")
        "/productos" in url -> ok("[]")
        else -> ResultadoTransporte.Respuesta(404, "")
    }

    @Test
    fun arma_todas_las_partes_con_dos_anios_de_inhabiles() = runTest {
        val r = fuente(::respuestas).obtener() as ResultadoFuente.Exito
        assertEquals(listOf("USD", "EUR", "CAD", "GBP", "JPY"), r.parte.cotizaciones?.map { it.monedaBase })
        assertEquals(BigDecimal("7.25"), r.parte.indicadores?.single()?.valor)
        assertEquals(listOf("2026-12-25", "2027-01-01"), r.parte.diasInhabiles)
        assertEquals(emptyList<Any>(), r.parte.productos)
    }

    @Test
    fun falta_un_anio_de_inhabiles_y_no_se_manda_ninguno() = runTest {
        val r = fuente { if ("anio=2027" in it) ResultadoTransporte.Respuesta(500, "") else respuestas(it) }
            .obtener() as ResultadoFuente.Exito
        assertNull(r.parte.diasInhabiles)
    }

    @Test
    fun cualquier_401_es_sesion_expirada() = runTest {
        val r = fuente { if ("/indicadores" in it) ResultadoTransporte.Respuesta(401, "") else respuestas(it) }.obtener()
        assertEquals(ResultadoFuente.SesionExpirada, r)
    }

    @Test
    fun todo_sin_red_es_sin_conexion() = runTest {
        assertEquals(ResultadoFuente.SinConexion, fuente { ResultadoTransporte.ErrorRed }.obtener())
    }
}
