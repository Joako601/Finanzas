package io.github.joako601.finanzas.ui.respaldo

import io.github.joako601.finanzas.core.data.respaldo.ErrorRespaldo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RespaldoViewModelTest {

    private val hoy = LocalDate(2026, 11, 19)
    private val sobre = byteArrayOf(1, 2, 3)

    /** Lo que escribe y lee el selector de archivos, en memoria. */
    private class ArchivosFalsos : Archivos {
        val escritos = mutableMapOf<String, ByteArray>()
        val paraLeer = mutableMapOf<String, ByteArray>()
        val borrados = mutableListOf<String>()
        var fallaAlEscribir = false

        override suspend fun leer(uri: String): ByteArray = paraLeer[uri] ?: throw IOException("no existe")
        override suspend fun escribir(uri: String, datos: ByteArray) {
            if (fallaAlEscribir) throw IOException("sin espacio")
            escritos[uri] = datos
        }
        override suspend fun borrar(uri: String) {
            borrados += uri
            escritos.remove(uri)
        }
    }

    private class UltimoEnMemoria(var fecha: LocalDate? = null) : UltimoRespaldo {
        override fun leer(): LocalDate? = fecha
        override fun guardar(fecha: LocalDate) {
            this.fecha = fecha
        }
    }

    private val archivos = ArchivosFalsos()
    private val ultimo = UltimoEnMemoria()
    private var contrasenaUsada: String? = null
    private var restaurado: Pair<ByteArray, String>? = null
    private var errorAlRestaurar: Exception? = null
    private var errorAlCrear: Exception? = null

    private val operaciones = OperacionesRespaldo(
        crear = { contrasena ->
            errorAlCrear?.let { throw it }
            contrasenaUsada = String(contrasena)
            sobre
        },
        restaurar = { datos, contrasena ->
            errorAlRestaurar?.let { throw it }
            restaurado = datos to String(contrasena)
        },
        pareceRespaldo = { it.isNotEmpty() && it[0] == 1.toByte() },
        csv = { "\uFEFFFecha,Tipo\r\n" },
    )

    private val despachador = UnconfinedTestDispatcher()

    @Before
    fun antes() = Dispatchers.setMain(despachador)

    @After
    fun despues() = Dispatchers.resetMain()

    private fun viewModel() = RespaldoViewModel(operaciones, archivos, ultimo, despachador) { hoy }

    @Test
    fun la_contrasena_debe_tener_8_caracteres_y_coincidir() {
        val vm = viewModel()
        vm.abrirNuevoRespaldo()

        vm.editarNuevo { copy(contrasena = "corta", confirmacion = "corta") }
        assertFalse(vm.validarNuevo())
        assertEquals(ErrorContrasena.CORTA, vm.nuevo.value?.error)

        vm.editarNuevo { copy(contrasena = "        ", confirmacion = "        ") }
        assertFalse(vm.validarNuevo())
        assertEquals(ErrorContrasena.CORTA, vm.nuevo.value?.error)

        vm.editarNuevo { copy(contrasena = "quincena del 15", confirmacion = "quincena del 30") }
        assertFalse(vm.validarNuevo())
        assertEquals(ErrorContrasena.NO_COINCIDE, vm.nuevo.value?.error)

        vm.editarNuevo { copy(confirmacion = "quincena del 15") }
        assertNull("editar borra el error", vm.nuevo.value?.error)
        assertTrue(vm.validarNuevo())
    }

    @Test
    fun guardar_escribe_el_archivo_cifrado_y_anota_la_fecha() = runTest {
        val vm = viewModel()
        vm.abrirNuevoRespaldo()
        vm.editarNuevo { copy(contrasena = "quincena del 15", confirmacion = "quincena del 15") }

        vm.guardarRespaldo("content://drive/respaldo")

        assertArrayEquals(sobre, archivos.escritos["content://drive/respaldo"])
        assertEquals("quincena del 15", contrasenaUsada)
        assertEquals(hoy, ultimo.fecha)
        assertEquals(hoy, vm.estado.value.ultimo)
        assertEquals(Aviso.RESPALDO_GUARDADO, vm.estado.value.aviso)
        assertNull("la contraseña no se queda en memoria", vm.nuevo.value)
        assertNull(vm.estado.value.tarea)
    }

    @Test
    fun si_no_se_puede_escribir_no_cuenta_como_respaldo() = runTest {
        archivos.fallaAlEscribir = true
        val vm = viewModel()
        vm.abrirNuevoRespaldo()
        vm.editarNuevo { copy(contrasena = "quincena del 15", confirmacion = "quincena del 15") }

        vm.guardarRespaldo("content://lleno")

        assertNull(ultimo.fecha)
        assertEquals(Aviso.NO_SE_PUDO_ESCRIBIR, vm.estado.value.aviso)
        assertEquals("el archivo a medias no se queda", listOf("content://lleno"), archivos.borrados)
    }

    @Test
    fun si_falla_el_cifrado_se_borra_el_archivo_vacio_y_se_avisa() = runTest {
        errorAlCrear = IllegalStateException("base cerrada")
        val vm = viewModel()
        vm.abrirNuevoRespaldo()
        vm.editarNuevo { copy(contrasena = "quincena del 15", confirmacion = "quincena del 15") }

        vm.guardarRespaldo("content://drive/vacio")

        assertEquals(listOf("content://drive/vacio"), archivos.borrados)
        assertEquals(Aviso.NO_SE_PUDO_ESCRIBIR, vm.estado.value.aviso)
        assertNull(vm.estado.value.tarea)
        assertNull(ultimo.fecha)
    }

    @Test
    fun si_android_cerro_la_app_mientras_elegia_carpeta_se_borra_el_archivo_y_se_avisa() = runTest {
        // Tras la muerte del proceso el ViewModel es nuevo: no tiene la contraseña.
        val vm = viewModel()

        vm.guardarRespaldo("content://drive/huerfano")

        assertEquals(listOf("content://drive/huerfano"), archivos.borrados)
        assertEquals(Aviso.RESPALDO_INTERRUMPIDO, vm.estado.value.aviso)
    }

    @Test
    fun un_error_inesperado_al_restaurar_se_muestra_como_respaldo_danado() = runTest {
        archivos.paraLeer["content://respaldo"] = sobre
        errorAlRestaurar = IllegalStateException("disco lleno")
        val vm = viewModel()
        vm.elegirParaRestaurar("content://respaldo")
        vm.editarRestaurar("quincena del 15")

        vm.confirmarRestaurar()

        assertEquals(ErrorRespaldo.Motivo.BASE_INVALIDA, vm.porRestaurar.value?.error)
        assertFalse(vm.porRestaurar.value!!.trabajando)
    }

    @Test
    fun al_cerrar_la_hoja_se_olvida_el_ultimo_aviso() = runTest {
        val vm = viewModel()
        vm.exportarCsv("content://descargas/movimientos.csv")
        assertEquals(Aviso.CSV_GUARDADO, vm.estado.value.aviso)

        vm.cerrarHoja()

        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun cancelar_el_selector_deja_abierto_el_dialogo_de_contrasena() {
        val vm = viewModel()
        vm.abrirNuevoRespaldo()
        vm.editarNuevo { copy(contrasena = "quincena del 15", confirmacion = "quincena del 15") }

        vm.guardarRespaldo(null)

        assertEquals("quincena del 15", vm.nuevo.value?.contrasena)
        assertTrue(archivos.escritos.isEmpty())
    }

    @Test
    fun un_archivo_que_no_es_respaldo_se_avisa_antes_de_pedir_contrasena() = runTest {
        archivos.paraLeer["content://foto"] = byteArrayOf(9, 9)
        val vm = viewModel()

        vm.elegirParaRestaurar("content://foto")

        assertNull(vm.porRestaurar.value)
        assertEquals(Aviso.NO_ES_RESPALDO, vm.estado.value.aviso)
    }

    @Test
    fun contrasena_equivocada_deja_el_dialogo_con_el_motivo() = runTest {
        archivos.paraLeer["content://respaldo"] = sobre
        errorAlRestaurar = ErrorRespaldo(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO)
        val vm = viewModel()
        vm.elegirParaRestaurar("content://respaldo")
        vm.editarRestaurar("otra cosa")

        vm.confirmarRestaurar()

        assertEquals(ErrorRespaldo.Motivo.CONTRASENA_O_DANADO, vm.porRestaurar.value?.error)
        assertFalse(vm.porRestaurar.value!!.trabajando)
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun restaurar_bien_cierra_el_dialogo_y_avisa() = runTest {
        archivos.paraLeer["content://respaldo"] = sobre
        val vm = viewModel()
        vm.elegirParaRestaurar("content://respaldo")
        vm.editarRestaurar("quincena del 15")

        vm.confirmarRestaurar()

        assertArrayEquals(sobre, restaurado?.first)
        assertEquals("quincena del 15", restaurado?.second)
        assertNull(vm.porRestaurar.value)
        assertEquals(Aviso.DATOS_RESTAURADOS, vm.estado.value.aviso)
    }

    @Test
    fun exportar_escribe_el_csv_en_utf8() = runTest {
        val vm = viewModel()

        vm.exportarCsv("content://descargas/movimientos.csv")

        assertEquals("\uFEFFFecha,Tipo\r\n", archivos.escritos["content://descargas/movimientos.csv"]?.decodeToString())
        assertEquals(Aviso.CSV_GUARDADO, vm.estado.value.aviso)
    }
}
