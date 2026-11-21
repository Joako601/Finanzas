package io.github.joako601.finanzas.nube.sesion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorioSesionTest {

    @Test
    fun login_no_distingue_usuario_inexistente_de_contrasena_incorrecta() {
        val codigos = listOf(
            "ERROR_USER_NOT_FOUND",
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_EMAIL",
            "ERROR_USER_DISABLED",
        )
        codigos.forEach { codigo ->
            assertEquals(
                "El código $codigo debe mapearse a CredencialesInvalidas (OWASP)",
                ResultadoLogin.CredencialesInvalidas,
                mapearErrorLogin(codigoError = codigo, esRed = false, demasiadosIntentos = false),
            )
        }
    }

    @Test
    fun login_reporta_red_y_demasiados_intentos() {
        assertEquals(
            ResultadoLogin.SinConexion,
            mapearErrorLogin(codigoError = null, esRed = true, demasiadosIntentos = false),
        )
        assertEquals(
            ResultadoLogin.DemasiadosIntentos,
            mapearErrorLogin(codigoError = null, esRed = false, demasiadosIntentos = true),
        )
        assertEquals(
            ResultadoLogin.DemasiadosIntentos,
            mapearErrorLogin(codigoError = "ERROR_TOO_MANY_REQUESTS", esRed = false, demasiadosIntentos = false),
        )
    }

    @Test
    fun registro_distingue_correo_en_uso_contrasena_debil_y_red() {
        assertEquals(
            ResultadoRegistro.CorreoEnUso,
            mapearErrorRegistro(codigoError = "ERROR_EMAIL_ALREADY_IN_USE", esRed = false),
        )
        assertEquals(
            ResultadoRegistro.ContrasenaDebil,
            mapearErrorRegistro(codigoError = "ERROR_WEAK_PASSWORD", esRed = false),
        )
        assertEquals(
            ResultadoRegistro.CorreoInvalido,
            mapearErrorRegistro(codigoError = "ERROR_INVALID_EMAIL", esRed = false),
        )
        assertEquals(
            ResultadoRegistro.SinConexion,
            mapearErrorRegistro(codigoError = null, esRed = true),
        )
    }

    @Test
    fun recuperacion_oculta_si_el_correo_no_existe() {
        assertEquals(
            ResultadoRecuperacion.Enviado,
            mapearErrorRecuperacion(codigoError = "ERROR_USER_NOT_FOUND", esRed = false, demasiadosIntentos = false),
        )
        assertEquals(
            ResultadoRecuperacion.CorreoInvalido,
            mapearErrorRecuperacion(codigoError = "ERROR_INVALID_EMAIL", esRed = false, demasiadosIntentos = false),
        )
    }

    @Test
    fun repositorio_falso_cubre_registro_login_recuperacion_y_cierre() = runTest {
        val repo = RepositorioSesionFalso()
        assertEquals(EstadoSesion.SinSesion, repo.estado.value)

        assertEquals(ResultadoRegistro.ContrasenaDebil, repo.registrar("ana@ejemplo.com", "corta"))
        assertEquals(ResultadoRegistro.Exito, repo.registrar("ana@ejemplo.com", "Segura123!"))
        assertTrue(repo.estado.value is EstadoSesion.Autenticado)
        assertEquals(ResultadoRegistro.CorreoEnUso, repo.registrar("ana@ejemplo.com", "OtraSegura1!"))

        repo.cerrarSesion()
        assertEquals(EstadoSesion.SinSesion, repo.estado.value)
        assertEquals(1, repo.vecesCerrarSesion)

        // OWASP: correo inexistente y contraseña incorrecta devuelven el mismo error.
        assertEquals(ResultadoLogin.CredencialesInvalidas, repo.iniciarSesion("nadie@ejemplo.com", "Segura123!"))
        assertEquals(ResultadoLogin.CredencialesInvalidas, repo.iniciarSesion("ana@ejemplo.com", "incorrecta"))
        assertEquals(ResultadoLogin.Exito, repo.iniciarSesion("ana@ejemplo.com", "Segura123!"))
        assertEquals(
            EstadoSesion.Autenticado(UsuarioSesion(uid = "falso-ana@ejemplo.com", correo = "ana@ejemplo.com")),
            repo.estado.value,
        )

        // Recuperación siempre responde Enviado incluso para correos desconocidos.
        assertEquals(ResultadoRecuperacion.Enviado, repo.enviarRecuperacion("desconocido@ejemplo.com"))
        assertEquals(listOf("desconocido@ejemplo.com"), repo.correosRecuperacionEnviados)
    }
}
