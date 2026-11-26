package io.github.joako601.finanzas.ui.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.ErrorCategoria
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import io.github.joako601.finanzas.core.domain.metas.TopeCategoria
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

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
    /** Efectivo, débito y tarjetas archivadas, para reactivarlas desde Perfil. */
    val archivadas: List<SaldoCuenta> = emptyList(),
    /** Saldo de cada cuenta; en una tarjeta es negativo cuando se debe. */
    val saldos: Map<CuentaId, Dinero> = emptyMap(),
    /** Categorías de gasto e ingreso archivadas, para reactivarlas desde Perfil. */
    val categoriasArchivadas: List<Cuenta> = emptyList(),
)

/** Diálogo de crear o editar una categoría propia (Perfil → Categorías). */
data class EditorCategoria(
    val tipo: TipoCuenta,
    val editando: Cuenta? = null,
    val nombre: String = editando?.nombre.orEmpty(),
    val icono: String? = editando?.icono,
    val error: ErrorCategoria? = null,
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
)

/**
 * Búsqueda y filtros sobre el historial de Inicio. `null` en cada campo = sin filtrar.
 * [mes] es el día 1 del mes.
 */
data class Filtro(
    val texto: String = "",
    val mes: LocalDate? = null,
    val cuentaId: CuentaId? = null,
    val clase: ClaseMovimiento? = null,
    val categoriaId: CuentaId? = null,
)

/**
 * Lo que queda tras filtrar. [total]: con un tipo elegido, la suma de sus montos; sin tipo,
 * el neto (ingresos − gastos; transferencias y pagos de tarjeta no cambian lo que tienes).
 */
data class ResultadoFiltro(val dias: List<DiaDeMovimientos>, val conteo: Int, val total: Dinero)

/** Tipos que ofrece el filtro; un pago de tarjeta cuenta como transferencia (dinero entre cuentas propias). */
val CLASES_FILTRO = listOf(ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO, ClaseMovimiento.TRANSFERENCIA)

fun filtrar(dias: List<DiaDeMovimientos>, filtro: Filtro): ResultadoFiltro {
    val texto = filtro.texto.trim().sinAcentos()
    val filtrados = dias.mapNotNull { dia ->
        if (filtro.mes != null && (dia.fecha.year != filtro.mes.year || dia.fecha.month != filtro.mes.month)) return@mapNotNull null
        val movimientos = dia.movimientos.filter { m ->
            val clase = if (m.clase == ClaseMovimiento.PAGO_TARJETA) ClaseMovimiento.TRANSFERENCIA else m.clase
            (filtro.clase == null || clase == filtro.clase) &&
                (filtro.cuentaId == null || m.cuenta?.id == filtro.cuentaId || m.contraparte?.id == filtro.cuentaId) &&
                (filtro.categoriaId == null || m.categoria?.id == filtro.categoriaId) &&
                (texto.isEmpty() || listOfNotNull(m.movimiento.descripcion, m.categoria?.nombre).any { it.sinAcentos().contains(texto) })
        }
        if (movimientos.isEmpty()) null else DiaDeMovimientos(dia.fecha, movimientos)
    }
    val todos = filtrados.flatMap { it.movimientos }
    return ResultadoFiltro(filtrados, todos.size, totalDe(todos, filtro.clase))
}

/**
 * Con [clase] elegida, la suma de sus montos; sin ella, el neto (ingresos − gastos). Se usa
 * en el resultado de la búsqueda y en el encabezado de cada día.
 */
fun totalDe(movimientos: List<MovimientoResumido>, clase: ClaseMovimiento?): Dinero = movimientos.map {
    when {
        clase != null -> it.monto
        it.clase == ClaseMovimiento.INGRESO -> it.monto
        it.clase == ClaseMovimiento.GASTO -> -it.monto
        else -> Dinero.CERO
    }
}.sumar()

/** Días hacia atrás que cuentan para ordenar las categorías de la captura por uso. */
private const val DIAS_USO_CATEGORIAS = 90

/**
 * Las más usadas en los últimos [DIAS_USO_CATEGORIAS] días primero; las demás (y los empates)
 * conservan su orden original, así la captura no se reacomoda sin motivo.
 */
