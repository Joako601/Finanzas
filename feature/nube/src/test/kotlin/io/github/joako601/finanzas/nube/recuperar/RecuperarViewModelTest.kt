package io.github.joako601.finanzas.nube.recuperar

import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.ResultadoRecuperacion
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecuperarViewModelTest {

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
    fun correo_vacio_no_llama_al_repositorio_y_muestra_error_de_formato() = runTest(despachador) {
        val repo = RepositorioSesionFalso()
        val vm = RecuperarViewModel(repo)

        vm.enviar()
        advanceUntilIdle()

        assertTrue(repo.correosRecuperacionEnviados.isEmpty())
        assertEquals(ErrorRecuperar.CORREO_INVALIDO, vm.estado.value.error)
        assertFalse(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun correo_mal_formado_no_llama_al_repositorio_y_muestra_error_de_formato() = runTest(despachador) {
        val invalidos = listOf(
            "   ",
            "sin-arroba.com",
            "usuario@",
            "@dominio.com",
            "usuario@sindominio",
            "usuario@dominio.",
            "usuario@.com",
            "usuario@dom..com",
            "con espacio@ejemplo.com",
        )

        invalidos.forEach { entrada ->
            val repo = RepositorioSesionFalso()
            val vm = RecuperarViewModel(repo)

            vm.alCambiarCorreo(entrada)
            vm.enviar()
            advanceUntilIdle()

            assertTrue(
                "No debe llamar al repositorio para '$entrada'",
                repo.correosRecuperacionEnviados.isEmpty(),
            )
            assertEquals(
                "Debe marcar error de formato para '$entrada'",
                ErrorRecuperar.CORREO_INVALIDO,
                vm.estado.value.error,
            )
            assertFalse(vm.estado.value.enviado)
        }
    }

    @Test
    fun editar_correo_limpia_error_previo() = runTest(despachador) {
        val repo = RepositorioSesionFalso()
        val vm = RecuperarViewModel(repo)

        vm.enviar()
        assertEquals(ErrorRecuperar.CORREO_INVALIDO, vm.estado.value.error)

        vm.alCambiarCorreo("ana@ejemplo.com")
        assertNull(vm.estado.value.error)
    }

    @Test
    fun correo_valido_llama_enviarRecuperacion_y_pasa_a_estado_enviado() = runTest(despachador) {
        val repo = RepositorioSesionFalso()
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("  ana@ejemplo.com  ")
        vm.enviar()
        advanceUntilIdle()

        assertEquals(listOf("ana@ejemplo.com"), repo.correosRecuperacionEnviados)
        assertTrue(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun mientras_envia_ignora_envios_duplicados() = runTest(despachador) {
        val repo = RepositorioSesionFalso()
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("ana@ejemplo.com")
        vm.enviar()
        assertTrue(vm.estado.value.enviando)

        // Segundo intento mientras el primero sigue en curso.
        vm.enviar()
        advanceUntilIdle()

        assertEquals(listOf("ana@ejemplo.com"), repo.correosRecuperacionEnviados)
        assertTrue(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun sin_conexion_produce_error_sin_romper_el_dialogo() = runTest(despachador) {
        val repo = RepositorioSesionFalso().apply {
            resultadoRecuperacionForzado = ResultadoRecuperacion.SinConexion
        }
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("ana@ejemplo.com")
        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorRecuperar.SIN_CONEXION, vm.estado.value.error)
        assertFalse(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun demasiados_intentos_produce_error_correspondiente() = runTest(despachador) {
        val repo = RepositorioSesionFalso().apply {
            resultadoRecuperacionForzado = ResultadoRecuperacion.DemasiadosIntentos
        }
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("ana@ejemplo.com")
        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorRecuperar.DEMASIADOS_INTENTOS, vm.estado.value.error)
        assertFalse(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun correo_invalido_del_servidor_produce_error_correspondiente() = runTest(despachador) {
        val repo = RepositorioSesionFalso().apply {
            resultadoRecuperacionForzado = ResultadoRecuperacion.CorreoInvalido
        }
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("ana@ejemplo.com")
        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorRecuperar.CORREO_INVALIDO, vm.estado.value.error)
        assertFalse(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun error_inesperado_produce_error_generico_sin_romper_el_dialogo() = runTest(despachador) {
        val repo = RepositorioSesionFalso().apply {
            resultadoRecuperacionForzado = ResultadoRecuperacion.ErrorInesperado
        }
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("ana@ejemplo.com")
        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorRecuperar.ERROR_INESPERADO, vm.estado.value.error)
        assertFalse(vm.estado.value.enviado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun reiniciar_restablece_el_estado_inicial() = runTest(despachador) {
        val repo = RepositorioSesionFalso()
        val vm = RecuperarViewModel(repo)

        vm.alCambiarCorreo("ana@ejemplo.com")
        vm.enviar()
        advanceUntilIdle()
        assertTrue(vm.estado.value.enviado)

        vm.reiniciar()

        assertEquals(EstadoRecuperar(), vm.estado.value)
    }
}
