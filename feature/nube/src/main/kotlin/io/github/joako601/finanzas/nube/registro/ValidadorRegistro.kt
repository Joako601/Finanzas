package io.github.joako601.finanzas.nube.registro

import java.util.Locale

/** Errores específicos que pueden mostrarse en el campo de correo durante el registro. */
enum class ErrorCorreoRegistro {
    Vacio,
    FormatoInvalido,
    DominioNoInstitucional,
    YaRegistrado,
}

/** Errores específicos que pueden mostrarse en el campo de contraseña durante el registro. */
enum class ErrorContrasenaRegistro {
    Vacia,
    RequisitosIncompletos,
    DebilEnServidor,
}

/** Errores específicos que pueden mostrarse en el campo de confirmación de contraseña. */
enum class ErrorConfirmacionRegistro {
    Vacia,
    NoCoincide,
}

/** Estado en tiempo real de las 5 reglas de contraseña exigidas al crear cuenta. */
data class RequisitosContrasena(
    val longitudMinima: Boolean = false,
    val tieneMayuscula: Boolean = false,
    val tieneMinuscula: Boolean = false,
    val tieneNumero: Boolean = false,
    val tieneSimbolo: Boolean = false,
) {
    val todosCumplidos: Boolean
        get() = longitudMinima && tieneMayuscula && tieneMinuscula && tieneNumero && tieneSimbolo
}

/**
 * Validaciones puras del formulario de registro.
 *
 * A diferencia del Login (donde OWASP exige mensajes genéricos), en el Registro se informa
 * el motivo concreto de cada campo para guiar al usuario con claridad.
 */
object ValidadorRegistro {

    const val DOMINIO_INSTITUCIONAL: String = "tecdesoftware.edu.mx"
    const val LONGITUD_MINIMA_CONTRASENA: Int = 8

    private const val LONGITUD_MAXIMA_CORREO: Int = 254
    private const val LONGITUD_MAXIMA_LOCAL: Int = 64
    private val REGEX_LOCAL = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+$")

    /** Normaliza el correo antes de enviarlo al repositorio de sesión. */
    fun normalizarCorreo(correo: String): String = correo.trim().lowercase(Locale.ROOT)

    /**
     * Valida que el correo no esté vacío, tenga formato válido y pertenezca al dominio
     * institucional `@tecdesoftware.edu.mx` (insensible a mayúsculas, tras `trim()`).
     */
    fun validarCorreo(correo: String): ErrorCorreoRegistro? {
        val limpio = correo.trim()
        if (limpio.isEmpty()) return ErrorCorreoRegistro.Vacio
        if (limpio.length > LONGITUD_MAXIMA_CORREO) return ErrorCorreoRegistro.FormatoInvalido

        val partes = limpio.split('@')
        if (partes.size != 2) return ErrorCorreoRegistro.FormatoInvalido

        val local = partes[0]
        val dominio = partes[1]

        if (!esParteLocalValida(local) || !esDominioValido(dominio)) {
            return ErrorCorreoRegistro.FormatoInvalido
        }

        if (dominio.lowercase(Locale.ROOT) != DOMINIO_INSTITUCIONAL) {
            return ErrorCorreoRegistro.DominioNoInstitucional
        }

        return null
    }

    /** Evalúa las 5 reglas de contraseña para mostrar progreso en tiempo real. */
    fun evaluarRequisitosContrasena(contrasena: String): RequisitosContrasena = RequisitosContrasena(
        longitudMinima = contrasena.length >= LONGITUD_MINIMA_CONTRASENA,
        tieneMayuscula = contrasena.any { it.isUpperCase() },
        tieneMinuscula = contrasena.any { it.isLowerCase() },
        tieneNumero = contrasena.any { it in '0'..'9' },
        tieneSimbolo = contrasena.any { esSimboloValido(it) },
    )

    /** Valida que la contraseña no esté vacía y cumpla las 5 reglas. */
    fun validarContrasena(contrasena: String): ErrorContrasenaRegistro? {
        if (contrasena.isEmpty()) return ErrorContrasenaRegistro.Vacia
        if (!evaluarRequisitosContrasena(contrasena).todosCumplidos) {
            return ErrorContrasenaRegistro.RequisitosIncompletos
        }
        return null
    }

    /** Valida que la confirmación no esté vacía y coincida exactamente con la contraseña. */
    fun validarConfirmacion(contrasena: String, confirmacion: String): ErrorConfirmacionRegistro? {
        if (confirmacion.isEmpty()) return ErrorConfirmacionRegistro.Vacia
        if (confirmacion != contrasena) return ErrorConfirmacionRegistro.NoCoincide
        return null
    }

    private fun esParteLocalValida(local: String): Boolean {
        if (local.isEmpty() || local.length > LONGITUD_MAXIMA_LOCAL) return false
        if (local.startsWith('.') || local.endsWith('.') || local.contains("..")) return false
        return REGEX_LOCAL.matches(local)
    }

    private fun esDominioValido(dominio: String): Boolean {
        if (dominio.isEmpty() || !dominio.contains('.')) return false
        val etiquetas = dominio.split('.')
        if (etiquetas.size < 2) return false
        val todasValidas = etiquetas.all { etiqueta ->
            etiqueta.isNotEmpty() &&
                !etiqueta.startsWith('-') &&
                !etiqueta.endsWith('-') &&
                etiqueta.all { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' }
        }
        if (!todasValidas) return false
        val tld = etiquetas.last()
        return tld.length >= 2 && tld.all { c -> c in 'A'..'Z' || c in 'a'..'z' }
    }

    private fun esSimboloValido(caracter: Char): Boolean =
        !caracter.isLetterOrDigit() &&
            !caracter.isWhitespace() &&
            !Character.isSpaceChar(caracter) &&
            !caracter.isISOControl()
}