fun ordenarPorUso(categorias: List<Cuenta>, dias: List<DiaDeMovimientos>, hoy: LocalDate): List<Cuenta> {
    val desde = hoy.minus(DIAS_USO_CATEGORIAS, DateTimeUnit.DAY)
    val usos = dias.asSequence()
        .filter { it.fecha >= desde }
        .flatMap { it.movimientos }
        .mapNotNull { it.categoria?.id }
        .groupingBy { it }
        .eachCount()
    // sortedByDescending es estable: los empates quedan en el orden de entrada.
    return categorias.sortedByDescending { usos[it.id] ?: 0 }
}

/** A partir de qué parte del tope se avisa en la captura: antes sería ruido. */
private const val UMBRAL_AVISO_TOPE = 0.8

/** Aviso discreto en la captura: lo que quedaría del tope de la categoría, o cuánto se pasaría. */
data class AvisoTope(val categoria: String, val restante: Dinero? = null, val excedente: Dinero? = null)

/**
 * Si este gasto deja la categoría en el [UMBRAL_AVISO_TOPE] de su tope o más, o lo pasa.
 * Cuenta lo gastado en el mes de la captura sin el movimiento que se está editando.
 */
fun avisoTope(captura: Captura, topes: List<TopeCategoria>, dias: List<DiaDeMovimientos>, categorias: List<Cuenta>): AvisoTope? {
    if (captura.clase != ClaseRegistro.GASTO) return null
    val categoriaId = captura.categoriaId ?: return null
    val tope = topes.firstOrNull { it.categoriaId == categoriaId } ?: return null
    val monto = Dinero.parsear(captura.montoTexto)?.takeIf { it.esPositivo } ?: return null
    val fecha = captura.fecha
    val gastado = dias.asSequence()
        .filter { it.fecha.year == fecha.year && it.fecha.month == fecha.month }
        .flatMap { it.movimientos }
        .filter { it.clase == ClaseMovimiento.GASTO && it.categoria?.id == categoriaId && it.movimiento.id != captura.editando }
        .map { it.monto }
        .toList()
        .sumar()
    val total = gastado + monto
    val nombre = categorias.firstOrNull { it.id == categoriaId }?.nombre ?: return null
    return when {
        total > tope.limiteMensual -> AvisoTope(nombre, excedente = total - tope.limiteMensual)
        total.centavos >= tope.limiteMensual.centavos * UMBRAL_AVISO_TOPE -> AvisoTope(nombre, restante = tope.limiteMensual - total)
        else -> null
    }
}

