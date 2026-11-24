package io.github.joako601.finanzas.nube.divisas

import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.red.ResultadoTransporte
import io.github.joako601.finanzas.nube.red.TransporteHttp
import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.UsuarioSesion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class DivisasTest {

    private val despachadorMain = StandardTestDispatcher()

    @Before
    fun configurar() {
        Dispatchers.setMain(despachadorMain)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun repositorio_remoto_obtiene_cotizaciones_y_convierte_divisa() = runTest(despachadorMain) {
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )
        val transporte = TransporteHttp { url, _ ->
            when {
                url.contains("base=USD") -> ResultadoTransporte.Respuesta(
                    200,
                    """{"monedaBase":"USD","monedaDestino":"MXN","fecha":"2026-11-23","tasa":19.8520,"fuente":"Frankfurter (BCE)","desdeCache":false}""",
                )
                url.contains("base=EUR") -> ResultadoTransporte.Respuesta(
                    200,
                    """{"monedaBase":"EUR","monedaDestino":"MXN","fecha":"2026-11-23","tasa":21.4100,"fuente":"Frankfurter (BCE)","desdeCache":true}""",
                )
                url.contains("base=CAD") -> ResultadoTransporte.Respuesta(
                    200,
                    """{"monedaBase":"CAD","monedaDestino":"MXN","fecha":"2026-11-23","tasa":14.3200,"fuente":"Frankfurter (BCE)","desdeCache":false}""",
                )
                url.contains("/convertir?monto=100.00&de=USD&a=MXN") -> ResultadoTransporte.Respuesta(
                    200,
                    """{"montoOrigen":100.00,"monedaOrigen":"USD","montoDestino":1985.20,"monedaDestino":"MXN","tasa":19.8520,"fechaTasa":"2026-11-23","fuente":"Frankfurter (BCE)","desdeCache":false}""",
                )
                else -> ResultadoTransporte.Respuesta(404, "")
            }
        }
        val repo = RepositorioDivisasRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = transporte,
                despachadorIo = despachadorMain,
            ),
        )

        val actuales = repo.obtenerCotizacionesPrincipales()
        assertTrue(actuales is ResultadoDivisasActuales.Exito)
        val lista = (actuales as ResultadoDivisasActuales.Exito).cotizaciones
        assertEquals(3, lista.size)
        assertEquals("USD", lista[0].monedaBase)
        assertEquals(BigDecimal("19.8520"), lista[0].tasa)

        val conversion = repo.convertir(BigDecimal("100"), "USD", "MXN")
        assertTrue(conversion is ResultadoConversion.Exito)
        val c = (conversion as ResultadoConversion.Exito).conversion
        assertEquals(BigDecimal("1985.20"), c.montoDestino)

        // Rechaza >2 decimales y misma moneda antes de llamar al servidor
        assertEquals(ResultadoConversion.MontoInvalido, repo.convertir(BigDecimal("10.125"), "USD", "MXN"))
        assertEquals(ResultadoConversion.MontoInvalido, repo.convertir(BigDecimal("10"), "USD", "USD"))
    }

    @Test
    fun repositorio_remoto_maneja_fallo_parcial_401_y_sin_conexion() = runTest(despachadorMain) {
        val sesion = RepositorioSesionFalso(
            estadoInicial = EstadoSesion.Autenticado(UsuarioSesion("u1", "ana@ejemplo.com")),
        )

        val repoParcial = RepositorioDivisasRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { url, _ ->
                    if (url.contains("base=USD")) {
                        ResultadoTransporte.Respuesta(
                            200,
                            """{"monedaBase":"USD","monedaDestino":"MXN","fecha":"2026-11-23","tasa":19.85,"fuente":"Banxico","desdeCache":true}""",
                        )
                    } else {
                        ResultadoTransporte.Respuesta(503, "")
                    }
                },
                despachadorIo = despachadorMain,
            ),
        )
        val resParcial = repoParcial.obtenerCotizacionesPrincipales()
        assertTrue(resParcial is ResultadoDivisasActuales.Exito)
        assertEquals(1, (resParcial as ResultadoDivisasActuales.Exito).cotizaciones.size)

        val repo401 = RepositorioDivisasRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { _, _ -> ResultadoTransporte.Respuesta(401, "") },
                despachadorIo = despachadorMain,
            ),
        )
        assertEquals(ResultadoDivisasActuales.SesionExpirada, repo401.obtenerCotizacionesPrincipales())

        val repoSinRed = RepositorioDivisasRemoto(
            ClienteHttpBackend(
                baseUrl = "http://localhost:8090",
                repositorioSesion = sesion,
                permitirHttpLocal = true,
                transporte = TransporteHttp { _, _ -> ResultadoTransporte.ErrorRed },
                despachadorIo = despachadorMain,
            ),
        )
        assertEquals(ResultadoDivisasActuales.SinConexion, repoSinRed.obtenerCotizacionesPrincipales())
    }

    @Test
    fun viewmodel_valida_montos_formatea_euro_y_reintenta_errores() = runTest(despachadorMain) {
        var devolverError = true
        val repo = object : RepositorioDivisas {
            private val falso = RepositorioDivisasFalso()
            override suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales =
                if (devolverError) ResultadoDivisasActuales.SinConexion else falso.obtenerCotizacionesPrincipales()

            override suspend fun convertir(monto: BigDecimal, de: String, a: String): ResultadoConversion =
                falso.convertir(monto, de, a)
        }
        val vm = DivisasViewModel(repositorioDivisas = repo)
        advanceUntilIdle()
        assertEquals(EstadoListaDivisas.Error(ErrorDivisas.SIN_CONEXION), vm.estado.value.lista)

        devolverError = false
        vm.reintentar()
        advanceUntilIdle()
        assertTrue(vm.estado.value.lista is EstadoListaDivisas.Contenido)

        // Rechaza >2 decimales y punto solo
        vm.cambiarMonto("12.34")
        vm.cambiarMonto("12.345")
        assertEquals("12.34", vm.estado.value.montoTexto)

        vm.cambiarMonto(".")
        vm.convertir()
        assertEquals(EstadoConversion.Error(ErrorConversion.MONTO_INVALIDO), vm.estado.value.conversion)

        // Acepta pegado con coma de miles
        vm.cambiarMonto("1,250.50")
        assertEquals("1250.50", vm.estado.value.montoTexto)

        // Conversión válida y formato de EUR y tasa
        vm.cambiarMonto("50")
        vm.convertir()
        advanceUntilIdle()
        val res = vm.estado.value.conversion as EstadoConversion.Resultado
        assertEquals(BigDecimal("992.50"), res.conversion.montoDestino)
        assertEquals("$992.50 MXN", formatearMontoDivisa(res.conversion.montoDestino, "MXN"))
        assertEquals("€100.00 EUR", formatearMontoDivisa(BigDecimal("100"), "EUR"))
        assertEquals("19.8520", formatearTasaDivisa(BigDecimal("19.852")))
    }

    @Test
    fun cambiar_par_o_monto_cancela_conversion_en_vuelo() = runTest(despachadorMain) {
        val diferida = CompletableDeferred<ResultadoConversion>()
        val repo = object : RepositorioDivisas {
            override suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales =
                RepositorioDivisasFalso().obtenerCotizacionesPrincipales()

            override suspend fun convertir(monto: BigDecimal, de: String, a: String): ResultadoConversion =
                diferida.await()
        }
        val vm = DivisasViewModel(repositorioDivisas = repo)
        advanceUntilIdle()

        vm.convertir()
        runCurrent()
        assertEquals(EstadoConversion.Convirtiendo, vm.estado.value.conversion)

        // Al cambiar de par mientras convierte, debe cancelar y quedarse Inactivo
        vm.seleccionarPar(ParDivisas("EUR", "MXN"))
        diferida.complete(
            ResultadoConversion.Exito(
                ConversionDivisa(
                    montoOrigen = BigDecimal("100.00"),
                    monedaOrigen = "USD",
                    montoDestino = BigDecimal("1985.00"),
                    monedaDestino = "MXN",
                    tasa = BigDecimal("19.85"),
                    fechaTasa = "2026-11-23",
                    fuente = "Test",
                    desdeCache = false,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(ParDivisas("EUR", "MXN"), vm.estado.value.parSeleccionado)
        assertEquals(EstadoConversion.Inactivo, vm.estado.value.conversion)
    }

    @Test
    fun refrescar_silencioso_en_divisas_detecta_caida_del_servidor() = runTest(despachadorMain) {
        var enLinea = true
        val repo = object : RepositorioDivisas {
            private val falso = RepositorioDivisasFalso()
            override suspend fun obtenerCotizacionesPrincipales(): ResultadoDivisasActuales =
                if (enLinea) falso.obtenerCotizacionesPrincipales() else ResultadoDivisasActuales.SinConexion

            override suspend fun convertir(monto: BigDecimal, de: String, a: String): ResultadoConversion =
                falso.convertir(monto, de, a)
        }
        val vm = DivisasViewModel(repositorioDivisas = repo)
        advanceUntilIdle()
        assertTrue(vm.estado.value.lista is EstadoListaDivisas.Contenido)

        enLinea = false
        vm.refrescar()
        assertTrue(vm.estado.value.refrescando)
        assertTrue(vm.estado.value.lista is EstadoListaDivisas.Contenido)

        advanceUntilIdle()
        assertEquals(false, vm.estado.value.refrescando)
        assertEquals(EstadoListaDivisas.Error(ErrorDivisas.SIN_CONEXION), vm.estado.value.lista)
    }
}
