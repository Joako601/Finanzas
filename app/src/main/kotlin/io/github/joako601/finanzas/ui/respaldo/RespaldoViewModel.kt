package io.github.joako601.finanzas.ui.respaldo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.data.respaldo.ErrorRespaldo
import io.github.joako601.finanzas.core.data.respaldo.Respaldo
import io.github.joako601.finanzas.core.domain.exportacion.ExportacionCsv
import io.github.joako601.finanzas.ui.hoy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Leer y escribir el archivo que el usuario eligió con el selector de Android (URI de contenido). */
interface Archivos {
    suspend fun leer(uri: String): ByteArray
    suspend fun escribir(uri: String, datos: ByteArray)
}

/** Fecha del último respaldo guardado, para recordarle al usuario cuándo fue. */
interface UltimoRespaldo {
    fun leer(): LocalDate?
    fun guardar(fecha: LocalDate)
}

/** Lo que el ViewModel necesita de core:data y del dominio, como funciones para poder probarlo sin Android. */
class OperacionesRespaldo(
    val crear: suspend (contrasena: CharArray) -> ByteArray,
    val restaurar: suspend (sobre: ByteArray, contrasena: CharArray) -> Unit,
    val pareceRespaldo: (ByteArray) -> Boolean,
    val csv: suspend () -> String,
)

enum class Tarea { CREANDO, EXPORTANDO }

/** Resultado de la última acción, que la hoja muestra hasta la siguiente. */
enum class Aviso { RESPALDO_GUARDADO, DATOS_RESTAURADOS, CSV_GUARDADO, NO_SE_PUDO_ESCRIBIR, NO_SE_PUDO_LEER, NO_ES_RESPALDO }

enum class ErrorContrasena { CORTA, NO_COINCIDE }

data class EstadoRespaldo(
    val ultimo: LocalDate? = null,
    val tarea: Tarea? = null,
    val aviso: Aviso? = null,
)

data class NuevoRespaldo(
    val contrasena: String = "",
    val confirmacion: String = "",
    val error: ErrorContrasena? = null,
)

/** El archivo ya leído, esperando la contraseña y la confirmación de reemplazar todo. */
class PorRestaurar(
    val sobre: ByteArray,
    val contrasena: String = "",
    val error: ErrorRespaldo.Motivo? = null,
    val trabajando: Boolean = false,
) {
    fun copy(contrasena: String = this.contrasena, error: ErrorRespaldo.Motivo? = this.error, trabajando: Boolean = this.trabajando) =
        PorRestaurar(sobre, contrasena, error, trabajando)
}

/**
 * Respaldo cifrado, restauración y exportación a CSV (D-018). Las URI vienen del selector
 * de archivos de Android: la app no pide permiso de almacenamiento ni de internet.
 */
