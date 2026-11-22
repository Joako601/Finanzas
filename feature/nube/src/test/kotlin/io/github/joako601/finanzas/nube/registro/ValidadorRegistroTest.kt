package io.github.joako601.finanzas.nube.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorRegistroTest {

    // --- Correo institucional ---

    @Test
    fun correo_vacio_o_solo_espacios_es_rechazado_como_vacio() {
        assertEquals(ErrorCorreoRegistro.Vacio, ValidadorRegistro.validarCorreo(""))
        assertEquals(ErrorCorreoRegistro.Vacio, ValidadorRegistro.validarCorreo("   \t\n  "))
    }

    @Test
    fun correo_sin_formato_valido_es_rechazado_como_formato_invalido() {
        val invalidos = listOf(
            "sin-arroba",
            "@tecdesoftware.edu.mx",
            "usuario@",
            "usu ario@tecdesoftware.edu.mx",
            "a@b@tecdesoftware.edu.mx",
            ".usuario@tecdesoftware.edu.mx",
            "usuario.@tecdesoftware.edu.mx",
            "usu..ario@tecdesoftware.edu.mx",
            "usuario@dominio",
            "usuario@.com",
        )
        invalidos.forEach { correo ->
            assertEquals(
                "Esperaba FormatoInvalido para '$correo'",
                ErrorCorreoRegistro.FormatoInvalido,
                ValidadorRegistro.validarCorreo(correo),
            )
        }
    }

    @Test
    fun correo_con_otro_dominio_o_subdominio_es_rechazado_como_no_institucional() {
        val externos = listOf(
            "ana@gmail.com",
            "ana@outlook.com",
            "ana@falso-tecdesoftware.edu.mx",
            "ana@sub.tecdesoftware.edu.mx",
            "ana@tecdesoftware.edu.mx.evil.com",
            "ana@tecdesoftware.com",
        )
        externos.forEach { correo ->
            assertEquals(
                "Esperaba DominioNoInstitucional para '$correo'",
                ErrorCorreoRegistro.DominioNoInstitucional,
                ValidadorRegistro.validarCorreo(correo),
            )
        }
    }

    @Test
    fun correo_institucional_valido_acepta_mayusculas_y_espacios_extremos() {
        assertNull(ValidadorRegistro.validarCorreo("ana.lopez@tecdesoftware.edu.mx"))
        assertNull(ValidadorRegistro.validarCorreo("  ANA.Lopez+1@TecDeSoftware.EDU.MX  "))
        assertNull(ValidadorRegistro.validarCorreo("alumno_2026@TECDESOFTWARE.EDU.MX"))
    }

    @Test
    fun normalizar_correo_recorta_espacios_y_convierte_a_minusculas() {
        assertEquals(
            "ana.lopez@tecdesoftware.edu.mx",
            ValidadorRegistro.normalizarCorreo("  Ana.Lopez@TecDeSoftware.EDU.MX \n"),
        )
    }

    // --- Reglas de contraseña (5 reglas en tiempo real) ---

    @Test
    fun contrasena_vacia_no_cumple_ningun_requisito() {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena("")
        assertFalse(requisitos.longitudMinima)
        assertFalse(requisitos.tieneMayuscula)
        assertFalse(requisitos.tieneMinuscula)
        assertFalse(requisitos.tieneNumero)
        assertFalse(requisitos.tieneSimbolo)
        assertFalse(requisitos.todosCumplidos)
        assertEquals(ErrorContrasenaRegistro.Vacia, ValidadorRegistro.validarContrasena(""))
    }

    @Test
    fun contrasena_de_siete_caracteres_falla_longitud_minima() {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena("Ab1!xyz")
        assertFalse(requisitos.longitudMinima)
        assertTrue(requisitos.tieneMayuscula)
        assertTrue(requisitos.tieneMinuscula)
        assertTrue(requisitos.tieneNumero)
        assertTrue(requisitos.tieneSimbolo)
        assertFalse(requisitos.todosCumplidos)
        assertEquals(
            ErrorContrasenaRegistro.RequisitosIncompletos,
            ValidadorRegistro.validarContrasena("Ab1!xyz"),
        )
    }

    @Test
    fun contrasena_sin_mayuscula_falla_regla_de_mayuscula() {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena("segura1!")
        assertTrue(requisitos.longitudMinima)
        assertFalse(requisitos.tieneMayuscula)
        assertTrue(requisitos.tieneMinuscula)
        assertTrue(requisitos.tieneNumero)
        assertTrue(requisitos.tieneSimbolo)
        assertFalse(requisitos.todosCumplidos)
    }

    @Test
    fun contrasena_sin_minuscula_falla_regla_de_minuscula() {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena("SEGURA1!")
        assertTrue(requisitos.longitudMinima)
        assertTrue(requisitos.tieneMayuscula)
        assertFalse(requisitos.tieneMinuscula)
        assertTrue(requisitos.tieneNumero)
        assertTrue(requisitos.tieneSimbolo)
        assertFalse(requisitos.todosCumplidos)
    }

    @Test
    fun contrasena_sin_numero_falla_regla_de_numero() {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena("Segura!!")
        assertTrue(requisitos.longitudMinima)
        assertTrue(requisitos.tieneMayuscula)
        assertTrue(requisitos.tieneMinuscula)
        assertFalse(requisitos.tieneNumero)
        assertTrue(requisitos.tieneSimbolo)
        assertFalse(requisitos.todosCumplidos)
    }

    @Test
    fun contrasena_sin_simbolo_falla_regla_de_simbolo() {
        val requisitos = ValidadorRegistro.evaluarRequisitosContrasena("Segura12")
        assertTrue(requisitos.longitudMinima)
        assertTrue(requisitos.tieneMayuscula)
        assertTrue(requisitos.tieneMinuscula)
        assertTrue(requisitos.tieneNumero)
        assertFalse(requisitos.tieneSimbolo)
        assertFalse(requisitos.todosCumplidos)
    }

    @Test
    fun espacios_y_letras_unicode_no_cuentan_como_simbolo() {
        val conEspacio = ValidadorRegistro.evaluarRequisitosContrasena("Segura 12")
        assertFalse("Un espacio normal no debe contar como símbolo", conEspacio.tieneSimbolo)

        val conEspacioNoSeparable = ValidadorRegistro.evaluarRequisitosContrasena("Segura\u00A012")
        assertFalse("Un espacio NBSP no debe contar como símbolo", conEspacioNoSeparable.tieneSimbolo)

        val conAcentosYEne = ValidadorRegistro.evaluarRequisitosContrasena("Seguñá12")
        assertFalse("Letras acentuadas y ñ no deben contar como símbolo", conAcentosYEne.tieneSimbolo)
    }

    @Test
    fun contrasena_que_cumple_las_cinco_reglas_es_valida() {
        val validas = listOf(
            "Segura1!",
            "MiClave#2026",
            "Aa0_bcde",
            "Contrasena9¿",
        )
        validas.forEach { contrasena ->
            val requisitos = ValidadorRegistro.evaluarRequisitosContrasena(contrasena)
            assertTrue("Todos los requisitos deben cumplirse para '$contrasena'", requisitos.todosCumplidos)
            assertNull("No debe haber error para '$contrasena'", ValidadorRegistro.validarContrasena(contrasena))
        }
    }

    // --- Confirmación de contraseña ---

    @Test
    fun confirmacion_vacia_es_rechazada_como_vacia() {
        assertEquals(
            ErrorConfirmacionRegistro.Vacia,
            ValidadorRegistro.validarConfirmacion("Segura1!", ""),
        )
    }

    @Test
    fun confirmacion_distinta_o_con_espacio_extra_no_coincide() {
        assertEquals(
            ErrorConfirmacionRegistro.NoCoincide,
            ValidadorRegistro.validarConfirmacion("Segura1!", "Segura2!"),
        )
        assertEquals(
            ErrorConfirmacionRegistro.NoCoincide,
            ValidadorRegistro.validarConfirmacion("Segura1!", "Segura1! "),
        )
    }

    @Test
    fun confirmacion_identica_es_valida() {
        assertNull(ValidadorRegistro.validarConfirmacion("Segura1!", "Segura1!"))
    }
}
