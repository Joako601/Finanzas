package io.github.joako601.finanzas.ui.recurrentes

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recurrentes.ErrorRecurrente
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.recurrentes.ServicioRecurrentes
import io.github.joako601.finanzas.ui.componentes.limpiarMonto
import io.github.joako601.finanzas.ui.diasDesde
import io.github.joako601.finanzas.ui.hoy
import io.github.joako601.finanzas.ui.movimientos.textoEditable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Las periodicidades que se ofrecen al dar de alta, en el orden en que se muestran. */
enum class Frecuencia(val periodicidad: Periodicidad, @StringRes val etiqueta: Int) {
    SEMANAL(Periodicidad.SEMANAL, R.string.frecuencia_semanal),
    CATORCENAL(Periodicidad.CATORCENAL, R.string.frecuencia_catorcenal),
    QUINCENAL(Periodicidad.QUINCENAL, R.string.frecuencia_quincenal),
    MENSUAL(Periodicidad.MENSUAL, R.string.frecuencia_mensual),
    BIMESTRAL(Periodicidad.BIMESTRAL, R.string.frecuencia_bimestral),
    ANUAL(Periodicidad.ANUAL, R.string.frecuencia_anual),
    ;

    companion object {
        /** La frecuencia de una periodicidad guardada; null si no es una de las que se ofrecen. */
        fun de(periodicidad: Periodicidad): Frecuencia? = entries.firstOrNull { it.periodicidad == periodicidad }
    }
}

/** Un pago o ingreso fijo con lo que la pantalla necesita mostrar. */
data class ResumenRecurrente(
    val recurrente: Recurrente,
    val categoria: Cuenta?,
    /** Negativo si la fecha ya pasó y sigue pendiente. */
    val diasParaProxima: Int,
)

data class EstadoRecurrentes(
    val recurrentes: List<ResumenRecurrente> = emptyList(),
    val categoriasDeGasto: List<Cuenta> = emptyList(),
    val categoriasDeIngreso: List<Cuenta> = emptyList(),
    /** Efectivo y débito: reciben ingresos y pagan gastos. */
    val cuentasDeDinero: List<Cuenta> = emptyList(),
    /** Tarjetas de crédito: solo pagan gastos. */
    val tarjetas: List<Cuenta> = emptyList(),
) {
    fun categoriasDe(clase: ClaseRegistro): List<Cuenta> = when (clase) {
        ClaseRegistro.GASTO -> categoriasDeGasto
        ClaseRegistro.INGRESO -> categoriasDeIngreso
    }

    fun cuentasPara(clase: ClaseRegistro): List<Cuenta> = when (clase) {
        ClaseRegistro.GASTO -> cuentasDeDinero + tarjetas
        ClaseRegistro.INGRESO -> cuentasDeDinero
    }
}

