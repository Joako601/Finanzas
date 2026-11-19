package io.github.joako601.finanzas.core.data.respaldo

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Por qué no se pudo leer un respaldo. La interfaz traduce cada motivo a un texto. */
public class ErrorRespaldo(public val motivo: Motivo, causa: Throwable? = null) : Exception(motivo.name, causa) {
    public enum class Motivo {
        /** El archivo no empieza como un respaldo de Finanzas (o el encabezado no tiene sentido). */
        NO_ES_RESPALDO,

        /** GCM no distingue una contraseña equivocada de un archivo alterado o cortado. */
        CONTRASENA_O_DANADO,

        /** Lo hizo una versión de la app más nueva que esta. */
        VERSION_NUEVA,

        /** Se descifró bien, pero la base de adentro no está completa o no es de Finanzas. */
        BASE_INVALIDA,
    }
}

/**
 * El sobre cifrado del respaldo (D-018). Todo con lo que trae Android, sin librerías:
 * - Llave: PBKDF2-HMAC-SHA256 sobre la contraseña, con sal aleatoria de 16 bytes y
 *   [ITERACIONES] vueltas (la cifra que recomienda OWASP para PBKDF2-SHA256).
 * - Cifrado: AES-256-GCM, que además detecta cualquier byte alterado.
 *
 * Formato del archivo:
 * ```
 * "FINANZAS" (8) | formato = 1 (1) | vueltas (4, big endian) | sal (16) | iv (12) | cifrado + etiqueta GCM
 * ```
 * El encabezado completo va como dato autenticado: cambiar las vueltas o la sal también
 * se detecta.
 *
 * shortcut: todo en memoria (ByteArray). La base pesa kilobytes; con años de movimientos
 * serán pocos MB. Si algún día pesa decenas de MB, pasar a flujos.
 */
internal object CifradoRespaldo {
    const val ITERACIONES = 600_000
    const val MINIMO_ITERACIONES = 100_000
    private const val MAXIMO_ITERACIONES = 10_000_000

    private val MAGIA = "FINANZAS".encodeToByteArray()
    private const val FORMATO: Byte = 1
    private const val LARGO_SAL = 16
    private const val LARGO_IV = 12
    private const val LARGO_ETIQUETA = 16
    private val LARGO_ENCABEZADO = MAGIA.size + 1 + 4 + LARGO_SAL + LARGO_IV

    private val aleatorio = SecureRandom()

    fun cifrar(datos: ByteArray, contrasena: CharArray, iteraciones: Int = ITERACIONES): ByteArray {
        require(iteraciones in MINIMO_ITERACIONES..MAXIMO_ITERACIONES)
        val sal = ByteArray(LARGO_SAL).also(aleatorio::nextBytes)
        val iv = ByteArray(LARGO_IV).also(aleatorio::nextBytes)
        val encabezado = ByteBuffer.allocate(LARGO_ENCABEZADO)
            .put(MAGIA).put(FORMATO).putInt(iteraciones).put(sal).put(iv)
            .array()
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, llave(contrasena, sal, iteraciones), GCMParameterSpec(LARGO_ETIQUETA * 8, iv))
            updateAAD(encabezado)
        }
        return encabezado + cifrador.doFinal(datos)
    }

    fun pareceRespaldo(sobre: ByteArray): Boolean =
        sobre.size > MAGIA.size && sobre.copyOf(MAGIA.size).contentEquals(MAGIA)

    fun descifrar(sobre: ByteArray, contrasena: CharArray): ByteArray {
        if (!pareceRespaldo(sobre)) throw ErrorRespaldo(ErrorRespaldo.Motivo.NO_ES_RESPALDO)
        if (sobre[MAGIA.size] > FORMATO) throw ErrorRespaldo(ErrorRespaldo.Motivo.VERSION_NUEVA)
        if (sobre[MAGIA.size] != FORMATO) throw ErrorRespaldo(ErrorRespaldo.Motivo.NO_ES_RESPALDO)
        if (sobre.size < LARGO_ENCABEZADO + LARGO_ETIQUETA) throw ErrorRespaldo(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO)

        val lector = ByteBuffer.wrap(sobre, MAGIA.size + 1, LARGO_ENCABEZADO - MAGIA.size - 1)
        val iteraciones = lector.getInt()
        if (iteraciones !in MINIMO_ITERACIONES..MAXIMO_ITERACIONES) throw ErrorRespaldo(ErrorRespaldo.Motivo.NO_ES_RESPALDO)
        val sal = ByteArray(LARGO_SAL).also { lector.get(it) }
        val iv = ByteArray(LARGO_IV).also { lector.get(it) }

        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, llave(contrasena, sal, iteraciones), GCMParameterSpec(LARGO_ETIQUETA * 8, iv))
                updateAAD(sobre, 0, LARGO_ENCABEZADO)
                doFinal(sobre, LARGO_ENCABEZADO, sobre.size - LARGO_ENCABEZADO)
            }
        } catch (e: GeneralSecurityException) {
            throw ErrorRespaldo(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO, e)
        }
    }

    private fun llave(contrasena: CharArray, sal: ByteArray, iteraciones: Int): SecretKeySpec {
        val especificacion = PBEKeySpec(contrasena, sal, iteraciones, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            especificacion.clearPassword()
        }
    }
}
