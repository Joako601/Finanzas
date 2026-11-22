package io.github.joako601.finanzas.ui.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorRegistro
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.libro.totalEnCuentas
import io.github.joako601.finanzas.ui.componentes.limpiarMonto
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

/** Aviso efímero para celebrar un ingreso o gasto recién registrado. */
data class AvisoMovimientoGuardado(val id: Long, val clase: ClaseRegistro)

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
    /** Movimiento que se está editando; `null` al capturar uno nuevo. */
    val editando: MovimientoId? = null,
    /** Al editar: su cuenta o categoría si ya está archivada, para poder conservarla. */
    val archivadas: List<Cuenta> = emptyList(),
)

/** Diálogo de transferencia entre cuentas propias abierto. */
data class Transferencia(
    val montoTexto: String = "",
    val desdeId: CuentaId? = null,
    val haciaId: CuentaId? = null,
    val fecha: LocalDate,
    val nota: String = "",
    val errores: Set<ErrorRegistro> = emptySet(),
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
    val editando: MovimientoId? = null,
    val archivadas: List<Cuenta> = emptyList(),
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

    private val _transferencia = MutableStateFlow<Transferencia?>(null)
    val transferencia: StateFlow<Transferencia?> = _transferencia.asStateFlow()

    private val _nuevaCuenta = MutableStateFlow<NuevaCuenta?>(null)
    val nuevaCuenta: StateFlow<NuevaCuenta?> = _nuevaCuenta.asStateFlow()

    private val _avisoGuardado = MutableStateFlow<AvisoMovimientoGuardado?>(null)
    val avisoGuardado: StateFlow<AvisoMovimientoGuardado?> = _avisoGuardado.asStateFlow()
    private var secuenciaAviso: Long = 0L

    fun consumirAvisoGuardado() {
        _avisoGuardado.value = null
    }

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

    /**
     * Abre un movimiento guardado para editarlo: un gasto o ingreso en la hoja de captura,
     * una transferencia en su diálogo. Los demás (saldo inicial, pago de tarjeta) no se editan.
     */
    fun abrirEdicion(resumen: MovimientoResumido) {
        val movimiento = resumen.movimiento
        val id = movimiento.id ?: return
        if (!esEditable(resumen)) return
        val archivadas = listOfNotNull(resumen.cuenta, resumen.categoria, resumen.contraparte).filter { it.archivada }
        when (resumen.clase) {
            ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO -> _captura.value = Captura(
                clase = if (resumen.clase == ClaseMovimiento.GASTO) ClaseRegistro.GASTO else ClaseRegistro.INGRESO,
                montoTexto = resumen.monto.textoEditable(),
                cuentaId = resumen.cuenta?.id,
                categoriaId = resumen.categoria?.id,
                fecha = movimiento.fecha,
                nota = movimiento.descripcion,
                editando = id,
                archivadas = archivadas,
            )
            ClaseMovimiento.TRANSFERENCIA -> _transferencia.value = Transferencia(
                montoTexto = resumen.monto.textoEditable(),
                desdeId = resumen.cuenta?.id,
                haciaId = resumen.contraparte?.id,
                fecha = movimiento.fecha,
                nota = movimiento.descripcion,
                editando = id,
                archivadas = archivadas,
            )
            else -> Unit
        }
    }

    /**
     * Gastos, ingresos y transferencias entre efectivo y débito se editan. Una
     * "transferencia" que toca una tarjeta (disposición de efectivo) no tiene formulario
     * todavía; como los saldos iniciales y los pagos de tarjeta, solo se puede eliminar.
     */
    fun esEditable(resumen: MovimientoResumido): Boolean = when (resumen.clase) {
        ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO -> true
        ClaseMovimiento.TRANSFERENCIA ->
            resumen.cuenta?.tipo == TipoCuenta.ACTIVO && resumen.contraparte?.tipo == TipoCuenta.ACTIVO
        else -> false
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
        it.copy(montoTexto = limpiarMonto(texto))
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
                        guardarOActualizar(resultado.movimiento, actual.editando)
                        ultimaCuenta = actual.cuentaId
                        _captura.value = null
                        if (actual.editando == null) {
                            secuenciaAviso += 1L
                            _avisoGuardado.value = AvisoMovimientoGuardado(secuenciaAviso, actual.clase)
                        }
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
            captura.archivadas.forEach { put(it.id, it) }
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

    // ---- Transferencia entre cuentas propias ----

    /** Propone de la primera cuenta a la segunda; lo normal es cambiar una de las dos. */
    fun abrirTransferencia() {
        val cuentas = estado.value.cuentasDeDinero.map { it.cuenta.id }
        _transferencia.value = Transferencia(fecha = fechaDeHoy(), desdeId = cuentas.getOrNull(0), haciaId = cuentas.getOrNull(1))
    }

    fun cerrarTransferencia() {
        _transferencia.value = null
    }

    /** Como en la captura: los errores se recalculan solo después del primer intento de guardar. */
    fun editarTransferencia(cambio: Transferencia.() -> Transferencia) {
        _transferencia.update { actual ->
            actual?.cambio()?.let { nueva ->
                val errores = if (nueva.errores.isEmpty()) emptySet() else validar(nueva).erroresO()
                nueva.copy(montoTexto = limpiarMonto(nueva.montoTexto), errores = errores, errorAlGuardar = false)
            }
        }
    }

    fun guardarTransferencia() {
        val actual = _transferencia.value ?: return
        if (actual.guardando) return
        when (val resultado = validar(actual)) {
            is ResultadoRegistro.Invalido -> _transferencia.value = actual.copy(errores = resultado.errores)
            is ResultadoRegistro.Valido -> {
                _transferencia.value = actual.copy(errores = emptySet(), guardando = true, errorAlGuardar = false)
                viewModelScope.launch {
                    try {
                        guardarOActualizar(resultado.movimiento, actual.editando)
                        _transferencia.value = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _transferencia.update { it?.copy(guardando = false, errorAlGuardar = true) }
                    }
                }
            }
        }
    }

    private fun validar(transferencia: Transferencia): ResultadoRegistro {
        val porId = (estado.value.cuentasDeDinero.map { it.cuenta } + transferencia.archivadas).associateBy { it.id }
        return Registro.desdeTransferencia(
            montoTexto = transferencia.montoTexto,
            desde = transferencia.desdeId?.let(porId::get),
            hacia = transferencia.haciaId?.let(porId::get),
            fecha = transferencia.fecha,
            nota = transferencia.nota,
        )
    }

    private suspend fun guardarOActualizar(movimiento: Movimiento, editando: MovimientoId?) {
        if (editando == null) repositorio.registrar(movimiento) else repositorio.actualizar(movimiento.copy(id = editando))
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
        _nuevaCuenta.update { it?.copy(nombre = nombre.take(Cuenta.LARGO_MAXIMO_NOMBRE), errorNombre = false, errorAlGuardar = false) }

    fun cambiarSaldoCuenta(texto: String) = _nuevaCuenta.update {
        it?.copy(saldoTexto = limpiarMonto(texto), errorSaldo = false, errorAlGuardar = false)
    }

    fun guardarNuevaCuenta() {
        val actual = _nuevaCuenta.value ?: return
        if (actual.guardando) return
        val saldo = if (actual.saldoTexto.isBlank()) Dinero.CERO else Dinero.parsear(actual.saldoTexto)
        val errorNombre = actual.nombre.isBlank()
        val errorSaldo = saldo == null
        if (errorNombre || errorSaldo) {
            _nuevaCuenta.value = actual.copy(errorNombre = errorNombre, errorSaldo = errorSaldo, errorAlGuardar = false)
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

private fun ResultadoRegistro.erroresO(): Set<ErrorRegistro> = (this as? ResultadoRegistro.Invalido)?.errores.orEmpty()

/** "1234.50", o "100" si no tiene centavos: lo que el usuario escribiría en el campo de monto. */
internal fun Dinero.textoEditable(): String {
    val absoluto = absoluto().centavos
    val centavos = absoluto % 100
    return if (centavos == 0L) "${absoluto / 100}" else "${absoluto / 100}.${centavos.toString().padStart(2, '0')}"
}
