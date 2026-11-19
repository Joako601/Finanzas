package io.github.joako601.finanzas.core.data.respaldo

import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CifradoRespaldoTest {

    private val datos = "SQLite format 3\u0000 cuentas y movimientos".encodeToByteArray()
    private val contrasena = "quincena del 15".toCharArray()

    /** Las pruebas usan el mínimo de vueltas para ir rápido; una usa el valor real. */
    private val rapido = CifradoRespaldo.MINIMO_ITERACIONES

    @Test
    fun ida_y_vuelta_con_las_vueltas_reales() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena)
        assertContentEquals(datos, CifradoRespaldo.descifrar(sobre, contrasena))
    }

    @Test
    fun el_archivo_no_contiene_los_datos_en_claro() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        assertFalse(sobre.decodeToString().contains("SQLite format"))
    }

    @Test
    fun dos_respaldos_iguales_no_producen_el_mismo_archivo() {
        val uno = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        val otro = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        assertFalse(uno.contentEquals(otro), "la sal y el vector inicial deben ser aleatorios")
    }

    @Test
    fun contrasena_equivocada_se_rechaza() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        val error = assertFailsWith<ErrorRespaldo> { CifradoRespaldo.descifrar(sobre, "quincena del 30".toCharArray()) }
        assertEquals(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO, error.motivo)
    }

    @Test
    fun cualquier_byte_alterado_se_detecta() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        // Uno del encabezado autenticado (la sal), uno del contenido y el último (la etiqueta).
        listOf(20, sobre.size / 2 + 20, sobre.size - 1).forEach { posicion ->
            val alterado = sobre.copyOf().also { it[posicion] = (it[posicion].toInt() xor 1).toByte() }
            val error = assertFailsWith<ErrorRespaldo>("byte $posicion") { CifradoRespaldo.descifrar(alterado, contrasena) }
            assertEquals(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO, error.motivo)
        }
    }

    @Test
    fun archivo_cortado_se_rechaza() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        listOf(sobre.size - 5, 30, 3).forEach { largo ->
            assertFailsWith<ErrorRespaldo>("largo $largo") { CifradoRespaldo.descifrar(sobre.copyOf(largo), contrasena) }
        }
    }

    @Test
    fun un_archivo_que_no_es_respaldo_se_reconoce() {
        listOf("\uFEFFFecha,Tipo\r\n".encodeToByteArray(), Random(7).nextBytes(500), ByteArray(0)).forEach { otro ->
            val error = assertFailsWith<ErrorRespaldo> { CifradoRespaldo.descifrar(otro, contrasena) }
            assertEquals(ErrorRespaldo.Motivo.NO_ES_RESPALDO, error.motivo)
        }
    }

    @Test
    fun vueltas_fuera_de_rango_en_el_encabezado_se_rechazan_sin_calcular() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        // Bytes 9..12: las vueltas. Un archivo con dos mil millones de vueltas congelaría la app.
        val enorme = sobre.copyOf().also { it[9] = 0x7F }
        val error = assertFailsWith<ErrorRespaldo> { CifradoRespaldo.descifrar(enorme, contrasena) }
        assertEquals(ErrorRespaldo.Motivo.NO_ES_RESPALDO, error.motivo)
    }

    @Test
    fun una_version_nueva_del_formato_se_reporta_como_tal() {
        val sobre = CifradoRespaldo.cifrar(datos, contrasena, rapido)
        val futuro = sobre.copyOf().also { it[8] = 2 }
        val error = assertFailsWith<ErrorRespaldo> { CifradoRespaldo.descifrar(futuro, contrasena) }
        assertEquals(ErrorRespaldo.Motivo.VERSION_NUEVA, error.motivo)
    }

    @Test
    fun el_encabezado_va_autenticado_aunque_la_llave_sea_la_correcta() {
        // Mismo encabezado, misma llave y mismo IV: lo único que cambia es si el encabezado
        // entra como dato autenticado de GCM. Así se prueba ese candado por separado.
        val sal = ByteArray(16) { 1 }
        val iv = ByteArray(12) { 2 }
        val encabezado = ByteBuffer.allocate(41)
            .put("FINANZAS".encodeToByteArray()).put(1).putInt(rapido).put(sal).put(iv)
            .array()
        val llave = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(contrasena, sal, rapido, 256)).encoded,
            "AES",
        )
        fun sobre(autenticado: Boolean): ByteArray = encabezado + Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, llave, GCMParameterSpec(128, iv))
            if (autenticado) updateAAD(encabezado)
            doFinal(datos)
        }

        // Control: armado igual que la app, se abre.
        assertContentEquals(datos, CifradoRespaldo.descifrar(sobre(autenticado = true), contrasena))
        // Sin el encabezado autenticado, se rechaza.
        val error = assertFailsWith<ErrorRespaldo> { CifradoRespaldo.descifrar(sobre(autenticado = false), contrasena) }
        assertEquals(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO, error.motivo)
    }
}
