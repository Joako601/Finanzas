package io.github.joako601.finanzas.nube.login

import io.github.joako601.finanzas.nube.sesion.EstadoSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesion
import io.github.joako601.finanzas.nube.sesion.RepositorioSesionFalso
import io.github.joako601.finanzas.nube.sesion.ResultadoLogin
import io.github.joako601.finanzas.nube.sesion.ResultadoRecuperacion
import io.github.joako601.finanzas.nube.sesion.ResultadoRegistro
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun configurar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun campos_vacios_no_llaman_al_repositorio_y_muestran_aviso_global() = runTest {
        val repo = RepositorioEspia()
        val vm = LoginViewModel(repo)

        // Ambos vacíos
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals(0, repo.llamadasIniciarSesion)
        assertEquals(AvisoLogin.CamposVacios, vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, vm.estado.value.estadoMascota)

        // Solo correo lleno
        vm.onCorreoCambio("ana@ejemplo.com")
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals(0, repo.llamadasIniciarSesion)
        assertEquals(AvisoLogin.CamposVacios, vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, vm.estado.value.estadoMascota)

        // Solo contraseña llena (correo con espacios)
        vm.onCorreoCambio("   ")
        vm.onContrasenaCambio("Secreta123!")
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals(0, repo.llamadasIniciarSesion)
        assertEquals(AvisoLogin.CamposVacios, vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, vm.estado.value.estadoMascota)
    }

    @Test
    fun credenciales_invalidas_producen_mensaje_generico_owasp_y_estado_error() = runTest {
        val repo = RepositorioSesionFalso(
            cuentasIniciales = mapOf("ana@ejemplo.com" to "Segura123!"),
        )
        val vm = LoginViewModel(repo)

        // Correo inexistente
        vm.onCorreoCambio("nadie@ejemplo.com")
        vm.onContrasenaCambio("Segura123!")
        vm.iniciarSesion()
        advanceUntilIdle()

        val estadoInexistente = vm.estado.value
        assertEquals(AvisoLogin.CredencialesInvalidas, estadoInexistente.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, estadoInexistente.estadoMascota)
        assertFalse(estadoInexistente.cargando)
        assertFalse(estadoInexistente.loginCompletado)
        assertEquals(1, estadoInexistente.intentoError)

        // Contraseña incorrecta para correo existente -> mismo aviso OWASP
        vm.onCorreoCambio("ana@ejemplo.com")
        vm.onContrasenaCambio("otra_clave")
        vm.iniciarSesion()
        advanceUntilIdle()

        val estadoContrasenaMala = vm.estado.value
        assertEquals(AvisoLogin.CredencialesInvalidas, estadoContrasenaMala.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, estadoContrasenaMala.estadoMascota)
        assertEquals(2, estadoContrasenaMala.intentoError)
    }

    @Test
    fun login_exitoso_muestra_estado_exito_espera_y_dispara_navegacion() = runTest {
        val repo = RepositorioSesionFalso(
            cuentasIniciales = mapOf("ana@ejemplo.com" to "Segura123!"),
        )
        val vm = LoginViewModel(repo, demoraExitoMs = 750L)

        vm.onCorreoCambio("  ana@ejemplo.com  ")
        vm.onContrasenaCambio("Segura123!")
        vm.iniciarSesion()

        // Ejecuta la llamada al repositorio y entra en la espera de celebración
        runCurrent()
        assertEquals(EstadoMascotaLogin.Exito, vm.estado.value.estadoMascota)
        assertNull(vm.estado.value.avisoGlobal)
        assertFalse(vm.estado.value.cargando)
        assertFalse(vm.estado.value.loginCompletado)

        // Antes de completar los 750 ms todavía no navega
        advanceTimeBy(700L)
        runCurrent()
        assertFalse(vm.estado.value.loginCompletado)

        // Al cumplir los 750 ms dispara la navegación y limpia la contraseña de memoria
        advanceTimeBy(50L)
        runCurrent()
        assertTrue(vm.estado.value.loginCompletado)
        assertEquals("", vm.estado.value.contrasena)
    }

    @Test
    fun sin_conexion_demasiados_intentos_y_error_inesperado_producen_avisos_globales() = runTest {
        val repo = RepositorioSesionFalso()
        val vm = LoginViewModel(repo)
        vm.onCorreoCambio("ana@ejemplo.com")
        vm.onContrasenaCambio("Segura123!")

        repo.resultadoLoginForzado = ResultadoLogin.SinConexion
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals(AvisoLogin.SinConexion, vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, vm.estado.value.estadoMascota)

        repo.resultadoLoginForzado = ResultadoLogin.DemasiadosIntentos
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals(AvisoLogin.DemasiadosIntentos, vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, vm.estado.value.estadoMascota)

        repo.resultadoLoginForzado = ResultadoLogin.ErrorInesperado
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals(AvisoLogin.ErrorInesperado, vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.Error, vm.estado.value.estadoMascota)
    }

    @Test
    fun evita_envios_duplicados_mientras_carga() = runTest {
        val diferido = CompletableDeferred<ResultadoLogin>()
        val repo = RepositorioEspia(respuestaDiferida = diferido)
        val vm = LoginViewModel(repo)

        vm.onCorreoCambio("ana@ejemplo.com")
        vm.onContrasenaCambio("Segura123!")

        vm.iniciarSesion()
        runCurrent()
        assertTrue(vm.estado.value.cargando)
        assertEquals(EstadoMascotaLogin.Cargando, vm.estado.value.estadoMascota)

        // Segundo intento mientras el primero sigue en curso debe ignorarse
        vm.iniciarSesion()
        runCurrent()
        assertEquals(1, repo.llamadasIniciarSesion)

        diferido.complete(ResultadoLogin.CredencialesInvalidas)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargando)
        assertEquals(AvisoLogin.CredencialesInvalidas, vm.estado.value.avisoGlobal)
    }

    @Test
    fun foco_y_edicion_actualizan_mascota_y_limpian_aviso_previo() = runTest {
        val repo = RepositorioSesionFalso()
        val vm = LoginViewModel(repo)

        assertEquals(EstadoMascotaLogin.Reposo, vm.estado.value.estadoMascota)

        vm.onFocoCorreo()
        assertEquals(EstadoMascotaLogin.EscribiendoCorreo, vm.estado.value.estadoMascota)

        vm.onFocoContrasena()
        assertEquals(EstadoMascotaLogin.EscribiendoContrasena, vm.estado.value.estadoMascota)

        vm.onAlternarVisibilidadContrasena()
        assertTrue(vm.estado.value.contrasenaVisible)

        vm.onSinFoco()
        assertEquals(EstadoMascotaLogin.Reposo, vm.estado.value.estadoMascota)

        // Un error previo se limpia al editar cualquier campo
        vm.iniciarSesion()
        assertEquals(AvisoLogin.CamposVacios, vm.estado.value.avisoGlobal)
        vm.onCorreoCambio("a")
        assertNull(vm.estado.value.avisoGlobal)
        assertEquals(EstadoMascotaLogin.EscribiendoCorreo, vm.estado.value.estadoMascota)
    }

    @Test
    fun toString_del_estado_no_expone_la_contrasena() {
        val estado = EstadoUiLogin(
            correo = "ana@ejemplo.com",
            contrasena = "MiContrasenaUltraSecreta99!",
        )
        val texto = estado.toString()
        assertFalse(
            "La contraseña no debe aparecer en toString() para evitar filtraciones en logs",
            texto.contains("MiContrasenaUltraSecreta99!"),
        )
    }

    @Test
    fun reiniciar_limpia_loginCompletado_para_evitar_bucle_al_cerrar_sesion() = runTest {
        val repo = RepositorioSesionFalso(
            cuentasIniciales = mapOf("ana@tecdesoftware.edu.mx" to "ClaveSegura1!"),
        )
        val viewModel = LoginViewModel(repo, demoraExitoMs = 50L)

        viewModel.onCorreoCambio("ana@tecdesoftware.edu.mx")
        viewModel.onContrasenaCambio("ClaveSegura1!")
        viewModel.iniciarSesion()
        advanceUntilIdle()
        assertTrue(viewModel.estado.value.loginCompletado)

        viewModel.reiniciar()
        assertEquals(EstadoUiLogin(), viewModel.estado.value)
    }

    @Test
    fun reiniciar_cancela_demora_de_exito_y_no_marca_loginCompletado() = runTest {
        val repo = RepositorioSesionFalso(
            cuentasIniciales = mapOf("ana@tecdesoftware.edu.mx" to "ClaveSegura1!"),
        )
        val viewModel = LoginViewModel(repo, demoraExitoMs = 750L)

        viewModel.onCorreoCambio("ana@tecdesoftware.edu.mx")
        viewModel.onContrasenaCambio("ClaveSegura1!")
        viewModel.iniciarSesion()

        // Entra en fase de celebración (delay de 750 ms en curso)
        runCurrent()
        assertEquals(EstadoMascotaLogin.Exito, viewModel.estado.value.estadoMascota)
        assertFalse(viewModel.estado.value.loginCompletado)

        // Salir antes de que termine el delay debe cancelar el Job y limpiar el estado
        viewModel.reiniciar()
        advanceUntilIdle()

        assertEquals(EstadoUiLogin(), viewModel.estado.value)
        assertFalse(viewModel.estado.value.loginCompletado)
    }

    private class RepositorioEspia(
        private val respuestaDiferida: CompletableDeferred<ResultadoLogin>? = null,
    ) : RepositorioSesion {
        override val estado: StateFlow<EstadoSesion> = MutableStateFlow(EstadoSesion.SinSesion)
        var llamadasIniciarSesion: Int = 0
            private set

        override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin {
            llamadasIniciarSesion += 1
            return respuestaDiferida?.await() ?: ResultadoLogin.CredencialesInvalidas
        }

        override suspend fun registrar(correo: String, contrasena: String): ResultadoRegistro =
            ResultadoRegistro.ErrorInesperado

        override suspend fun enviarRecuperacion(correo: String): ResultadoRecuperacion =
            ResultadoRecuperacion.ErrorInesperado

        override suspend fun cerrarSesion() = Unit
    }
}
