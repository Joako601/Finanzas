package io.github.joako601.finanzas.nube.registro

import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.ResultadoLogin
import io.github.joako601.finanzas.nube.sesion.ResultadoRegistro
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
class RegistroViewModelTest {

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
    fun estado_inicial_esta_vacio_y_sin_errores() {
        val viewModel = RegistroViewModel(RepositorioSesionFalso())
        val estado = viewModel.estado.value

        assertEquals("", estado.correo)
        assertEquals("", estado.contrasena)
        assertEquals("", estado.confirmarContrasena)
        assertFalse(estado.mostrarContrasena)
        assertFalse(estado.mostrarConfirmarContrasena)
        assertFalse(estado.requisitosContrasena.todosCumplidos)
        assertNull(estado.errorCorreo)
        assertNull(estado.errorContrasena)
        assertNull(estado.errorConfirmacion)
        assertNull(estado.errorGlobal)
        assertFalse(estado.cargando)
        assertFalse(estado.registroExitoso)
    }

    @Test
    fun escribir_contrasena_actualiza_requisitos_en_tiempo_real() {
        val viewModel = RegistroViewModel(RepositorioSesionFalso())

        viewModel.alCambiarContrasena("abc")
        assertTrue(viewModel.estado.value.requisitosContrasena.tieneMinuscula)
        assertFalse(viewModel.estado.value.requisitosContrasena.longitudMinima)
        assertFalse(viewModel.estado.value.requisitosContrasena.tieneMayuscula)
        assertFalse(viewModel.estado.value.requisitosContrasena.tieneNumero)
        assertFalse(viewModel.estado.value.requisitosContrasena.tieneSimbolo)

        viewModel.alCambiarContrasena("Segura1!")
        assertTrue(viewModel.estado.value.requisitosContrasena.todosCumplidos)
    }

    @Test
    fun alternar_visibilidad_actualiza_cada_campo_de_contrasena() {
        val viewModel = RegistroViewModel(RepositorioSesionFalso())

        viewModel.alAlternarMostrarContrasena()
        assertTrue(viewModel.estado.value.mostrarContrasena)
        assertFalse(viewModel.estado.value.mostrarConfirmarContrasena)

        viewModel.alAlternarMostrarConfirmarContrasena()
        assertTrue(viewModel.estado.value.mostrarConfirmarContrasena)

        viewModel.alAlternarMostrarContrasena()
        assertFalse(viewModel.estado.value.mostrarContrasena)
    }

    @Test
    fun validacion_local_bloquea_envio_y_muestra_errores_especificos_por_campo() = runTest {
        val repo = RepositorioSesionFalso().apply {
            // Si llegara a llamarse el repositorio, fallaría con SinConexion; no debe llamarse.
            resultadoRegistroForzado = ResultadoRegistro.SinConexion
        }
        val viewModel = RegistroViewModel(repo)

        viewModel.alCambiarCorreo("ana@gmail.com")
        viewModel.alCambiarContrasena("corta")
        viewModel.alCambiarConfirmarContrasena("otra")
        viewModel.alEnviar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals(ErrorCorreoRegistro.DominioNoInstitucional, estado.errorCorreo)
        assertEquals(ErrorContrasenaRegistro.RequisitosIncompletos, estado.errorContrasena)
        assertEquals(ErrorConfirmacionRegistro.NoCoincide, estado.errorConfirmacion)
        assertNull(estado.errorGlobal)
        assertFalse(estado.cargando)
        assertFalse(estado.registroExitoso)

        // Editar cada campo limpia su error correspondiente.
        viewModel.alCambiarCorreo("ana@tecdesoftware.edu.mx")
        assertNull(viewModel.estado.value.errorCorreo)
        viewModel.alCambiarContrasena("Segura1!")
        assertNull(viewModel.estado.value.errorContrasena)
        viewModel.alCambiarConfirmarContrasena("Segura1!")
        assertNull(viewModel.estado.value.errorConfirmacion)
    }

    @Test
    fun correo_en_uso_muestra_error_especifico_en_campo_correo() = runTest {
        val repo = RepositorioSesionFalso(
            cuentasIniciales = mapOf("ana@tecdesoftware.edu.mx" to "Existente1!"),
        )
        val viewModel = RegistroViewModel(repo)

        llenarFormularioValido(viewModel, correo = "ana@tecdesoftware.edu.mx")
        viewModel.alEnviar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals(ErrorCorreoRegistro.YaRegistrado, estado.errorCorreo)
        assertNull(estado.errorContrasena)
        assertNull(estado.errorGlobal)
        assertFalse(estado.cargando)
        assertFalse(estado.registroExitoso)
    }