/** "Súper" encuentra "super": sin acentos y en minúsculas. */
private fun String.sinAcentos(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

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
    /** Plazo a meses sin intereses; solo cuenta en un gasto con tarjeta (D-022). */
    val meses: Int? = null,
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

/** Diálogo de nueva cuenta o edición de cuenta existente abierto. */
data class NuevaCuenta(
    val nombre: String = "",
    val saldoTexto: String = "",
    val editando: SaldoCuenta? = null,
    val saldoModificado: Boolean = false,
    val errorNombre: Boolean = false,
    val errorSaldo: Boolean = false,
    val errorAlGuardar: Boolean = false,
    val guardando: Boolean = false,
    /** Al editar: el diálogo muestra la confirmación de archivar en lugar de los campos. */
    val confirmandoArchivar: Boolean = false,
)

/** Dónde se recuerda la última cuenta usada entre aperturas de la app. */
interface UltimaCuentaUsada {
    fun leer(): CuentaId?
    fun guardar(id: CuentaId?)

    /** Para pruebas y vistas previas: solo vive mientras exista el objeto. */
    class EnMemoria(private var id: CuentaId? = null) : UltimaCuentaUsada {
        override fun leer(): CuentaId? = id
        override fun guardar(id: CuentaId?) {
            this.id = id
        }
    }
}

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
    private val ultimaCuentaUsada: UltimaCuentaUsada = UltimaCuentaUsada.EnMemoria(),
    topes: Flow<List<TopeCategoria>> = flowOf(emptyList()),
) : ViewModel() {

    val estado: StateFlow<EstadoMovimientos> = combine(
        repositorio.observarCuentas(),
        repositorio.observarSaldos(),
        repositorio.observarMovimientos(),
    ) { cuentas, saldos, movimientos ->
        val porId = cuentas.associateBy { it.id }
        val activas = cuentas.filterNot { it.archivada }
        val dias = movimientos
            .map { it.resumir(porId) }
            .groupBy { it.movimiento.fecha }
            .map { (fecha, lista) -> DiaDeMovimientos(fecha, lista) }
        EstadoMovimientos(
            cargando = false,
            cuentasDeDinero = activas
                .filter { it.tipo == TipoCuenta.ACTIVO }
                .map { SaldoCuenta(it, saldos[it.id] ?: Dinero.CERO) },
            tarjetas = activas.filter { it.tipo == TipoCuenta.PASIVO },
            totalEnCuentas = totalEnCuentas(cuentas, saldos),
            categoriasDeGasto = ordenarPorUso(activas.filter { it.tipo == TipoCuenta.GASTO }, dias, fechaDeHoy()),
            categoriasDeIngreso = ordenarPorUso(activas.filter { it.tipo == TipoCuenta.INGRESO }, dias, fechaDeHoy()),
            dias = dias,
            archivadas = cuentas
                .filter { it.archivada && it.tipo.esDeDinero }
                .map { SaldoCuenta(it, saldos[it.id] ?: Dinero.CERO) },
            saldos = saldos,
            categoriasArchivadas = cuentas.filter { it.archivada && (it.tipo == TipoCuenta.GASTO || it.tipo == TipoCuenta.INGRESO) },
        )
    }
        // Agrupar y resumir no debe correr en el hilo de la interfaz.
        .flowOn(Dispatchers.Default)
        // Mientras la pantalla esté visible se mantiene suscrito; 5 s de gracia evitan
        // reconsultar la base en una rotación de pantalla.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMovimientos())

    // ---- Búsqueda y filtros ----

    private val _filtro = MutableStateFlow<Filtro?>(null)

    /** `null` = búsqueda cerrada. */
    val filtro: StateFlow<Filtro?> = _filtro.asStateFlow()

    /** Filtra el mismo historial que ya se leyó: ninguna consulta nueva a la base. */
    val resultado: StateFlow<ResultadoFiltro?> = combine(estado, _filtro) { e, f -> f?.let { filtrar(e.dias, it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** [inicial]: p. ej. ya filtrada por la cuenta que se tocó en "Ver movimientos". */
    fun abrirBusqueda(inicial: Filtro = Filtro()) {
        _filtro.value = inicial
    }

    /** Desde la edición de una cuenta: sus movimientos, con todo lo demás sin filtrar. */
    fun verMovimientosDe(cuentaId: CuentaId) {
        _nuevaCuenta.value = null
        abrirBusqueda(Filtro(cuentaId = cuentaId))
    }

    fun cerrarBusqueda() {
        _filtro.value = null
    }

    fun cambiarFiltro(cambio: Filtro.() -> Filtro) = _filtro.update { it?.cambio() }

    private val _captura = MutableStateFlow<Captura?>(null)
    val captura: StateFlow<Captura?> = _captura.asStateFlow()

    /** Aviso de tope de la captura abierta; `null` sin captura o lejos del tope. */
    val avisoTope: StateFlow<AvisoTope?> = combine(_captura, estado, topes) { c, e, t ->
        c?.let { avisoTope(it, t, e.dias, e.categoriasDeGasto + e.categoriasArchivadas) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val _cuentaAlRegistrar = MutableStateFlow(ultimaCuentaUsada.leer())

    /** La cuenta con la que se abre la captura: la última usada o la que se eligió en Perfil. */
    val cuentaAlRegistrar: StateFlow<CuentaId?> = _cuentaAlRegistrar.asStateFlow()

    /** Última cuenta usada, para preseleccionarla y ahorrar un toque; sobrevive a cerrar la app. */
    private var ultimaCuenta: CuentaId?
        get() = _cuentaAlRegistrar.value
        set(valor) {
            _cuentaAlRegistrar.value = valor
            ultimaCuentaUsada.guardar(valor)
        }

    fun elegirCuentaAlRegistrar(id: CuentaId) {
        ultimaCuenta = id
    }

    // ---- Archivar y reactivar desde Perfil ----

    private val _errorCuentas = MutableStateFlow(false)
    val errorCuentas: StateFlow<Boolean> = _errorCuentas.asStateFlow()

    fun consumirErrorCuentas() {
        _errorCuentas.value = false
    }

    /** Nunca deja sin efectivo ni débito activos: sin ellos no hay disponible ni dónde registrar un ingreso. */
    fun puedeArchivar(cuenta: Cuenta): Boolean =
        cuenta.tipo != TipoCuenta.ACTIVO || estado.value.cuentasDeDinero.count { it.cuenta.id != cuenta.id } > 0

    /** Archivar o reactivar conserva el historial; solo cambia si aparece y si cuenta en los totales. */
    fun cambiarArchivada(cuenta: Cuenta, archivada: Boolean) {
        if (archivada && !puedeArchivar(cuenta)) return
        _errorCuentas.value = false
        viewModelScope.launch {
            try {
                repositorio.cambiarArchivada(cuenta.id, archivada)
                if (archivada && ultimaCuenta == cuenta.id) ultimaCuenta = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorCuentas.value = true
            }
        }
    }

    // ---- Categorías propias (Perfil) ----

    private val _editorCategoria = MutableStateFlow<EditorCategoria?>(null)
    val editorCategoria: StateFlow<EditorCategoria?> = _editorCategoria.asStateFlow()

    fun abrirNuevaCategoria(tipo: TipoCuenta) {
        _editorCategoria.value = EditorCategoria(tipo)
    }

    fun abrirEditarCategoria(categoria: Cuenta) {
        _editorCategoria.value = EditorCategoria(categoria.tipo, editando = categoria)
    }

    fun cerrarEditorCategoria() {
        _editorCategoria.value = null
    }

    fun editarCategoria(cambio: EditorCategoria.() -> EditorCategoria) =
        _editorCategoria.update { it?.cambio()?.copy(error = null, errorAlGuardar = false) }

    fun guardarCategoria() {
        val actual = _editorCategoria.value ?: return
        if (actual.guardando) return
        _editorCategoria.value = actual.copy(guardando = true)
        viewModelScope.launch {
            try {
                val errores = servicio.guardarCategoria(actual.editando, actual.nombre, actual.tipo, actual.icono)
                _editorCategoria.value = if (errores.isEmpty()) null else actual.copy(error = errores.first())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editorCategoria.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    // ---- Captura de gasto o ingreso ----

    /** [monto] y [nota] llegan prellenados desde "Retirar y registrar gasto" de una meta. */
    fun abrirCaptura(monto: Dinero? = null, nota: String = "") {
        val cuentas = estado.value.cuentasDeDinero.map { it.cuenta.id } + estado.value.tarjetas.map { it.id }
        val preseleccion = ultimaCuenta?.takeIf { it in cuentas } ?: cuentas.firstOrNull()
        _captura.value = Captura(
            fecha = fechaDeHoy(),
            cuentaId = preseleccion,
            montoTexto = monto?.textoEditable().orEmpty(),
            nota = nota,
        )
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
                meses = movimiento.meses,
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
            val esTarjeta = estado.value.tarjetas.any { t -> t.id == it.cuentaId } ||
                it.archivadas.any { a -> a.id == it.cuentaId && a.tipo == TipoCuenta.PASIVO }
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

    fun elegirMeses(meses: Int?) = editarCaptura { it.copy(meses = meses) }

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
            captura.archivadas
                .filter { captura.clase == ClaseRegistro.GASTO || it.tipo != TipoCuenta.PASIVO }
                .forEach { put(it.id, it) }
        }
        return Registro.desdeCaptura(
            clase = captura.clase,
            montoTexto = captura.montoTexto,
            cuenta = captura.cuentaId?.let(porId::get),
            categoria = captura.categoriaId?.let(porId::get),
            fecha = captura.fecha,
            nota = captura.nota,
            meses = captura.meses,
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

    private val _errorAlEliminar = MutableStateFlow(false)
    val errorAlEliminar: StateFlow<Boolean> = _errorAlEliminar.asStateFlow()

    fun consumirErrorAlEliminar() {
        _errorAlEliminar.value = false
    }

    fun eliminar(id: MovimientoId) {
        viewModelScope.launch {
            try {
                repositorio.eliminar(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorAlEliminar.value = true
            }
        }
    }

    // ---- Nueva cuenta y edición de cuenta ----

    fun abrirNuevaCuenta() {
        _nuevaCuenta.value = NuevaCuenta()
    }

    fun abrirEditarCuenta(saldoCuenta: SaldoCuenta) {
        val monto = saldoCuenta.saldo.textoEditable()
        _nuevaCuenta.value = NuevaCuenta(
            nombre = saldoCuenta.cuenta.nombre,
            saldoTexto = if (saldoCuenta.saldo.esNegativo) "-$monto" else monto,
            editando = saldoCuenta,
        )
    }

    fun cerrarNuevaCuenta() {
        _nuevaCuenta.value = null
    }

    fun pedirArchivarCuenta() = _nuevaCuenta.update {
        if (it?.editando == null || it.guardando) it else it.copy(confirmandoArchivar = true, errorAlGuardar = false)
    }

    fun cancelarArchivarCuenta() = _nuevaCuenta.update { it?.copy(confirmandoArchivar = false) }

    /** Archiva la cuenta en edición: deja de aparecer y de contar, pero conserva su historial. */
    fun archivarCuenta() {
        val actual = _nuevaCuenta.value ?: return
        val cuenta = actual.editando?.cuenta ?: return
        if (actual.guardando || !actual.confirmandoArchivar || !puedeArchivar(cuenta)) return
        _nuevaCuenta.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                repositorio.cambiarArchivada(cuenta.id, archivada = true)
                if (ultimaCuenta == cuenta.id) ultimaCuenta = null
                _nuevaCuenta.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _nuevaCuenta.update { it?.copy(guardando = false, errorAlGuardar = true) }
            }
        }
    }

    fun cambiarNombreCuenta(nombre: String) =
        _nuevaCuenta.update { it?.copy(nombre = nombre.take(Cuenta.LARGO_MAXIMO_NOMBRE), errorNombre = false, errorAlGuardar = false) }

    fun cambiarSaldoCuenta(texto: String) = _nuevaCuenta.update { actual ->
        actual?.let {
            val limpio = if (it.editando != null && texto.startsWith("-")) {
                "-" + limpiarMonto(texto.removePrefix("-"))
            } else {
                limpiarMonto(texto)
            }
            it.copy(
                saldoTexto = limpio,
                saldoModificado = true,
                errorSaldo = false,
                errorAlGuardar = false,
            )
        }
    }

    fun guardarNuevaCuenta(onCuentaCreada: (CuentaId) -> Unit = {}) {
        val actual = _nuevaCuenta.value ?: return
        if (actual.guardando) return
        val editando = actual.editando
        val saldo = when {
            editando != null && !actual.saldoModificado -> editando.saldo
            actual.saldoTexto.isBlank() -> Dinero.CERO
            editando != null && actual.saldoTexto.startsWith("-") ->
                Dinero.parsear(actual.saldoTexto.removePrefix("-"))?.let { -it }
            else -> Dinero.parsear(actual.saldoTexto)
        }
        val errorNombre = actual.nombre.isBlank()
        val errorSaldo = saldo == null
        if (errorNombre || errorSaldo) {
            _nuevaCuenta.value = actual.copy(errorNombre = errorNombre, errorSaldo = errorSaldo, errorAlGuardar = false)
            return
        }
        _nuevaCuenta.value = actual.copy(guardando = true, errorAlGuardar = false)
        viewModelScope.launch {
            try {
                if (editando == null) {
                    val creada = servicio.crearCuentaDeDinero(actual.nombre, saldo, fechaDeHoy())
                    ultimaCuenta = creada.id
                    // Si el usuario abrió "+ Agregar cuenta" desde HojaCaptura, seleccionarla de inmediato.
                    _captura.update { cap ->
                        cap?.copy(cuentaId = creada.id, errores = cap.errores - ErrorRegistro.SIN_CUENTA)
                    }
                    onCuentaCreada(creada.id)
                } else {
                    servicio.editarCuentaDeDinero(
                        cuenta = editando.cuenta,
                        nuevoNombre = actual.nombre,
                        saldoActual = editando.saldo,
                        nuevoSaldo = saldo,
                        fecha = fechaDeHoy(),
                    )
                }
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
                    ultimaCuentaUsada = PreferenciaUltimaCuenta(app),
                    topes = app.contenedor.repositorioMetas.observarTopes(),
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