/** Diálogo de alta o edición de un pago o ingreso fijo. */
data class NuevoRecurrente(
    val clase: ClaseRegistro = ClaseRegistro.GASTO,
    val nombre: String = "",
    val monto: String = "",
    val categoriaId: CuentaId? = null,
    val frecuencia: Frecuencia = Frecuencia.MENSUAL,
    val inicio: LocalDate,
    val editando: RecurrenteId? = null,
    val inicioOriginal: LocalDate? = null,
    val proximaOriginal: LocalDate? = null,
    val periodicidadOriginal: Periodicidad? = null,
    val periodicidadPersonalizada: Periodicidad? = null,
    val fechaModificada: Boolean = false,
    val errores: Set<ErrorRecurrente> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/** Diálogo para registrar la ocurrencia pendiente con lo que pasó en realidad. */
data class RegistroRecurrente(
    val resumen: ResumenRecurrente,
    val monto: String,
    val cuentaId: CuentaId?,
    val fecha: LocalDate,
    val confirmandoEliminar: Boolean = false,
    val errores: Set<ErrorRegistro> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/**
 * ViewModel de la sección de pagos y cobros fijos. Como el de tarjetas, convive en la
 * pantalla principal y lee la misma base: lo que se registra aquí aparece solo en la
 * lista de movimientos.
 */
class RecurrentesViewModel(
    libro: RepositorioLibro,
    repositorio: RepositorioRecurrentes,
    private val servicio: ServicioRecurrentes,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    private val dias = diasDesde(fechaDeHoy)

    val estado: StateFlow<EstadoRecurrentes> = combine(
        libro.observarCuentas(),
        repositorio.observarRecurrentes(),
        dias,
    ) { cuentas, recurrentes, hoy ->
        val porId = cuentas.associateBy { it.id }
        val activas = cuentas.filterNot { it.archivada }
        val cuentasDinero = activas.filter { it.tipo == TipoCuenta.ACTIVO }
        val tarjetasActivas = activas.filter { it.tipo == TipoCuenta.PASIVO }

        EstadoRecurrentes(
            recurrentes = recurrentes.map { ResumenRecurrente(it, porId[it.categoria], hoy.daysUntil(it.proxima)) },
            categoriasDeGasto = activas.filter { it.tipo == TipoCuenta.GASTO },
            categoriasDeIngreso = activas.filter { it.tipo == TipoCuenta.INGRESO },
            cuentasDeDinero = cuentasDinero,
            tarjetas = tarjetasActivas,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoRecurrentes())

    private val _nuevo = MutableStateFlow<NuevoRecurrente?>(null)
    val nuevo: StateFlow<NuevoRecurrente?> = _nuevo.asStateFlow()

    private val _registro = MutableStateFlow<RegistroRecurrente?>(null)
    val registro: StateFlow<RegistroRecurrente?> = _registro.asStateFlow()

    // ---- Alta ----

    fun abrirNuevo() {
        _nuevo.value = NuevoRecurrente(inicio = fechaDeHoy())
    }

    fun cerrarNuevo() {
        _nuevo.value = null
    }

    /** Cambia un campo; los errores se limpian para no regañar mientras se corrige. */
    fun editarNuevo(cambio: NuevoRecurrente.() -> NuevoRecurrente) = _nuevo.update { actual ->
        actual?.cambio()?.let { nuevo ->
            // Las categorías de pago e ingreso son distintas: al cambiar de clase se borra la elegida.
            val categoria = if (nuevo.clase != actual.clase) null else nuevo.categoriaId
            val fechaModificada = actual.fechaModificada || nuevo.inicio != actual.inicio
            val personalizada = if (nuevo.frecuencia != actual.frecuencia) null else nuevo.periodicidadPersonalizada
            nuevo.copy(
                monto = limpiarMonto(nuevo.monto),
                categoriaId = categoria,
                fechaModificada = fechaModificada,
                periodicidadPersonalizada = personalizada,
                errores = emptySet(),
                errorAlGuardar = false,
            )
        }
    }

    fun guardarNuevo() {
        val actual = _nuevo.value ?: return
        if (actual.guardando) return
        val categoria = estado.value.categoriasDe(actual.clase).firstOrNull { it.id == actual.categoriaId }
        val errores = servicio.validarAlta(actual.nombre, actual.monto, actual.clase, categoria)
        if (errores.isNotEmpty()) {
            _nuevo.value = actual.copy(errores = errores)
            return
        }
        _nuevo.value = actual.copy(guardando = true)
        viewModelScope.launch {
            try {
                val idEditando = actual.editando
                val periodicidadFinal = actual.periodicidadPersonalizada ?: actual.frecuencia.periodicidad
                if (idEditando == null) {
                    servicio.crear(
                        nombre = actual.nombre,
                        clase = actual.clase,
                        montoEstimado = Dinero.parsear(actual.monto)!!,
                        categoria = categoria!!,
                        periodicidad = periodicidadFinal,
                        inicio = actual.inicio,
                        hoy = fechaDeHoy(),
                    )
                } else {
                    val existente = estado.value.recurrentes.firstOrNull { it.recurrente.id == idEditando }?.recurrente
                    val proximaAnterior = actual.proximaOriginal ?: existente?.proxima
                    val periodicidadOriginal = actual.periodicidadOriginal ?: existente?.periodicidad
                    val conservarAnclaYProxima = !actual.fechaModificada && periodicidadFinal == periodicidadOriginal
                    val inicioFinal = if (conservarAnclaYProxima) {
                        actual.inicioOriginal ?: existente?.inicio ?: actual.inicio
                    } else {
                        actual.inicio
                    }
                    servicio.actualizar(
                        id = idEditando,
                        nombre = actual.nombre,
                        clase = actual.clase,
                        montoEstimado = Dinero.parsear(actual.monto)!!,
                        categoria = categoria!!,
                        periodicidad = periodicidadFinal,
                        inicio = inicioFinal,
                        hoy = fechaDeHoy(),
                        proximaAnterior = proximaAnterior,
                        conservarProxima = conservarAnclaYProxima,
                    )
                }
                _nuevo.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _nuevo.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    // ---- Registrar, editar, omitir, eliminar ----

    fun abrirRegistro(resumen: ResumenRecurrente) {
        val cuentasDisponibles = estado.value.cuentasPara(resumen.recurrente.clase)
        _registro.value = RegistroRecurrente(
            resumen = resumen,
            monto = resumen.recurrente.montoEstimado.textoEditable(),
            cuentaId = estado.value.cuentasDeDinero.firstOrNull()?.id ?: cuentasDisponibles.firstOrNull()?.id,
            fecha = fechaDeHoy(),
        )
    }

    fun abrirEdicionDesdeRegistro() {
        val actual = _registro.value ?: return
        val r = actual.resumen.recurrente
        val frecuenciaConocida = Frecuencia.de(r.periodicidad)
        _registro.value = null
        _nuevo.value = NuevoRecurrente(
            clase = r.clase,
            nombre = r.nombre,
            monto = r.montoEstimado.textoEditable(),
            categoriaId = r.categoria,
            frecuencia = frecuenciaConocida ?: Frecuencia.MENSUAL,
            inicio = r.proxima,
            editando = r.id,
            inicioOriginal = r.inicio,
            proximaOriginal = r.proxima,
            periodicidadOriginal = r.periodicidad,
            periodicidadPersonalizada = if (frecuenciaConocida == null) r.periodicidad else null,
        )
    }

    fun pedirConfirmarEliminar() {
        _registro.update { it?.copy(confirmandoEliminar = true) }
    }

    fun cancelarConfirmarEliminar() {
        _registro.update { it?.copy(confirmandoEliminar = false) }
    }

    fun cerrarRegistro() {
        _registro.value = null
    }

    /** Selecciona explícitamente una cuenta recién creada desde "+ Agregar cuenta" mientras el diálogo de registro está abierto. */
    fun seleccionarCuentaRegistro(id: CuentaId) {
        _registro.update { actual ->
            actual?.copy(cuentaId = id, errores = actual.errores - ErrorRegistro.SIN_CUENTA)
        }
    }

    fun editarRegistro(cambio: RegistroRecurrente.() -> RegistroRecurrente) = _registro.update {
        it?.cambio()?.let { r -> r.copy(monto = limpiarMonto(r.monto), errores = emptySet(), errorAlGuardar = false) }
    }

    fun guardarRegistro() = conRegistro { actual ->
        val recurrente = actual.resumen.recurrente
        val cuenta = estado.value.cuentasPara(recurrente.clase).firstOrNull { it.id == actual.cuentaId }
        val resultado = servicio.registrar(recurrente, actual.monto, cuenta, actual.resumen.categoria, actual.fecha)
        if (resultado is ResultadoRegistro.Invalido) actual.copy(errores = resultado.errores, guardando = false) else null
    }

    fun omitir() = conRegistro { actual ->
        servicio.omitir(actual.resumen.recurrente)
        null
    }

    fun eliminar() = conRegistro { actual ->
        servicio.eliminar(actual.resumen.recurrente)
        null
    }

    /**
     * Ejecuta una acción del diálogo de registro. La acción devuelve el estado con el que
     * queda el diálogo, o null para cerrarlo. Si falla la base, el diálogo sigue abierto
     * con el aviso de error.
     */
    private fun conRegistro(accion: suspend (RegistroRecurrente) -> RegistroRecurrente?) {
        val actual = _registro.value ?: return
        if (actual.guardando) return
        _registro.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                _registro.value = accion(actual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _registro.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    companion object {
        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val contenedor = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp).contenedor
                RecurrentesViewModel(
                    libro = contenedor.repositorioLibro,
                    repositorio = contenedor.repositorioRecurrentes,
                    servicio = contenedor.servicioRecurrentes,
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