    @Test
    fun correo_invalido_del_servidor_muestra_error_en_campo_correo() = runTest {
        val repo = RepositorioSesionFalso().apply {
            resultadoRegistroForzado = ResultadoRegistro.CorreoInvalido
        }
        val viewModel = RegistroViewModel(repo)

        llenarFormularioValido(viewModel)
        viewModel.alEnviar()
        advanceUntilIdle()

        assertEquals(ErrorCorreoRegistro.FormatoInvalido, viewModel.estado.value.errorCorreo)
        assertFalse(viewModel.estado.value.registroExitoso)
    }

    @Test
    fun contrasena_debil_del_servidor_muestra_error_en_campo_contrasena() = runTest {
        val repo = RepositorioSesionFalso().apply {
            resultadoRegistroForzado = ResultadoRegistro.ContrasenaDebil
        }
        val viewModel = RegistroViewModel(repo)

        llenarFormularioValido(viewModel)
        viewModel.alEnviar()
        advanceUntilIdle()

        assertEquals(ErrorContrasenaRegistro.DebilEnServidor, viewModel.estado.value.errorContrasena)
        assertNull(viewModel.estado.value.errorGlobal)
        assertFalse(viewModel.estado.value.registroExitoso)
    }

    @Test
    fun sin_conexion_y_error_inesperado_muestran_aviso_global() = runTest {
        val repo = RepositorioSesionFalso().apply {
            resultadoRegistroForzado = ResultadoRegistro.SinConexion
        }
        val viewModel = RegistroViewModel(repo)

        llenarFormularioValido(viewModel)
        viewModel.alEnviar()
        advanceUntilIdle()

        assertEquals(ErrorGlobalRegistro.SinConexion, viewModel.estado.value.errorGlobal)
        assertFalse(viewModel.estado.value.registroExitoso)

        repo.resultadoRegistroForzado = ResultadoRegistro.ErrorInesperado
        viewModel.alEnviar()
        advanceUntilIdle()

        assertEquals(ErrorGlobalRegistro.ErrorInesperado, viewModel.estado.value.errorGlobal)
        assertFalse(viewModel.estado.value.registroExitoso)
    }

    @Test
    fun registro_exitoso_crea_cuenta_avisa_y_permite_volver_al_login() = runTest {
        val repo = RepositorioSesionFalso()
        val viewModel = RegistroViewModel(repo)

        llenarFormularioValido(viewModel, correo = "  Ana.Lopez@TecDeSoftware.EDU.MX  ")
        viewModel.alEnviar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue(estado.registroExitoso)
        assertFalse(estado.cargando)
        assertEquals("", estado.contrasena)
        assertEquals("", estado.confirmarContrasena)
        assertFalse(estado.mostrarContrasena)
        assertFalse(estado.mostrarConfirmarContrasena)
        assertNull(estado.errorCorreo)
        assertNull(estado.errorContrasena)
        assertNull(estado.errorConfirmacion)
        assertNull(estado.errorGlobal)
        // Registrar no abre sesión automáticamente; la cuenta queda lista para iniciar sesión.
        assertEquals(EstadoSesion.SinSesion, repo.estado.value)
        assertEquals(
            ResultadoLogin.Exito,
            repo.iniciarSesion("ana.lopez@tecdesoftware.edu.mx", "Segura123!"),
        )

        viewModel.alConfirmarExito()
        assertEquals(EstadoRegistroUi(), viewModel.estado.value)
    }

    @Test
    fun reiniciar_limpia_campos_y_errores_del_formulario() {
        val viewModel = RegistroViewModel(RepositorioSesionFalso())

        viewModel.alCambiarCorreo("ana@gmail.com")
        viewModel.alCambiarContrasena("corta")
        viewModel.alCambiarConfirmarContrasena("otra")
        viewModel.alAlternarMostrarContrasena()
        viewModel.alEnviar()

        assertEquals(ErrorCorreoRegistro.DominioNoInstitucional, viewModel.estado.value.errorCorreo)

        viewModel.reiniciar()
        assertEquals(EstadoRegistroUi(), viewModel.estado.value)
    }

    @Test
    fun estadoRegistro_toString_no_expone_correo_ni_contrasenas() {
        val estado = EstadoRegistroUi(
            correo = "secreto@tecdesoftware.edu.mx",
            contrasena = "MiPassword1!",
            confirmarContrasena = "MiPassword1!",
        )
        val texto = estado.toString()
        assertFalse(texto.contains("secreto@tecdesoftware.edu.mx"))
        assertFalse(texto.contains("MiPassword1!"))
    }

    private fun llenarFormularioValido(
        viewModel: RegistroViewModel,
        correo: String = "nuevo@tecdesoftware.edu.mx",
        contrasena: String = "Segura123!",
    ) {
        viewModel.alCambiarCorreo(correo)
        viewModel.alCambiarContrasena(contrasena)
        viewModel.alCambiarConfirmarContrasena(contrasena)
    }
}
