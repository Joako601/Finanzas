package io.github.joako601.finanzas.ui.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.libro.totalEnCuentas
import io.github.joako601.finanzas.ui.hoy
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

/** Lo que muestra la pantalla principal. Inmutable: cada cambio produce un estado nuevo. */
data class EstadoMovimientos(
    val cargando: Boolean = true,
    val cuentasDeDinero: List<SaldoCuenta> = emptyList(),
    /** Tarjetas de crédito: se ofrecen como forma de pago de un gasto, no de un ingreso. */
    val tarjetas: List<Cuenta> = emptyList(),
    val totalEnCuentas: Dinero = Dinero.CERO,
    val categoriasDeGasto: List<Cuenta> = emptyList(),
    val categoriasDeIngreso: List<Cuenta> = emptyList(),
    val dias: List<DiaDeMovimientos> = emptyList(),
)

data class SaldoCuenta(val cuenta: Cuenta, val saldo: Dinero)

data class DiaDeMovimientos(val fecha: LocalDate, val movimientos: List<MovimientoResumido>)

/** Formulario de captura abierto. `null` en el ViewModel significa hoja cerrada. */
data class Captura(
    val clase: ClaseRegistro = ClaseRegistro.GASTO,
    val montoTexto: String = "",
    val cuentaId: CuentaId? = null,
    val categoriaId: CuentaId? = null,
    val fecha: LocalDate,
    val nota: String = "",
    val errores: Set<ErrorRegistro> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/** Diálogo de nueva cuenta abierto. */
data class NuevaCuenta(
    val nombre: String = "",
    val saldoTexto: String = "",
    val errorNombre: Boolean = false,
    val errorSaldo: Boolean = false,
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/**
 * ViewModel de la pantalla principal.
 *
 * Flujo unidireccional: la pantalla lee [estado], [captura] y [nuevaCuenta], y solo
 * los cambia llamando funciones de aquí. El ViewModel sobrevive a rotaciones de
 * pantalla; lo que el usuario estaba capturando no se pierde al girar el teléfono.
 */
class MovimientosViewModel(
    private val repositorio: RepositorioLibro,
    private val servicio: ServicioLibro,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    val estado: StateFlow<EstadoMovimientos> = combine(
        repositorio.observarCuentas(),
        repositorio.observarSaldos(),
        repositorio.observarMovimientos(),
    ) { cuentas, saldos, movimientos ->
        val porId = cuentas.associateBy { it.id }
        val activas = cuentas.filterNot { it.archivada }
        EstadoMovimientos(
            cargando = false,
            cuentasDeDinero = activas
                .filter { it.tipo == TipoCuenta.ACTIVO }
                .map { SaldoCuenta(it, saldos[it.id] ?: Dinero.CERO) },
            tarjetas = activas.filter { it.tipo == TipoCuenta.PASIVO },
            totalEnCuentas = totalEnCuentas(cuentas, saldos),
            categoriasDeGasto = activas.filter { it.tipo == TipoCuenta.GASTO },
            categoriasDeIngreso = activas.filter { it.tipo == TipoCuenta.INGRESO },
            dias = movimientos
                .map { it.resumir(porId) }
                .groupBy { it.movimiento.fecha }
                .map { (fecha, lista) -> DiaDeMovimientos(fecha, lista) },
        )
    }
        // Agrupar y resumir no debe correr en el hilo de la interfaz.
        .flowOn(Dispatchers.Default)
        // Mientras la pantalla esté visible se mantiene suscrito; 5 s de gracia evitan
        // reconsultar la base en una rotación de pantalla.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMovimientos())

    private val _captura = MutableStateFlow<Captura?>(null)
    val captura: StateFlow<Captura?> = _captura.asStateFlow()

    private val _nuevaCuenta = MutableStateFlow<NuevaCuenta?>(null)
    val nuevaCuenta: StateFlow<NuevaCuenta?> = _nuevaCuenta.asStateFlow()

    /** Última cuenta usada, para preseleccionarla y ahorrar un toque. Solo en memoria por ahora. */
    private var ultimaCuenta: CuentaId? = null

    // ---- Captura de gasto o ingreso ----

    fun abrirCaptura() {
        val cuentas = estado.value.cuentasDeDinero.map { it.cuenta.id } + estado.value.tarjetas.map { it.id }
        val preseleccion = ultimaCuenta?.takeIf { it in cuentas } ?: cuentas.firstOrNull()
        _captura.value = Captura(fecha = fechaDeHoy(), cuentaId = preseleccion)
    }

    fun cerrarCaptura() {
        _captura.value = null
    }

    fun cambiarClase(clase: ClaseRegistro) = editarCaptura {
        // Las categorías de gasto e ingreso son distintas: al cambiar, se borra la elegida.
        // Un ingreso no llega a una tarjeta: si estaba elegida una, se vuelve a la primera cuenta.
        if (it.clase == clase) {
            it
        } else {
            val esTarjeta = estado.value.tarjetas.any { t -> t.id == it.cuentaId }
            val cuenta = if (clase == ClaseRegistro.INGRESO && esTarjeta) {
                estado.value.cuentasDeDinero.firstOrNull()?.cuenta?.id
            } else {
                it.cuentaId
            }
            it.copy(clase = clase, categoriaId = null, cuentaId = cuenta)
        }
    }

    fun cambiarMonto(texto: String) = editarCaptura {
        // Solo caracteres que pueden formar un monto; el teclado numérico a veces deja pasar otros.
        it.copy(montoTexto = texto.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(20))
    }

    fun elegirCategoria(id: CuentaId) = editarCaptura { it.copy(categoriaId = id) }

    fun elegirCuenta(id: CuentaId) = editarCaptura { it.copy(cuentaId = id) }

    fun cambiarFecha(fecha: LocalDate) = editarCaptura { it.copy(fecha = fecha) }

    fun cambiarNota(nota: String) = editarCaptura { it.copy(nota = nota) }

    fun guardarCaptura() {
        val actual = _captura.value ?: return
        if (actual.guardando) return
        when (val resultado = validar(actual)) {
            is ResultadoRegistro.Invalido -> _captura.value = actual.copy(errores = resultado.errores)
            is ResultadoRegistro.Valido -> {
                _captura.value = actual.copy(errores = emptySet(), guardando = true, errorAlGuardar = false)
                viewModelScope.launch {
                    try {
                        repositorio.registrar(resultado.movimiento)
                        ultimaCuenta = actual.cuentaId
                        _captura.value = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _captura.update { it?.copy(guardando = false, errorAlGuardar = true) }
                    }
                }
            }
        }
    }

    /**
     * Aplica un cambio al formulario. Los errores solo aparecen después del primer
     * intento de guardar; a partir de ahí se recalculan con cada cambio para que
     * desaparezcan en cuanto el usuario los corrige.
     */
    private fun editarCaptura(cambio: (Captura) -> Captura) {
        _captura.update { actual ->
            actual?.let(cambio)?.let { nueva ->
                if (nueva.errores.isEmpty()) {
                    nueva
                } else {
                    val resultado = validar(nueva)
                    nueva.copy(errores = (resultado as? ResultadoRegistro.Invalido)?.errores.orEmpty())
                }
            }
        }
    }

    private fun validar(captura: Captura): ResultadoRegistro {
        val porId = buildMap {
            estado.value.cuentasDeDinero.forEach { put(it.cuenta.id, it.cuenta) }
            // Una tarjeta solo es válida para gastos; en un ingreso se trata como cuenta no elegida.
            if (captura.clase == ClaseRegistro.GASTO) estado.value.tarjetas.forEach { put(it.id, it) }
            estado.value.categoriasDeGasto.forEach { put(it.id, it) }
            estado.value.categoriasDeIngreso.forEach { put(it.id, it) }
        }
        return Registro.desdeCaptura(
            clase = captura.clase,
            montoTexto = captura.montoTexto,
            cuenta = captura.cuentaId?.let(porId::get),
            categoria = captura.categoriaId?.let(porId::get),
            fecha = captura.fecha,
            nota = captura.nota,
        )
    }

    // ---- Eliminar ----

    fun eliminar(id: MovimientoId) {
        viewModelScope.launch { repositorio.eliminar(id) }
    }

    // ---- Nueva cuenta ----

    fun abrirNuevaCuenta() {
        _nuevaCuenta.value = NuevaCuenta()
    }

    fun cerrarNuevaCuenta() {
        _nuevaCuenta.value = null
    }

    fun cambiarNombreCuenta(nombre: String) =
        _nuevaCuenta.update { it?.copy(nombre = nombre.take(Cuenta.LARGO_MAXIMO_NOMBRE), errorNombre = false) }

    fun cambiarSaldoCuenta(texto: String) = _nuevaCuenta.update {
        it?.copy(saldoTexto = texto.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(20), errorSaldo = false)
    }

    fun guardarNuevaCuenta() {
        val actual = _nuevaCuenta.value ?: return
        if (actual.guardando) return
        val saldo = if (actual.saldoTexto.isBlank()) Dinero.CERO else Dinero.parsear(actual.saldoTexto)
        val errorNombre = actual.nombre.isBlank()
        val errorSaldo = saldo == null
        if (errorNombre || errorSaldo) {
            _nuevaCuenta.value = actual.copy(errorNombre = errorNombre, errorSaldo = errorSaldo)
            return
        }
        _nuevaCuenta.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                servicio.crearCuentaDeDinero(actual.nombre, saldo, fechaDeHoy())
                _nuevaCuenta.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _nuevaCuenta.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    companion object {
        /** Cómo crear este ViewModel con sus dependencias tomadas del contenedor de la app. */
        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp
                MovimientosViewModel(
                    repositorio = app.contenedor.repositorioLibro,
                    servicio = app.contenedor.servicioLibro,
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