class RespaldoViewModel(
    private val operaciones: OperacionesRespaldo,
    private val archivos: Archivos,
    private val ultimoRespaldo: UltimoRespaldo,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoRespaldo(ultimo = ultimoRespaldo.leer()))
    val estado: StateFlow<EstadoRespaldo> = _estado.asStateFlow()

    private val _nuevo = MutableStateFlow<NuevoRespaldo?>(null)
    val nuevo: StateFlow<NuevoRespaldo?> = _nuevo.asStateFlow()

    private val _porRestaurar = MutableStateFlow<PorRestaurar?>(null)
    val porRestaurar: StateFlow<PorRestaurar?> = _porRestaurar.asStateFlow()

    fun abrirNuevoRespaldo() {
        _nuevo.value = NuevoRespaldo()
    }

    fun editarNuevo(cambio: NuevoRespaldo.() -> NuevoRespaldo) {
        _nuevo.update { it?.cambio()?.copy(error = null) }
    }

    fun cerrarNuevo() {
        _nuevo.value = null
    }

    /** Antes de abrir el selector de archivos: si es falso, el diálogo ya muestra el error. */
    fun validarNuevo(): Boolean {
        val actual = _nuevo.value ?: return false
        val error = when {
            actual.contrasena.length < MINIMO_CONTRASENA -> ErrorContrasena.CORTA
            actual.contrasena != actual.confirmacion -> ErrorContrasena.NO_COINCIDE
            else -> null
        }
        _nuevo.value = actual.copy(error = error)
        return error == null
    }

    /** [uri] es null si el usuario canceló el selector: el diálogo sigue abierto. */
    fun guardarRespaldo(uri: String?) {
        val actual = _nuevo.value ?: return
        if (uri == null) return
        _nuevo.value = null
        trabajar(Tarea.CREANDO) {
            val sobre = operaciones.crear(actual.contrasena.toCharArray())
            if (!escribir(uri, sobre)) return@trabajar
            val hoy = fechaDeHoy()
            ultimoRespaldo.guardar(hoy)
            _estado.update { it.copy(ultimo = hoy, aviso = Aviso.RESPALDO_GUARDADO) }
        }
    }

    fun exportarCsv(uri: String?) {
        if (uri == null) return
        trabajar(Tarea.EXPORTANDO) {
            if (escribir(uri, operaciones.csv().encodeToByteArray())) avisar(Aviso.CSV_GUARDADO)
        }
    }

    /** Lee el archivo elegido y, si parece un respaldo, pide la contraseña. */
    fun elegirParaRestaurar(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val sobre = try {
                archivos.leer(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                avisar(Aviso.NO_SE_PUDO_LEER)
                return@launch
            }
            if (operaciones.pareceRespaldo(sobre)) {
                avisar(null)
                _porRestaurar.value = PorRestaurar(sobre)
            } else {
                avisar(Aviso.NO_ES_RESPALDO)
            }
        }
    }

    fun editarRestaurar(contrasena: String) {
        _porRestaurar.update { it?.copy(contrasena = contrasena, error = null) }
    }

    fun cerrarRestaurar() {
        if (_porRestaurar.value?.trabajando != true) _porRestaurar.value = null
    }

    fun confirmarRestaurar() {
        val actual = _porRestaurar.value ?: return
        if (actual.trabajando) return
        _porRestaurar.value = actual.copy(trabajando = true)
        viewModelScope.launch {
            try {
                operaciones.restaurar(actual.sobre, actual.contrasena.toCharArray())
                _porRestaurar.value = null
                avisar(Aviso.DATOS_RESTAURADOS)
            } catch (e: ErrorRespaldo) {
                _porRestaurar.value = actual.copy(error = e.motivo, trabajando = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un error inesperado de la base: la transacción se deshizo y no cambió nada.
                _porRestaurar.value = actual.copy(error = ErrorRespaldo.Motivo.BASE_INVALIDA, trabajando = false)
            }
        }
    }

    private fun avisar(aviso: Aviso?) = _estado.update { it.copy(aviso = aviso) }

    private suspend fun escribir(uri: String, datos: ByteArray): Boolean = try {
        archivos.escribir(uri, datos)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        avisar(Aviso.NO_SE_PUDO_ESCRIBIR)
        false
    }

    private fun trabajar(tarea: Tarea, bloque: suspend () -> Unit) {
        if (_estado.value.tarea != null) return
        _estado.update { it.copy(tarea = tarea, aviso = null) }
        viewModelScope.launch {
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Falló la base o el cifrado al crear: no se escribió nada.
                avisar(Aviso.NO_SE_PUDO_ESCRIBIR)
            } finally {
                _estado.update { it.copy(tarea = null) }
            }
        }
    }

    companion object {
        const val MINIMO_CONTRASENA = 8

        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp
                val contenedor = app.contenedor
                RespaldoViewModel(
                    operaciones = OperacionesRespaldo(
                        crear = contenedor.respaldo::crear,
                        restaurar = contenedor.respaldo::restaurar,
                        pareceRespaldo = Respaldo::pareceRespaldo,
                        csv = {
                            val libro = contenedor.repositorioLibro
                            ExportacionCsv.generar(
                                libro.observarMovimientos().first(),
                                libro.observarCuentas().first().associateBy { it.id },
                            )
                        },
                    ),
                    archivos = ArchivosDelSistema(app.contentResolver),
                    ultimoRespaldo = PreferenciasRespaldo(app),
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
